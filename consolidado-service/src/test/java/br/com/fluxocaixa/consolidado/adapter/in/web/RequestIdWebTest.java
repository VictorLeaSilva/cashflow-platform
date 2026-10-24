package br.com.fluxocaixa.consolidado.adapter.in.web;

import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.periodo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.periodoComo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.support.IntegracaoWeb;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** AC-T023.3 (RNF-012): o X-Request-Id volta em toda resposta, inclusive nos erros. */
@Tag("RNF-012")
class RequestIdWebTest extends IntegracaoWeb {

  private static final String CABECALHO = "X-Request-Id";
  private static final String PERIODO = "dataInicio=2026-10-21&dataFim=2026-10-21";

  @Test
  void requestIdValidoEhDevolvido() throws Exception {
    mvc.perform(periodo(PERIODO).header(CABECALHO, "cliente-123.abc_X"))
        .andExpect(status().isOk())
        .andExpect(header().string(CABECALHO, "cliente-123.abc_X"));
  }

  @Test
  void requestIdAusenteEhGeradoPeloIdGenerator() throws Exception {
    mvc.perform(periodo(PERIODO))
        .andExpect(status().isOk())
        .andExpect(header().string(CABECALHO, new UUID(0L, 1L).toString()));
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
        mvc.perform(periodo(PERIODO).header(CABECALHO, invalido))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(resultado.getResponse().getHeader(CABECALHO))
        .isNotEqualTo(invalido)
        .isEqualTo(new UUID(0L, 1L).toString());
  }

  @Test
  void errosTrazemOMesmoRequestIdNoCabecalhoENoProblema() throws Exception {
    String semScope = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA_DE_LANCAMENTOS);

    verificarErro(periodo("dataInicio=x").header(CABECALHO, "rid-400"), 400, "rid-400");
    verificarErro(periodoComo(null, PERIODO).header(CABECALHO, "rid-401"), 401, "rid-401");
    verificarErro(periodoComo(semScope, PERIODO).header(CABECALHO, "rid-403"), 403, "rid-403");
    verificarErro(
        periodo(PERIODO).accept(MediaType.APPLICATION_XML).header(CABECALHO, "rid-406"),
        406,
        "rid-406");
  }

  @Test
  void mdcEhLimpoAoFimDaRequisicao() throws Exception {
    mvc.perform(periodo(PERIODO).header(CABECALHO, "rid-mdc")).andExpect(status().isOk());

    assertThat(MDC.get("requestId")).isNull();
  }

  private void verificarErro(MockHttpServletRequestBuilder requisicao, int status, String requestId)
      throws Exception {
    mvc.perform(requisicao)
        .andExpect(status().is(status))
        .andExpect(header().string(CABECALHO, requestId))
        .andExpect(jsonPath("$.requestId").value(requestId));
  }
}
