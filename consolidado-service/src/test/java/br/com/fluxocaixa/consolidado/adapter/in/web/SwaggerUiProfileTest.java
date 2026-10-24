package br.com.fluxocaixa.consolidado.adapter.in.web;

import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.COMERCIANTE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.support.IntegracaoWeb;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/** AC-T023.2: Swagger UI e contrato só com o profile {@code local} (como na T-013). */
@Tag("RNF-014")
@Tag("RNF-005")
class SwaggerUiProfileTest {

  @Nested
  @ActiveProfiles("local")
  class ComProfileLocal extends IntegracaoWeb {

    @Test
    void swaggerUiResponde200SemToken() throws Exception {
      mvc.perform(get("/swagger-ui/index.html"))
          .andExpect(status().isOk())
          .andExpect(content().string(Matchers.containsString("/v3/api-docs")));
    }

    @Test
    void assetsDaSwaggerUiRespondem200SemToken() throws Exception {
      mvc.perform(get("/webjars/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
    }

    @Test
    void contratoEhOYamlVersionado() throws Exception {
      mvc.perform(get("/v3/api-docs"))
          .andExpect(status().isOk())
          .andExpect(content().string(Matchers.containsString("openapi: 3.1")))
          .andExpect(content().string(Matchers.containsString("/consolidado/{data}")));
    }
  }

  @Nested
  class SemProfile extends IntegracaoWeb {

    @Test
    void rotasRespondem401SemToken() throws Exception {
      mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
      mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void rotasNaoExistemMesmoComToken() throws Exception {
      String token = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);
      mvc.perform(get("/swagger-ui/index.html").header("Authorization", "Bearer " + token))
          .andExpect(status().isNotFound());
      mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
          .andExpect(status().isNotFound());
      mvc.perform(
              get("/webjars/swagger-ui/swagger-ui-bundle.js")
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isNotFound());
    }
  }
}
