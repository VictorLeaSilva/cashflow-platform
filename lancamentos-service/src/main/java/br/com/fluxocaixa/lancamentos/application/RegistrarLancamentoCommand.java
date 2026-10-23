package br.com.fluxocaixa.lancamentos.application;

import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de registro de crédito ou débito.
 *
 * @param comercianteId do claim {@code merchant_id} (A1)
 * @param chave {@code Idempotency-Key}
 * @param dataOcorrencia opcional; ausente, vale o instante do {@code Clock} (03 §4.1)
 * @param requestId {@code X-Request-Id} (03 §5.2); opcional, não entra no {@code request_hash}
 */
public record RegistrarLancamentoCommand(
    UUID comercianteId,
    String chave,
    TipoLancamento tipo,
    BigDecimal valor,
    String descricao,
    Instant dataOcorrencia,
    String identificadorExterno,
    String requestId) {

  /** Sem chave, valor, descrição nem identificador externo (AC-T012.4). */
  @Override
  public String toString() {
    return "RegistrarLancamentoCommand[tipo=" + tipo + ", requestId=" + requestId + "]";
  }
}
