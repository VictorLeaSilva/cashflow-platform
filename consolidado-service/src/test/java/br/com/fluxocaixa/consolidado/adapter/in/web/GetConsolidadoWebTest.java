package br.com.fluxocaixa.consolidado.adapter.in.web;

import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.URL;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.dia;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.erroDeCampo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.periodo;
import static br.com.fluxocaixa.consolidado.adapter.in.web.Chamadas.problema;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import br.com.fluxocaixa.consolidado.support.IntegracaoWeb;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * AC-005.1 a AC-005.4, AC-006.1 e AC-T023.4 na fatia web, sobre a leitura em memória: zero-fill,
 * fórmula do saldo, saldo anterior e acumulado, validação estrita da query (Q-052).
 */
@Tag("RF-005")
@Tag("RF-006")
@Tag("RNF-013")
class GetConsolidadoWebTest extends IntegracaoWeb {

  private static final Instant EM = Instant.parse("2026-10-20T13:02:11.004001Z");

  @Test
  void periodoTemUmItemPorDiaEmOrdemComZeroFillESaldos() throws Exception {
    gravar("2026-10-10", "500.00", "0", "0", "0", 1);
    gravar("2026-10-18", "1000.00", "0", "0", "0", 2);
    gravar("2026-10-20", "0", "250.00", "100.00", "0", 2);
    gravar("2026-10-21", "80.00", "0", "0", "250.00", 2);

    mvc.perform(periodo("dataInicio=2026-10-18&dataFim=2026-10-21"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comercianteId").value(COMERCIANTE.toString()))
        .andExpect(jsonPath("$.dataInicio").value("2026-10-18"))
        .andExpect(jsonPath("$.dataFim").value("2026-10-21"))
        .andExpect(jsonPath("$.saldoAnterior").value("500.00"))
        .andExpect(jsonPath("$.dias", hasSize(4)))
        .andExpect(jsonPath("$.dias[0].data").value("2026-10-18"))
        .andExpect(jsonPath("$.dias[0].saldoDia").value("1000.00"))
        .andExpect(jsonPath("$.dias[0].saldoAcumulado").value("1500.00"))
        .andExpect(jsonPath("$.dias[0].qtdLancamentos").value(2))
        .andExpect(jsonPath("$.dias[1].data").value("2026-10-19"))
        .andExpect(jsonPath("$.dias[1].totalCreditos").value("0.00"))
        .andExpect(jsonPath("$.dias[1].totalDebitos").value("0.00"))
        .andExpect(jsonPath("$.dias[1].totalEstornosCredito").value("0.00"))
        .andExpect(jsonPath("$.dias[1].totalEstornosDebito").value("0.00"))
        .andExpect(jsonPath("$.dias[1].saldoDia").value("0.00"))
        .andExpect(jsonPath("$.dias[1].saldoAcumulado").value("1500.00"))
        .andExpect(jsonPath("$.dias[1].qtdLancamentos").value(0))
        .andExpect(jsonPath("$.dias[1].atualizadoEm").value(nullValue()))
        .andExpect(jsonPath("$.dias[2].totalDebitos").value("250.00"))
        .andExpect(jsonPath("$.dias[2].totalEstornosCredito").value("100.00"))
        .andExpect(jsonPath("$.dias[2].saldoDia").value("-350.00"))
        .andExpect(jsonPath("$.dias[2].saldoAcumulado").value("1150.00"))
        .andExpect(jsonPath("$.dias[2].atualizadoEm").value(EM.toString()))
        .andExpect(jsonPath("$.dias[3].totalEstornosDebito").value("250.00"))
        .andExpect(jsonPath("$.dias[3].saldoDia").value("330.00"))
        .andExpect(jsonPath("$.dias[3].saldoAcumulado").value("1480.00"));
  }

  @Test
  void semHistoricoOSaldoAnteriorEZeroEOFuturoVemZerado() throws Exception {
    mvc.perform(periodo("dataInicio=2027-01-01&dataFim=2027-01-02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saldoAnterior").value("0.00"))
        .andExpect(jsonPath("$.dias", hasSize(2)))
        .andExpect(jsonPath("$.dias[1].saldoAcumulado").value("0.00"))
        .andExpect(jsonPath("$.dias[1].atualizadoEm").value(nullValue()));
  }

  @Test
  void saldoNegativoEPermitido() throws Exception {
    gravar("2026-10-01", "0", "120.50", "0", "0", 1);
    gravar("2026-10-22", "20.00", "0", "0", "0", 1);

    mvc.perform(periodo("dataInicio=2026-10-22&dataFim=2026-10-22"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saldoAnterior").value("-120.50"))
        .andExpect(jsonPath("$.dias", hasSize(1)))
        .andExpect(jsonPath("$.dias[0].saldoAcumulado").value("-100.50"));
  }

  @Test
  void ateTrezentosESessentaESeisDiasContandoOsDoisExtremos() throws Exception {
    mvc.perform(periodo("dataInicio=2024-01-01&dataFim=2024-12-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dias", hasSize(366)));
    mvc.perform(periodo("dataInicio=2025-01-01&dataFim=2026-01-02"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("dataFim", "intervalo-maximo"));
  }

  @Test
  void intervaloInvertidoEhInvalido() throws Exception {
    mvc.perform(periodo("dataInicio=2026-10-22&dataFim=2026-10-21"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("dataInicio", "intervalo-invalido"));
  }

  @Test
  void parametrosAusentesSaoObrigatoriosETodosOsErrosVoltamJuntos() throws Exception {
    mvc.perform(periodo(""))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("dataInicio", "obrigatorio"))
        .andExpect(erroDeCampo("dataFim", "obrigatorio"));
    mvc.perform(periodo("dataInicio=2026-10-2&dataFim=2026-02-30&comercianteId=x"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("dataInicio", "formato-invalido"))
        .andExpect(erroDeCampo("dataFim", "formato-invalido"))
        .andExpect(erroDeCampo("comercianteId", "parametro-desconhecido"))
        .andExpect(jsonPath("$.errors", hasSize(3)));
  }

  @Test
  void parametroRepetidoEhRejeitado() throws Exception {
    mvc.perform(periodo("dataInicio=2026-10-01&dataInicio=2026-10-02&dataFim=2026-10-03"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("dataInicio", "parametro-repetido"));
  }

  @Test
  void diaTrazOAcumuladoDesdeOInicio() throws Exception {
    gravar("2026-10-10", "500.00", "0", "0", "0", 1);
    gravar("2026-10-21", "80.00", "0", "0", "250.00", 2);

    mvc.perform(dia("2026-10-21"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value("2026-10-21"))
        .andExpect(jsonPath("$.saldoDia").value("330.00"))
        .andExpect(jsonPath("$.saldoAcumulado").value("830.00"))
        .andExpect(jsonPath("$.qtdLancamentos").value(2));
  }

  @Test
  void diaSemMovimentoResponde200ComZeros() throws Exception {
    gravar("2026-10-10", "500.00", "0", "0", "0", 1);

    mvc.perform(dia("2026-10-19"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCreditos").value("0.00"))
        .andExpect(jsonPath("$.saldoDia").value("0.00"))
        .andExpect(jsonPath("$.saldoAcumulado").value("500.00"))
        .andExpect(jsonPath("$.qtdLancamentos").value(0))
        .andExpect(jsonPath("$.atualizadoEm").value(nullValue()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-02-30", "2026-10-2", "21-10-2026", "hoje"})
  void diaComDataInvalidaResponde400(String data) throws Exception {
    mvc.perform(dia(data))
        .andExpect(problema(400, "requisicao-invalida", URL + "/" + data))
        .andExpect(erroDeCampo("data", "formato-invalido"));
  }

  @Test
  void diaNaoAceitaParametroDeQuery() throws Exception {
    mvc.perform(dia("2026-10-21?comercianteId=" + COMERCIANTE))
        .andExpect(problema(400, "requisicao-invalida", URL + "/2026-10-21"))
        .andExpect(erroDeCampo("comercianteId", "parametro-desconhecido"));
  }

  @Test
  void acceptSemJsonResponde406() throws Exception {
    mvc.perform(
            periodo("dataInicio=2026-10-21&dataFim=2026-10-21").accept(MediaType.APPLICATION_XML))
        .andExpect(problema(406, "nao-aceitavel"));
    mvc.perform(dia("2026-10-21").accept(MediaType.APPLICATION_XML))
        .andExpect(problema(406, "nao-aceitavel", URL + "/2026-10-21"));
  }

  /** O filtro de 16 KiB vale para toda requisição, como no Lançamentos (03 §5.2). */
  @Test
  void corpoAcimaDe16KiBResponde413MesmoNoGet() throws Exception {
    mvc.perform(
            periodo("dataInicio=2026-10-21&dataFim=2026-10-21")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recheio\":\"" + "x".repeat(17_000) + "\"}"))
        .andExpect(problema(413, "payload-muito-grande"));
  }

  @Test
  void falhaInesperadaResponde500SemDetalheInterno() throws Exception {
    leitura.simularFalhaInesperada();

    mvc.perform(periodo("dataInicio=2026-10-21&dataFim=2026-10-21"))
        .andExpect(problema(500, "interno"))
        .andExpect(jsonPath("$.detail").value("erro interno; tente novamente"));
  }

  private void gravar(
      String data,
      String creditos,
      String debitos,
      String estornosCredito,
      String estornosDebito,
      int qtd) {
    leitura.gravar(
        COMERCIANTE,
        LocalDate.parse(data),
        new MovimentoDiario(
            new BigDecimal(creditos),
            new BigDecimal(debitos),
            new BigDecimal(estornosCredito),
            new BigDecimal(estornosDebito),
            qtd),
        EM);
  }
}
