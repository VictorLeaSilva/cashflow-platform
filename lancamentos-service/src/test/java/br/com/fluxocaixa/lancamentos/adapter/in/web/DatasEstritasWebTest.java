package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.erroDeCampo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.problema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.net.URI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * AC-T034.5 (RNF-013; N1 da T-023): as datas do Lançamentos aceitam só ano de 4 dígitos sem sinal,
 * como o Consolidado; o ISO do JDK também aceita ano expandido, que aqui é rejeitado em 400 com o
 * campo em {@code errors[]}.
 */
@Tag("RNF-013")
class DatasEstritasWebTest extends IntegracaoWeb {

  private static String autorizacao() {
    return "Bearer " + TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);
  }

  @ParameterizedTest(name = "dataInicio={0}")
  @ValueSource(
      strings = {"+2026-10-01", "%2B2026-10-01", "-2026-10-01", "02026-10-01", "12026-10-01"})
  void dataInicioComAnoForaDeAaaaEhRejeitada(String valor) throws Exception {
    mvc.perform(
            get(URI.create("/api/v1/lancamentos?dataInicio=" + valor))
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", autorizacao()))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("dataInicio", "formato-invalido"));
  }

  @ParameterizedTest(name = "dataFim={0}")
  @ValueSource(strings = {"%2B2026-10-01", "-2026-10-01", "02026-10-01"})
  void dataFimComAnoForaDeAaaaEhRejeitada(String valor) throws Exception {
    mvc.perform(
            get(URI.create("/api/v1/lancamentos?dataFim=" + valor))
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", autorizacao()))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("dataFim", "formato-invalido"));
  }

  @Test
  void dataComQuatroDigitosContinuaAceita() throws Exception {
    mvc.perform(
            get(URI.create("/api/v1/lancamentos?dataInicio=2026-10-01&dataFim=2026-10-31"))
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", autorizacao()))
        .andExpect(status().isOk());
  }

  @ParameterizedTest(name = "dataOcorrencia={0}")
  @ValueSource(
      strings = {
        "12026-10-21T10:00:00Z",
        "02026-10-21T10:00:00Z",
        "+2026-10-21T10:00:00Z",
        "-2026-10-21T10:00:00Z"
      })
  void dataOcorrenciaComAnoForaDeAaaaEhRejeitada(String valor) throws Exception {
    String corpo =
        "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\",\"dataOcorrencia\":\"" + valor + "\"}";

    mvc.perform(postar(corpo, "chave-ano-" + Math.abs(valor.hashCode())))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("dataOcorrencia", "formato-invalido"));
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }
}
