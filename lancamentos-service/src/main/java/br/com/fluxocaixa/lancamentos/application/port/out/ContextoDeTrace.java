package br.com.fluxocaixa.lancamentos.application.port.out;

/**
 * Contexto de <i>tracing</i> da requisição corrente (Q-061): o evento leva o {@code traceId} de
 * origem para ligar o POST ao consumo, sem coluna nova na outbox.
 */
public interface ContextoDeTrace {

  /** {@code traceId} da requisição corrente, ou {@code null} se não houver <i>trace</i> ativo. */
  String traceIdAtual();
}
