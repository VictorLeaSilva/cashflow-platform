package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-T032.3 (RNF-012, 03 §5.2): um erro devolve {@code problem+json} com {@code traceId} não nulo,
 * igual ao {@code traceId} da linha do log de acesso da mesma requisição. Vale para erros que
 * nascem no controller (400) e na cadeia de segurança (401).
 */
@Tag("RNF-012")
@Tag("RNF-006")
@AutoConfigureMetrics
@ExtendWith(OutputCaptureExtension.class)
class TraceIdNosErrosWebTest extends IntegracaoWeb {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  void erroDeValidacaoTrazOTraceIdDoLogDeAcesso(CapturedOutput saida) throws Exception {
    MvcResult resultado =
        mvc.perform(
                postar("{\"tipo\":\"CREDITO\",\"valor\":\"-1\"}", "chave-trace-400")
                    .header("X-Request-Id", "rid-trace-400"))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertTraceIdIgualAoDoLog(resultado, saida, "rid-trace-400");
  }

  @Test
  void erroDeAutenticacaoTrazOTraceIdDoLogDeAcesso(CapturedOutput saida) throws Exception {
    MvcResult resultado =
        mvc.perform(
                postarComo(null, "{}", "chave-trace-401").header("X-Request-Id", "rid-trace-401"))
            .andExpect(status().isUnauthorized())
            .andReturn();

    assertTraceIdIgualAoDoLog(resultado, saida, "rid-trace-401");
  }

  private static void assertTraceIdIgualAoDoLog(
      MvcResult resultado, CapturedOutput saida, String requestId) throws Exception {
    Map<?, ?> problema = JSON.readValue(resultado.getResponse().getContentAsString(), Map.class);
    Object traceId = problema.get("traceId");

    assertThat(traceId).isInstanceOf(String.class);
    assertThat((String) traceId).matches("[0-9a-f]{32}");
    Map<?, ?> acesso =
        saida
            .getAll()
            .lines()
            .filter(l -> l.contains("br.com.fluxocaixa.lancamentos.acesso"))
            .filter(l -> l.contains("\"" + requestId + "\""))
            .map(l -> JSON.readValue(l, Map.class))
            .findFirst()
            .orElseThrow();
    assertThat(acesso.get("traceId")).isEqualTo(traceId);
    assertThat(problema.get("requestId")).isEqualTo(requestId);
  }
}
