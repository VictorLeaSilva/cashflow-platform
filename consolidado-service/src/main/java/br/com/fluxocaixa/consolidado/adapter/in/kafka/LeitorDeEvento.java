package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import br.com.fluxocaixa.consolidado.domain.TipoLancamento;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê uma mensagem do tópico como {@code LancamentoRegistrado.v1} (03 §4): <i>parse</i> com Jackson
 * 3, validação contra o JSON Schema do artefato {@code contracts} com {@code format} conferido
 * (Q-006, Q-043) e só então o valor de domínio, que confere de novo as próprias invariantes
 * (Q-034). A data de referência é a do evento, nunca recalculada (03 §4.1, item 3).
 *
 * @throws EventoForaDoContratoException JSON malformado ou fora do schema (não recuperável)
 */
public final class LeitorDeEvento {

  /** Recurso do artefato {@code contracts}, sem cópia no serviço (Q-043). */
  static final String SCHEMA = "events/lancamento-registrado.v1.schema.json";

  /** Violações na raiz usam o ponteiro vazio (RFC 6901): {@code :json-malformado}. */
  static final String JSON_MALFORMADO = ":json-malformado";

  private final ObjectReader json;
  private final Schema schema;

  public LeitorDeEvento(JsonMapper json) {
    this.json = json.reader();
    this.schema = carregarSchema();
  }

  public EventoLido ler(String texto) {
    JsonNode evento = parse(texto);
    validar(evento);
    try {
      return new EventoLido(
          new LancamentoRegistrado(
              UUID.fromString(evento.get("eventId").asString()),
              UUID.fromString(evento.get("comercianteId").asString()),
              UUID.fromString(evento.get("lancamentoId").asString()),
              TipoLancamento.valueOf(evento.get("tipo").asString()),
              new BigDecimal(evento.get("valor").asString()),
              LocalDate.parse(evento.get("dataReferencia").asString()),
              opcional(evento, "tipoOriginal") == null
                  ? null
                  : TipoLancamento.valueOf(opcional(evento, "tipoOriginal")),
              opcional(evento, "referenciaLancamentoId") == null
                  ? null
                  : UUID.fromString(opcional(evento, "referenciaLancamentoId"))),
          OffsetDateTime.parse(evento.get("occurredAt").asString()).toInstant());
    } catch (IllegalArgumentException | DateTimeParseException e) {
      // Defesa: o schema com format conferido já barra isso; o texto do erro não é repassado.
      throw new EventoForaDoContratoException(List.of(":formato"));
    }
  }

  private JsonNode parse(String texto) {
    if (texto == null || texto.isBlank()) {
      throw new EventoForaDoContratoException(List.of(JSON_MALFORMADO));
    }
    try {
      JsonNode evento = json.readTree(texto);
      if (evento == null || evento.isMissingNode()) {
        throw new EventoForaDoContratoException(List.of(JSON_MALFORMADO));
      }
      return evento;
    } catch (JacksonException e) {
      throw new EventoForaDoContratoException(List.of(JSON_MALFORMADO));
    }
  }

  /** Só ponteiro e palavra-chave: a mensagem do validador pode citar o valor recebido. */
  private void validar(JsonNode evento) {
    List<Error> erros =
        schema.validate(
            evento,
            contexto -> contexto.executionConfig(config -> config.formatAssertionsEnabled(true)));
    if (!erros.isEmpty()) {
      throw new EventoForaDoContratoException(
          erros.stream()
              .map(erro -> erro.getInstanceLocation() + ":" + erro.getKeyword())
              .distinct()
              .toList());
    }
  }

  private static String opcional(JsonNode evento, String campo) {
    JsonNode valor = evento.get(campo);
    return valor == null || valor.isNull() ? null : valor.asString();
  }

  private static Schema carregarSchema() {
    try (InputStream recurso = LeitorDeEvento.class.getClassLoader().getResourceAsStream(SCHEMA)) {
      if (recurso == null) {
        throw new IllegalStateException("schema do evento ausente no classpath: " + SCHEMA);
      }
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(recurso, InputFormat.JSON);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
