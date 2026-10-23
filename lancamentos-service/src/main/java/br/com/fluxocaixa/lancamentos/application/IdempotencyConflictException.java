package br.com.fluxocaixa.lancamentos.application;

/**
 * O passo 3 de 03 §3.1.1 não se resolveu em {@link ExecucaoIdempotente#MAX_TENTATIVAS} tentativas
 * (concorrentes sucessivas com rollback). O mapeamento HTTP é da T-013. A mensagem não inclui a
 * chave.
 */
public class IdempotencyConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IdempotencyConflictException(int tentativas) {
    super("chave de idempotência em disputa após " + tentativas + " tentativas");
  }
}
