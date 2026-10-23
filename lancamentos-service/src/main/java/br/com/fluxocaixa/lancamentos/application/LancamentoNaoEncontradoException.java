package br.com.fluxocaixa.lancamentos.application;

/**
 * O lançamento não existe ou é de outro comerciante: os dois casos são indistinguíveis (RF-010) e
 * viram 404 {@code lancamento-nao-encontrado}. A mensagem não traz o id nem o comerciante.
 */
public class LancamentoNaoEncontradoException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LancamentoNaoEncontradoException() {
    super("lançamento não encontrado");
  }
}
