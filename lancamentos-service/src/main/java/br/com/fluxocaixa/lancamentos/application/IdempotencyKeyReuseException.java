package br.com.fluxocaixa.lancamentos.application;

/**
 * A chave de idempotência já foi usada com outro comando (AC-007.3); vira 422 {@code
 * idempotency-key-reuse} na T-013. A mensagem não inclui a chave.
 */
public class IdempotencyKeyReuseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IdempotencyKeyReuseException() {
    super("a chave de idempotência já foi usada com outro comando");
  }
}
