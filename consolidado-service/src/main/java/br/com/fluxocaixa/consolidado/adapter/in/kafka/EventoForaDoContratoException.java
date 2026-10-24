package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import java.io.Serial;
import java.util.List;

/**
 * Mensagem que não é um {@code LancamentoRegistrado.v1} válido: JSON malformado ou fora do schema
 * de {@code contracts/} (Q-043). Não é recuperável e vai direto para a DLT (Q-045). As violações só
 * trazem o ponteiro do campo e a palavra-chave do schema (JSON Pointer, RFC 6901; ex.: {@code
 * /valor:pattern}; a raiz é o ponteiro vazio), nunca o valor recebido.
 */
public final class EventoForaDoContratoException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final transient List<String> violacoes;

  public EventoForaDoContratoException(List<String> violacoes) {
    super(descrever(violacoes));
    this.violacoes = List.copyOf(violacoes);
  }

  public List<String> violacoes() {
    return violacoes;
  }

  private static String descrever(List<String> violacoes) {
    if (violacoes == null || violacoes.isEmpty()) {
      throw new IllegalArgumentException("ao menos uma violação é obrigatória");
    }
    return "Evento fora do contrato LancamentoRegistrado.v1: " + String.join(", ", violacoes);
  }
}
