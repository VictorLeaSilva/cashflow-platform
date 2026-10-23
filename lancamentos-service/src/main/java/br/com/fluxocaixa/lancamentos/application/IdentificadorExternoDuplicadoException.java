package br.com.fluxocaixa.lancamentos.application;

/**
 * O comerciante já registrou um lançamento com o mesmo {@code identificadorExterno} (Q-015); vira
 * 409 {@code identificador-externo-duplicado} na API. A mensagem não traz o identificador nem o
 * comerciante.
 */
public class IdentificadorExternoDuplicadoException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IdentificadorExternoDuplicadoException() {
    super("identificadorExterno já usado por este comerciante");
  }
}
