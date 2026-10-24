package br.com.fluxocaixa.consolidado.application.port.out;

import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import java.time.Instant;
import java.util.Objects;

/** Linha de {@code consolidado_diario}: o movimento do dia e quando ele foi atualizado (Q-038). */
public record MovimentoGravado(MovimentoDiario movimento, Instant atualizadoEm) {

  public MovimentoGravado {
    Objects.requireNonNull(movimento, "movimento");
    Objects.requireNonNull(atualizadoEm, "atualizadoEm");
  }
}
