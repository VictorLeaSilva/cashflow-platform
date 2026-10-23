package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/** AC-T013.5: uma linha JSON de acesso por requisição, sem dados sensíveis. */
@Tag("RNF-012")
@Tag("RNF-005")
@ExtendWith(OutputCaptureExtension.class)
class LogDeAcessoWebTest extends IntegracaoWeb {

  private static final String LOGGER = "br.com.fluxocaixa.lancamentos.acesso";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  void registroGeraUmaLinhaDeAcessoSemDadosSensiveis(CapturedOutput saida) throws Exception {
    String token = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.ESCRITA);
    String corpo =
        "{\"tipo\":\"CREDITO\",\"valor\":\"4321.09\",\"descricao\":\"descricao-secreta\","
            + "\"identificadorExterno\":\"externo-secreto\"}";

    mvc.perform(
            postarComo(token, corpo, "chave-secreta-de-log").header("X-Request-Id", "rid-log-1"))
        .andExpect(status().isCreated());

    List<Map<String, Object>> linhas = linhasDeAcesso(saida, "rid-log-1");
    assertThat(linhas).hasSize(1);
    Map<String, Object> linha = linhas.getFirst();
    assertThat(linha)
        .containsEntry("requestId", "rid-log-1")
        .containsEntry("metodo", "POST")
        .containsEntry("rota", "/api/v1/lancamentos")
        .containsEntry("status", 201)
        .containsKey("duracaoMs");

    assertThat(saida.getAll())
        .doesNotContain(
            token,
            "chave-secreta-de-log",
            COMERCIANTE.toString(),
            "4321.09",
            "descricao-secreta",
            "externo-secreto");
  }

  @Test
  void erroDeAutenticacaoTambemGeraLinhaDeAcesso(CapturedOutput saida) throws Exception {
    mvc.perform(postarComo(null, "{}", "chave").header("X-Request-Id", "rid-log-401"))
        .andExpect(status().isUnauthorized());

    List<Map<String, Object>> linhas = linhasDeAcesso(saida, "rid-log-401");
    assertThat(linhas).hasSize(1);
    assertThat(linhas.getFirst())
        .containsEntry("status", 401)
        .containsEntry("metodo", "POST")
        .containsEntry("rota", "/api/v1/lancamentos");
  }

  @Test
  void respostasAntesDoHandlerRegistramARotaPadrao(CapturedOutput saida) throws Exception {
    String token = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.ESCRITA);
    mvc.perform(
            postarComo(token, "{}", "chave-415")
                .contentType(MediaType.TEXT_PLAIN)
                .header("X-Request-Id", "rid-log-415"))
        .andExpect(status().isUnsupportedMediaType());
    mvc.perform(
            get("/api/v1/lancamentos")
                .header("Authorization", "Bearer " + token)
                .header("X-Request-Id", "rid-log-405"))
        .andExpect(status().isMethodNotAllowed());

    assertThat(linhasDeAcesso(saida, "rid-log-415").getFirst())
        .containsEntry("status", 415)
        .containsEntry("rota", "/api/v1/lancamentos");
    assertThat(linhasDeAcesso(saida, "rid-log-405").getFirst())
        .containsEntry("status", 405)
        .containsEntry("rota", "/api/v1/lancamentos");
  }

  /** AC-T014.10: a rota do estorno aparece como padrão, sem o UUID do caminho. */
  @Test
  void estornoRegistraARotaPadraoSemOId(CapturedOutput saida) throws Exception {
    UUID original = UUID.randomUUID();
    mvc.perform(
            Chamadas.estornar(original, "chave-log-estorno").header("X-Request-Id", "rid-log-est"))
        .andExpect(status().isNotFound());

    assertThat(linhasDeAcesso(saida, "rid-log-est").getFirst())
        .containsEntry("status", 404)
        .containsEntry("metodo", "POST")
        .containsEntry("rota", "/api/v1/lancamentos/{id}/estornos");
    assertThat(saida.getAll()).doesNotContain(original.toString());
  }

  @Test
  void rotaInexistenteEhDesconhecida(CapturedOutput saida) throws Exception {
    String token = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.ESCRITA);
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
