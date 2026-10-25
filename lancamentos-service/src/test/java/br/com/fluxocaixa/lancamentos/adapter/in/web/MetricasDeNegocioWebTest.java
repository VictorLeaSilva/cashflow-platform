package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.credito;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.estornar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AC-T032.2 (03 §8, Q-063): {@code lancamentos.registrados{tipo}} sobe 1 por lançamento novo e não
 * sobe no <i>replay</i>; {@code idempotency.replays} sobe no <i>replay</i>; {@code
 * idempotency.conflicts} sobe no 422 de reuso de chave. Um 409 de estorno duplicado e um 400 de
 * validação não mexem em nenhuma.
 */
@Tag("RNF-006")
class MetricasDeNegocioWebTest extends IntegracaoWeb {

  @Autowired private MeterRegistry metricas;

  @Test
  void lancamentoNovoContaUmRegistradoDoTipoEReplayContaSoOReplay() throws Exception {
    double creditosAntes = registrados("CREDITO");
    double replaysAntes = contador("idempotency.replays");

    mvc.perform(postar(credito("10.00"), "chave-metrica-1")).andExpect(status().isCreated());
    assertThat(registrados("CREDITO")).isEqualTo(creditosAntes + 1);

    mvc.perform(postar(credito("10.00"), "chave-metrica-1")).andExpect(status().isCreated());
    assertThat(registrados("CREDITO")).isEqualTo(creditosAntes + 1);
    assertThat(contador("idempotency.replays")).isEqualTo(replaysAntes + 1);
  }

  @Test
  void debitoContaNoSeuProprioTipo() throws Exception {
    double debitosAntes = registrados("DEBITO");
    double creditosAntes = registrados("CREDITO");

    mvc.perform(postar("{\"tipo\":\"DEBITO\",\"valor\":\"3.00\"}", "chave-metrica-debito"))
        .andExpect(status().isCreated());

    assertThat(registrados("DEBITO")).isEqualTo(debitosAntes + 1);
    assertThat(registrados("CREDITO")).isEqualTo(creditosAntes);
  }

  @Test
  void reusoDeChaveComOutroCorpoContaUmConflito() throws Exception {
    mvc.perform(postar(credito("10.00"), "chave-metrica-2")).andExpect(status().isCreated());
    double conflitosAntes = contador("idempotency.conflicts");
    double creditosAntes = registrados("CREDITO");
    double replaysAntes = contador("idempotency.replays");

    mvc.perform(postar(credito("11.00"), "chave-metrica-2"))
        .andExpect(status().isUnprocessableContent());

    assertThat(contador("idempotency.conflicts")).isEqualTo(conflitosAntes + 1);
    assertThat(registrados("CREDITO")).isEqualTo(creditosAntes);
    assertThat(contador("idempotency.replays")).isEqualTo(replaysAntes);
  }

  @Test
  void estornoContaNoTipoEstornoEODuplicadoNaoMexeEmNada() throws Exception {
    MvcResult criado = mvc.perform(postar(credito("20.00"), "chave-metrica-3")).andReturn();
    String id = Chamadas.campo(criado, "id");
    double estornosAntes = registrados("ESTORNO");
    double conflitosAntes = contador("idempotency.conflicts");

    mvc.perform(estornar(id, "chave-metrica-4")).andExpect(status().isCreated());
    assertThat(registrados("ESTORNO")).isEqualTo(estornosAntes + 1);

    mvc.perform(estornar(id, "chave-metrica-5")).andExpect(status().isConflict());
    assertThat(registrados("ESTORNO")).isEqualTo(estornosAntes + 1);
    assertThat(contador("idempotency.conflicts")).isEqualTo(conflitosAntes);
  }

  @Test
  void validacaoQueFalhaNaoMexeEmNenhumaMetrica() throws Exception {
    double creditosAntes = registrados("CREDITO");
    double replaysAntes = contador("idempotency.replays");
    double conflitosAntes = contador("idempotency.conflicts");

    mvc.perform(postar("{\"tipo\":\"CREDITO\",\"valor\":\"-1\"}", "chave-metrica-6"))
        .andExpect(status().isBadRequest());

    assertThat(registrados("CREDITO")).isEqualTo(creditosAntes);
    assertThat(contador("idempotency.replays")).isEqualTo(replaysAntes);
    assertThat(contador("idempotency.conflicts")).isEqualTo(conflitosAntes);
  }

  private double registrados(String tipo) {
    return metricas.get("lancamentos.registrados").tag("tipo", tipo).counter().count();
  }

  private double contador(String nome) {
    return metricas.get(nome).counter().count();
  }
}
