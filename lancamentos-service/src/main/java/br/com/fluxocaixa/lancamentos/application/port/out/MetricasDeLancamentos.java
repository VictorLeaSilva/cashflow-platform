package br.com.fluxocaixa.lancamentos.application.port.out;

import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;

/**
 * Métricas de negócio dos casos de uso (03 §8, Q-063): {@code application} não conhece o
 * Micrometer. O adaptador conta fatos de negócio, não respostas HTTP.
 */
public interface MetricasDeLancamentos {

  /** Lançamento novo confirmado (depois do <i>commit</i>): crédito, débito ou estorno. */
  void registrado(TipoLancamento tipo);

  /** Resposta reconstruída de uma {@code Idempotency-Key} já usada com o mesmo comando. */
  void replay();

  /** {@code Idempotency-Key} reutilizada com outro comando (422 {@code idempotency-key-reuse}). */
  void conflito();
}
