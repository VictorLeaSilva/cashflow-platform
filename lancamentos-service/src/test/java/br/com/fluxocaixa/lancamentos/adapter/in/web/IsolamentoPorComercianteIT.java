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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-004.3 e AC-010.1 (risco de 04 §2.7, parte de Lançamentos): com dados de A e de B no PostgreSQL
 * real, nenhum enxerga, lê nem estorna os lançamentos do outro, nos dois sentidos.
 */
@Tag("RF-010")
@Tag("RF-004")
@AutoConfigureMockMvc
class IsolamentoPorComercianteIT extends IntegracaoPostgres {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mvc;

  @Test
  void aEBNaoSeEnxergamEmNenhumSentido() throws Exception {
    Comerciante a = new Comerciante();
    Comerciante b = new Comerciante();
    List<String> deA = List.of(a.registrar("iso-a-1"), a.registrar("iso-a-2"));
    List<String> deB = List.of(b.registrar("iso-b-1"));

    verificarIsolamento(a, deA, b, deB.getFirst());
    verificarIsolamento(b, deB, a, deA.getFirst());
  }

  private void verificarIsolamento(
      Comerciante quem, List<String> proprios, Comerciante outro, String doOutro) throws Exception {
    JsonNode historico =
        JSON.readTree(
            mvc.perform(listarComo(quem.leitura, "size=100"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    List<String> ids = new ArrayList<>();
    historico.path("itens").forEach(item -> ids.add(item.path("id").asString()));
    assertThat(ids).containsExactlyInAnyOrderElementsOf(proprios);
    assertThat(historico.path("total").asLong()).isEqualTo(proprios.size());
    historico
        .path("itens")
        .forEach(
            item ->
                assertThat(item.path("comercianteId").asString()).isEqualTo(quem.id.toString()));

    MvcResult cruzado =
        mvc.perform(obterComo(quem.leitura, doOutro)).andExpect(status().isNotFound()).andReturn();
    MvcResult inexistente =
        mvc.perform(obterComo(quem.leitura, UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andReturn();
    assertThat(semVariaveis(cruzado)).isEqualTo(semVariaveis(inexistente));
    assertThat(semVariaveis(cruzado)).doesNotContain(doOutro, outro.id.toString());

    mvc.perform(estornarComo(quem.escrita, doOutro, "iso-estorno-" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
    mvc.perform(obterComo(outro.leitura, doOutro)).andExpect(status().isOk());
  }

  /** Corpo do problema sem os membros que variam por requisição. */
  private static String semVariaveis(MvcResult resultado) throws Exception {
    return resultado
        .getResponse()
        .getContentAsString()
        .replaceAll("\"(instance|requestId|traceId|timestamp)\":\"[^\"]*\"", "");
  }

  private final class Comerciante {

    private final UUID id = UUID.randomUUID();
    private final String leitura = TokensDeTeste.valido(id, TokensDeTeste.LEITURA);
    private final String escrita = TokensDeTeste.valido(id, TokensDeTeste.ESCRITA);

    String registrar(String chave) throws Exception {
      return campo(
          mvc.perform(postarComo(escrita, "{\"tipo\":\"CREDITO\",\"valor\":\"7.00\"}", chave))
              .andExpect(status().isCreated())
              .andReturn(),
          "id");
    }
  }
}
