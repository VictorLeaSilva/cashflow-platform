package br.com.fluxocaixa.lancamentos.application;

/**
 * O lançamento já tem estorno (A5, {@code ux_estorno_unico}); vira 409 {@code
 * lancamento-ja-estornado}. A mensagem não traz o id nem o comerciante.
 */
public class LancamentoJaEstornadoException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LancamentoJaEstornadoException() {
    super("o lançamento já foi estornado");
  }
}
