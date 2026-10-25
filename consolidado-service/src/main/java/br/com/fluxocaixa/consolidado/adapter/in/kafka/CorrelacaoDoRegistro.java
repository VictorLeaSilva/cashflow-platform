package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chaves de correlação de um registro do tópico para o MDC (03 §5.2, 03 §8, Q-046, Q-061): {@code
 * requestId} (cabeçalho {@code x-request-id} válido), {@code eventId} e {@code traceIdOrigem}
 * (campos do payload, lidos sem validar o schema) e {@code traceId} do <i>trace</i> que o
 * publicador propagou em {@code traceparent}, quando o consumidor não está dentro de um <i>span</i>
 * (por exemplo, no tratamento de erro). Valores fora do formato esperado são omitidos, e nada do
 * payload além desses identificadores é lido.
 */
final class CorrelacaoDoRegistro {

  static final String CABECALHO_REQUEST_ID = "x-request-id";
  static final String CABECALHO_TRACEPARENT = "traceparent";
  static final String REQUEST_ID = "requestId";
  static final String EVENT_ID = "eventId";
  static final String TRACE_ID = "traceId";
  static final String TRACE_ID_ORIGEM = "traceIdOrigem";

  /** Mesma regra do {@code X-Request-Id} HTTP (03 §5.2); vale também para o {@code traceId}. */
  private static final Pattern IDENTIFICADOR_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

  private static final Pattern UUID_VALIDO =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  /** {@code traceparent} W3C: versão, trace-id (32 hex), parent-id (16 hex) e flags. */
  private static final Pattern TRACEPARENT =
      Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private CorrelacaoDoRegistro() {}

  /** Chaves de correlação do registro, na ordem em que entram no MDC. */
  static Map<String, String> de(ConsumerRecord<String, String> registro) {
    Map<String, String> chaves = new LinkedHashMap<>();
    requestId(registro).ifPresent(id -> chaves.put(REQUEST_ID, id));
    JsonNode payload = lerPayload(registro.value());
    campo(payload, "eventId")
        .filter(id -> UUID_VALIDO.matcher(id).matches())
        .ifPresent(id -> chaves.put(EVENT_ID, id));
    campo(payload, "traceId")
        .filter(id -> IDENTIFICADOR_VALIDO.matcher(id).matches())
        .ifPresent(id -> chaves.put(TRACE_ID_ORIGEM, id));
    traceIdDoTraceparent(registro).ifPresent(id -> chaves.put(TRACE_ID, id));
    return chaves;
  }

  /** {@code x-request-id} válido do registro (03 §5.2). */
  static Optional<String> requestId(ConsumerRecord<String, String> registro) {
    return cabecalho(registro, CABECALHO_REQUEST_ID)
        .filter(id -> IDENTIFICADOR_VALIDO.matcher(id).matches());
  }

  /** Valida o {@code traceId} de origem do payload antes de ele entrar no log. */
  static Optional<String> traceIdDeOrigemValido(String traceId) {
    return Optional.ofNullable(traceId).filter(id -> IDENTIFICADOR_VALIDO.matcher(id).matches());
  }

  /**
   * Põe as chaves no MDC, sem sobrescrever o que já estiver lá (o {@code traceId} do <i>span</i>
   * corrente vale mais que o do cabeçalho), e devolve o que pôs para {@link #remover}.
   */
  static Map<String, String> aplicar(Map<String, String> chaves) {
    Map<String, String> postas = new LinkedHashMap<>();
    chaves.forEach(
        (chave, valor) -> {
          if (MDC.get(chave) == null) {
            MDC.put(chave, valor);
            postas.put(chave, valor);
          }
        });
    return postas;
  }

  static void remover(Map<String, String> postas) {
    postas.keySet().forEach(MDC::remove);
  }

  private static Optional<String> traceIdDoTraceparent(ConsumerRecord<String, String> registro) {
    return cabecalho(registro, CABECALHO_TRACEPARENT)
        .map(TRACEPARENT::matcher)
        .filter(Matcher::matches)
        .map(m -> m.group(1));
  }

  private static Optional<String> cabecalho(ConsumerRecord<String, String> registro, String nome) {
    Header cabecalho = registro.headers().lastHeader(nome);
    if (cabecalho == null || cabecalho.value() == null) {
      return Optional.empty();
    }
    return Optional.of(new String(cabecalho.value(), StandardCharsets.UTF_8));
  }

  private static JsonNode lerPayload(String valor) {
    if (valor == null || valor.isBlank()) {
      return null;
    }
    try {
      return JSON.readTree(valor);
    } catch (JacksonException e) {
      return null;
    }
  }

  private static Optional<String> campo(JsonNode payload, String nome) {
    if (payload == null || !payload.isObject()) {
      return Optional.empty();
    }
    JsonNode campo = payload.get(nome);
    return campo != null && campo.isString() ? Optional.of(campo.asString()) : Optional.empty();
  }
}
