package br.com.fluxocaixa.lancamentos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-T010.1: Dinheiro aceita exatamente os valores com escala ≤ 2 em (0, máximo]. */
@Tag("RF-001")
@Tag("RF-002")
class DinheiroPropertiesTest {

  private static final BigDecimal MAXIMO = PoliticaLancamento.padrao().valorMaximo();

  private static final List<BigDecimal> FRONTEIRAS =
      List.of(
          new BigDecimal("0.01"),
          new BigDecimal("0.00"),
          new BigDecimal("-0.01"),
          new BigDecimal("0.001"),
          MAXIMO,
          new BigDecimal("10000000.01"),
          new BigDecimal("1E+2"),
          new BigDecimal("9999999.999"));

  @Test
  void aceitaSoValoresPositivosComAteDuasCasasAteOMaximo() {
    Propriedades.paraTodo(
        "Dinheiro.de aceita (0, max] com escala <= 2",
        FRONTEIRAS,
        DinheiroPropertiesTest::valorQualquer,
        valor -> {
          boolean valido = valor.signum() > 0 && valor.scale() <= 2 && valor.compareTo(MAXIMO) <= 0;
          if (valido) {
            Dinheiro dinheiro = Dinheiro.de(valor, MAXIMO);
            assertThat(dinheiro.valor()).isEqualByComparingTo(valor);
            assertThat(dinheiro.valor().scale()).isEqualTo(2);
          } else {
            assertThatThrownBy(() -> Dinheiro.de(valor, MAXIMO))
                .isInstanceOfSatisfying(
                    LancamentoInvalidoException.class,
                    e -> assertThat(e.erros()).extracting("campo").containsOnly("valor"));
          }
        });
  }

  /** Mistura de magnitudes, sinais e escalas de -2 a 4, cobrindo dentro e fora do domínio. */
  static BigDecimal valorQualquer(SplittableRandom aleatorio) {
    int escala = aleatorio.nextInt(-2, 5);
    long limite = aleatorio.nextBoolean() ? 1_000L : 2_000_000_000_000L;
    long naoEscalado = aleatorio.nextLong(-limite / 10, limite);
    return new BigDecimal(BigInteger.valueOf(naoEscalado), escala);
  }
}
