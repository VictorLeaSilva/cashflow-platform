package br.com.fluxocaixa.lancamentos.application.port.out;

import br.com.fluxocaixa.lancamentos.application.LancamentoRegistradoV1;
import java.util.UUID;

/**
 * Linha de {@code outbox_event} (03 §3.1): metadados de roteamento e o evento a serializar.
 *
 * @param requestId {@code X-Request-Id} de origem (03 §5.2); pode ser nulo
 */
public record EventoOutbox(
    UUID eventId,
    UUID aggregateId,
    String topic,
    String messageKey,
    String eventType,
    int schemaVersion,
    LancamentoRegistradoV1 payload,
    String requestId) {}
