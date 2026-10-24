package br.com.fluxocaixa.consolidado.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Movimento de um comerciante num dia ({@code 03 §3.2}): os quatro totais (escala 2, nunca
 * negativos) e a quantidade de lançamentos. {@link #somar} é comutativa e associativa, com {@link
 * #ZERO} como neutro; é isso que torna a projeção indiferente à ordem dos eventos (D-06, ADR-005).
 */
public record MovimentoDiario(
    BigDecimal totalCreditos,
    BigDecimal totalDebitos,
    BigDecimal totalEstornosCredito,
    BigDecimal totalEstornosDebito,
    int qtdLancamentos) {

  static final int ESCALA = 2;

  public static final MovimentoDiario ZERO =
      new MovimentoDiario(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);

  /**
   * @throws IllegalArgumentException se algum total for negativo ou tiver mais de duas casas, ou se
   *     a quantidade for negativa
   */
  public MovimentoDiario {
    totalCreditos = total(totalCreditos, "totalCreditos");
    totalDebitos = total(totalDebitos, "totalDebitos");
    totalEstornosCredito = total(totalEstornosCredito, "totalEstornosCredito");
    totalEstornosDebito = total(totalEstornosDebito, "totalEstornosDebito");
    if (qtdLancamentos < 0) {
      throw new IllegalArgumentException("qtdLancamentos não pode ser negativa");
    }
  }

  /**
   * Soma campo a campo.
   *
   * @throws ArithmeticException se a quantidade estourar {@code int} (a coluna é {@code INTEGER})
   */
  public MovimentoDiario somar(MovimentoDiario outro) {
    Objects.requireNonNull(outro, "outro");
    return new MovimentoDiario(
        totalCreditos.add(outro.totalCreditos),
        totalDebitos.add(outro.totalDebitos),
        totalEstornosCredito.add(outro.totalEstornosCredito),
        totalEstornosDebito.add(outro.totalEstornosDebito),
        Math.addExact(qtdLancamentos, outro.qtdLancamentos));
  }

  /**
   * AC-005.2: {@code (créditos − estornosCrédito) − (débitos − estornosDébito)}. Pode ser negativo
   * (A6).
   */
  public BigDecimal saldoDia() {
    return totalCreditos
        .subtract(totalEstornosCredito)
        .subtract(totalDebitos.subtract(totalEstornosDebito));
  }

  private static BigDecimal total(BigDecimal valor, String campo) {
    Objects.requireNonNull(valor, campo);
    if (valor.signum() < 0 || valor.scale() > ESCALA) {
      throw new IllegalArgumentException(campo + " exige valor não negativo com até 2 casas");
    }
    return valor.setScale(ESCALA);
  }
}
