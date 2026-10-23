package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.credito;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.problema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Ponta a ponta com PostgreSQL real: AC-001.1, AC-002.1 e AC-007.2 no HTTP, o X-Request-Id na
 * outbox (AC-T013.2) e o identificador externo duplicado (AC-T013.11).
 */
@Tag("RF-001")
@Tag("RF-002")
@Tag("RF-007")
@Tag("RNF-012")
@AutoConfigureMockMvc
class PostLancamentoIT extends IntegracaoPostgres {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;

  @Test
  void registroGravaLancamentoEventoComRequestIdEReplayDevolveOMesmoCorpo() throws Exception {
    UUID comerciante = UUID.randomUUID();
    String token = TokensDeTeste.valido(comerciante, TokensDeTeste.ESCRITA);
    String corpo = "{\"tipo\":\"DEBITO\",\"valor\":\"80.25\",\"descricao\":\"Fornecedor\"}";

    MvcResult primeiro =
        mvc.perform(postarComo(token, corpo, "it-chave-1").header("X-Request-Id", "it-rid-1"))
            .andExpect(status().isCreated())
            .andReturn();
    MvcResult replay =
        mvc.perform(postarComo(token, corpo, "it-chave-1").header("X-Request-Id", "it-rid-2"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andExpect(header().string("X-Request-Id", "it-rid-2"))
            .andReturn();

    assertThat(replay.getResponse().getContentAsString())
        .isEqualTo(primeiro.getResponse().getContentAsString());
    assertThat(contar("lancamento", comerciante)).isEqualTo(1);
    assertThat(contar("idempotency_key", comerciante)).isEqualTo(1);
    assertThat(
            jdbc.sql("SELECT request_id FROM outbox_event WHERE message_key = ?")
                .param(comerciante.toString())
                .query(String.class)
                .list())
        .containsExactly("it-rid-1");
  }

  @Test
  void creditoComComercianteDoToken() throws Exception {
    UUID comerciante = UUID.randomUUID();
    String token = TokensDeTeste.valido(comerciante, TokensDeTeste.ESCRITA);

    mvc.perform(postarComo(token, credito("100.00"), "it-credito")).andExpect(status().isCreated());

    assertThat(contar("lancamento", comerciante)).isEqualTo(1);
  }

  @Test
  void identificadorExternoRepetidoResponde409SemGravar() throws Exception {
    UUID comerciante = UUID.randomUUID();
    String token = TokensDeTeste.valido(comerciante, TokensDeTeste.ESCRITA);
    String corpo =
        "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\",\"identificadorExterno\":\"nf-it-1\"}";
    mvc.perform(postarComo(token, corpo, "it-ext-a")).andExpect(status().isCreated());

    MvcResult duplicado =
        mvc.perform(postarComo(token, corpo, "it-ext-b"))
            .andExpect(problema(409, "identificador-externo-duplicado"))
            .andReturn();

    assertThat(duplicado.getResponse().getContentAsString())
        .doesNotContain("nf-it-1", comerciante.toString());
    assertThat(contar("lancamento", comerciante)).isEqualTo(1);
    assertThat(contar("idempotency_key", comerciante)).isEqualTo(1);
  }

  private long contar(String tabela, UUID comerciante) {
    return jdbc.sql("SELECT count(*) FROM " + tabela + " WHERE comerciante_id = ?")
        .param(comerciante)
        .query(Long.class)
        .single();
  }
}
