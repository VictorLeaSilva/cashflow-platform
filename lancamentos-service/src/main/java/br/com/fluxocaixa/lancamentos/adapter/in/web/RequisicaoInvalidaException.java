package br.com.fluxocaixa.lancamentos.adapter.in.web;

import br.com.fluxocaixa.lancamentos.domain.LancamentoInvalidoException.ErroCampo;
import java.util.List;

/** Erros de campo e de cabeçalho da requisição, todos juntos (400 {@code requisicao-invalida}). */
class RequisicaoInvalidaException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<ErroCampo> erros;

  RequisicaoInvalidaException(List<ErroCampo> erros) {
    super("requisição inválida: " + erros.size() + " erro(s) de campo");
    this.erros = List.copyOf(erros);
  }

  List<ErroCampo> erros() {
    return erros;
  }
}
