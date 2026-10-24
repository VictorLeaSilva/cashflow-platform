package br.com.fluxocaixa.consolidado.adapter.in.web;

import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.COMERCIANTE;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import br.com.fluxocaixa.consolidado.support.IntegracaoWeb;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * AC-T023.5 (RNF-014): as respostas reais (corpo e cabeçalhos) validam contra o contrato, para cada
 * status declarado das duas operações.
 */
@Tag("RNF-014")
class ContratoRespostasWebTest extends IntegracaoWeb {

  private static final String JSON = "application/json";
  private static final String PROBLEMA = MediaType.APPLICATION_PROBLEM_JSON_VALUE;
  private static final String PERIODO = "/consolidado";
  private static final String DIA = "/consolidado/{data}";

  private final ContratoOpenApi contrato = ContratoOpenApi.carregar();

  @Test
  void respostasDoPeriodoValidamContraOContrato() throws Exception {
    gravarMovimento();
    String semScope = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA_DE_LANCAMENTOS);

    verificar(PERIODO, Chamadas.periodo("dataInicio=2026-10-19&dataFim=2026-10-21"), 200, JSON);
    verificar(PERIODO, Chamadas.periodo("dataInicio=2027-01-01&dataFim=2027-01-01"), 200, JSON);
    verificar(PERIODO, Chamadas.periodo("dataInicio=x&comercianteId=1"), 400, PROBLEMA);
    verificar(PERIODO, Chamadas.periodoComo(null, ""), 401, PROBLEMA);
    verificar(PERIODO, Chamadas.periodoComo(semScope, ""), 403, PROBLEMA);
    verificar(
        PERIODO,
        Chamadas.periodo("dataInicio=2026-10-21&dataFim=2026-10-21")
            .accept(MediaType.APPLICATION_XML),
        406,
        PROBLEMA);
    leitura.simularFalhaInesperada();
    verificar(PERIODO, Chamadas.periodo("dataInicio=2026-10-21&dataFim=2026-10-21"), 500, PROBLEMA);
  }

  @Test
  void respostasDoDiaValidamContraOContrato() throws Exception {
    gravarMovimento();
    String semScope = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA_DE_LANCAMENTOS);

    verificar(DIA, Chamadas.dia("2026-10-20"), 200, JSON);
    verificar(DIA, Chamadas.dia("2026-10-19"), 200, JSON);
    verificar(DIA, Chamadas.dia("2026-02-30"), 400, PROBLEMA);
    verificar(DIA, Chamadas.diaComo(null, "2026-10-20"), 401, PROBLEMA);
    verificar(DIA, Chamadas.diaComo(semScope, "2026-10-20"), 403, PROBLEMA);
    verificar(DIA, Chamadas.dia("2026-10-20").accept(MediaType.APPLICATION_XML), 406, PROBLEMA);
    leitura.simularFalhaInesperada();
    verificar(DIA, Chamadas.dia("2026-10-20"), 500, PROBLEMA);
  }

  private void gravarMovimento() {
    leitura.gravar(
        COMERCIANTE,
        LocalDate.parse("2026-10-20"),
        new MovimentoDiario(
            new BigDecimal("10.00"),
            new BigDecimal("25.50"),
            new BigDecimal("1.00"),
            new BigDecimal("2.00"),
            4),
        Instant.parse("2026-10-20T13:02:11.004001Z"));
  }

  /** Corpo contra o schema do status; cabeçalhos declarados contra os schemas deles. */
  private void verificar(
      String caminho, MockHttpServletRequestBuilder requisicao, int status, String midia)
      throws Exception {
    MvcResult resultado = mvc.perform(requisicao).andReturn();
    MockHttpServletResponse resposta = resultado.getResponse();

    assertThat(resposta.getStatus()).isEqualTo(status);
    assertThat(resposta.getContentType()).startsWith(midia);
    assertThat(
            contrato.validar(
                contrato.schemaDaResposta(caminho, "get", status, midia),
                resposta.getContentAsString()))
        .as("corpo da resposta %s de %s contra o contrato", status, caminho)
        .isEmpty();
    JsonNode declarados =
        contrato
            .resolver(contrato.operacao(caminho, "get").path("responses").path("" + status))
            .path("headers");
    assertThat(declarados.has("X-Request-Id")).isTrue();
    for (String nome : List.copyOf(declarados.propertyNames())) {
      String valor = resposta.getHeader(nome);
      assertThat(valor).as("cabeçalho %s ausente no %s", nome, status).isNotNull();
      assertThat(
              contrato.validar(
                  contrato.resolver(declarados.path(nome)).path("schema"), contrato.texto(valor)))
          .as("cabeçalho %s do %s", nome, status)
          .isEmpty();
    }
  }
}
