package br.com.fluxocaixa.lancamentos.config;

import java.time.Duration;

/**
 * Intervalo entre ciclos do publicador (03 §7.1): {@code base} sem falha; zero com lote cheio, para
 * drenar o acúmulo; e, com falhas seguidas, o dobro a cada ciclo até {@code maximo}.
 */
public record PoliticaDeIntervalo(Duration base, Duration maximo) {

  /** Acima disso, o deslocamento já passa de qualquer teto razoável (2^20 × base). */
  private static final int EXPOENTE_MAXIMO = 20;

  public Duration aposSucesso(boolean loteCheio) {
    return loteCheio ? Duration.ZERO : base;
  }

  public Duration aposFalha(int falhasSeguidas) {
    int expoente = Math.min(Math.max(falhasSeguidas, 0), EXPOENTE_MAXIMO);
    Duration dobrado = base.multipliedBy(1L << expoente);
    return dobrado.compareTo(maximo) > 0 ? maximo : dobrado;
  }
}
