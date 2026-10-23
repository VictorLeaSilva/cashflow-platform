package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.campo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.estornarComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AC-003.5 (risco de 04 §2.7): N requisições HTTP simultâneas, cada uma com uma chave diferente,
 * para o mesmo original → exatamente um 201 e N−1 409 {@code lancamento-ja-estornado}; o índice
 * único parcial é a única garantia.
 */
@Tag("RF-003")
@Tag("RNF-004")
@AutoConfigureMockMvc
class EstornoConcorrenteIT extends IntegracaoPostgres {

  private static final int N = 10;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;

  @Test
  void estornosSimultaneosComChavesDiferentesGeramUm201ENove409() throws Exception {
    UUID comerciante = UUID.randomUUID();
    String token = TokensDeTeste.valido(comerciante, TokensDeTeste.ESCRITA);
    String original =
        campo(
            mvc.perform(postarComo(token, "{\"tipo\":\"CREDITO\",\"valor\":\"500.00\"}", "conc-o"))
                .andExpect(status().isCreated())
                .andReturn(),
            "id");

    List<MockHttpServletResponse> respostas = estornarAoMesmoTempo(token, original);

    assertThat(respostas).filteredOn(r -> r.getStatus() == 201).hasSize(1);
    assertThat(respostas).filteredOn(r -> r.getStatus() == 409).hasSize(N - 1);
    for (MockHttpServletResponse resposta : respostas) {
      if (resposta.getStatus() == 409) {
        assertThat(resposta.getContentAsString()).contains("/erros/lancamento-ja-estornado");
      }
    }
    assertThat(contar("SELECT count(*) FROM lancamento WHERE comerciante_id = ?", comerciante))
        .isEqualTo(2);
    assertThat(
            contar(
                "SELECT count(*) FROM lancamento WHERE comerciante_id = ? AND tipo = 'ESTORNO'",
                comerciante))
        .isEqualTo(1);
    assertThat(contar("SELECT count(*) FROM idempotency_key WHERE comerciante_id = ?", comerciante))
        .isEqualTo(2);
    assertThat(
            jdbc.sql("SELECT count(*) FROM outbox_event WHERE message_key = ?")
                .param(comerciante.toString())
                .query(Long.class)
                .single())
        .isEqualTo(2);
  }

  /** N estornos do mesmo original, cada um com sua chave, liberados juntos por um latch. */
  private List<MockHttpServletResponse> estornarAoMesmoTempo(String token, String original)
      throws Exception {
    CountDownLatch prontas = new CountDownLatch(N);
    CountDownLatch largada = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(N);
    try {
      List<Future<MockHttpServletResponse>> futuras = new ArrayList<>();
      for (int i = 0; i < N; i++) {
        String chave = "conc-estorno-" + i;
        futuras.add(
            executor.submit(
                () -> {
                  prontas.countDown();
                  largada.await();
                  return mvc.perform(estornarComo(token, original, chave))
                      .andReturn()
                      .getResponse();
                }));
      }
      assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
      largada.countDown();
      List<MockHttpServletResponse> respostas = new ArrayList<>();
      for (Future<MockHttpServletResponse> futura : futuras) {
        respostas.add(futura.get(30, TimeUnit.SECONDS));
      }
      return respostas;
    } finally {
      executor.shutdownNow();
    }
  }

  private long contar(String sql, UUID comerciante) {
    return jdbc.sql(sql).param(comerciante).query(Long.class).single();
  }
}
