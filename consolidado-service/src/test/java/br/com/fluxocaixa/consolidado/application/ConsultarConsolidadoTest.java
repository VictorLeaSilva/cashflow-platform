package br.com.fluxocaixa.consolidado.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import br.com.fluxocaixa.consolidado.support.LeituraEmMemoria;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** RF-005 e RF-006 no caso de uso, sobre a porta em memória: zero-fill e saldos (03 §3.2). */
@Tag("RF-005")
@Tag("RF-006")
class ConsultarConsolidadoTest {

  private static final UUID COMERCIANTE = new UUID(0L, 0xaaL);
  private static final Instant EM = Instant.parse("2026-10-20T13:02:11.004001Z");

  private final LeituraEmMemoria.Estado leitura = new LeituraEmMemoria.Estado();
  private final ConsultarConsolidado consultar = new ConsultarConsolidado(leitura);

  @Test
  void periodoComZeroFillEmOrdemESaldoAcumulado() {
    gravar("2026-10-10", "500.00", "0", "0", "0");
    gravar("2026-10-20", "0", "250.00", "100.00", "0");
    gravar("2026-10-22", "9.99", "0", "0", "0");

    ConsolidadoDoPeriodo periodo =
        consultar.intervalo(COMERCIANTE, data("2026-10-19"), data("2026-10-21"));

    assertThat(periodo.comercianteId()).isEqualTo(COMERCIANTE);
    assertThat(periodo.saldoAnterior()).isEqualTo(new BigDecimal("500.00"));
    assertThat(periodo.dias())
        .extracting(DiaConsolidado::data)
        .containsExactly(data("2026-10-19"), data("2026-10-20"), data("2026-10-21"));
    DiaConsolidado vazio = periodo.dias().get(0);
    assertThat(vazio.movimento()).isEqualTo(MovimentoDiario.ZERO);
    assertThat(vazio.saldoDia()).isEqualTo(new BigDecimal("0.00"));
    assertThat(vazio.saldoAcumulado()).isEqualTo(new BigDecimal("500.00"));
    assertThat(vazio.atualizadoEm()).isNull();
    DiaConsolidado comMovimento = periodo.dias().get(1);
    assertThat(comMovimento.saldoDia()).isEqualTo(new BigDecimal("-350.00"));
    assertThat(comMovimento.saldoAcumulado()).isEqualTo(new BigDecimal("150.00"));
    assertThat(comMovimento.atualizadoEm()).isEqualTo(EM);
    assertThat(periodo.dias().get(2).saldoAcumulado()).isEqualTo(new BigDecimal("150.00"));
  }

  @Test
  void diaTrazOAcumuladoDesdeOInicio() {
    gravar("2026-10-10", "500.00", "0", "0", "0");
    gravar("2026-10-20", "0", "250.00", "100.00", "0");

    DiaConsolidado dia = consultar.dia(COMERCIANTE, data("2026-10-20"));

    assertThat(dia.saldoDia()).isEqualTo(new BigDecimal("-350.00"));
    assertThat(dia.saldoAcumulado()).isEqualTo(new BigDecimal("150.00"));
    assertThat(consultar.dia(COMERCIANTE, data("2026-10-11")).saldoAcumulado())
        .isEqualTo(new BigDecimal("500.00"));
  }

  @Test
  void outroComercianteNaoVeNada() {
    gravar("2026-10-20", "100.00", "0", "0", "0");

    ConsolidadoDoPeriodo periodo =
        consultar.intervalo(new UUID(0L, 0xbbL), data("2026-10-20"), data("2026-10-20"));

    assertThat(periodo.saldoAnterior()).isEqualTo(new BigDecimal("0.00"));
    assertThat(periodo.dias().getFirst().movimento()).isEqualTo(MovimentoDiario.ZERO);
  }

  @Test
  void limitesDoPeriodoSaoConferidosTambemNoCasoDeUso() {
    assertThat(consultar.intervalo(COMERCIANTE, data("2024-01-01"), data("2024-12-31")).dias())
        .hasSize(ConsultarConsolidado.MAXIMO_DE_DIAS);
    assertThatThrownBy(
            () -> consultar.intervalo(COMERCIANTE, data("2025-01-01"), data("2026-01-02")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> consultar.intervalo(COMERCIANTE, data("2026-10-02"), data("2026-10-01")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void gravar(
      String dia, String creditos, String debitos, String estornosCredito, String estornosDebito) {
    leitura.gravar(
        COMERCIANTE,
        data(dia),
        new MovimentoDiario(
            new BigDecimal(creditos),
            new BigDecimal(debitos),
            new BigDecimal(estornosCredito),
            new BigDecimal(estornosDebito),
            1),
        EM);
  }

  private static LocalDate data(String texto) {
    return LocalDate.parse(texto);
  }
}
