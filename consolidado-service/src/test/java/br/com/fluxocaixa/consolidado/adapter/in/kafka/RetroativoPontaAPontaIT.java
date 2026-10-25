package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.domain.ChaveDiaria;
import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * AC-T031.5 (RF-009, AC-009.1 e AC-009.2) de ponta a ponta no Consolidado: eventos pelo Kafka,
 * projeção no banco e consulta pela API. Um evento retroativo (D-3) chega depois dos de D-2, D-1 e
 * D: o movimento de D-3 aparece e o {@code saldoAcumulado} muda de D-3 em diante, sem mexer no
 * movimento de D-2..D. O estorno de um lançamento de D-3, recebido "hoje", entra em D.
 */
@Tag("RF-009")
@AutoConfigureMockMvc
class RetroativoPontaAPontaIT extends IntegracaoKafka {

  private static final Duration LIMITE = Duration.ofSeconds(60);
  private static final LocalDate D = LocalDate.parse("2026-10-23");
  private static final String PERIODO = "dataInicio=2026-10-18&dataFim=2026-10-23";

  @Autowired private MockMvc mvc;

  @DynamicPropertySource
  static void jwt(DynamicPropertyRegistry registro) {
    TokensDeTeste.registrarPropriedades(registro);
  }

  @Test
  void retroativoAlteraOAcumuladoDeD3EmDianteEEstornoDeDiaPassadoEntraNoDiaDoEstorno()
      throws Exception {
    UUID comerciante = UUID.randomUUID();
    String chave = comerciante.toString();
    String token = TokensDeTeste.valido(comerciante, TokensDeTeste.LEITURA);

    publicarCredito(chave, comerciante, D.minusDays(2), "100.00");
    publicarCredito(chave, comerciante, D.minusDays(1), "50.00");
    publicarCredito(chave, comerciante, D, "10.00");
    esperarDias(comerciante, 3);

    // Antes do retroativo: D-3 sem movimento; acumulados 100, 150 e 160.
    consultar(token)
        .andExpect(jsonPath("$.dias", hasSize(6)))
        .andExpect(jsonPath("$.dias[2].saldoDia").value("0.00"))
        .andExpect(jsonPath("$.dias[3].saldoAcumulado").value("100.00"))
        .andExpect(jsonPath("$.dias[4].saldoAcumulado").value("150.00"))
        .andExpect(jsonPath("$.dias[5].saldoAcumulado").value("160.00"));

    // AC-009.1: o lançamento de D-3 chega por último.
    UUID lancamentoDeD3 = publicarCredito(chave, comerciante, D.minusDays(3), "200.00");
    esperarDias(comerciante, 4);
    consultar(token)
        .andExpect(jsonPath("$.dias[2].totalCreditos").value("200.00"))
        .andExpect(jsonPath("$.dias[2].saldoDia").value("200.00"))
        .andExpect(jsonPath("$.dias[2].saldoAcumulado").value("200.00"))
        .andExpect(jsonPath("$.dias[3].saldoDia").value("100.00"))
        .andExpect(jsonPath("$.dias[3].saldoAcumulado").value("300.00"))
        .andExpect(jsonPath("$.dias[4].saldoDia").value("50.00"))
        .andExpect(jsonPath("$.dias[4].saldoAcumulado").value("350.00"))
        .andExpect(jsonPath("$.dias[5].saldoDia").value("10.00"))
        .andExpect(jsonPath("$.dias[5].saldoAcumulado").value("360.00"));

    // AC-009.2: o estorno do lançamento de D-3, recebido hoje, entra em D.
    KafkaConsolidado.publicar(
        chave,
        EventosJson.json(
            EventosJson.estorno(
                UUID.randomUUID(), comerciante, "200.00", D.toString(), "CREDITO", lancamentoDeD3)),
        null);
    await()
        .atMost(LIMITE)
        .until(
            () ->
                TabelasDoConsolidado.diasDe(comerciante)
                        .get(new ChaveDiaria(comerciante, D))
                        .qtdLancamentos()
                    == 2);
    consultar(token)
        .andExpect(jsonPath("$.dias[2].totalCreditos").value("200.00"))
        .andExpect(jsonPath("$.dias[2].totalEstornosCredito").value("0.00"))
        .andExpect(jsonPath("$.dias[2].saldoDia").value("200.00"))
        .andExpect(jsonPath("$.dias[5].totalEstornosCredito").value("200.00"))
        .andExpect(jsonPath("$.dias[5].saldoDia").value("-190.00"))
        .andExpect(jsonPath("$.dias[5].saldoAcumulado").value("160.00"));
  }

  private UUID publicarCredito(String chave, UUID comerciante, LocalDate dia, String valor) {
    UUID evento = UUID.randomUUID();
    Map<String, Object> campos = EventosJson.credito(evento, comerciante, valor, dia.toString());
    KafkaConsolidado.publicar(chave, EventosJson.json(campos), null);
    return UUID.fromString((String) campos.get("lancamentoId"));
  }

  private static void esperarDias(UUID comerciante, int quantidade) {
    await()
        .atMost(LIMITE)
        .until(() -> TabelasDoConsolidado.diasDe(comerciante).size() == quantidade);
  }

  private ResultActions consultar(String token) throws Exception {
    return mvc.perform(
            get("/api/v1/consolidado?" + PERIODO)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }
}
