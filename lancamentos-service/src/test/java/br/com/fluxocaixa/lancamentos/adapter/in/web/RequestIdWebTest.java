package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.credito;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MvcResult;

/** AC-T013.2 (RNF-012): o X-Request-Id volta em toda resposta e chega ao comando (outbox). */
@Tag("RNF-012")
class RequestIdWebTest extends IntegracaoWeb {

  private static final String CABECALHO = "X-Request-Id";

  @Test
  void requestIdValidoEhDevolvidoEVaiParaOEvento() throws Exception {
    mvc.perform(postar(credito("10.00"), "chave-rid").header(CABECALHO, "cliente-123.abc_X"))
        .andExpect(status().isCreated())
        .andExpect(header().string(CABECALHO, "cliente-123.abc_X"));

    assertThat(portas.eventos().getFirst().requestId()).isEqualTo("cliente-123.abc_X");
  }

  @Test
  void requestIdAusenteEhGeradoPeloIdGenerator() throws Exception {
    MvcResult resultado =
        mvc.perform(postar(credito("10.00"), "chave-sem-rid"))
            .andExpect(status().isCreated())
            .andReturn();

    String gerado = resultado.getResponse().getHeader(CABECALHO);
    assertThat(gerado).isEqualTo(portas.idsGerados().getFirst().toString());
    assertThat(portas.eventos().getFirst().requestId()).isEqualTo(gerado);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "com\nquebra",
        "com espaco",
        "acentuação",
        "01234567890123456789012345678901234567890123456789012345678901234"
      })
  void requestIdInvalidoEhSubstituido(String invalido) throws Exception {
    MvcResult resultado =
        mvc.perform(postar(credito("10.00"), "chave-rid-invalido").header(CABECALHO, invalido))
            .andExpect(status().isCreated())
            .andReturn();

    String devolvido = resultado.getResponse().getHeader(CABECALHO);
    assertThat(devolvido)
        .isNotEqualTo(invalido)
        .isEqualTo(portas.idsGerados().getFirst().toString());
  }

  @Test
  void errosTrazemOMesmoRequestIdNoCabecalhoENoProblema() throws Exception {
    String token = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.ESCRITA);
    String leitura = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);

    verificarErro(postar("{", "chave-400").header(CABECALHO, "rid-400"), 400, "rid-400");
    verificarErro(
        postarComo(null, credito("1.00"), "c").header(CABECALHO, "rid-401"), 401, "rid-401");
    verificarErro(
        postarComo(leitura, credito("1.00"), "c").header(CABECALHO, "rid-403"), 403, "rid-403");
    verificarErro(
        postarComo(
                token,
                "{\"tipo\":\"CREDITO\",\"valor\":\"1.00\",\"dataOcorrencia\":\"2026-11-30T00:00:00Z\"}",
                "chave-422")
            .header(CABECALHO, "rid-422"),
        422,
        "rid-422");
  }

  @Test
  void mdcEhLimpoAoFimDaRequisicao() throws Exception {
    mvc.perform(postar(credito("10.00"), "chave-mdc").header(CABECALHO, "rid-mdc"))
        .andExpect(status().isCreated());

    assertThat(MDC.get("requestId")).isNull();
  }

  private void verificarErro(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requisicao,
      int status,
      String requestId)
      throws Exception {
    mvc.perform(requisicao)
        .andExpect(status().is(status))
        .andExpect(header().string(CABECALHO, requestId))
        .andExpect(jsonPath("$.requestId").value(requestId));
  }
}
