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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/** Requisições e verificações comuns aos testes de {@code POST /api/v1/lancamentos} e estorno. */
final class Chamadas {

  static final String URL = "/api/v1/lancamentos";
  static final MediaType PROBLEMA = MediaType.APPLICATION_PROBLEM_JSON;
  static final UUID COMERCIANTE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

  private static final JsonMapper JSON = JsonMapper.builder().build();

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
    return autenticar(requisicao, token, chave);
  }

  /** Caminho do estorno de {@code id} (texto livre, para os testes de formato). */
  static String urlDoEstorno(Object id) {
    return URL + "/" + id + "/estornos";
  }

  /** POST de estorno sem corpo e sem {@code Content-Type}, como {@link #COMERCIANTE}. */
  static MockHttpServletRequestBuilder estornar(Object id, String chave) {
    return estornarComo(TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.ESCRITA), id, chave);
  }

  static MockHttpServletRequestBuilder estornarComo(String token, Object id, String chave) {
    return autenticar(post(urlDoEstorno(id)).accept(MediaType.APPLICATION_JSON), token, chave);
  }

  private static MockHttpServletRequestBuilder autenticar(
      MockHttpServletRequestBuilder requisicao, String token, String chave) {
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

  /** Campo de texto do corpo JSON de uma resposta. */
  static String campo(MvcResult resultado, String nome) throws Exception {
    return JSON.readTree(resultado.getResponse().getContentAsString()).path(nome).asString();
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

  /** Simula corpo sem tamanho declarado (transfer-encoding chunked). */
  static RequestPostProcessor semContentLength() {
    return requisicao -> new SemContentLength(requisicao);
  }

  private static final class SemContentLength extends MockHttpServletRequest {

    private final MockHttpServletRequest original;

    SemContentLength(MockHttpServletRequest original) {
      super(original.getServletContext(), original.getMethod(), original.getRequestURI());
      this.original = original;
      setContent(original.getContentAsByteArray());
      setContentType(original.getContentType());
      original
          .getHeaderNames()
          .asIterator()
          .forEachRemaining(
              nome ->
                  original.getHeaders(nome).asIterator().forEachRemaining(v -> addHeader(nome, v)));
      setParameters(original.getParameterMap());
    }

    @Override
    public int getContentLength() {
      return -1;
    }

    @Override
    public long getContentLengthLong() {
      return -1;
    }

    @Override
    public String getHeader(String nome) {
      return "Content-Length".equalsIgnoreCase(nome) ? null : super.getHeader(nome);
    }

    @Override
    public String toString() {
      return "SemContentLength[" + original.getRequestURI() + "]";
    }
  }
}
