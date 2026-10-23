package br.com.fluxocaixa.lancamentos.adapter.in.web;

import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lançamento na resposta (03 §5.1): {@code valor} como string com 2 casas, instantes ISO-8601 em
 * UTC e campos nulos presentes como {@code null}; {@code tipoOriginal} só no estorno (Q-020).
 * Reconstruído do lançamento imutável, então o replay devolve o mesmo corpo (D-08, AC-007.2).
 */
public record LancamentoResponse(
    UUID id,
    UUID comercianteId,
    String tipo,
    String valor,
    String descricao,
    Instant dataOcorrencia,
    LocalDate dataReferencia,
    UUID referenciaLancamentoId,
    String tipoOriginal,
    String identificadorExterno,
    Instant criadoEm) {

  static LancamentoResponse de(Lancamento lancamento) {
    return new LancamentoResponse(
        lancamento.id(),
        lancamento.comercianteId(),
        lancamento.tipo().name(),
        lancamento.valor().valor().toPlainString(),
        lancamento.descricao(),
        lancamento.dataOcorrencia(),
        lancamento.dataReferencia(),
        lancamento.referenciaLancamentoId(),
        lancamento.tipoOriginal() != null ? lancamento.tipoOriginal().name() : null,
        lancamento.identificadorExterno(),
        lancamento.criadoEm());
  }

  /** Sem valor, descrição nem identificador externo. */
  @Override
  public String toString() {
    return "LancamentoResponse[id=" + id + ", tipo=" + tipo + "]";
  }
}
