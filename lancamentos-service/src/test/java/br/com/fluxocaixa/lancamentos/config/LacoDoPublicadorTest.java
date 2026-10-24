package br.com.fluxocaixa.lancamentos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.fluxocaixa.lancamentos.application.PublicarOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxPendente;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicadorDeEventos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** AC-T016.9: com o publicador desligado, o laço não roda nem toca nas portas. */
@Tag("RNF-007")
class LacoDoPublicadorTest {

  private final OutboxRepository outbox = mock(OutboxRepository.class);
  private final PublicadorDeEventos publicador = mock(PublicadorDeEventos.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneOffset.UTC);

  @Test
  void desligadoNaoIniciaNemConsultaAOutbox() {
    LacoDoPublicador laco = laco(false);

    laco.start();

    assertThat(laco.isRunning()).isFalse();
    assertThat(laco.intervaloAtual()).isEqualTo(Duration.ofMillis(500));
    laco.stop();
    verifyNoInteractions(outbox, publicador);
  }

  @Test
  void ligadoIniciaEParaSemFicarRodando() {
    LacoDoPublicador laco = laco(true);

    laco.start();
    assertThat(laco.isRunning()).isTrue();
    laco.stop();

    assertThat(laco.isRunning()).isFalse();
  }

  /**
   * AC-T016.6: com o intervalo de 10 minutos, um acúmulo de 250 (lotes de 100) só é drenado em
   * segundos se os ciclos com lote cheio não esperarem o intervalo; o ciclo seguinte ao lote
   * parcial volta a esperar.
   */
  @Test
  void loteCheioDispensaAEsperaAteDrenarOAcumulo() {
    when(outbox.travarPendentes(100))
        .thenReturn(pendentes(100), pendentes(100), pendentes(50), List.of());
    when(publicador.publicar(any())).thenReturn(Map.of());
    LacoDoPublicador laco = laco(true, Duration.ofMinutes(10));

    laco.start();
    try {
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> verify(outbox, times(3)).travarPendentes(100));
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> laco.intervaloAtual().equals(Duration.ofMinutes(10)));
      verify(outbox, times(3)).travarPendentes(100);
    } finally {
      laco.stop();
    }
  }

  private static List<OutboxPendente> pendentes(int quantidade) {
    return Stream.generate(
            () ->
                new OutboxPendente(
                    UUID.randomUUID(),
                    "cashflow.lancamentos.v1",
                    "chave",
                    "{}",
                    null,
                    Instant.EPOCH))
        .limit(quantidade)
        .toList();
  }

  private LacoDoPublicador laco(boolean ligado) {
    return laco(ligado, Duration.ofMillis(500));
  }

  private LacoDoPublicador laco(boolean ligado, Duration intervalo) {
    OutboxProperties propriedades =
        new OutboxProperties(
            7,
            Duration.ofHours(1),
            new OutboxProperties.Publisher(
                ligado, intervalo, Duration.ofMinutes(30), 100, Duration.ofSeconds(15), 2));
    PublicarOutbox publicar =
        new PublicarOutbox(
            outbox,
            publicador,
            TransactionOperations.withoutTransaction(),
            clock,
            100,
            Duration.ofDays(7));
    return new LacoDoPublicador(publicar, propriedades, clock);
  }
}
