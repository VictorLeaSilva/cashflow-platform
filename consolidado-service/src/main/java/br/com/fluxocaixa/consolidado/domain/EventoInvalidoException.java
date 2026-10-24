package br.com.fluxocaixa.consolidado.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Evento que viola as invariantes do domínio (Q-034). Não é recuperável: repetir não muda o
 * resultado, então o consumidor (T-022) o manda direto para a DLT. A mensagem cita só campos e
 * códigos, nunca valores.
 */
public final class EventoInvalidoException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Violação de um campo: nome do campo e código estável. */
  public record ErroCampo(String campo, String codigo) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public ErroCampo {
      Objects.requireNonNull(campo, "campo");
      Objects.requireNonNull(codigo, "codigo");
    }
  }

  private final transient List<ErroCampo> erros;

  public EventoInvalidoException(List<ErroCampo> erros) {
    super(descrever(erros));
    this.erros = List.copyOf(erros);
  }

  public List<ErroCampo> erros() {
    return erros;
  }

  private static String descrever(List<ErroCampo> erros) {
    if (erros == null || erros.isEmpty()) {
      throw new IllegalArgumentException("ao menos um erro de campo é obrigatório");
    }
    return "Evento LancamentoRegistrado inválido: "
        + String.join(", ", erros.stream().map(e -> e.campo() + "=" + e.codigo()).toList());
  }
}
