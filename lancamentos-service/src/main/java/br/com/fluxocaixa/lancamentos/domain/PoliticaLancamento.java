package br.com.fluxocaixa.lancamentos.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Limites configuráveis das regras de lançamento. O binding das chaves {@code
 * lancamentos.max-valor}, {@code lancamentos.max-backdate-days} e {@code
 * lancamentos.max-future-skew} é feito na T-013; aqui a política é só um valor.
 *
 * @param valorMaximo valor máximo por lançamento (A7)
 * @param maxBackdateDays retroatividade máxima em dias (A4)
 * @param maxFutureSkew tolerância de relógio para data futura (A11)
 * @param zonaNegocio fuso usado para calcular a data de referência
 */
public record PoliticaLancamento(
    BigDecimal valorMaximo, int maxBackdateDays, Duration maxFutureSkew, ZoneId zonaNegocio) {

  public static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Sao_Paulo");

  public PoliticaLancamento {
    Objects.requireNonNull(valorMaximo, "valorMaximo");
    Objects.requireNonNull(maxFutureSkew, "maxFutureSkew");
    Objects.requireNonNull(zonaNegocio, "zonaNegocio");
    if (valorMaximo.signum() <= 0) {
      throw new IllegalArgumentException("valorMaximo deve ser positivo");
    }
    if (maxBackdateDays < 0) {
      throw new IllegalArgumentException("maxBackdateDays não pode ser negativo");
    }
    if (maxFutureSkew.isNegative()) {
      throw new IllegalArgumentException("maxFutureSkew não pode ser negativo");
    }
  }

  /** Valores padrão da constituição: A7 = 10000000.00, A4 = 30 dias, A11 = 60 s. */
  public static PoliticaLancamento padrao() {
    return new PoliticaLancamento(
        new BigDecimal("10000000.00"), 30, Duration.ofSeconds(60), ZONA_NEGOCIO);
  }
}
