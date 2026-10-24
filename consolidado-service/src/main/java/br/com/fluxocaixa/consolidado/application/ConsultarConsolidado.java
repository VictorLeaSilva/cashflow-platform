package br.com.fluxocaixa.consolidado.application;

import br.com.fluxocaixa.consolidado.application.port.out.ConsultaConsolidadoRepository;
import br.com.fluxocaixa.consolidado.application.port.out.LeituraDoPeriodo;
import br.com.fluxocaixa.consolidado.application.port.out.MovimentoGravado;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Consulta do consolidado (RF-005, RF-006; 03 §3.2): o período com <i>zero-fill</i>, em ordem
 * crescente, e o saldo acumulado calculado aqui ({@code saldoAnterior + Σ saldoDia}). A fórmula do
 * {@code saldoDia} é só a do domínio (Q-035); a leitura vem de um único <i>snapshot</i> (Q-053).
 */
public final class ConsultarConsolidado {

  /** AC-005.3: no máximo 366 dias, contando os dois extremos (Q-052). */
  public static final int MAXIMO_DE_DIAS = 366;

  private final ConsultaConsolidadoRepository leitura;

  public ConsultarConsolidado(ConsultaConsolidadoRepository leitura) {
    this.leitura = Objects.requireNonNull(leitura, "leitura");
  }

  /**
   * @throws IllegalArgumentException se {@code inicio > fim} ou o período passar de {@value
   *     #MAXIMO_DE_DIAS} dias (a entrada HTTP já rejeita os dois com 400)
   */
  public ConsolidadoDoPeriodo intervalo(UUID comercianteId, LocalDate inicio, LocalDate fim) {
    Objects.requireNonNull(comercianteId, "comercianteId");
    long dias = diasNoPeriodo(inicio, fim);
    if (dias < 1 || dias > MAXIMO_DE_DIAS) {
      throw new IllegalArgumentException("período de 1 a " + MAXIMO_DE_DIAS + " dias");
    }
    LeituraDoPeriodo lido = leitura.ler(comercianteId, inicio, fim);
    BigDecimal saldoAnterior = lido.anterior().saldoDia();
    List<DiaConsolidado> consolidados = new ArrayList<>();
    BigDecimal acumulado = saldoAnterior;
    for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {
      DiaConsolidado dia = dia(data, lido.dias().get(data), acumulado);
      consolidados.add(dia);
      acumulado = dia.saldoAcumulado();
    }
    return new ConsolidadoDoPeriodo(comercianteId, inicio, fim, saldoAnterior, consolidados);
  }

  /** O dia, com o saldo acumulado desde o início (AC-005.4). */
  public DiaConsolidado dia(UUID comercianteId, LocalDate data) {
    return intervalo(comercianteId, data, data).dias().getFirst();
  }

  /** Um dia, gravado ou não (zero-fill: movimento zero e {@code atualizadoEm} nulo). */
  private static DiaConsolidado dia(
      LocalDate data, MovimentoGravado gravado, BigDecimal acumuladoAnterior) {
    MovimentoDiario movimento = gravado == null ? MovimentoDiario.ZERO : gravado.movimento();
    BigDecimal saldoDia = movimento.saldoDia();
    return new DiaConsolidado(
        data,
        movimento,
        saldoDia,
        acumuladoAnterior.add(saldoDia),
        gravado == null ? null : gravado.atualizadoEm());
  }

  private static long diasNoPeriodo(LocalDate inicio, LocalDate fim) {
    Objects.requireNonNull(inicio, "inicio");
    Objects.requireNonNull(fim, "fim");
    return fim.toEpochDay() - inicio.toEpochDay() + 1;
  }
}
