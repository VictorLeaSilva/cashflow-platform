package br.com.fluxocaixa.consolidado.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-005.2 (parte de domínio, Q-035) e invariantes do movimento diário. */
@Tag("RF-005")
@Tag("RF-008")
class MovimentoDiarioTest {

  @Test
  void zeroTemTodosOsTotaisZeradosComEscala2() {
    assertThat(MovimentoDiario.ZERO).isEqualTo(movimento("0", "0", "0", "0", 0));
    assertThat(MovimentoDiario.ZERO.totalCreditos().scale()).isEqualTo(2);
    assertThat(MovimentoDiario.ZERO.saldoDia()).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void saldoDiaSegueAFormulaDoAc0052() {
    MovimentoDiario dia = movimento("1000.00", "300.00", "100.00", "50.00", 4);

    assertThat(dia.saldoDia()).isEqualTo(new BigDecimal("650.00"));
  }

  @Test
  void estornoDeCreditoReduzOSaldoEEstornoDeDebitoAumenta() {
    assertThat(movimento("0", "0", "100.00", "0", 1).saldoDia())
        .isEqualTo(new BigDecimal("-100.00"));
    assertThat(movimento("0", "0", "0", "100.00", 1).saldoDia())
        .isEqualTo(new BigDecimal("100.00"));
  }

  @Test
  void saldoNegativoEPermitido() {
    assertThat(movimento("10.00", "25.50", "0", "0", 2).saldoDia())
        .isEqualTo(new BigDecimal("-15.50"));
  }

  @Test
  void somarSomaCampoACampo() {
    MovimentoDiario soma =
        movimento("1.00", "2.00", "3.00", "4.00", 1)
            .somar(movimento("10.00", "20.00", "30.00", "40.00", 2));

    assertThat(soma).isEqualTo(movimento("11.00", "22.00", "33.00", "44.00", 3));
  }

  @Test
  void normalizaTotaisParaEscala2() {
    assertThat(movimento("5", "5.0", "0", "0", 0).totalCreditos())
        .isEqualTo(new BigDecimal("5.00"));
  }

  @Test
  void rejeitaTotalNegativoComMaisDeDuasCasasOuNulo() {
    assertThatThrownBy(() -> movimento("-0.01", "0", "0", "0", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> movimento("0", "0.001", "0", "0", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new MovimentoDiario(BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO, 0))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejeitaQuantidadeNegativa() {
    assertThatThrownBy(() -> movimento("0", "0", "0", "0", -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void quantidadeNaoEstouraEmSilencio() {
    MovimentoDiario cheio = movimento("0", "0", "0", "0", Integer.MAX_VALUE);

    assertThatThrownBy(() -> cheio.somar(movimento("0", "0", "0", "0", 1)))
        .isInstanceOf(ArithmeticException.class);
  }

  static MovimentoDiario movimento(
      String creditos, String debitos, String estornosCredito, String estornosDebito, int qtd) {
    return new MovimentoDiario(
        new BigDecimal(creditos),
        new BigDecimal(debitos),
        new BigDecimal(estornosCredito),
        new BigDecimal(estornosDebito),
        qtd);
  }
}
