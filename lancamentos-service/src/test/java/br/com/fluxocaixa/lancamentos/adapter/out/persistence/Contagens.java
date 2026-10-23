package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Contagens por comerciante nas três tabelas que o registro escreve (como {@code lancamentos_app}).
 */
record Contagens(long lancamentos, long eventos, long chaves) {

  static Contagens de(JdbcClient jdbc, UUID comercianteId) {
    return new Contagens(
        contar(jdbc, "SELECT count(*) FROM lancamento WHERE comerciante_id = ?", comercianteId),
        contar(
            jdbc,
            "SELECT count(*) FROM outbox_event WHERE message_key = ?",
            comercianteId.toString()),
        contar(
            jdbc, "SELECT count(*) FROM idempotency_key WHERE comerciante_id = ?", comercianteId));
  }

  private static long contar(JdbcClient jdbc, String sql, Object parametro) {
    return jdbc.sql(sql).param(parametro).query(Long.class).single();
  }
}
