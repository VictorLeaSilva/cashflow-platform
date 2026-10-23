package br.com.fluxocaixa.lancamentos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Intervalo entre ciclos do publicador (03 §7.1, AC-T016.4 e AC-T016.6). */
@Tag("RNF-007")
class PoliticaDeIntervaloTest {

  private final PoliticaDeIntervalo politica =
      new PoliticaDeIntervalo(Duration.ofMillis(500), Duration.ofSeconds(30));

  @Test
  void semFalhaEsperaOIntervaloBaseELoteCheioNaoEspera() {
    assertThat(politica.aposSucesso(false)).isEqualTo(Duration.ofMillis(500));
    assertThat(politica.aposSucesso(true)).isZero();
  }

  @Test
  void falhasSeguidasDobramOIntervaloAteOTeto() {
    assertThat(politica.aposFalha(1)).isEqualTo(Duration.ofSeconds(1));
    assertThat(politica.aposFalha(2)).isEqualTo(Duration.ofSeconds(2));
    assertThat(politica.aposFalha(5)).isEqualTo(Duration.ofSeconds(16));
    assertThat(politica.aposFalha(6)).isEqualTo(Duration.ofSeconds(30));
    assertThat(politica.aposFalha(7)).isEqualTo(Duration.ofSeconds(30));
  }

  /** Sem transbordo mesmo com muitas falhas seguidas (Kafka fora por horas). */
  @Test
  void muitasFalhasFicamNoTeto() {
    assertThat(politica.aposFalha(1_000_000)).isEqualTo(Duration.ofSeconds(30));
  }
}
