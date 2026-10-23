package br.com.fluxocaixa.lancamentos.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Valor monetário positivo em BRL com exatamente duas casas (P3). Entrada com mais de duas casas é
 * rejeitada, nunca arredondada; {@code 100} e {@code 100.0} são normalizados para {@code 100.00}.
 */
public record Dinheiro(BigDecimal valor) {

  static final String CAMPO = "valor";
  static final int ESCALA = 2;

  public Dinheiro {
    Objects.requireNonNull(valor, "valor");
    if (valor.signum() <= 0 || valor.scale() != ESCALA) {
      throw new IllegalArgumentException("Dinheiro exige valor positivo com escala 2: " + valor);
    }
  }

  /**
   * Cria um valor de lançamento novo, aplicando o limite da política.
   *
   * @throws LancamentoInvalidoException com o campo {@code valor} se ausente, não positivo, com
   *     mais de duas casas ou acima do máximo
   */
  public static Dinheiro de(BigDecimal valor, BigDecimal valorMaximo) {
    Objects.requireNonNull(valorMaximo, "valorMaximo");
    Dinheiro dinheiro = validarEstrutura(valor);
    if (dinheiro.valor.compareTo(valorMaximo) > 0) {
      throw LancamentoInvalidoException.campo(
          CAMPO, "acima-do-maximo", "valor acima do máximo permitido de " + valorMaximo);
    }
    return dinheiro;
  }

  /** Reidrata um valor já persistido: valida positivo e casas, sem aplicar o máximo vigente. */
  public static Dinheiro reconstituir(BigDecimal valor) {
    return validarEstrutura(valor);
  }

  private static Dinheiro validarEstrutura(BigDecimal valor) {
    if (valor == null) {
      throw LancamentoInvalidoException.campo(CAMPO, "obrigatorio", "valor é obrigatório");
    }
    if (valor.signum() <= 0) {
      throw LancamentoInvalidoException.campo(CAMPO, "nao-positivo", "valor deve ser maior que 0");
    }
    if (valor.scale() > ESCALA) {
      throw LancamentoInvalidoException.campo(
          CAMPO, "casas-decimais", "valor deve ter no máximo 2 casas decimais");
    }
    return new Dinheiro(valor.setScale(ESCALA));
  }
}
