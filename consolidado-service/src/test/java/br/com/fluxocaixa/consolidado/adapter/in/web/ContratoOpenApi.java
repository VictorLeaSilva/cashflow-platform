package br.com.fluxocaixa.consolidado.adapter.in.web;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * O contrato {@code openapi/consolidado-v1.yaml} (OpenAPI 3.1) e a validação de JSON contra os
 * schemas dele (Q-002). Os {@code $ref} internos ({@code #/components/...}) são resolvidos no
 * próprio documento: o schema validado é embrulhado junto com {@code components}.
 */
final class ContratoOpenApi {

  static final String ARQUIVO = "openapi/consolidado-v1.yaml";

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final SchemaRegistry SCHEMAS =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

  private final JsonNode documento;

  private ContratoOpenApi(JsonNode documento) {
    this.documento = documento;
  }

  static ContratoOpenApi carregar() {
    try (InputStream yaml = ContratoOpenApi.class.getClassLoader().getResourceAsStream(ARQUIVO)) {
      if (yaml == null) {
        throw new IllegalStateException(ARQUIVO + " não está no classpath");
      }
      return new ContratoOpenApi(YAMLMapper.builder().build().readTree(yaml));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  JsonNode documento() {
    return documento;
  }

  /** Prefixo do servidor (ex.: {@code /api/v1}). */
  String prefixo() {
    return documento.path("servers").path(0).path("url").asString();
  }

  JsonNode operacao(String caminho, String metodo) {
    JsonNode operacao = documento.path("paths").path(caminho).path(metodo.toLowerCase());
    if (operacao.isMissingNode()) {
      throw new IllegalArgumentException("operação ausente no contrato: " + metodo + " " + caminho);
    }
    return operacao;
  }

  /** Schema da resposta de {@code status} na operação, para o tipo de mídia informado. */
  JsonNode schemaDaResposta(String caminho, String metodo, int status, String midia) {
    JsonNode resposta =
        resolver(operacao(caminho, metodo).path("responses").path(String.valueOf(status)));
    JsonNode schema = resposta.path("content").path(midia).path("schema");
    if (schema.isMissingNode()) {
      throw new IllegalArgumentException(
          "resposta " + status + " (" + midia + ") sem schema em " + metodo + " " + caminho);
    }
    return schema;
  }

  /** Segue um {@code $ref} interno de primeiro nível (ex.: respostas em components). */
  JsonNode resolver(JsonNode no) {
    JsonNode ref = no.path("$ref");
    if (ref.isString() && ref.asString().startsWith("#/")) {
      return documento.at(ref.asString().substring(1));
    }
    return no;
  }

  /** Erros de validação de {@code instancia} contra {@code schema}; vazio quando válida. */
  List<String> validar(JsonNode schema, JsonNode instancia) {
    ObjectNode embrulho = JSON.createObjectNode();
    embrulho.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    embrulho.putArray("allOf").add(schema);
    embrulho.set("components", documento.path("components"));
    Schema compilado = SCHEMAS.getSchema(JSON.writeValueAsString(embrulho), InputFormat.JSON);
    List<String> erros = new ArrayList<>();
    // format (uuid, date, date-time...) é asserção, não só anotação.
    for (Error erro :
        compilado.validate(
            instancia,
            contexto -> contexto.executionConfig(config -> config.formatAssertionsEnabled(true)))) {
      erros.add(erro.getInstanceLocation() + ": " + erro.getMessage());
    }
    return erros;
  }

  /** Valor de cabeçalho como instância JSON (texto). */
  JsonNode texto(String valor) {
    return JSON.getNodeFactory().stringNode(valor);
  }

  List<String> validar(JsonNode schema, String json) {
    return validar(schema, JSON.readTree(json));
  }

  /** Exemplos nomeados de um objeto de mídia: {@code nome → valor}. */
  static Map<String, JsonNode> exemplos(JsonNode midia, ContratoOpenApi contrato) {
    Map<String, JsonNode> exemplos = new java.util.LinkedHashMap<>();
    midia
        .path("examples")
        .properties()
        .forEach(
            exemplo ->
                exemplos.put(
                    exemplo.getKey(), contrato.resolver(exemplo.getValue()).path("value")));
    return exemplos;
  }
}
