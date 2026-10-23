package br.com.fluxocaixa.lancamentos.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento pendente travado pelo publicador (T-016).
 *
 * @param payload o JSON gravado em {@code outbox_event.payload}, enviado sem alteração
 * @param requestId {@code X-Request-Id} de origem; vira o cabeçalho Kafka {@code x-request-id}
 *     quando não é nulo (03 §5.2)
 */
public record OutboxPendente(
    UUID eventId,
    String topic,
    String messageKey,
    String payload,
    String requestId,
    Instant createdAt) {

  /** Sem payload, chave nem request ID (AC-T016.10). */
  @Override
  public String toString() {
    return "OutboxPendente[eventId=" + eventId + ", topic=" + topic + "]";
  }
}
