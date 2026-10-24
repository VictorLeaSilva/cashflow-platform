package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import br.com.fluxocaixa.lancamentos.application.PublicarOutbox;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicadorDeEventos;
import br.com.fluxocaixa.lancamentos.support.IntegracaoKafka;
import br.com.fluxocaixa.lancamentos.support.KafkaLancamentos.Mensagem;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * AC-T016.5 (SKIP LOCKED): o primeiro ciclo fica retido dentro da transação, com o lote travado,
 * enquanto o segundo ciclo roda inteiro. Se o segundo esperasse pelo lock do primeiro, o teste não
 * terminaria. Cada evento é publicado uma única vez.
 */
@Tag("RNF-004")
class PublicadorConcorrenteIT extends IntegracaoKafka {

  private static final int EVENTOS = 150;

  @MockitoSpyBean private PublicadorDeEventos publicador;
  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private PublicarOutbox publicarOutbox;

  @AfterEach
  void restaurar() {
    reset(publicador);
  }

  @Test
  void doisCiclosSimultaneosPublicamCadaEventoUmaVez() throws Exception {
    Set<UUID> criados = new HashSet<>();
    UUID comerciante = UUID.randomUUID();
    for (int i = 0; i < EVENTOS; i++) {
      criados.add(Eventos.registrar(registrarLancamento, jdbc, comerciante, null));
    }
    CountDownLatch primeiroTravou = new CountDownLatch(1);
    CountDownLatch segundoTerminou = new CountDownLatch(1);
    AtomicInteger chamadas = new AtomicInteger();
    doAnswer(
            invocacao -> {
              if (chamadas.incrementAndGet() == 1) {
                primeiroTravou.countDown();
                assertThat(segundoTerminou.await(30, TimeUnit.SECONDS))
                    .as("o segundo ciclo terminou sem esperar o lock do primeiro")
                    .isTrue();
              }
              return invocacao.callRealMethod();
            })
        .when(publicador)
        .publicar(any());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<PublicarOutbox.ResultadoDoLote> primeiro =
          CompletableFuture.supplyAsync(publicarOutbox::publicarLote, executor);
      assertThat(primeiroTravou.await(30, TimeUnit.SECONDS)).isTrue();
      PublicarOutbox.ResultadoDoLote segundo =
          CompletableFuture.supplyAsync(publicarOutbox::publicarLote, executor)
              .get(30, TimeUnit.SECONDS);
      segundoTerminou.countDown();

      assertThat(segundo.publicados()).isEqualTo(EVENTOS - 100);
      assertThat(primeiro.get(30, TimeUnit.SECONDS).publicados()).isEqualTo(100);
    } finally {
      segundoTerminou.countDown();
      executor.shutdownNow();
    }

    List<Mensagem> lidas = Eventos.lerAte(criados, Duration.ofSeconds(60));
    Map<UUID, Long> vezes =
        lidas.stream().collect(Collectors.groupingBy(Eventos::eventId, Collectors.counting()));
    assertThat(vezes.keySet()).isEqualTo(criados);
    assertThat(vezes.values()).allMatch(v -> v == 1);
    assertThat(Eventos.pendentes(jdbc)).isZero();
  }
}
