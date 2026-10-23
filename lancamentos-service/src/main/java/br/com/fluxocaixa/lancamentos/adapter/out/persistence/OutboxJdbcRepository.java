package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import br.com.fluxocaixa.lancamentos.application.port.out.EventoOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxPendente;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * {@link OutboxRepository} com {@code JdbcClient}. A gravação usa o pool da API, na transação do
 * lançamento (D-02); a leitura, a marcação e a limpeza usam o pool dedicado do publicador (D-09, 03
 * §7.1), na transação dele. O payload é serializado aqui, com um mapper próprio para o contrato do
 * evento (03 §4): {@code valor} como string com 2 casas, datas ISO-8601 e campos nulos omitidos.
 */
@Repository
public class OutboxJdbcRepository implements OutboxRepository {

  /** {@code created_at} é o instante do evento (Clock), não o {@code now()} do banco (T-016). */
  private static final String INSERIR =
      """
      INSERT INTO outbox_event (event_id, aggregate_id, topic, message_key, event_type,
                                schema_version, payload, request_id, created_at)
      VALUES (:eventId, :aggregateId, :topic, :messageKey, :eventType,
              :schemaVersion, CAST(:payload AS JSONB), :requestId, :createdAt)
      """;

  /** Usa ix_outbox_pendentes; o event_id só desempata instantes iguais. */
  private static final String TRAVAR_PENDENTES =
      """
      SELECT event_id, topic, message_key, payload::text AS payload, request_id, created_at
        FROM outbox_event
       WHERE published_at IS NULL
       ORDER BY created_at, event_id
       LIMIT :limite
         FOR UPDATE SKIP LOCKED
      """;

  private static final String MARCAR_PUBLICADOS =
      "UPDATE outbox_event SET published_at = :publicadoEm WHERE event_id IN (:eventIds)";

  private static final String REGISTRAR_FALHA =
      """
      UPDATE outbox_event
         SET attempt_count = attempt_count + 1, last_error = :erro
       WHERE event_id = :eventId
      """;

  private static final String EXCLUIR_PUBLICADOS =
      """
      DELETE FROM outbox_event
       WHERE event_id IN (SELECT event_id FROM outbox_event
                           WHERE published_at < :antesDe
                           LIMIT :limite)
      """;

  private static final String CONTAR_PENDENTES =
      "SELECT count(*) FROM outbox_event WHERE published_at IS NULL";

  private static final String MAIS_ANTIGO_PENDENTE =
      "SELECT min(created_at) FROM outbox_event WHERE published_at IS NULL";

  private static final JsonMapper EVENTOS =
      JsonMapper.builder()
          .addModule(new SimpleModule().addSerializer(BigDecimal.class, new ValorMonetario()))
          .changeDefaultPropertyInclusion(
              inclusao -> inclusao.withValueInclusion(JsonInclude.Include.NON_NULL))
          .build();

  private final JdbcClient jdbc;
  private final JdbcClient jdbcDoPublicador;

  /**
   * @param dataSourceDoPublicador pool dedicado do publicador ({@code PublicadorConfig})
   */
  public OutboxJdbcRepository(
      JdbcClient jdbc, @Qualifier("publicador") DataSource dataSourceDoPublicador) {
    this.jdbc = jdbc;
    this.jdbcDoPublicador = JdbcClient.create(dataSourceDoPublicador);
  }

  @Override
  public void inserir(EventoOutbox evento) {
    jdbc.sql(INSERIR)
        .param("eventId", evento.eventId())
        .param("aggregateId", evento.aggregateId())
        .param("topic", evento.topic())
        .param("messageKey", evento.messageKey())
        .param("eventType", evento.eventType())
        .param("schemaVersion", evento.schemaVersion())
        .param("payload", EVENTOS.writeValueAsString(evento.payload()))
        .param("requestId", evento.requestId())
        .param("createdAt", emUtc(evento.payload().occurredAt()))
        .update();
  }

  @Override
  public List<OutboxPendente> travarPendentes(int limite) {
    return jdbcDoPublicador
        .sql(TRAVAR_PENDENTES)
        .param("limite", limite)
        .query((rs, linha) -> pendente(rs))
        .list();
  }

  @Override
  public void marcarPublicados(Collection<UUID> eventIds, Instant publicadoEm) {
    jdbcDoPublicador
        .sql(MARCAR_PUBLICADOS)
        .param("publicadoEm", emUtc(publicadoEm))
        .param("eventIds", eventIds)
        .update();
  }

  @Override
  public void registrarFalhas(Map<UUID, String> falhasPorEvento) {
    falhasPorEvento.forEach(
        (eventId, erro) ->
            jdbcDoPublicador
                .sql(REGISTRAR_FALHA)
                .param("erro", erro)
                .param("eventId", eventId)
                .update());
  }

  @Override
  public int excluirPublicadosAntesDe(Instant instante, int limite) {
    return jdbcDoPublicador
        .sql(EXCLUIR_PUBLICADOS)
        .param("antesDe", emUtc(instante))
        .param("limite", limite)
        .update();
  }

  @Override
  public long contarPendentes() {
    return jdbcDoPublicador.sql(CONTAR_PENDENTES).query(Long.class).single();
  }

  @Override
  public Optional<Instant> maisAntigoPendente() {
    // min() sem linhas pendentes devolve uma linha com NULL.
    List<OffsetDateTime> minimo =
        jdbcDoPublicador
            .sql(MAIS_ANTIGO_PENDENTE)
            .query((rs, linha) -> rs.getObject(1, OffsetDateTime.class))
            .list();
    return minimo.isEmpty()
        ? Optional.empty()
        : Optional.ofNullable(minimo.getFirst()).map(OffsetDateTime::toInstant);
  }

  private static OutboxPendente pendente(ResultSet rs) throws SQLException {
    return new OutboxPendente(
        rs.getObject("event_id", UUID.class),
        rs.getString("topic"),
        rs.getString("message_key"),
        rs.getString("payload"),
        rs.getString("request_id"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }

  /** O driver PostgreSQL mapeia {@code TIMESTAMPTZ} para {@link OffsetDateTime}. */
  private static OffsetDateTime emUtc(Instant instante) {
    return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
  }

  /** Dinheiro em JSON como string decimal sem notação científica (constituição §4). */
  private static final class ValorMonetario extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal valor, JsonGenerator gerador, SerializationContext contexto) {
      gerador.writeString(valor.toPlainString());
    }
  }
}
