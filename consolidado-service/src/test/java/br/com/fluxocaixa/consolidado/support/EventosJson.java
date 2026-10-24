package br.com.fluxocaixa.consolidado.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensagens {@code LancamentoRegistrado.v1} de teste, montadas como o produtor as publica ({@code
 * 03 §4}). Os campos vêm num mapa ordenado para o teste poder tirar, trocar ou acrescentar um campo
 * e gerar uma mensagem fora do contrato.
 */
public final class EventosJson {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private EventosJson() {}

  /** Campos de um crédito válido; {@code occurredAt} às 13:30Z do dia de referência. */
  public static Map<String, Object> credito(
      UUID eventId, UUID comerciante, String valor, String dataReferencia) {
    Map<String, Object> campos = new LinkedHashMap<>();
    campos.put("eventId", eventId.toString());
    campos.put("eventType", "LancamentoRegistrado");
    campos.put("schemaVersion", 1);
    campos.put("occurredAt", dataReferencia + "T13:30:00Z");
    campos.put("comercianteId", comerciante.toString());
    campos.put("lancamentoId", new UUID(eventId.getLeastSignificantBits(), 7L).toString());
    campos.put("tipo", "CREDITO");
    campos.put("valor", valor);
    campos.put("dataReferencia", dataReferencia);
    return campos;
  }

  /** Campos de um estorno válido do lançamento {@code original}. */
  public static Map<String, Object> estorno(
      UUID eventId,
      UUID comerciante,
      String valor,
      String dataReferencia,
      String tipoOriginal,
      UUID original) {
    Map<String, Object> campos = credito(eventId, comerciante, valor, dataReferencia);
    campos.put("tipo", "ESTORNO");
    campos.put("tipoOriginal", tipoOriginal);
    campos.put("referenciaLancamentoId", original.toString());
    return campos;
  }

  public static String json(Map<String, Object> campos) {
    return JSON.writeValueAsString(campos);
  }

  /** Exemplo <i>golden</i> de {@code contracts/src/main/resources/examples/}, como está. */
  public static String golden(String arquivo) {
    try (InputStream exemplo =
        EventosJson.class.getClassLoader().getResourceAsStream("examples/" + arquivo)) {
      if (exemplo == null) {
        throw new IllegalStateException("exemplo golden ausente no classpath: " + arquivo);
      }
      return new String(exemplo.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
