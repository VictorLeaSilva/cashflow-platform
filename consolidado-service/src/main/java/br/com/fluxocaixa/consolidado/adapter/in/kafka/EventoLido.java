package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import java.time.Instant;
import java.util.Objects;

/**
 * Evento já validado: o valor de domínio e o {@code occurredAt}, que só serve à métrica de
 * <i>lag</i> e por isso fica fora do domínio (Q-034).
 */
public record EventoLido(LancamentoRegistrado evento, Instant occurredAt) {

  public EventoLido {
    Objects.requireNonNull(evento, "evento");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
