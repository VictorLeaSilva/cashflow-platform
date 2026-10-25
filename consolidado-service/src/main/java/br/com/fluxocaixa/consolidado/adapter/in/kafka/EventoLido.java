package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import java.time.Instant;
import java.util.Objects;

/**
 * Evento já validado: o valor de domínio, o {@code occurredAt}, que só serve à métrica de
 * <i>lag</i>, e o {@code traceId} de origem (opcional, Q-061), que só serve ao log. Os dois ficam
 * fora do domínio (Q-034).
 *
 * @param traceId {@code traceId} da requisição que originou o evento; {@code null} se o evento não
 *     o trouxer
 */
public record EventoLido(LancamentoRegistrado evento, Instant occurredAt, String traceId) {

  public EventoLido {
    Objects.requireNonNull(evento, "evento");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
