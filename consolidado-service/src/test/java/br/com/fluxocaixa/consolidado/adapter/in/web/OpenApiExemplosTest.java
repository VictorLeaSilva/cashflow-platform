package br.com.fluxocaixa.consolidado.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** AC-T023.5 (RNF-014): o contrato é OpenAPI 3.1 e cada exemplo é válido contra o seu schema. */
@Tag("RNF-014")
class OpenApiExemplosTest {

  private static final String PERIODO = "/consolidado";
  private static final String DIA = "/consolidado/{data}";
  private static final Set<String> STATUS = Set.of("200", "400", "401", "403", "406", "500");

  private final ContratoOpenApi contrato = ContratoOpenApi.carregar();

  @Test
  void contratoEhOpenApi31ComPrefixoDaApi() {
    assertThat(contrato.documento().path("openapi").asString()).startsWith("3.1.");
    assertThat(contrato.prefixo()).isEqualTo("/api/v1");
    assertThat(contrato.documento().at("/components/securitySchemes/bearerAuth/scheme").asString())
        .isEqualTo("bearer");
  }

  /** Q-051: 200, 400, 401, 403 e 406 de 03 §5, mais o 500. */
  @Test
  void operacoesDeclaramOsStatusDaSpec() {
    assertThat(contrato.operacao(PERIODO, "get").path("responses").propertyNames())
        .containsExactlyInAnyOrderElementsOf(STATUS);
    assertThat(contrato.operacao(DIA, "get").path("responses").propertyNames())
        .containsExactlyInAnyOrderElementsOf(STATUS);
  }

  @Test
  void operacoesDocumentamParametrosEScope() {
    assertThat(parametros(PERIODO)).containsExactly("dataInicio", "dataFim", "X-Request-Id");
    assertThat(parametros(DIA)).containsExactly("data", "X-Request-Id");
    for (String caminho : List.of(PERIODO, DIA)) {
      assertThat(contrato.operacao(caminho, "get").at("/security/0/bearerAuth/0").asString())
          .isEqualTo("consolidado:read");
    }
  }

  @Test
  void exemplosNomeadosDaSpec() {
    assertThat(exemplos(PERIODO, "200", "application/json"))
        .contains("semana-com-movimento", "periodo-sem-movimento", "saldo-anterior-negativo");
    assertThat(exemplos(DIA, "200", "application/json"))
        .contains("dia-com-movimento", "dia-sem-movimento");
    assertThat(exemplos(PERIODO, "400", "application/problem+json"))
        .contains(
            "parametro-ausente",
            "intervalo-invalido",
            "intervalo-maximo",
            "data-invalida",
            "parametro-desconhecido");
    assertThat(exemplos(DIA, "400", "application/problem+json"))
        .contains("data-invalida", "parametro-desconhecido");
  }

  @Test
  void cadaStatusTemExemploPreenchidoEValido() {
    verificarExemplosDosStatus(PERIODO);
    verificarExemplosDosStatus(DIA);
  }

  /** Controle negativo: sem {@code format} conferido e sem os padrões, este teste falharia. */
  @Test
  void validadorVerificaFormatEPadroes() {
    JsonNode dia = contrato.documento().at("/components/schemas/DiaConsolidado");
    String valido =
        "{\"data\":\"2026-10-21\",\"totalCreditos\":\"1.00\",\"totalDebitos\":\"0.00\","
            + "\"totalEstornosCredito\":\"0.00\",\"totalEstornosDebito\":\"0.00\","
            + "\"saldoDia\":\"-1.00\",\"saldoAcumulado\":\"1.00\",\"qtdLancamentos\":1,"
            + "\"atualizadoEm\":null}";

    assertThat(contrato.validar(dia, valido)).isEmpty();
    assertThat(contrato.validar(dia, valido.replace("2026-10-21", "2026-13-40"))).isNotEmpty();
    assertThat(
            contrato.validar(
                dia, valido.replace("\"1.00\",\"totalDebitos", "\"-1.00\",\"totalDebitos")))
        .isNotEmpty();
    assertThat(
            contrato.validar(
                dia, valido.replace("\"atualizadoEm\":null", "\"atualizadoEm\":\"ontem\"")))
        .isNotEmpty();
    assertThat(contrato.validar(dia, valido.replace(",\"atualizadoEm\":null", ""))).isNotEmpty();
  }

  private List<String> parametros(String caminho) {
    List<String> nomes = new ArrayList<>();
    contrato
        .operacao(caminho, "get")
        .path("parameters")
        .forEach(p -> nomes.add(contrato.resolver(p).path("name").asString()));
    return nomes;
  }

  private Set<String> exemplos(String caminho, String status, String midia) {
    JsonNode conteudo =
        contrato
            .resolver(contrato.operacao(caminho, "get").path("responses").path(status))
            .path("content");
    return ContratoOpenApi.exemplos(conteudo.path(midia), contrato).keySet();
  }

  private void verificarExemplosDosStatus(String caminho) {
    contrato
        .operacao(caminho, "get")
        .path("responses")
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
}
