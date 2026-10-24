package br.com.fluxocaixa.consolidado.adapter.in.web;

import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.diaComo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.periodoComo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.domain.TipoLancamento;
import br.com.fluxocaixa.consolidado.support.IntegracaoApiPostgres;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-005.1, AC-005.2, AC-005.4, AC-006.1 e AC-006.2 de ponta a ponta na API, com a projeção montada
 * pela porta de escrita (T-021) e lida pelo adaptador JDBC.
 */
@Tag("RF-005")
@Tag("RF-006")
class GetConsolidadoIT extends IntegracaoApiPostgres {

  @Test
  void periodoComEstornosZeroFillESaldosVindosDoBanco() throws Exception {
    UUID comerciante = UUID.randomUUID();
    String token = TokensDeTeste.valido(comerciante, TokensDeTeste.LEITURA);
    credito(comerciante, "2025-01-15", "300.00");
    debito(comerciante, "2026-10-01", "100.00");
    credito(comerciante, "2026-10-18", "1000.00");
    debito(comerciante, "2026-10-20", "250.00");
    estorno(comerciante, TipoLancamento.CREDITO, "2026-10-20", "100.00");
    credito(comerciante, "2026-10-21", "80.00");
    estorno(comerciante, TipoLancamento.DEBITO, "2026-10-21", "250.00");

    mvc.perform(periodoComo(token, "dataInicio=2026-10-18&dataFim=2026-10-21"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saldoAnterior").value("200.00"))
        .andExpect(jsonPath("$.dias", hasSize(4)))
        .andExpect(jsonPath("$.dias[0].saldoAcumulado").value("1200.00"))
        .andExpect(jsonPath("$.dias[0].atualizadoEm").value(EM.toString()))
        .andExpect(jsonPath("$.dias[1].qtdLancamentos").value(0))
        .andExpect(jsonPath("$.dias[1].atualizadoEm").value(nullValue()))
        .andExpect(jsonPath("$.dias[2].totalDebitos").value("250.00"))
        .andExpect(jsonPath("$.dias[2].totalEstornosCredito").value("100.00"))
        .andExpect(jsonPath("$.dias[2].saldoDia").value("-350.00"))
        .andExpect(jsonPath("$.dias[2].qtdLancamentos").value(2))
        .andExpect(jsonPath("$.dias[3].totalEstornosDebito").value("250.00"))
        .andExpect(jsonPath("$.dias[3].saldoDia").value("330.00"))
        .andExpect(jsonPath("$.dias[3].saldoAcumulado").value("1180.00"));
  }

  @Test
  void diaSemMovimentoTemZerosEOAcumuladoDesdeOInicio() throws Exception {
    UUID comerciante = UUID.randomUUID();
    String token = TokensDeTeste.valido(comerciante, TokensDeTeste.LEITURA);
    credito(comerciante, "2025-01-15", "300.00");
    debito(comerciante, "2026-10-01", "100.00");

    mvc.perform(diaComo(token, "2026-10-19"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCreditos").value("0.00"))
        .andExpect(jsonPath("$.saldoDia").value("0.00"))
        .andExpect(jsonPath("$.saldoAcumulado").value("200.00"))
        .andExpect(jsonPath("$.atualizadoEm").value(nullValue()));
    mvc.perform(diaComo(token, "2026-10-01"))
        .andExpect(jsonPath("$.saldoDia").value("-100.00"))
        .andExpect(jsonPath("$.saldoAcumulado").value("200.00"));
  }
}
