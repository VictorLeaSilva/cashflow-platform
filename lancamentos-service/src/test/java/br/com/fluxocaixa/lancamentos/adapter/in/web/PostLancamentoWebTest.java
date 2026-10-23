package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.URL;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.credito;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.erroDeCampo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.problema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/**
 * POST /api/v1/lancamentos na fatia web: sucesso, replay, validação de campos, regras de data,
 * idempotência e erros (AC-001.x, AC-007.1..3, AC-010.2, AC-T013.6, AC-T013.11).
 */
@Tag("RF-001")
@Tag("RF-002")
@Tag("RF-007")
@Tag("RF-010")
@ExtendWith(OutputCaptureExtension.class)
class PostLancamentoWebTest extends IntegracaoWeb {

  @Test
  void creditoValidoResponde201ComCorpoELocation() throws Exception {
    MvcResult resultado =
        mvc.perform(postar(credito("100.00"), "chave-credito"))
            .andExpect(status().isCreated())
            .andExpect(header().doesNotExist("Idempotent-Replayed"))
            .andExpect(jsonPath("$.comercianteId").value(COMERCIANTE.toString()))
            .andExpect(jsonPath("$.tipo").value("CREDITO"))
            .andExpect(jsonPath("$.valor").value("100.00"))
            .andExpect(jsonPath("$.descricao").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.dataOcorrencia").value(RelogioFixo.AGORA.toString()))
            .andExpect(jsonPath("$.dataReferencia").value("2026-10-23"))
            .andExpect(jsonPath("$.referenciaLancamentoId").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.identificadorExterno").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.criadoEm").value(RelogioFixo.AGORA.toString()))
            .andReturn();

    String id = portas.lancamentos().getFirst().id().toString();
    assertThat(resultado.getResponse().getHeader("Location")).isEqualTo(URL + "/" + id);
    assertThat(resultado.getResponse().getContentAsString()).contains("\"id\":\"" + id + "\"");
    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(1);
    assertThat(portas.quantidadeDeEventos()).isEqualTo(1);
  }

