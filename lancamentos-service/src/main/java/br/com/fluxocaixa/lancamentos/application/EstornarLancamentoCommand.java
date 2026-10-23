package br.com.fluxocaixa.lancamentos.application;

import java.util.UUID;

/**
 * Pedido de estorno total de um lançamento (RF-003, A5).
 *
 * @param comercianteId do claim {@code merchant_id} (A1)
 * @param chave {@code Idempotency-Key}
 * @param lancamentoOriginalId id do lançamento a estornar (caminho da requisição)
 * @param requestId {@code X-Request-Id} (03 §5.2); opcional, não entra no {@code request_hash}
 */
public record EstornarLancamentoCommand(
    UUID comercianteId, String chave, UUID lancamentoOriginalId, String requestId) {

  /** Sem a chave (AC-T012.4). */
  @Override
  public String toString() {
    return "EstornarLancamentoCommand[lancamentoOriginalId="
        + lancamentoOriginalId
        + ", requestId="
        + requestId
        + "]";
  }
}
