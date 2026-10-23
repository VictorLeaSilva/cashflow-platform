package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.credito;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.problema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
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

/** AC-001.4 e AC-T013.1: autenticação JWT (ADR-007, Q-008) e autorização por scope. */
@Tag("RNF-005")
class SegurancaJwtWebTest extends IntegracaoWeb {

  @Test
  void semTokenResponde401ComBearer() throws Exception {
    mvc.perform(postarComo(null, credito("10.00"), "chave-sem-token"))
        .andExpect(problema(401, "nao-autenticado"))
        .andExpect(header().string("WWW-Authenticate", Matchers.startsWith("Bearer")));
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }

  static Stream<Arguments> tokensInvalidos() {
    return Stream.of(
        caso("assinado com outra chave", c -> c.assinadoComOutraChave()),
        caso("alg none", c -> c.semAssinatura()),
        caso("HS256", c -> c.hs256()),
        caso("iss errado", c -> c.emissor("https://outro.emissor")),
        caso("aud errada", c -> c.audiencia("outra-api")),
        caso("expirado", c -> c.expiraEm(RelogioFixo.AGORA.minus(Duration.ofMinutes(5)))),
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
                    .scopes(TokensDeTeste.ESCRITA))
            .emitir();

    mvc.perform(postarComo(token, credito("10.00"), "chave-" + nome.hashCode()))
        .andExpect(problema(401, "nao-autenticado"))
        .andExpect(header().string("WWW-Authenticate", Matchers.containsString("invalid_token")));
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }

  @Test
  void tokenSemScopeDeEscritaResponde403() throws Exception {
    String token = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);

    mvc.perform(postarComo(token, credito("10.00"), "chave-sem-scope"))
        .andExpect(problema(403, "acesso-negado"))
        .andExpect(
            header().string("WWW-Authenticate", Matchers.containsString("insufficient_scope")));
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }

  @Test
  void comercianteVemDoToken() throws Exception {
    UUID outro = UUID.randomUUID();
    String token = TokensDeTeste.valido(outro, TokensDeTeste.ESCRITA);

    mvc.perform(postarComo(token, credito("10.00"), "chave-outro"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.comercianteId").value(outro.toString()));
  }

  @Test
  void dentroDaToleranciaDeRelogioOTokenAindaVale() throws Exception {
    String token =
        TokensDeTeste.novo()
            .comerciante(COMERCIANTE.toString())
            .scopes(TokensDeTeste.ESCRITA)
            .claim("iat", RelogioFixo.AGORA.minus(Duration.ofHours(1)).getEpochSecond())
            .expiraEm(RelogioFixo.AGORA.minus(Duration.ofSeconds(30)))
            .emitir();

    mvc.perform(postarComo(token, credito("10.00"), "chave-tolerancia"))
        .andExpect(status().isCreated());
  }
}
