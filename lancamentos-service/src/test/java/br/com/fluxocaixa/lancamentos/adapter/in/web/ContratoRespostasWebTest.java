package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.credito;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** AC-T013.4 (RNF-014): as respostas reais validam contra o schema do status no contrato. */
@Tag("RNF-014")
class ContratoRespostasWebTest extends IntegracaoWeb {

  private final ContratoOpenApi contrato = ContratoOpenApi.carregar();

  @Test
  void respostas201ValidamContraOContrato() throws Exception {
    verificar(postar(credito("100.00"), "contrato-201"), 201, "application/json");
    verificar(
        postar(
            "{\"tipo\":\"DEBITO\",\"valor\":\"5.00\",\"descricao\":\"Fornecedor\","
                + "\"dataOcorrencia\":\"2026-10-22T10:00:00-03:00\",\"identificadorExterno\":\"nf-1\"}",
            "contrato-201-completo"),
        201,
        "application/json");
  }

  @Test
  void respostasDeErroValidamContraOContrato() throws Exception {
    String leitura = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);
    String problema = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    verificar(postar("{\"tipo\":\"PIX\",\"valor\":\"x\"}", "contrato-400"), 400, problema);
    verificar(postarComo(null, credito("1.00"), "contrato-401"), 401, problema);
    verificar(postarComo(leitura, credito("1.00"), "contrato-403"), 403, problema);
    verificar(
        postar(credito("1.00"), "contrato-406").accept(MediaType.APPLICATION_XML), 406, problema);
    verificar(
        postar("{\"recheio\":\"" + "x".repeat(17_000) + "\"}", "contrato-413"), 413, problema);
    verificar(
        postar(credito("1.00"), "contrato-415").contentType(MediaType.TEXT_PLAIN), 415, problema);
    verificar(
        postar(
            "{\"tipo\":\"CREDITO\",\"valor\":\"1.00\",\"dataOcorrencia\":\"2026-12-01T00:00:00Z\"}",
            "contrato-422"),
        422,
        problema);

    portas.simularDisputaPermanente();
    verificar(postar(credito("1.00"), "contrato-409"), 409, problema);
    portas.limpar();

    portas.simularFalhaInesperada();
    verificar(postar(credito("1.00"), "contrato-500"), 500, problema);
  }

  private void verificar(MockHttpServletRequestBuilder requisicao, int status, String midia)
      throws Exception {
    MvcResult resultado = mvc.perform(requisicao).andReturn();

    assertThat(resultado.getResponse().getStatus()).isEqualTo(status);
    assertThat(resultado.getResponse().getContentType()).startsWith(midia);
    assertThat(
            contrato.validar(
                contrato.schemaDaResposta("/lancamentos", "post", status, midia),
                resultado.getResponse().getContentAsString()))
        .as("resposta %s contra o contrato", status)
        .isEmpty();
  }
}
