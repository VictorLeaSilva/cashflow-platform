package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.credito;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.estornar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.estornarComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * AC-T013.4 (RNF-014): as respostas reais (corpo e cabeçalhos) validam contra o contrato, para cada
 * status e cada {@code type} do catálogo.
 */
@Tag("RNF-014")
class ContratoRespostasWebTest extends IntegracaoWeb {

  private static final String PROBLEMA = MediaType.APPLICATION_PROBLEM_JSON_VALUE;
  private static final String ESTORNO = "/lancamentos/{id}/estornos";
  private static final String CREDITO_COM_EXTERNO =
      "{\"tipo\":\"CREDITO\",\"valor\":\"1.00\",\"identificadorExterno\":\"nf-contrato\"}";

  private final ContratoOpenApi contrato = ContratoOpenApi.carregar();

  @Test
  void respostas201ValidamContraOContrato() throws Exception {
    verificar(postar(credito("100.00"), "contrato-201"), 201, "application/json");
    verificar(
        postar(
            "{\"tipo\":\"DEBITO\",\"valor\":\"5.00\",\"descricao\":\"Fornecedor\","
                + "\"dataOcorrencia\":\"2026-10-22T10:00:00.123456-03:00\","
                + "\"identificadorExterno\":\"nf-1\"}",
            "contrato-201-completo"),
        201,
        "application/json");
    MockHttpServletResponse replay =
        verificar(postar(credito("100.00"), "contrato-201"), 201, "application/json");
    assertThat(replay.getHeader("Idempotent-Replayed")).isEqualTo("true");
  }

  @Test
  void respostasDeErroValidamContraOContrato() throws Exception {
    String leitura = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);

    verificar(postar("{\"tipo\":\"PIX\",\"valor\":\"x\"}", "contrato-400"), 400, PROBLEMA);
    verificar(postarComo(null, credito("1.00"), "contrato-401"), 401, PROBLEMA);
    verificar(postarComo(leitura, credito("1.00"), "contrato-403"), 403, PROBLEMA);
    verificar(
        postar(credito("1.00"), "contrato-406").accept(MediaType.APPLICATION_XML), 406, PROBLEMA);
    verificar(
        postar("{\"recheio\":\"" + "x".repeat(17_000) + "\"}", "contrato-413"), 413, PROBLEMA);
    verificar(
        postar(credito("1.00"), "contrato-415").contentType(MediaType.TEXT_PLAIN), 415, PROBLEMA);
    verificar(
        postar(
            "{\"tipo\":\"CREDITO\",\"valor\":\"1.00\",\"dataOcorrencia\":\"2026-12-01T00:00:00Z\"}",
            "contrato-422-futura"),
        422,
        PROBLEMA);
    verificar(
        postar(
            "{\"tipo\":\"CREDITO\",\"valor\":\"1.00\",\"dataOcorrencia\":\"2026-08-01T00:00:00Z\"}",
            "contrato-422-retroativa"),
        422,
        PROBLEMA);
    mvc.perform(postar(credito("2.00"), "contrato-422-reuso"));
    verificar(postar(credito("2.01"), "contrato-422-reuso"), 422, PROBLEMA);

    mvc.perform(postar(CREDITO_COM_EXTERNO, "contrato-409-a"));
    verificar(postar(CREDITO_COM_EXTERNO, "contrato-409-b"), 409, PROBLEMA);

    portas.simularDisputaPermanente();
    MockHttpServletResponse disputa =
        verificar(postar(credito("1.00"), "contrato-409"), 409, PROBLEMA);
    assertThat(disputa.getHeader("Retry-After")).isEqualTo("1");
    portas.limpar();

