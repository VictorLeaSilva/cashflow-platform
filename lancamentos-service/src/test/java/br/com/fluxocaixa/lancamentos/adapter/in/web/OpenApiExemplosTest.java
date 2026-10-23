package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** AC-T013.4 (RNF-014): o contrato é OpenAPI 3.1 e cada exemplo é válido contra o seu schema. */
@Tag("RNF-014")
class OpenApiExemplosTest {

  private static final Set<String> STATUS_DO_POST =
      Set.of("201", "400", "401", "403", "406", "409", "413", "415", "422", "500");

  private final ContratoOpenApi contrato = ContratoOpenApi.carregar();

  @Test
  void contratoEhOpenApi31ComPrefixoDaApi() {
    assertThat(contrato.documento().path("openapi").asString()).startsWith("3.1.");
    assertThat(contrato.prefixo()).isEqualTo("/api/v1");
    assertThat(contrato.documento().at("/components/securitySchemes/bearerAuth/scheme").asString())
        .isEqualTo("bearer");
  }

  @Test
  void postDeclaraTodosOsStatusDaSpec() {
    JsonNode respostas = contrato.operacao("/lancamentos", "post").path("responses");

    assertThat(respostas.propertyNames()).containsExactlyInAnyOrderElementsOf(STATUS_DO_POST);
  }

  @Test
  void postDocumentaOsCabecalhos() {
    JsonNode operacao = contrato.operacao("/lancamentos", "post");
    List<String> parametros = new ArrayList<>();
    operacao
        .path("parameters")
        .forEach(p -> parametros.add(contrato.resolver(p).path("name").asString()));
    assertThat(parametros).contains("Idempotency-Key", "X-Request-Id");

    JsonNode criado = contrato.resolver(operacao.path("responses").path("201"));
    assertThat(criado.path("headers").propertyNames())
        .contains("Location", "X-Request-Id", "Idempotent-Replayed");
    JsonNode conflito = contrato.resolver(operacao.path("responses").path("409"));
    assertThat(conflito.path("headers").propertyNames()).contains("Retry-After", "X-Request-Id");
  }

  @Test
  void exemplosDeRequisicaoSaoValidos() {
    JsonNode midia =
        contrato
            .operacao("/lancamentos", "post")
            .path("requestBody")
            .path("content")
            .path("application/json");
    Map<String, JsonNode> exemplos = ContratoOpenApi.exemplos(midia, contrato);

    assertThat(exemplos.keySet()).contains("credito", "debito", "retroativo", "com-texto-opcional");
    exemplos.forEach(
        (nome, valor) ->
            assertThat(contrato.validar(midia.path("schema"), valor))
                .as("exemplo %s", nome)
                .isEmpty());
  }

  @Test
  void cadaStatusTemExemploPreenchidoEValido() {
    JsonNode respostas = contrato.operacao("/lancamentos", "post").path("responses");
    respostas
        .properties()
        .forEach(
            resposta -> {
              JsonNode conteudo = contrato.resolver(resposta.getValue()).path("content");
              assertThat(conteudo.isEmpty())
                  .as("status %s sem conteúdo", resposta.getKey())
                  .isFalse();
              conteudo
                  .properties()
                  .forEach(
                      midia -> {
                        Map<String, JsonNode> exemplos =
                            ContratoOpenApi.exemplos(midia.getValue(), contrato);
                        assertThat(exemplos)
                            .as("status %s sem exemplo", resposta.getKey())
                            .isNotEmpty();
                        exemplos.forEach(
                            (nome, valor) -> {
                              assertThat(valor.isObject())
                                  .as("exemplo %s/%s preenchido", resposta.getKey(), nome)
                                  .isTrue();
                              assertThat(contrato.validar(midia.getValue().path("schema"), valor))
                                  .as("exemplo %s/%s", resposta.getKey(), nome)
                                  .isEmpty();
                            });
                      });
            });
  }

  @Test
  void respostasDeSucessoTemOsExemplosNomeados() {
    JsonNode midia =
        contrato
            .resolver(contrato.operacao("/lancamentos", "post").path("responses").path("201"))
            .path("content")
            .path("application/json");

    assertThat(ContratoOpenApi.exemplos(midia, contrato).keySet())
        .contains("credito", "debito", "replay-idempotente");
  }

  @Test
  void validadorRejeitaUmExemploInvalido() {
    JsonNode schema = contrato.schemaDaResposta("/lancamentos", "post", 201, "application/json");

    assertThat(contrato.validar(schema, "{\"id\":\"nao-uuid\",\"valor\":10}")).isNotEmpty();
  }
}
