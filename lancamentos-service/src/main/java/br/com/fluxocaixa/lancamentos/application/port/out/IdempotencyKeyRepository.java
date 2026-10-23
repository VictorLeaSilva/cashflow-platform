package br.com.fluxocaixa.lancamentos.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Chaves de idempotência por (comerciante, chave), na transação do comando (03 §3.1.1, D-08). */
public interface IdempotencyKeyRepository {

  /** O que já está registrado para uma chave. */
  record ChaveRegistrada(String requestHash, UUID lancamentoId) {}

  /**
   * {@code INSERT ... ON CONFLICT DO NOTHING}. Se outra transação tiver a mesma chave em aberto, o
   * PostgreSQL faz esta chamada aguardar o commit ou rollback dela.
   *
   * @return {@code true} se a chave foi registrada por esta transação
   */
  boolean tentarRegistrar(UUID comercianteId, String chave, String requestHash, UUID lancamentoId);

  Optional<ChaveRegistrada> buscar(UUID comercianteId, String chave);
}
