package br.com.fluxocaixa.consolidado.application.port.out;

import java.time.LocalDate;
import java.util.UUID;

/** Leitura da projeção (03 §3.2, RF-005, RF-006); toda consulta filtra por comerciante (RF-010). */
public interface ConsultaConsolidadoRepository {

  /**
   * O que é preciso para o período {@code [inicio, fim]}, num único <i>snapshot</i> (Q-053): a soma
   * de tudo antes de {@code inicio} e os dias gravados no intervalo.
   */
  LeituraDoPeriodo ler(UUID comercianteId, LocalDate inicio, LocalDate fim);
}
