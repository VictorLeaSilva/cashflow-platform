package br.com.fluxocaixa.lancamentos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.fluxocaixa.lancamentos.application.PublicarOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicadorDeEventos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

  private LacoDoPublicador laco(boolean ligado) {
    OutboxProperties propriedades =
        new OutboxProperties(
            7,
            Duration.ofHours(1),
            new OutboxProperties.Publisher(
                ligado,
                Duration.ofMillis(500),
                Duration.ofSeconds(30),
                100,
                Duration.ofSeconds(15),
                2));
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
