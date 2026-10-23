package br.com.fluxocaixa.lancamentos.application.port.out;

import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Consulta do histórico (T-015): sempre de um comerciante (RF-010), com filtros opcionais
 * inclusivos sobre {@code dataReferencia} (Q-023) e página a partir de 0 (Q-024).
 *
 * @param dataInicio inclusivo; {@code null} = sem limite inferior
 * @param dataFim inclusivo; {@code null} = sem limite superior
 * @param tipo {@code null} = todos
 * @param page a partir de 0
 * @param size de 1 a 100 (validado na entrada HTTP)
 */
public record FiltroDeHistorico(
    UUID comercianteId,
    LocalDate dataInicio,
    LocalDate dataFim,
    TipoLancamento tipo,
    int page,
    int size) {

  public FiltroDeHistorico {
    Objects.requireNonNull(comercianteId, "comercianteId");
  }

  /** Deslocamento da página em {@code long}: {@code page * size} não transborda. */
  public long offset() {
    return (long) page * size;
  }
}
