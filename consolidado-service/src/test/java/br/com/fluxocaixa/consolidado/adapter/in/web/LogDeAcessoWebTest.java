package br.com.fluxocaixa.consolidado.adapter.in.web;

import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.dia;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.periodo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.periodoComo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.support.IntegracaoWeb;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

/** AC-T023.3: uma linha JSON de acesso por requisição, com a rota padrão e sem dados sensíveis. */
@Tag("RNF-012")
@Tag("RNF-005")
@ExtendWith(OutputCaptureExtension.class)
class LogDeAcessoWebTest extends IntegracaoWeb {

  private static final String LOGGER = "br.com.fluxocaixa.consolidado.acesso";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  void consultaGeraUmaLinhaComARotaPadraoSemQueryNemDadosSensiveis(CapturedOutput saida)
      throws Exception {
    String token = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);

    mvc.perform(
            periodoComo(token, "dataInicio=2026-10-01&dataFim=2026-10-03")
                .header("X-Request-Id", "rid-log-1"))
        .andExpect(status().isOk());
    mvc.perform(dia("2026-10-21").header("X-Request-Id", "rid-log-dia")).andExpect(status().isOk());

    List<Map<String, Object>> linhas = linhasDeAcesso(saida, "rid-log-1");
    assertThat(linhas).hasSize(1);
    assertThat(linhas.getFirst())
        .containsEntry("requestId", "rid-log-1")
        .containsEntry("metodo", "GET")
        .containsEntry("rota", "/api/v1/consolidado")
        .containsEntry("status", 200)
        .containsKey("duracaoMs");
    assertThat(linhasDeAcesso(saida, "rid-log-dia").getFirst())
        .containsEntry("rota", "/api/v1/consolidado/{data}");
    assertThat(saida.getAll())
        .doesNotContain(token, COMERCIANTE.toString(), "dataInicio=2026-10-01", "2026-10-21");
  }

  @Test
  void respostasAntesDoHandlerRegistramARotaPadrao(CapturedOutput saida) throws Exception {
    String token = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);
    mvc.perform(periodoComo(null, "").header("X-Request-Id", "rid-log-401"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            delete("/api/v1/consolidado")
                .header("Authorization", "Bearer " + token)
                .header("X-Request-Id", "rid-log-405"))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(periodo("dataInicio=x").header("X-Request-Id", "rid-log-400"))
        .andExpect(status().isBadRequest());

    assertThat(linhasDeAcesso(saida, "rid-log-401").getFirst())
        .containsEntry("status", 401)
        .containsEntry("rota", "/api/v1/consolidado");
    assertThat(linhasDeAcesso(saida, "rid-log-405").getFirst())
        .containsEntry("status", 405)
        .containsEntry("rota", "/api/v1/consolidado");
    assertThat(linhasDeAcesso(saida, "rid-log-400").getFirst()).containsEntry("status", 400);
  }

  @Test
  void rotaInexistenteEhDesconhecida(CapturedOutput saida) throws Exception {
    String token = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);
    mvc.perform(
            get("/api/v1/nao-existe/123")
                .header("Authorization", "Bearer " + token)
                .header("X-Request-Id", "rid-log-404"))
        .andExpect(status().isNotFound());

    assertThat(linhasDeAcesso(saida, "rid-log-404").getFirst())
        .containsEntry("status", 404)
        .containsEntry("rota", "desconhecida");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> linhasDeAcesso(CapturedOutput saida, String requestId) {
    return saida
        .getOut()
        .lines()
        .filter(linha -> linha.startsWith("{") && linha.contains(LOGGER))
        .map(linha -> (Map<String, Object>) JSON.readValue(linha, Map.class))
        .filter(linha -> requestId.equals(linha.get("requestId")))
        .toList();
  }
}
