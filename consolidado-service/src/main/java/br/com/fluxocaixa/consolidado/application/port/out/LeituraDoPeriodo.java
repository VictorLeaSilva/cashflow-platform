package br.com.fluxocaixa.consolidado.application.port.out;

import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Leitura de um período num único <i>snapshot</i> (Q-053).
 *
 * @param anterior soma, coluna a coluna, de todos os dias antes do início; {@link
 *     MovimentoDiario#ZERO} sem histórico
 * @param dias só os dias gravados no intervalo (o <i>zero-fill</i> é do caso de uso)
 */
public record LeituraDoPeriodo(MovimentoDiario anterior, Map<LocalDate, MovimentoGravado> dias) {

  public LeituraDoPeriodo {
    Objects.requireNonNull(anterior, "anterior");
    dias = Map.copyOf(dias);
  }
}
