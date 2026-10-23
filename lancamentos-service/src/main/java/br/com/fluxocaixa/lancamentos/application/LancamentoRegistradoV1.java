package br.com.fluxocaixa.lancamentos.application;

import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento {@code LancamentoRegistrado.v1} (03 §4). {@code traceId} fica de fora até a T-032; {@code
 * tipoOriginal} e {@code referenciaLancamentoId} só existem em estornos. Sem descrição nem dados
 * pessoais.
 */
public record LancamentoRegistradoV1(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    UUID comercianteId,
    UUID lancamentoId,
    TipoLancamento tipo,
    BigDecimal valor,
    LocalDate dataReferencia,
    TipoLancamento tipoOriginal,
    UUID referenciaLancamentoId) {

  public static final String TOPICO = "cashflow.lancamentos.v1";
  public static final String EVENT_TYPE = "LancamentoRegistrado";
  public static final int SCHEMA_VERSION = 1;

  /** Evento do lançamento recém-criado; {@code occurredAt} é o instante do {@code Clock}. */
  public static LancamentoRegistradoV1 de(Lancamento lancamento, UUID eventId, Instant occurredAt) {
    return new LancamentoRegistradoV1(
        eventId,
        EVENT_TYPE,
        SCHEMA_VERSION,
        occurredAt,
        lancamento.comercianteId(),
        lancamento.id(),
        lancamento.tipo(),
        lancamento.valor().valor(),
        lancamento.dataReferencia(),
        lancamento.tipoOriginal(),
        lancamento.referenciaLancamentoId());
  }

  /** Sem valor (sem payload financeiro em log). */
  @Override
  public String toString() {
    return "LancamentoRegistradoV1[eventId=" + eventId + ", lancamentoId=" + lancamentoId + "]";
  }
}
