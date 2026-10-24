package br.com.fluxocaixa.lancamentos.config;

import br.com.fluxocaixa.lancamentos.application.PublicarOutbox;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Laço do publicador (03 §7.1, T-016): uma única thread por instância. Cada ciclo agenda o próximo
 * pela {@link PoliticaDeIntervalo} (base sem falha, imediato com lote cheio, dobro com falhas
 * seguidas até o teto). A limpeza (Q-029) roda no mesmo laço, a cada {@code cleanup-interval}, e
 * uma falha nela não impede a publicação. Os logs não levam payload, chave nem request ID dos
 * eventos (AC-T016.10).
 */
public class LacoDoPublicador implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(LacoDoPublicador.class);
  private static final Duration ESPERA_AO_PARAR = Duration.ofSeconds(5);

  private final PublicarOutbox publicar;
  private final OutboxProperties propriedades;
  private final Clock clock;
  private final PoliticaDeIntervalo politica;

  private volatile ScheduledExecutorService executor;
  private volatile Duration intervaloAtual;

  // Lidos e alterados só pela thread do laço.
  private int falhasSeguidas;
  private Instant proximaLimpeza;

  public LacoDoPublicador(PublicarOutbox publicar, OutboxProperties propriedades, Clock clock) {
    this.publicar = publicar;
    this.propriedades = propriedades;
    this.clock = clock;
    this.politica =
        new PoliticaDeIntervalo(
            propriedades.publisher().interval(), propriedades.publisher().maxBackoff());
    this.intervaloAtual = politica.base();
  }

  @Override
  public synchronized void start() {
    if (!propriedades.publisher().enabled() || executor != null) {
      return;
    }
    executor =
        Executors.newSingleThreadScheduledExecutor(
            tarefa -> {
              Thread thread = new Thread(tarefa, "publicador-outbox");
              thread.setDaemon(true);
              return thread;
            });
    agendar(executor, Duration.ZERO);
  }

  @Override
  public synchronized void stop() {
    ScheduledExecutorService atual = executor;
    executor = null;
    if (atual == null) {
      return;
    }
    atual.shutdownNow();
    try {
      atual.awaitTermination(ESPERA_AO_PARAR.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return executor != null;
  }

  /** Intervalo até o próximo ciclo, decidido pelo último ciclo (visível para testes). */
  public Duration intervaloAtual() {
    return intervaloAtual;
  }

  private void agendar(ScheduledExecutorService alvo, Duration atraso) {
    if (!alvo.isShutdown()) {
      alvo.schedule(() -> ciclo(alvo), atraso.toNanos(), TimeUnit.NANOSECONDS);
    }
  }

  private void ciclo(ScheduledExecutorService alvo) {
    try {
      limparSeDevido();
      registrar(publicar.publicarLote());
    } catch (RuntimeException e) {
      registrarFalha(0, e.getClass().getName());
    } finally {
      agendar(alvo, intervaloAtual);
    }
  }

  private void registrar(PublicarOutbox.ResultadoDoLote resultado) {
    if (resultado.falhas() > 0) {
      registrarFalha(resultado.falhas(), "envio ao broker");
      return;
    }
    if (falhasSeguidas > 0) {
      LOG.atInfo()
          .addKeyValue("ciclosComFalha", falhasSeguidas)
          .log("Publicador da outbox voltou a publicar");
    }
    falhasSeguidas = 0;
    intervaloAtual = politica.aposSucesso(resultado.cheio());
  }

  private void registrarFalha(int eventos, String causa) {
    falhasSeguidas++;
    intervaloAtual = politica.aposFalha(falhasSeguidas);
    LOG.atWarn()
        .addKeyValue("eventosComFalha", eventos)
        .addKeyValue("causa", causa)
        .addKeyValue("ciclosComFalha", falhasSeguidas)
        .addKeyValue("proximoCicloMs", intervaloAtual.toMillis())
        .log("Publicador da outbox com falha; recuando");
  }

  private void limparSeDevido() {
    Instant agora = clock.instant();
    // A primeira limpeza é no primeiro ciclo; depois, a cada cleanup-interval.
    if (proximaLimpeza != null && agora.isBefore(proximaLimpeza)) {
      return;
    }
    proximaLimpeza = agora.plus(propriedades.cleanupInterval());
    try {
      int removidas = publicar.limpar();
      LOG.atInfo()
          .addKeyValue("removidas", removidas)
          .log("Publicador da outbox limpou publicadas");
    } catch (RuntimeException e) {
      LOG.atWarn().addKeyValue("causa", e.getClass().getName()).log("Limpeza da outbox falhou");
    }
  }
}
