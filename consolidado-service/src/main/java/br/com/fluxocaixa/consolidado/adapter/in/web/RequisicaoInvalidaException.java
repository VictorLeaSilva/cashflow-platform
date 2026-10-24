package br.com.fluxocaixa.consolidado.adapter.in.web;

import java.util.List;

/** Erros de campo e de cabeçalho da requisição, todos juntos (400 {@code requisicao-invalida}). */
class RequisicaoInvalidaException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<ErroDeCampo> erros;

  RequisicaoInvalidaException(List<ErroDeCampo> erros) {
    super("requisição inválida: " + erros.size() + " erro(s) de campo");
    this.erros = List.copyOf(erros);
  }

  List<ErroDeCampo> erros() {
    return erros;
  }
}