    portas.simularFalhaInesperada();
    verificar(postar(credito("1.00"), "contrato-500"), 500, PROBLEMA);
  }

  /** AC-T014.11: as respostas reais do estorno validam contra a operação do estorno. */
  @Test
  void respostasDoEstornoValidamContraOContrato() throws Exception {
    String leitura = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);
    String original = criarCredito("contrato-est-original");
    String debito =
        Chamadas.campo(
            mvc.perform(postar("{\"tipo\":\"DEBITO\",\"valor\":\"3.00\"}", "contrato-est-deb"))
                .andReturn(),
            "id");

    verificarEstorno(estornar(original, "contrato-est-201"), 201, "application/json");
    verificarEstorno(estornar(debito, "contrato-est-201-deb"), 201, "application/json");
    MockHttpServletResponse replay =
        verificarEstorno(estornar(original, "contrato-est-201"), 201, "application/json");
    assertThat(replay.getHeader("Idempotent-Replayed")).isEqualTo("true");

    verificarEstorno(estornar("abc", null), 400, PROBLEMA);
    verificarEstorno(estornarComo(null, original, "contrato-est-401"), 401, PROBLEMA);
    verificarEstorno(estornarComo(leitura, original, "contrato-est-403"), 403, PROBLEMA);
    verificarEstorno(estornar(UUID.randomUUID(), "contrato-est-404"), 404, PROBLEMA);
    verificarEstorno(
        estornar(original, "contrato-est-406").accept(MediaType.APPLICATION_XML), 406, PROBLEMA);
    verificarEstorno(estornar(original, "contrato-est-409"), 409, PROBLEMA);
    verificarEstorno(
        estornar(original, "contrato-est-413")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"recheio\":\"" + "x".repeat(17_000) + "\"}"),
        413,
        PROBLEMA);
    verificarEstorno(
        estornar(original, "contrato-est-415").contentType(MediaType.TEXT_PLAIN).content("{}"),
        415,
        PROBLEMA);
    String estornoId =
        Chamadas.campo(mvc.perform(estornar(original, "contrato-est-201")).andReturn(), "id");
    verificarEstorno(estornar(estornoId, "contrato-est-422"), 422, PROBLEMA);
    verificarEstorno(estornar(debito, "contrato-est-201"), 422, PROBLEMA);

    portas.simularDisputaPermanente();
    MockHttpServletResponse disputa =
        verificarEstorno(estornar(debito, "contrato-est-disputa"), 409, PROBLEMA);
    assertThat(disputa.getHeader("Retry-After")).isEqualTo("1");
    portas.limpar();

    String outro = criarCredito("contrato-est-500-original");
    portas.simularFalhaInesperada();
    verificarEstorno(estornar(outro, "contrato-est-500"), 500, PROBLEMA);
  }

  private String criarCredito(String chave) throws Exception {
    return Chamadas.campo(mvc.perform(postar(credito("4.00"), chave)).andReturn(), "id");
  }

  private MockHttpServletResponse verificar(
      MockHttpServletRequestBuilder requisicao, int status, String midia) throws Exception {
    return verificar("/lancamentos", requisicao, status, midia);
  }

  private MockHttpServletResponse verificarEstorno(
      MockHttpServletRequestBuilder requisicao, int status, String midia) throws Exception {
    return verificar(ESTORNO, requisicao, status, midia);
  }

  /** Corpo contra o schema do status; cabeçalhos declarados contra os schemas deles. */
  private MockHttpServletResponse verificar(
      String caminho, MockHttpServletRequestBuilder requisicao, int status, String midia)
      throws Exception {
    MvcResult resultado = mvc.perform(requisicao).andReturn();
    MockHttpServletResponse resposta = resultado.getResponse();

    assertThat(resposta.getStatus()).isEqualTo(status);
    assertThat(resposta.getContentType()).startsWith(midia);
    assertThat(
            contrato.validar(
                contrato.schemaDaResposta(caminho, "post", status, midia),
                resposta.getContentAsString()))
        .as("corpo da resposta %s de %s contra o contrato", status, caminho)
        .isEmpty();
    verificarCabecalhos(caminho, resposta, status);
    return resposta;
  }

  private void verificarCabecalhos(String caminho, MockHttpServletResponse resposta, int status) {
    JsonNode declarados =
        contrato
            .resolver(contrato.operacao(caminho, "post").path("responses").path("" + status))
            .path("headers");
    assertThat(declarados.has("X-Request-Id")).isTrue();
    for (String nome : List.copyOf(declarados.propertyNames())) {
      String valor = resposta.getHeader(nome);
      boolean opcional = List.of("Idempotent-Replayed", "Retry-After").contains(nome);
      if (valor == null) {
        assertThat(opcional).as("cabeçalho %s ausente no %s", nome, status).isTrue();
        continue;
      }
      JsonNode cabecalho = contrato.resolver(declarados.path(nome));
      assertThat(contrato.validar(cabecalho.path("schema"), contrato.texto(valor)))
          .as("cabeçalho %s do %s", nome, status)
          .isEmpty();
    }
  }
}
