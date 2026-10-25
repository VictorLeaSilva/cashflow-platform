package br.com.fluxocaixa.consolidado.adapter.in.web;

import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.URL;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.periodo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.periodoComo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.support.IntegracaoWeb;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * AC-T034.1 (RNF-005, API8): toda resposta da API, de sucesso ou de erro, traz os cabeçalhos
 * seguros da Q-067, e nenhuma traz HSTS (fica na borda, ADR-010) nem CORS.
 */
@Tag("RNF-005")
class CabecalhosDeSegurancaWebTest extends IntegracaoWeb {

  static final String CSP = "default-src 'none'; frame-ancestors 'none'";
  private static final String PERIODO = "dataInicio=2026-10-21&dataFim=2026-10-21";

  private static Arguments caso(
      String nome, int status, Supplier<MockHttpServletRequestBuilder> requisicao) {
    return Arguments.of(nome, status, requisicao);
  }

  static Stream<Arguments> respostas() {
    return Stream.of(
        caso("200 período", 200, () -> periodo(PERIODO)),
        caso("401 sem token", 401, () -> periodoComo(null, PERIODO)),
        caso(
            "403 sem scope de leitura",
            403,
            () ->
                periodoComo(
                    TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA_DE_LANCAMENTOS),
                    PERIODO)),
        caso("400 data inválida", 400, () -> periodo("dataInicio=ontem&dataFim=hoje")),
        caso(
            "404 rota inexistente",
            404,
            () ->
                get("/api/v1/inexistente")
                    .header(
                        "Authorization",
                        "Bearer " + TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA))),
        caso(
            "405 método não permitido",
            405,
            () ->
                delete(URL)
                    .header(
                        "Authorization",
                        "Bearer " + TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA))),
        caso(
            "406 accept incompatível",
            406,
            () -> periodo(PERIODO).accept(MediaType.APPLICATION_XML)),
        caso(
            "413 corpo grande",
            413,
            () ->
                periodo(PERIODO)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("x".repeat(LimiteDeCorpoFilter.LIMITE_BYTES + 1))),
        caso("405 options", 405, () -> options(URL)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("respostas")
  void todaRespostaTrazOsCabecalhosSeguros(
      String nome, int status, Supplier<MockHttpServletRequestBuilder> requisicao)
      throws Exception {
    mvc.perform(requisicao.get())
        .andExpect(status().is(status))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
        .andExpect(header().string("Content-Security-Policy", CSP))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().doesNotExist("Strict-Transport-Security"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }
}