  @Test
  void debitoValidoResponde201() throws Exception {
    mvc.perform(postar("{\"tipo\":\"DEBITO\",\"valor\":\"35.10\"}", "chave-debito"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tipo").value("DEBITO"))
        .andExpect(jsonPath("$.valor").value("35.10"));
  }

  @Test
  void replayDevolveOMesmoCorpoComCabecalho() throws Exception {
    String corpo =
        "{\"tipo\":\"CREDITO\",\"valor\":\"100\",\"descricao\":\"Venda\","
            + "\"dataOcorrencia\":\"2026-10-22T21:30:00-03:00\",\"identificadorExterno\":\"pdv-1\"}";
    MvcResult primeiro =
        mvc.perform(postar(corpo, "chave-replay")).andExpect(status().isCreated()).andReturn();

    MvcResult segundo =
        mvc.perform(postar(corpo, "chave-replay"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andReturn();

    assertThat(segundo.getResponse().getContentAsString())
        .isEqualTo(primeiro.getResponse().getContentAsString());
    assertThat(segundo.getResponse().getHeader("Location"))
        .isEqualTo(primeiro.getResponse().getHeader("Location"));
    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(1);
    assertThat(portas.quantidadeDeEventos()).isEqualTo(1);
  }

  @Test
  void mesmaChaveComOutroCorpoResponde422() throws Exception {
    mvc.perform(postar(credito("100.00"), "chave-reuso")).andExpect(status().isCreated());

    mvc.perform(postar(credito("100.01"), "chave-reuso"))
        .andExpect(problema(422, "idempotency-key-reuse"));
    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(1);
  }

  @Test
  void semIdempotencyKeyResponde400() throws Exception {
    mvc.perform(postar(credito("10.00"), null))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("Idempotency-Key", "obrigatorio"));
    assertThat(portas.quantidadeDeChaves()).isZero();
  }

  @ParameterizedTest
  @CsvSource({
    "chave com espaço",
    "chave/barra",
    "chave-com-ç",
    "01234567890123456789012345678901234567890123456789012345678901234"
  })
  void idempotencyKeyForaDoFormatoResponde400(String chave) throws Exception {
    mvc.perform(postar(credito("10.00"), chave))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("Idempotency-Key", "formato-invalido"));
  }

  @ParameterizedTest(name = "valor {0}")
  @CsvSource({
    "'\"valor\":\"0.00\"', valor",
    "'\"valor\":\"-1.00\"', valor",
    "'\"valor\":\"1.001\"', valor",
    "'\"valor\":\"10000000.01\"', valor",
    "'\"valor\":\"abc\"', valor",
    "'\"valor\":\"1e3\"', valor"
  })
  void valorInvalidoResponde400SemGravar(String campoValor, String campo) throws Exception {
    mvc.perform(postar("{\"tipo\":\"CREDITO\"," + campoValor + "}", "chave-valor"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(jsonPath("$.errors[*].campo", Matchers.hasItem(campo)));
    assertThat(portas.quantidadeDeLancamentos()).isZero();
    assertThat(portas.quantidadeDeChaves()).isZero();
  }

  @Test
  void valorAusenteResponde400() throws Exception {
    mvc.perform(postar("{\"tipo\":\"CREDITO\"}", "chave-sem-valor"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("valor", "obrigatorio"));
  }

  @Test
  void valorComoNumeroJsonResponde400() throws Exception {
    mvc.perform(postar("{\"tipo\":\"CREDITO\",\"valor\":100.00}", "chave-numero"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("valor", "tipo-invalido"));
  }

  @ParameterizedTest
  @CsvSource({
    "'{\"valor\":\"10.00\"}', obrigatorio",
    "'{\"tipo\":\"ESTORNO\",\"valor\":\"10.00\"}', tipo-invalido",
    "'{\"tipo\":\"PIX\",\"valor\":\"10.00\"}', valor-nao-permitido"
  })
  void tipoInvalidoResponde400(String corpo, String codigo) throws Exception {
    mvc.perform(postar(corpo, "chave-tipo"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("tipo", codigo));
  }

  @Test
  void dataSemOffsetResponde400() throws Exception {
    mvc.perform(
            postar(
                "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\",\"dataOcorrencia\":\"2026-10-21T23:30:00\"}",
                "chave-sem-offset"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("dataOcorrencia", "formato-invalido"));
  }

  @Test
  void dataFuturaResponde422SemGravarAChave() throws Exception {
    mvc.perform(
            postar(
                "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\",\"dataOcorrencia\":\"2026-10-23T15:01:01Z\"}",
                "chave-futura"))
        .andExpect(problema(422, "data-futura"));
    assertThat(portas.quantidadeDeChaves()).isZero();
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }

  @Test
  void dataRetroativaAlemDoLimiteResponde422() throws Exception {
    mvc.perform(
            postar(
                "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\",\"dataOcorrencia\":\"2026-09-23T02:59:59Z\"}",
                "chave-retroativa"))
        .andExpect(problema(422, "data-retroativa-excedida"));
    assertThat(portas.quantidadeDeChaves()).isZero();
  }

  @Test
  void comercianteIdNoCorpoResponde400() throws Exception {
    mvc.perform(
            postar(
                "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\",\"comercianteId\":\""
                    + UUID.randomUUID()
                    + "\"}",
                "chave-bola"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("comercianteId", "propriedade-desconhecida"));
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }

  @Test
  void comercianteIdNaQueryEhIgnorado() throws Exception {
    mvc.perform(
            postar(credito("10.00"), "chave-query")
                .queryParam("comercianteId", UUID.randomUUID().toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.comercianteId").value(COMERCIANTE.toString()));
  }

  @Test
  void jsonMalformadoResponde400() throws Exception {
    mvc.perform(postar("{\"tipo\":\"CREDITO\",", "chave-malformado"))
        .andExpect(problema(400, "requisicao-invalida"));
  }

  @Test
  void propriedadeDuplicadaResponde400() throws Exception {
    mvc.perform(
            postar(
                "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\",\"valor\":\"99.00\"}",
                "chave-duplicada"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("valor", "propriedade-duplicada"));
  }

  @Test
  void errosDeCampoVoltamJuntos() throws Exception {
    mvc.perform(
            postar(
                "{\"tipo\":\"CREDITO\",\"valor\":\"1.001\",\"descricao\":\"a\\nb\","
                    + "\"identificadorExterno\":\" nf\"}",
                "chave inválida"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("Idempotency-Key", "formato-invalido"))
        .andExpect(erroDeCampo("descricao", "caractere-de-controle"))
        .andExpect(erroDeCampo("identificadorExterno", "espacos-nas-pontas"))
        .andExpect(jsonPath("$.errors[*].campo", Matchers.hasItem("valor")))
        .andExpect(jsonPath("$.errors.length()").value(4));
  }

  @Test
  void disputaDeChaveSemResolucaoResponde409ComRetryAfter() throws Exception {
    portas.simularDisputaPermanente();

    mvc.perform(postar(credito("10.00"), "chave-disputa"))
        .andExpect(problema(409, "idempotency-conflict"))
        .andExpect(header().string("Retry-After", "1"));
  }

  @Test
  void identificadorExternoRepetidoResponde409SemExporOIdentificador(CapturedOutput saida)
      throws Exception {
    String corpo = "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\",\"identificadorExterno\":\"nf-777\"}";
    mvc.perform(postar(corpo, "chave-a")).andExpect(status().isCreated());

    MvcResult resultado =
        mvc.perform(postar(corpo, "chave-b"))
            .andExpect(problema(409, "identificador-externo-duplicado"))
            .andReturn();

    assertThat(resultado.getResponse().getContentAsString()).doesNotContain("nf-777");
    assertThat(saida.getAll()).doesNotContain("nf-777");
    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(1);
    assertThat(portas.quantidadeDeChaves()).isEqualTo(1);
  }

  @Test
  void erroInesperadoResponde500Generico(CapturedOutput saida) throws Exception {
    portas.simularFalhaInesperada();

    MvcResult resultado =
        mvc.perform(postar(credito("10.00"), "chave-500"))
            .andExpect(problema(500, "interno"))
            .andReturn();

    assertThat(resultado.getResponse().getContentAsString())
        .doesNotContain("falha inesperada simulada", "IllegalStateException", "at br.com");
    // A mensagem da exceção pode trazer dados da linha (ex.: erro do driver): não vai para o log.
    assertThat(saida.getAll()).doesNotContain("falha inesperada simulada");
    assertThat(saida.getAll()).contains("erro inesperado", IllegalStateException.class.getName());
  }

  /** AC-T013.12 (Q-016): mais de 6 casas nos segundos seria arredondado pelo banco. */
  @Test
  void dataComMaisDeSeisCasasNosSegundosResponde400() throws Exception {
    mvc.perform(
            postar(
                "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\","
                    + "\"dataOcorrencia\":\"2026-10-23T14:00:00.1234567Z\"}",
                "chave-nanos"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("dataOcorrencia", "formato-invalido"));
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }

  @Test
  void dataComSeisCasasNosSegundosVoltaExata() throws Exception {
    mvc.perform(
            postar(
                "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\","
                    + "\"dataOcorrencia\":\"2026-10-23T11:00:00.123456-03:00\"}",
                "chave-micros"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.dataOcorrencia").value("2026-10-23T14:00:00.123456Z"));
  }

  /** Rotas e métodos fora do contrato seguem o padrão do Spring, em problem+json. */
  @Test
  void metodoNaoSuportadoRespondeProblemJsonPadrao() throws Exception {
    mvc.perform(
            delete(URL)
                .header(
                    "Authorization",
                    "Bearer " + TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.ESCRITA)))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.requestId").exists());
  }
}
