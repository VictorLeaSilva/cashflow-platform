package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.URL;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.credito;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.listar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
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

  private static String token(String scope) {
    return "Bearer " + TokensDeTeste.valido(COMERCIANTE, scope);
  }

  private static Arguments caso(
      String nome, int status, Supplier<MockHttpServletRequestBuilder> requisicao) {
    return Arguments.of(nome, status, requisicao);
  }

  static Stream<Arguments> respostas() {
    return Stream.of(
        caso("201 registro", 201, () -> postar(credito("10.00"), "chave-hdr-201")),
        caso("200 histórico", 200, () -> listar("")),
        caso("401 sem token", 401, () -> postarComo(null, credito("10.00"), "chave-hdr-401")),
        caso(
            "403 sem scope de escrita",
            403,
            () ->
                postarComo(
                    TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA),
                    credito("10.00"),
                    "chave-hdr-403")),
        caso("400 valor negativo", 400, () -> postar(credito("-1.00"), "chave-hdr-400")),
        caso(
            "404 rota inexistente",
            404,
            () -> get("/api/v1/inexistente").header("Authorization", token("lancamentos:read"))),
        caso(
            "405 método não permitido",
            405,
            () -> delete(URL).header("Authorization", token("lancamentos:write"))),
        caso(
            "406 accept incompatível",
            406,
            () -> postar(credito("10.00"), "chave-hdr-406").accept(MediaType.APPLICATION_XML)),
        caso(
            "413 corpo grande",
            413,
            () -> postar("x".repeat(LimiteDeCorpoFilter.LIMITE_BYTES + 1), "chave-hdr-413")),
        caso(
            "415 content-type",
            415,
            () -> postar(credito("10.00"), "chave-hdr-415").contentType(MediaType.TEXT_PLAIN)),
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
