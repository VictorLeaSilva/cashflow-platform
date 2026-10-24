package br.com.fluxocaixa.consolidado.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O consolidado de um período (03 §5.1): um item por dia, em ordem crescente, e o saldo anterior.
 */
public record ConsolidadoDoPeriodo(
    UUID comercianteId,
    LocalDate dataInicio,
    LocalDate dataFim,
    BigDecimal saldoAnterior,
    List<DiaConsolidado> dias) {

  public ConsolidadoDoPeriodo {
    Objects.requireNonNull(comercianteId, "comercianteId");
    Objects.requireNonNull(dataInicio, "dataInicio");
    Objects.requireNonNull(dataFim, "dataFim");
    Objects.requireNonNull(saldoAnterior, "saldoAnterior");
    dias = List.copyOf(dias);
  }
}
