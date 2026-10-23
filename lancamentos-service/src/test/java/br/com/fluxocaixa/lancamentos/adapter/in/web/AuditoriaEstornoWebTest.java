package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.campo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.estornarComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-T014.9 (03 §6, Q-021): um estorno criado gera exatamente uma linha de auditoria; replay e erro
 * não geram; a linha não tem valor, comerciante, token nem chave.
 */
@Tag("RF-003")
@Tag("RNF-012")
@Tag("RNF-005")
@ExtendWith(OutputCaptureExtension.class)
class AuditoriaEstornoWebTest extends IntegracaoWeb {

  private static final String LOGGER = "br.com.fluxocaixa.lancamentos.auditoria";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  void estornoCriadoGeraUmaLinhaDeAuditoriaSemDadosSensiveis(CapturedOutput saida)
      throws Exception {
    String token = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.ESCRITA);
    MvcResult original =
        mvc.perform(
                postarComo(
                    token, "{\"tipo\":\"DEBITO\",\"valor\":\"9876.54\"}", "chave-original-aud"))
            .andExpect(status().isCreated())
            .andReturn();
    String originalId = campo(original, "id");

    MvcResult estorno =
        mvc.perform(
                estornarComo(token, originalId, "chave-secreta-aud")
                    .header("X-Request-Id", "rid-aud-1"))
            .andExpect(status().isCreated())
            .andReturn();
    mvc.perform(
            estornarComo(token, originalId, "chave-secreta-aud")
                .header("X-Request-Id", "rid-aud-2"))
        .andExpect(status().isCreated());
    mvc.perform(
            estornarComo(token, originalId, "outra-chave-aud").header("X-Request-Id", "rid-aud-3"))
        .andExpect(status().isConflict());

    List<Map<String, Object>> linhas = linhasDeAuditoria(saida);
    assertThat(linhas).hasSize(1);
    assertThat(linhas.getFirst())
        .containsEntry("evento", "estorno-registrado")
        .containsEntry("requestId", "rid-aud-1")
        .containsEntry("estornoId", campo(estorno, "id"))
        .containsEntry("lancamentoOriginalId", originalId)
        .containsEntry("tipoOriginal", "DEBITO")
        .containsEntry("dataReferencia", "2026-10-23");
    assertThat(linhas.getFirst().toString())
        .doesNotContain("9876.54", COMERCIANTE.toString(), token, "chave-secreta-aud");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> linhasDeAuditoria(CapturedOutput saida) {
    return saida
        .getOut()
        .lines()
        .filter(linha -> linha.startsWith("{") && linha.contains(LOGGER))
        .map(linha -> (Map<String, Object>) JSON.readValue(linha, Map.class))
        .toList();
  }
}
