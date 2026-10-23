package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Requisições e verificações comuns aos testes de {@code POST /api/v1/lancamentos}. */
final class Chamadas {

  static final String URL = "/api/v1/lancamentos";
  static final MediaType PROBLEMA = MediaType.APPLICATION_PROBLEM_JSON;
  static final UUID COMERCIANTE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

  private Chamadas() {}

  /** POST JSON autenticado como {@link #COMERCIANTE} com escrita, com a chave informada. */
  static MockHttpServletRequestBuilder postar(String corpo, String chave) {
    return postarComo(TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.ESCRITA), corpo, chave);
  }

  static MockHttpServletRequestBuilder postarComo(String token, String corpo, String chave) {
    MockHttpServletRequestBuilder requisicao =
        post(URL)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .content(corpo);
    if (token != null) {
      requisicao.header("Authorization", "Bearer " + token);
    }
    if (chave != null) {
      requisicao.header("Idempotency-Key", chave);
    }
    return requisicao;
  }

  static String credito(String valor) {
    return "{\"tipo\":\"CREDITO\",\"valor\":\"" + valor + "\"}";
  }

  /** Erro em {@code problem+json} com os membros de 03 §5.2 e sem detalhe interno. */
  static ResultMatcher problema(int status, String tipo) {
    return resultado -> {
      status().is(status).match(resultado);
      content().contentTypeCompatibleWith(PROBLEMA).match(resultado);
      jsonPath("$.type").value("/erros/" + tipo).match(resultado);
      jsonPath("$.status").value(status).match(resultado);
      jsonPath("$.title").isNotEmpty().match(resultado);
      jsonPath("$.instance").value(URL).match(resultado);
      jsonPath("$.timestamp").value(RelogioFixo.AGORA.toString()).match(resultado);
      String requestId = resultado.getResponse().getHeader("X-Request-Id");
      jsonPath("$.requestId").value(Matchers.equalTo(requestId)).match(resultado);
      jsonPath("$.trace").doesNotExist().match(resultado);
      jsonPath("$.exception").doesNotExist().match(resultado);
      header().exists("X-Request-Id").match(resultado);
    };
  }

  /** Um item de {@code errors[]} com o campo e o código informados. */
  static ResultMatcher erroDeCampo(String campo, String codigo) {
    return jsonPath(
        "$.errors[?(@.campo == '" + campo + "' && @.codigo == '" + codigo + "')]",
        Matchers.not(Matchers.empty()));
  }
}
