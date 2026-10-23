package br.com.fluxocaixa.lancamentos.config;

import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limites das regras de lançamento (A4, A7, A11; Q-007). O fuso de negócio não é configurável.
 *
 * @param maxValor {@code lancamentos.max-valor}
 * @param maxBackdateDays {@code lancamentos.max-backdate-days}
 * @param maxFutureSkew {@code lancamentos.max-future-skew}
 */
@ConfigurationProperties("lancamentos")
public record PoliticaLancamentoProperties(
    @DefaultValue("10000000.00") BigDecimal maxValor,
    @DefaultValue("30") int maxBackdateDays,
    @DefaultValue("60s") Duration maxFutureSkew) {

  PoliticaLancamento paraPolitica() {
    return new PoliticaLancamento(
        maxValor, maxBackdateDays, maxFutureSkew, PoliticaLancamento.ZONA_NEGOCIO);
  }
}
