package br.com.fluxocaixa.consolidado.adapter.in.web;

import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.URL;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.periodo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.problema;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.support.IntegracaoWeb;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-T034.2 e AC-T034.3 (RNF-005, API8/API9; Q-067, Q-075): CORS fechado e {@code OPTIONS} em
 * {@code /api/v1/**} responde 405 em {@code problem+json}, com ou sem token e com ou sem {@code
 * Origin}, passando pela cadeia de segurança real.
 */
@Tag("RNF-005")
class CorsEOptionsWebTest extends IntegracaoWeb {

  private static final String ORIGEM = "https://outro-site.exemplo";

  @Test
  void optionsSemTokenResponde405ComRequestIdETraceId() throws Exception {
    mvc.perform(options(URL))
        .andExpect(problema(405, "metodo-nao-permitido"))
        .andExpect(
            jsonPath("$.traceId").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{32}")));
  }

  @Test
  void optionsComTokenTambemResponde405() throws Exception {
    String token = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);

    mvc.perform(options(URL).header("Authorization", "Bearer " + token))
        .andExpect(problema(405, "metodo-nao-permitido"));
  }

  @Test
  void optionsEmRotaDeRecursoEEmRotaInexistenteResponde405() throws Exception {
    String estorno = URL + "/2026-10-21";
    mvc.perform(options(estorno)).andExpect(problema(405, "metodo-nao-permitido", estorno));
    mvc.perform(options("/api/v1/nada"))
        .andExpect(problema(405, "metodo-nao-permitido", "/api/v1/nada"));
  }

  @Test
  void preflightDeQualquerOrigemEhRecusadoSemCabecalhosCors() throws Exception {
    mvc.perform(
            options(URL)
                .header("Origin", ORIGEM)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
        .andExpect(problema(405, "metodo-nao-permitido"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Methods"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Headers"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
  }

  @Test
  void requisicaoComOriginNaoRecebeAllowOrigin() throws Exception {
    mvc.perform(periodo("dataInicio=2026-10-21&dataFim=2026-10-21").header("Origin", ORIGEM))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
  }
}
