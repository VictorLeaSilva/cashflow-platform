package br.com.fluxocaixa.consolidado.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.support.RelogioFixo;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Requisições e verificações comuns aos testes de {@code GET /api/v1/consolidado}. */
final class Chamadas {

  static final String URL = "/api/v1/consolidado";
  static final MediaType PROBLEMA = MediaType.APPLICATION_PROBLEM_JSON;
  static final UUID COMERCIANTE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

  private Chamadas() {}

  /** GET do período com a query informada (sem o {@code ?}), como {@link #COMERCIANTE}. */
  static MockHttpServletRequestBuilder periodo(String query) {
    return periodoComo(TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA), query);
  }

  static MockHttpServletRequestBuilder periodoComo(String token, String query) {
    String url = query == null || query.isEmpty() ? URL : URL + "?" + query;
    return autenticar(get(url).accept(MediaType.APPLICATION_JSON), token);
  }

  /** GET do dia (texto livre, para os testes de formato), como {@link #COMERCIANTE}. */
  static MockHttpServletRequestBuilder dia(String data) {
    return diaComo(TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA), data);
  }

  static MockHttpServletRequestBuilder diaComo(String token, String data) {
    return autenticar(get(URL + "/" + data).accept(MediaType.APPLICATION_JSON), token);
  }

  private static MockHttpServletRequestBuilder autenticar(
      MockHttpServletRequestBuilder requisicao, String token) {
    if (token != null) {
      requisicao.header("Authorization", "Bearer " + token);
    }
    return requisicao;
  }

  /** Erro em {@code problem+json} com os membros de 03 §5.2 e sem detalhe interno. */
  static ResultMatcher problema(int status, String tipo) {
    return problema(status, tipo, URL);
  }

  /** Como {@link #problema(int, String)}, para a requisição feita em {@code instancia}. */
  static ResultMatcher problema(int status, String tipo, String instancia) {
    return resultado -> {
      status().is(status).match(resultado);
      content().contentTypeCompatibleWith(PROBLEMA).match(resultado);
      jsonPath("$.type").value("/erros/" + tipo).match(resultado);
      jsonPath("$.status").value(status).match(resultado);
      jsonPath("$.title").isNotEmpty().match(resultado);
      jsonPath("$.instance").value(instancia).match(resultado);
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
