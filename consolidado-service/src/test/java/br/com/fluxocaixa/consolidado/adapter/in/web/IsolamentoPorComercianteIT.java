package br.com.fluxocaixa.consolidado.adapter.in.web;

import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.diaComo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.erroDeCampo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.periodoComo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.problema;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.support.IntegracaoApiPostgres;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-010.1 e AC-010.2 do lado do Consolidado (risco "isolamento por comerciante A × B" de {@code 04
 * §2.7}): cada token vê só o próprio consolidado; um terceiro vê zeros; o comerciante nunca vem da
 * query.
 */
@Tag("RF-010")
class IsolamentoPorComercianteIT extends IntegracaoApiPostgres {

  private static final String PERIODO = "dataInicio=2026-10-20&dataFim=2026-10-21";

  @Test
  void cadaComercianteVeSoOProprioConsolidado() throws Exception {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    credito(a, "2026-10-01", "10.00");
    credito(a, "2026-10-21", "100.00");
    credito(b, "2026-10-01", "7000.00");
    debito(b, "2026-10-21", "999.00");

    mvc.perform(periodoComo(TokensDeTeste.valido(a, TokensDeTeste.LEITURA), PERIODO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comercianteId").value(a.toString()))
        .andExpect(jsonPath("$.saldoAnterior").value("10.00"))
        .andExpect(jsonPath("$.dias[1].totalCreditos").value("100.00"))
        .andExpect(jsonPath("$.dias[1].totalDebitos").value("0.00"))
        .andExpect(jsonPath("$.dias[1].saldoAcumulado").value("110.00"));
    mvc.perform(periodoComo(TokensDeTeste.valido(b, TokensDeTeste.LEITURA), PERIODO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comercianteId").value(b.toString()))
        .andExpect(jsonPath("$.saldoAnterior").value("7000.00"))
        .andExpect(jsonPath("$.dias[1].totalCreditos").value("0.00"))
        .andExpect(jsonPath("$.dias[1].totalDebitos").value("999.00"));
    mvc.perform(diaComo(TokensDeTeste.valido(b, TokensDeTeste.LEITURA), "2026-10-21"))
        .andExpect(jsonPath("$.saldoAcumulado").value("6001.00"));
  }

  @Test
  void terceiroSemDadosVeZerosENuncaOsDosOutros() throws Exception {
    UUID a = UUID.randomUUID();
    credito(a, "2026-10-21", "100.00");
    UUID c = UUID.randomUUID();

    mvc.perform(periodoComo(TokensDeTeste.valido(c, TokensDeTeste.LEITURA), PERIODO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saldoAnterior").value("0.00"))
        .andExpect(jsonPath("$.dias[1].totalCreditos").value("0.00"))
        .andExpect(jsonPath("$.dias[1].qtdLancamentos").value(0));
  }

  @Test
  void comercianteNaQueryEhRejeitadoENuncaUsado() throws Exception {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    credito(b, "2026-10-21", "100.00");

    mvc.perform(
            periodoComo(
                TokensDeTeste.valido(a, TokensDeTeste.LEITURA), PERIODO + "&comercianteId=" + b))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("comercianteId", "parametro-desconhecido"));
  }
}
