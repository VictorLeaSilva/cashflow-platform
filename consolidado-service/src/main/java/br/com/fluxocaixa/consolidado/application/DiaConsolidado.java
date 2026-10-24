package br.com.fluxocaixa.consolidado.application;

import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Um dia da consulta (03 §5.1): o movimento, o {@code saldoDia} do domínio, o saldo acumulado até o
 * dia e quando a linha foi atualizada.
 *
 * @param atualizadoEm {@code null} num dia sem movimento (zero-fill)
 */
public record DiaConsolidado(
    LocalDate data,
    MovimentoDiario movimento,
    BigDecimal saldoDia,
    BigDecimal saldoAcumulado,
    Instant atualizadoEm) {

  public DiaConsolidado {
    Objects.requireNonNull(data, "data");
    Objects.requireNonNull(movimento, "movimento");
    Objects.requireNonNull(saldoDia, "saldoDia");
    Objects.requireNonNull(saldoAcumulado, "saldoAcumulado");
  }
}
