package br.com.fluxocaixa.consolidado.adapter.in.web;

import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.URL;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.diaComo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.periodoComo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.problema;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.support.IntegracaoWeb;
import br.com.fluxocaixa.consolidado.support.RelogioFixo;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import java.time.Duration;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * AC-T023.2 (RNF-005): autenticação JWT (ADR-007, Q-008), audiência própria (Q-050) e o scope
 * {@code consolidado:read}, para GET e HEAD (armadilha da T-015).
 */
@Tag("RNF-005")
class SegurancaJwtWebTest extends IntegracaoWeb {

  private static final String PERIODO = "dataInicio=2026-10-21&dataFim=2026-10-21";

  @Test
  void semTokenResponde401ComBearer() throws Exception {
    mvc.perform(periodoComo(null, PERIODO))
        .andExpect(problema(401, "nao-autenticado"))
        .andExpect(header().string("WWW-Authenticate", Matchers.startsWith("Bearer")));
    mvc.perform(diaComo(null, "2026-10-21"))
        .andExpect(problema(401, "nao-autenticado", URL + "/2026-10-21"));
  }

  static Stream<Arguments> tokensInvalidos() {
    return Stream.of(
        caso("assinado com outra chave", c -> c.assinadoComOutraChave()),
        caso("alg none", c -> c.semAssinatura()),
        caso("HS256", c -> c.hs256()),
        caso("iss errado", c -> c.emissor("https://outro.emissor")),
        caso("aud errada", c -> c.audiencia("outra-api")),
        caso("aud só do Lançamentos", c -> c.audiencia("lancamentos-api")),
        caso(
            "expirado",
            c ->
                c.claim("iat", RelogioFixo.AGORA.minus(Duration.ofHours(2)).getEpochSecond())
                    .expiraEm(RelogioFixo.AGORA.minus(Duration.ofMinutes(5)))),
        caso("sem exp", c -> c.claim("exp", null)),
        caso("nbf no futuro", c -> c.naoAntesDe(RelogioFixo.AGORA.plus(Duration.ofMinutes(5)))),
        caso("sem merchant_id", c -> c.comerciante(null)),
        caso("merchant_id não UUID", c -> c.comerciante("loja-123")));
  }

  private static Arguments caso(String nome, UnaryOperator<TokensDeTeste.Construtor> ajuste) {
    return Arguments.of(nome, ajuste);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("tokensInvalidos")
  void tokenInvalidoResponde401(String nome, UnaryOperator<TokensDeTeste.Construtor> ajuste)
      throws Exception {
    String token =
        ajuste
            .apply(
                TokensDeTeste.novo()
                    .comerciante(COMERCIANTE.toString())
                    .scopes(TokensDeTeste.LEITURA))
            .emitir();

    mvc.perform(periodoComo(token, PERIODO))
        .andExpect(problema(401, "nao-autenticado"))
        .andExpect(header().string("WWW-Authenticate", Matchers.containsString("invalid_token")));
  }

  @Test
  void tokenComAsDuasAudienciasDoDevTokenEhAceito() throws Exception {
    String token =
        TokensDeTeste.novo()
            .comerciante(COMERCIANTE.toString())
            .scopes(TokensDeTeste.LEITURA)
            .audiencias("lancamentos-api", "consolidado-api")
            .emitir();

    mvc.perform(periodoComo(token, PERIODO)).andExpect(status().isOk());
  }

  @Test
  void tokenSemScopeDeLeituraDoConsolidadoResponde403() throws Exception {
    String token =
        TokensDeTeste.valido(
            COMERCIANTE, TokensDeTeste.LEITURA_DE_LANCAMENTOS, "lancamentos:write");

    mvc.perform(periodoComo(token, PERIODO))
        .andExpect(problema(403, "acesso-negado"))
        .andExpect(
            header().string("WWW-Authenticate", Matchers.containsString("insufficient_scope")));
    mvc.perform(diaComo(token, "2026-10-21"))
        .andExpect(problema(403, "acesso-negado", URL + "/2026-10-21"));
  }

  @Test
  void headSegueAsMesmasRegrasDoGet() throws Exception {
    String semScope = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA_DE_LANCAMENTOS);
    String comScope = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);

    mvc.perform(head(URL + "?" + PERIODO)).andExpect(status().isUnauthorized());
    mvc.perform(head(URL + "/2026-10-21").header("Authorization", "Bearer " + semScope))
        .andExpect(status().isForbidden());
    mvc.perform(head(URL + "/2026-10-21").header("Authorization", "Bearer " + comScope))
        .andExpect(status().isOk());
  }

  @Test
  void comercianteVemDoToken() throws Exception {
    UUID outro = UUID.randomUUID();

    mvc.perform(periodoComo(TokensDeTeste.valido(outro, TokensDeTeste.LEITURA), PERIODO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comercianteId").value(outro.toString()));
  }

  @Test
  void dentroDaToleranciaDeRelogioOTokenAindaVale() throws Exception {
    String token =
        TokensDeTeste.novo()
            .comerciante(COMERCIANTE.toString())
            .scopes(TokensDeTeste.LEITURA)
            .claim("iat", RelogioFixo.AGORA.minus(Duration.ofHours(1)).getEpochSecond())
            .expiraEm(RelogioFixo.AGORA.minus(Duration.ofSeconds(30)))
            .emitir();

    mvc.perform(periodoComo(token, PERIODO)).andExpect(status().isOk());
  }
}
