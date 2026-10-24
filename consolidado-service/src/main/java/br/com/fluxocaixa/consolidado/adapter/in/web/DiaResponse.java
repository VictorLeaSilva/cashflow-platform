package br.com.fluxocaixa.consolidado.adapter.in.web;

import br.com.fluxocaixa.consolidado.application.DiaConsolidado;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Um dia na resposta (03 §5.1): valores como string com 2 casas, {@code atualizadoEm} em UTC
 * ISO-8601 ou {@code null} num dia sem movimento.
 */
public record DiaResponse(
    LocalDate data,
    String totalCreditos,
    String totalDebitos,
    String totalEstornosCredito,
    String totalEstornosDebito,
    String saldoDia,
    String saldoAcumulado,
    int qtdLancamentos,
    Instant atualizadoEm) {

  static DiaResponse de(DiaConsolidado dia) {
    MovimentoDiario movimento = dia.movimento();
    return new DiaResponse(
        dia.data(),
        movimento.totalCreditos().toPlainString(),
        movimento.totalDebitos().toPlainString(),
        movimento.totalEstornosCredito().toPlainString(),
        movimento.totalEstornosDebito().toPlainString(),
        dia.saldoDia().toPlainString(),
        dia.saldoAcumulado().toPlainString(),
        movimento.qtdLancamentos(),
        dia.atualizadoEm());
  }

  /** Sem valores: dado financeiro não vai para log. */
  @Override
  public String toString() {
    return "DiaResponse[data=" + data + "]";
  }
}
