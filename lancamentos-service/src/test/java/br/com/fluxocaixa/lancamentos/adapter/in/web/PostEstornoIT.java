package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.campo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.estornarComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Estorno ponta a ponta com PostgreSQL real: replay igual byte a byte (AC-007.2), replay no dia
 * seguinte sem recalcular a data (AC-T014.2, Q-011) e o evento do estorno na outbox com o request
 * ID (AC-T014.3).
 */
@Tag("RF-003")
@Tag("RF-007")
@Tag("RNF-012")
@AutoConfigureMockMvc
class PostEstornoIT extends IntegracaoPostgres {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;
  @Autowired private RelogioFixo.Controlado relogio;

  @Test
  void replayDevolveOMesmoCorpoMesmoNoDiaSeguinte() throws Exception {
    UUID comerciante = UUID.randomUUID();
    // Válido até depois do relógio adiantado para o dia seguinte.
    String token =
        TokensDeTeste.novo()
            .comerciante(comerciante.toString())
            .scopes(TokensDeTeste.ESCRITA)
            .expiraEm(Instant.parse("2026-10-25T00:00:00Z"))
            .emitir();
    String original = registrar(token, "{\"tipo\":\"CREDITO\",\"valor\":\"70.00\"}", "it-est-o");

    MvcResult primeiro =
        mvc.perform(estornarComo(token, original, "it-est-1"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.dataReferencia").value("2026-10-23"))
            .andReturn();
    relogio.ajustar(Instant.parse("2026-10-24T15:00:00Z"));
    MvcResult replay =
        mvc.perform(estornarComo(token, original, "it-est-1"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andReturn();

    assertThat(replay.getResponse().getContentAsString())
        .isEqualTo(primeiro.getResponse().getContentAsString());
    assertThat(replay.getResponse().getHeader("Location"))
        .isEqualTo(primeiro.getResponse().getHeader("Location"));
    assertThat(contar("lancamento", comerciante)).isEqualTo(2);
    assertThat(contar("idempotency_key", comerciante)).isEqualTo(2);
    assertThat(eventos(comerciante)).isEqualTo(2);
  }

  @Test
  void eventoDoEstornoTemOsCamposDoContratoEORequestId() throws Exception {
    UUID comerciante = UUID.randomUUID();
    String token = TokensDeTeste.valido(comerciante, TokensDeTeste.ESCRITA);
    String original = registrar(token, "{\"tipo\":\"DEBITO\",\"valor\":\"12.30\"}", "it-ev-o");

    MvcResult estorno =
        mvc.perform(estornarComo(token, original, "it-ev-1").header("X-Request-Id", "it-rid-est"))
            .andExpect(status().isCreated())
            .andReturn();
    String estornoId = campo(estorno, "id");

    Map<String, Object> linha =
        jdbc.sql(
                "SELECT payload::text AS payload, message_key, request_id, event_type"
                    + " FROM outbox_event WHERE aggregate_id = ?")
            .param(UUID.fromString(estornoId))
            .query()
            .singleRow();
    JsonNode payload = JSON.readTree((String) linha.get("payload"));
    assertThat(linha)
        .containsEntry("message_key", comerciante.toString())
        .containsEntry("request_id", "it-rid-est")
        .containsEntry("event_type", "LancamentoRegistrado");
    assertThat(payload.path("tipo").asString()).isEqualTo("ESTORNO");
    assertThat(payload.path("tipoOriginal").asString()).isEqualTo("DEBITO");
    assertThat(payload.path("referenciaLancamentoId").asString()).isEqualTo(original);
    assertThat(payload.path("lancamentoId").asString()).isEqualTo(estornoId);
    assertThat(payload.path("valor").isString()).isTrue();
    assertThat(payload.path("valor").asString()).isEqualTo("12.30");
    assertThat(payload.path("dataReferencia").asString()).isEqualTo("2026-10-23");
    assertThat(payload.path("comercianteId").asString()).isEqualTo(comerciante.toString());
  }

  private String registrar(String token, String corpo, String chave) throws Exception {
    return campo(
        mvc.perform(postarComo(token, corpo, chave)).andExpect(status().isCreated()).andReturn(),
        "id");
  }

  private long contar(String tabela, UUID comerciante) {
    return jdbc.sql("SELECT count(*) FROM " + tabela + " WHERE comerciante_id = ?")
        .param(comerciante)
        .query(Long.class)
        .single();
  }

  private long eventos(UUID comerciante) {
    return jdbc.sql("SELECT count(*) FROM outbox_event WHERE message_key = ?")
        .param(comerciante.toString())
        .query(Long.class)
        .single();
  }
}
