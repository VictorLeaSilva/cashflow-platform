package br.com.fluxocaixa.lancamentos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("RF-001")
@Tag("RF-002")
class DinheiroTest {

  private static final BigDecimal MAXIMO = new BigDecimal("10000000.00");

  @ParameterizedTest
  @CsvSource({
    "100,100.00",
    "100.0,100.00",
    "100.00,100.00",
    "0.01,0.01",
    "10000000.00,10000000.00"
  })
  void aceitaValorValidoENormalizaParaDuasCasas(String entrada, String esperado) {
    Dinheiro dinheiro = Dinheiro.de(new BigDecimal(entrada), MAXIMO);

    assertThat(dinheiro.valor()).isEqualByComparingTo(esperado);
    assertThat(dinheiro.valor().scale()).isEqualTo(2);
    assertThat(dinheiro.valor().toPlainString()).isEqualTo(esperado);
  }

  @ParameterizedTest
  @CsvSource({
    "0,nao-positivo",
    "0.00,nao-positivo",
    "-1.00,nao-positivo",
    "1.005,casas-decimais",
    "100.000,casas-decimais",
    "10000000.01,acima-do-maximo"
  })
  void rejeitaValorInvalidoComErroNoCampoValor(String entrada, String codigo) {
    assertThatThrownBy(() -> Dinheiro.de(new BigDecimal(entrada), MAXIMO))
        .isInstanceOfSatisfying(
            LancamentoInvalidoException.class,
            e ->
                assertThat(e.erros())
                    .singleElement()
                    .satisfies(
                        erro -> {
                          assertThat(erro.campo()).isEqualTo("valor");
                          assertThat(erro.codigo()).isEqualTo(codigo);
                        }));
  }

  @Test
  void rejeitaValorAusente() {
    assertThatThrownBy(() -> Dinheiro.de(null, MAXIMO))
        .isInstanceOfSatisfying(
            LancamentoInvalidoException.class,
            e -> assertThat(e.erros().get(0).codigo()).isEqualTo("obrigatorio"));
  }

  @Test
  void reconstituirNaoAplicaOMaximoVigente() {
    assertThat(Dinheiro.reconstituir(new BigDecimal("20000000.00")).valor())
        .isEqualByComparingTo("20000000.00");
  }

  @Test
  void construtorExigeEscalaDoisSemExporOValorNaMensagem() {
    assertThatThrownBy(() -> new Dinheiro(new BigDecimal("1234.5")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("1234");
  }

  /** Expoente gigante é rejeitado pelo máximo antes de qualquer normalização cara de escala. */
  @Test
  void rejeitaExpoenteGiganteSemCustoDeProcessamento() {
    BigDecimal gigante = new BigDecimal("1E+100000000");

    assertTimeoutPreemptively(
        Duration.ofSeconds(2),
        () ->
            assertThatThrownBy(() -> Dinheiro.de(gigante, MAXIMO))
                .isInstanceOfSatisfying(
                    LancamentoInvalidoException.class,
                    e -> assertThat(e.erros().get(0).codigo()).isEqualTo("acima-do-maximo")));
  }

  @Test
  void toStringNaoExpoeOValor() {
    assertThat(Dinheiro.de(new BigDecimal("1234.56"), MAXIMO).toString()).doesNotContain("1234");
  }
}
