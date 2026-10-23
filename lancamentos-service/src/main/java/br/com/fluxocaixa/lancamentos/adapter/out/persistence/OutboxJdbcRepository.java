package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import br.com.fluxocaixa.lancamentos.application.port.out.EventoOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * {@link OutboxRepository} com {@code JdbcClient}. O payload é serializado aqui, com um mapper
 * próprio para o contrato do evento (03 §4), independente da configuração JSON da API: {@code
 * valor} como string com 2 casas, datas ISO-8601 e campos nulos omitidos.
 */
@Repository
public class OutboxJdbcRepository implements OutboxRepository {

  private static final String INSERIR =
      """
      INSERT INTO outbox_event (event_id, aggregate_id, topic, message_key, event_type,
                                schema_version, payload, request_id)
      VALUES (:eventId, :aggregateId, :topic, :messageKey, :eventType,
              :schemaVersion, CAST(:payload AS JSONB), :requestId)
      """;

  private static final JsonMapper EVENTOS =
      JsonMapper.builder()
          .addModule(new SimpleModule().addSerializer(BigDecimal.class, new ValorMonetario()))
          .changeDefaultPropertyInclusion(
              inclusao -> inclusao.withValueInclusion(JsonInclude.Include.NON_NULL))
          .build();

  private final JdbcClient jdbc;

  public OutboxJdbcRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
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
        .update();
  }

  /** Dinheiro em JSON como string decimal sem notação científica (constituição §4). */
  private static final class ValorMonetario extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal valor, JsonGenerator gerador, SerializationContext contexto) {
      gerador.writeString(valor.toPlainString());
    }
  }
}
