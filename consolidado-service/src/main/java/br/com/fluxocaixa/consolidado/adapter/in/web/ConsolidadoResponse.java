package br.com.fluxocaixa.consolidado.adapter.in.web;

import br.com.fluxocaixa.consolidado.application.ConsolidadoDoPeriodo;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** O consolidado de um período na resposta (03 §5.1): um item por dia, em ordem crescente. */
public record ConsolidadoResponse(
    UUID comercianteId,
    LocalDate dataInicio,
    LocalDate dataFim,
    String saldoAnterior,
    List<DiaResponse> dias) {

  public ConsolidadoResponse {
    dias = List.copyOf(dias);
  }

  static ConsolidadoResponse de(ConsolidadoDoPeriodo periodo) {
    return new ConsolidadoResponse(
        periodo.comercianteId(),
        periodo.dataInicio(),
        periodo.dataFim(),
        periodo.saldoAnterior().toPlainString(),
        periodo.dias().stream().map(DiaResponse::de).toList());
  }

  /** Sem valores nem comerciante. */
  @Override
  public String toString() {
    return "ConsolidadoResponse[dataInicio=" + dataInicio + ", dataFim=" + dataFim + "]";
  }
}
