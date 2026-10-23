package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import br.com.fluxocaixa.lancamentos.application.port.out.IdempotencyKeyRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link IdempotencyKeyRepository} com {@code INSERT ... ON CONFLICT DO NOTHING} (03 §3.1.1): em
 * conflito com uma transação em aberto, o PostgreSQL aguarda o desfecho dela, e esta transação
 * continua utilizável (ao contrário de capturar a violação de unicidade).
 */
@Repository
public class IdempotencyKeyJdbcRepository implements IdempotencyKeyRepository {

  private static final String REGISTRAR =
      """
      INSERT INTO idempotency_key (comerciante_id, chave, request_hash, lancamento_id)
      VALUES (:comercianteId, :chave, :requestHash, :lancamentoId)
      ON CONFLICT (comerciante_id, chave) DO NOTHING
      """;

  private static final String BUSCAR =
      """
      SELECT request_hash, lancamento_id
        FROM idempotency_key
       WHERE comerciante_id = :comercianteId AND chave = :chave
      """;

  private final JdbcClient jdbc;

  public IdempotencyKeyJdbcRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean tentarRegistrar(
      UUID comercianteId, String chave, String requestHash, UUID lancamentoId) {
    return jdbc.sql(REGISTRAR)
            .param("comercianteId", comercianteId)
            .param("chave", chave)
            .param("requestHash", requestHash)
            .param("lancamentoId", lancamentoId)
            .update()
        == 1;
  }

  @Override
  public Optional<ChaveRegistrada> buscar(UUID comercianteId, String chave) {
    return jdbc.sql(BUSCAR)
        .param("comercianteId", comercianteId)
        .param("chave", chave)
        .query(
            (rs, linha) ->
                new ChaveRegistrada(
                    rs.getString("request_hash"), rs.getObject("lancamento_id", UUID.class)))
        .optional();
  }
}
