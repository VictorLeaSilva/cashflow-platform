package br.com.fluxocaixa.lancamentos.domain;

import java.io.Serial;
import java.util.Objects;

/** Regra de negócio violada, identificada por código estável (vira 422 na T-013). */
public final class RegraDeNegocioException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public static final String DATA_FUTURA = "data-futura";
  public static final String DATA_RETROATIVA_EXCEDIDA = "data-retroativa-excedida";
  public static final String ESTORNO_DE_ESTORNO = "estorno-de-estorno";

  private final String codigo;

  public RegraDeNegocioException(String codigo, String mensagem) {
    super(mensagem);
    this.codigo = Objects.requireNonNull(codigo, "codigo");
  }

  public String codigo() {
    return codigo;
  }
}
