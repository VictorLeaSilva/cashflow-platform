package br.com.fluxocaixa.consolidado.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.consolidado.domain.GeradorDeEventos;
import br.com.fluxocaixa.consolidado.domain.GeradorDeEventos.Cenario;
import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import br.com.fluxocaixa.consolidado.domain.ProjecaoDeReferencia;
import br.com.fluxocaixa.consolidado.domain.Propriedades;
import br.com.fluxocaixa.consolidado.support.LeituraEmMemoria;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-006.2 por propriedade (gerador de semente fixa da T-020, Q-009): para projeções e períodos
 * sorteados, o saldo anterior e o acumulado de cada dia batem com um cálculo independente sobre os
 * eventos (a soma com sinal de {@code GeradorDeEventos.efeitoNoSaldo}, sem passar por {@code
 * saldoDia}).
 */
@Tag("RF-006")
class ConsultarConsolidadoPropertiesTest {

  private static final Instant EM = Instant.parse("2026-10-20T13:02:11Z");

  /** Um cenário da T-020, o comerciante consultado e o período. */
  record Caso(Cenario cenario, UUID comerciante, LocalDate inicio, LocalDate fim) {}

  @Test
  void saldoAnteriorEAcumuladoBatemComOsEventos() {
    Propriedades.paraTodo(
        "saldoAnterior + Σ saldoDia até o dia = soma com sinal dos eventos até o dia",
        GeradorDeEventos.fronteiras().stream()
            .map(
                c ->
                    new Caso(
                        c,
                        new UUID(0L, 0xaaL),
                        GeradorDeEventos.DIA_BASE,
                        GeradorDeEventos.DIA_BASE.plusDays(7)))
            .toList(),
        ConsultarConsolidadoPropertiesTest::caso,
        caso -> {
          LeituraEmMemoria.Estado leitura = new LeituraEmMemoria.Estado();
          ProjecaoDeReferencia.aplicandoTodos(caso.cenario().entrega1())
              .dias()
              .forEach(
                  (chave, movimento) ->
                      leitura.gravar(chave.comercianteId(), chave.dataReferencia(), movimento, EM));

          ConsolidadoDoPeriodo periodo =
              new ConsultarConsolidado(leitura)
                  .intervalo(caso.comerciante(), caso.inicio(), caso.fim());

          List<LancamentoRegistrado> doComerciante =
              caso.cenario().distintos().stream()
                  .filter(e -> e.comercianteId().equals(caso.comerciante()))
                  .toList();
          assertThat(periodo.saldoAnterior())
              .isEqualByComparingTo(
                  soma(doComerciante, e -> e.dataReferencia().isBefore(caso.inicio())));
          assertThat(periodo.dias())
              .hasSize((int) (caso.fim().toEpochDay() - caso.inicio().toEpochDay() + 1));
          for (DiaConsolidado dia : periodo.dias()) {
            assertThat(dia.saldoAcumulado())
                .as("acumulado de %s", dia.data())
                .isEqualByComparingTo(
                    soma(doComerciante, e -> !e.dataReferencia().isAfter(dia.data())));
          }
          BigDecimal somaDoIntervalo =
              periodo.dias().stream()
                  .map(DiaConsolidado::saldoDia)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          assertThat(periodo.dias().getLast().saldoAcumulado())
              .isEqualByComparingTo(periodo.saldoAnterior().add(somaDoIntervalo));
        });
  }

  private static Caso caso(SplittableRandom aleatorio) {
    Cenario cenario = GeradorDeEventos.cenario(aleatorio);
    UUID comerciante =
        cenario.distintos().isEmpty()
            ? GeradorDeEventos.uuid(aleatorio)
            : cenario
                .distintos()
                .get(aleatorio.nextInt(cenario.distintos().size()))
                .comercianteId();
    LocalDate inicio = GeradorDeEventos.DIA_BASE.plusDays(aleatorio.nextInt(-3, 10));
    return new Caso(cenario, comerciante, inicio, inicio.plusDays(aleatorio.nextInt(0, 15)));
  }

  private static BigDecimal soma(
      List<LancamentoRegistrado> eventos, Predicate<LancamentoRegistrado> filtro) {
    return eventos.stream()
        .filter(filtro)
        .map(GeradorDeEventos::efeitoNoSaldo)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
