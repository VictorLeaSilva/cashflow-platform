package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.campo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.estornarComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.listarComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.obterComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-004.1 com PostgreSQL real: o detalhe de um lançamento, inclusive de um estorno, é igual byte a
 * byte ao 201 que o criou, e o item do histórico tem a mesma representação.
 */
@Tag("RF-004")
@AutoConfigureMockMvc
class GetLancamentosIT extends IntegracaoPostgres {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mvc;

  @Test
  void detalheEItemDoHistoricoSaoIguaisAo201() throws Exception {
    UUID comerciante = UUID.randomUUID();
    String escrita = TokensDeTeste.valido(comerciante, TokensDeTeste.ESCRITA);
    String leitura = TokensDeTeste.valido(comerciante, TokensDeTeste.LEITURA);
    MvcResult credito =
        mvc.perform(
                postarComo(
                    escrita,
                    "{\"tipo\":\"CREDITO\",\"valor\":\"321.09\",\"descricao\":\"Venda\","
                        + "\"dataOcorrencia\":\"2026-10-22T10:00:00.123456-03:00\","
                        + "\"identificadorExterno\":\"nf-get-it\"}",
                    "get-it-c"))
            .andExpect(status().isCreated())
            .andReturn();
    String creditoId = campo(credito, "id");
    MvcResult estorno =
        mvc.perform(estornarComo(escrita, creditoId, "get-it-e"))
            .andExpect(status().isCreated())
            .andReturn();

    assertThat(
            corpo(
                mvc.perform(obterComo(leitura, creditoId)).andExpect(status().isOk()).andReturn()))
        .isEqualTo(corpo(credito));
    assertThat(
            corpo(
                mvc.perform(obterComo(leitura, campo(estorno, "id")))
                    .andExpect(status().isOk())
                    .andReturn()))
        .isEqualTo(corpo(estorno));

    String historico =
        corpo(mvc.perform(listarComo(leitura, "")).andExpect(status().isOk()).andReturn());
    assertThat(JSON.readTree(historico).path("itens"))
        .containsExactly(JSON.readTree(corpo(estorno)), JSON.readTree(corpo(credito)));
  }

  private static String corpo(MvcResult resultado) throws Exception {
    return resultado.getResponse().getContentAsString();
  }
}
