package br.com.fluxocaixa.lancamentos.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Dados de entrada inválidos, com um erro por campo (vira 400 na T-013). */
public final class LancamentoInvalidoException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Erro de um campo: nome do campo, código estável e mensagem legível. */
  public record ErroCampo(String campo, String codigo, String mensagem) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public ErroCampo {
      Objects.requireNonNull(campo, "campo");
      Objects.requireNonNull(codigo, "codigo");
      Objects.requireNonNull(mensagem, "mensagem");
    }
  }

  private final transient List<ErroCampo> erros;

  public LancamentoInvalidoException(List<ErroCampo> erros) {
    super(descrever(erros));
    this.erros = List.copyOf(erros);
  }

  static LancamentoInvalidoException campo(String campo, String codigo, String mensagem) {
    return new LancamentoInvalidoException(List.of(new ErroCampo(campo, codigo, mensagem)));
  }

  public List<ErroCampo> erros() {
    return erros;
  }

  private static String descrever(List<ErroCampo> erros) {
    if (erros == null || erros.isEmpty()) {
      throw new IllegalArgumentException("ao menos um erro de campo é obrigatório");
    }
    return "Lançamento inválido: "
        + String.join(", ", erros.stream().map(e -> e.campo() + "=" + e.codigo()).toList());
  }
}
