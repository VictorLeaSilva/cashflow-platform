package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.URL;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.campo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.erroDeCampo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.estornar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.listar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.listarComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.obter;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.obterComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.problema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET de detalhe e de histórico na fatia web (T-015): corpo, paginação, filtros, validação estrita
 * da query (Q-023..Q-026), isolamento, segurança e request ID (AC-004.x, AC-010.x, AC-T015.1..3).
 */
@Tag("RF-004")
@Tag("RF-010")
@Tag("RNF-005")
@Tag("RNF-012")
@Tag("RNF-013")
class GetLancamentosWebTest extends IntegracaoWeb {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  void detalheDevolveOMesmoCorpoDo201() throws Exception {
    MvcResult criado =
        mvc.perform(postar("{\"tipo\":\"DEBITO\",\"valor\":\"12.34\"}", "get-detalhe"))
            .andExpect(status().isCreated())
            .andReturn();
    String id = campo(criado, "id");

    MvcResult lido =
        mvc.perform(obter(id))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Idempotent-Replayed"))
            .andReturn();

    assertThat(lido.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    assertThat(lido.getResponse().getContentAsString())
        .isEqualTo(criado.getResponse().getContentAsString());
  }

  @Test
  void detalheDeEstornoTrazTipoOriginalEReferencia() throws Exception {
    String original = registrar("{\"tipo\":\"CREDITO\",\"valor\":\"5.00\"}", "get-est-o");
    String estorno =
        campo(
            mvc.perform(estornar(original, "get-est")).andExpect(status().isCreated()).andReturn(),
            "id");

    mvc.perform(obter(estorno))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tipo").value("ESTORNO"))
        .andExpect(jsonPath("$.tipoOriginal").value("CREDITO"))
        .andExpect(jsonPath("$.referenciaLancamentoId").value(original));
  }

  @Test
  void detalheInexistenteResponde404() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(obter(id)).andExpect(problema(404, "lancamento-nao-encontrado", URL + "/" + id));
  }

  /** AC-010.1 na fatia web: de outro comerciante é igual ao inexistente. */
  @Test
  void detalheDeOutroComercianteResponde404IgualAoInexistente() throws Exception {
    String doOutro = registrar("{\"tipo\":\"CREDITO\",\"valor\":\"5.00\"}", "get-outro");
    String outroToken = TokensDeTeste.valido(UUID.randomUUID(), TokensDeTeste.LEITURA);

    MvcResult cruzado =
        mvc.perform(obterComo(outroToken, doOutro))
            .andExpect(problema(404, "lancamento-nao-encontrado", URL + "/" + doOutro))
            .andReturn();
    MvcResult ausente =
        mvc.perform(obterComo(outroToken, UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andReturn();

    assertThat(semVariaveis(cruzado)).isEqualTo(semVariaveis(ausente));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "1-1-1-1-1", "0f8fad5b-d9cb-469f-a165-70867728950e0"})
  void detalheComIdForaDaFormaCanonicaResponde400(String id) throws Exception {
    mvc.perform(obter(id))
        .andExpect(problema(400, "requisicao-invalida", URL + "/" + id))
        .andExpect(erroDeCampo("id", "formato-invalido"));
  }

  @Test
  void detalheComParametroNaQueryResponde400() throws Exception {
    String id = registrar("{\"tipo\":\"CREDITO\",\"valor\":\"5.00\"}", "get-query");

    mvc.perform(obter(id + "?comercianteId=" + UUID.randomUUID()))
        .andExpect(problema(400, "requisicao-invalida", URL + "/" + id))
        .andExpect(erroDeCampo("comercianteId", "parametro-desconhecido"));
  }

  @Test
  void historicoOrdenaPorDataDecrescenteEIdCrescente() throws Exception {
    String dia21a = registrar(comData("CREDITO", "1.00", "2026-10-21T10:00:00-03:00"), "h-1");
    String dia23 = registrar(comData("DEBITO", "2.00", "2026-10-23T09:00:00-03:00"), "h-2");
    String dia21b = registrar(comData("DEBITO", "3.00", "2026-10-21T11:00:00-03:00"), "h-3");
    String dia22 = registrar(comData("CREDITO", "4.00", "2026-10-22T10:00:00-03:00"), "h-4");

    JsonNode pagina = json(mvc.perform(listar("")).andExpect(status().isOk()).andReturn());

    assertThat(ids(pagina))
        .containsExactly(dia23, dia22, menor(dia21a, dia21b), maior(dia21a, dia21b));
    assertThat(pagina.path("page").asInt()).isZero();
    assertThat(pagina.path("size").asInt()).isEqualTo(20);
    assertThat(pagina.path("total").asLong()).isEqualTo(4);
    assertThat(pagina.path("itens").get(0).path("valor").asString()).isEqualTo("2.00");
    assertThat(pagina.path("itens").get(0).has("tipoOriginal")).isTrue();
  }

  @Test
  void paginasNaoRepetemNemPulamEAlemDoFimEhVazia() throws Exception {
    for (int i = 1; i <= 5; i++) {
      // Dias 19 a 23: nenhum no futuro em relação ao relógio fixo (23/10, 12:00 em SP).
      registrar(comData("CREDITO", i + ".00", "2026-10-" + (18 + i) + "T08:00:00-03:00"), "p-" + i);
    }
    List<String> todos = ids(json(mvc.perform(listar("size=100")).andReturn()));

    List<String> paginados = new ArrayList<>();
    for (int pagina = 0; pagina < 3; pagina++) {
      JsonNode corpo =
          json(
              mvc.perform(listar("page=" + pagina + "&size=2"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.page").value(pagina))
                  .andExpect(jsonPath("$.size").value(2))
                  .andExpect(jsonPath("$.total").value(5))
                  .andReturn());
      paginados.addAll(ids(corpo));
    }

    assertThat(todos).hasSize(5);
    assertThat(paginados).containsExactlyElementsOf(todos);
    mvc.perform(listar("page=3&size=2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itens", Matchers.empty()))
        .andExpect(jsonPath("$.total").value(5));
  }

  @Test
  void filtrosDeDataSaoInclusivosETipoFiltra() throws Exception {
    String d20 = registrar(comData("CREDITO", "1.00", "2026-10-20T10:00:00-03:00"), "f-20");
    String d21 = registrar(comData("DEBITO", "2.00", "2026-10-21T10:00:00-03:00"), "f-21");
    String d22 = registrar(comData("CREDITO", "3.00", "2026-10-22T10:00:00-03:00"), "f-22");
    registrar(comData("CREDITO", "4.00", "2026-10-23T10:00:00-03:00"), "f-23");
    String estorno =
        campo(
            mvc.perform(estornar(d22, "f-est")).andExpect(status().isCreated()).andReturn(), "id");

    assertThat(
            ids(json(mvc.perform(listar("dataInicio=2026-10-21&dataFim=2026-10-22")).andReturn())))
        .containsExactly(d22, d21);
    assertThat(
            json(mvc.perform(listar("dataInicio=2026-10-22")).andReturn()).path("total").asLong())
        .isEqualTo(3);
    assertThat(json(mvc.perform(listar("dataFim=2026-10-21")).andReturn()).path("total").asLong())
        .isEqualTo(2);
    assertThat(ids(json(mvc.perform(listar("tipo=ESTORNO")).andReturn()))).containsExactly(estorno);
    JsonNode combinados = json(mvc.perform(listar("tipo=CREDITO&dataFim=2026-10-22")).andReturn());
    assertThat(combinados.path("total").asLong()).isEqualTo(2);
    assertThat(ids(combinados)).containsExactly(d22, d20);
  }

  @Test
  void sizeNoLimiteEhAceitoEAcimaResponde400() throws Exception {
    mvc.perform(listar("size=100")).andExpect(status().isOk());
    mvc.perform(listar("size=101"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("size", "tamanho-maximo"));
  }

  /** AC-T015.1: cada parâmetro inválido com o seu código; todos voltam juntos. */
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "dataInicio=2026-13-01|dataInicio|formato-invalido",
        "dataInicio=23/10/2026|dataInicio|formato-invalido",
        "dataFim=2026-10-2|dataFim|formato-invalido",
        "dataInicio=2026-10-22&dataFim=2026-10-21|dataInicio|intervalo-invalido",
        "tipo=PIX|tipo|valor-nao-permitido",
        "page=-1|page|formato-invalido",
        "page=abc|page|formato-invalido",
        "page=99999999999|page|formato-invalido",
        "size=0|size|formato-invalido",
        "size=99999999999|size|tamanho-maximo",
        "size=abc|size|formato-invalido",
        "tipos=CREDITO|tipos|parametro-desconhecido",
        "tipo=CREDITO&tipo=DEBITO|tipo|parametro-repetido"
      })
  void parametroInvalidoResponde400ComOCodigo(String query, String campo, String codigo)
      throws Exception {
    mvc.perform(listar(query))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo(campo, codigo));
  }

  @Test
  void errosDosParametrosVoltamJuntos() throws Exception {
    mvc.perform(listar("tipo=PIX&size=500&page=-2&extra=1"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(jsonPath("$.errors.length()").value(4))
        .andExpect(erroDeCampo("tipo", "valor-nao-permitido"))
        .andExpect(erroDeCampo("size", "tamanho-maximo"))
        .andExpect(erroDeCampo("page", "formato-invalido"))
        .andExpect(erroDeCampo("extra", "parametro-desconhecido"));
  }

  /** AC-010.2 e AC-004.3: comercianteId na query é rejeitado; o histórico é só do token. */
  @Test
  void historicoTrazSoOComercianteDoTokenERejeitaComercianteNaQuery() throws Exception {
    String meu = registrar("{\"tipo\":\"CREDITO\",\"valor\":\"1.00\"}", "iso-meu");
    UUID outro = UUID.randomUUID();
    mvc.perform(
            Chamadas.postarComo(
                TokensDeTeste.valido(outro, TokensDeTeste.ESCRITA),
                "{\"tipo\":\"CREDITO\",\"valor\":\"9.00\"}",
                "iso-outro"))
        .andExpect(status().isCreated());

    JsonNode pagina = json(mvc.perform(listar("")).andReturn());
    assertThat(ids(pagina)).containsExactly(meu);
    assertThat(pagina.path("total").asLong()).isEqualTo(1);
    mvc.perform(listar("comercianteId=" + outro))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo("comercianteId", "parametro-desconhecido"));
  }

  @Test
  void semTokenResponde401SoComEscritaResponde403EComLeitura200() throws Exception {
    String id = registrar("{\"tipo\":\"CREDITO\",\"valor\":\"1.00\"}", "seg-get");
    String escrita = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.ESCRITA);

    mvc.perform(listarComo(null, "")).andExpect(problema(401, "nao-autenticado"));
    mvc.perform(obterComo(null, id)).andExpect(problema(401, "nao-autenticado", URL + "/" + id));
    mvc.perform(listarComo(escrita, "")).andExpect(problema(403, "acesso-negado"));
    mvc.perform(obterComo(escrita, id)).andExpect(problema(403, "acesso-negado", URL + "/" + id));
    mvc.perform(listar("")).andExpect(status().isOk());
    mvc.perform(obter(id)).andExpect(status().isOk());
  }

  /**
   * O Spring MVC atende HEAD com os handlers de GET: a regra de scope precisa valer também para
   * HEAD, senão um token só de escrita sonda a existência dos próprios lançamentos (ADR-007).
   */
  @Test
  void headExigeOMesmoScopeDeLeituraQueGet() throws Exception {
    String id = registrar("{\"tipo\":\"CREDITO\",\"valor\":\"1.00\"}", "seg-head");
    String escrita = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.ESCRITA);
    String leitura = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);

    mvc.perform(head(URL + "/" + id).header("Authorization", "Bearer " + escrita))
        .andExpect(status().isForbidden());
    mvc.perform(head(URL).header("Authorization", "Bearer " + escrita))
        .andExpect(status().isForbidden());
    mvc.perform(head(URL + "/" + id)).andExpect(status().isUnauthorized());
    mvc.perform(head(URL + "/" + id).header("Authorization", "Bearer " + leitura))
        .andExpect(status().isOk());
  }

  @Test
  void acceptIncompativelResponde406() throws Exception {
    String id = registrar("{\"tipo\":\"CREDITO\",\"valor\":\"1.00\"}", "get-406");

    mvc.perform(listar("").accept(MediaType.APPLICATION_XML))
        .andExpect(problema(406, "nao-aceitavel"));
    mvc.perform(obter(id).accept(MediaType.APPLICATION_XML))
        .andExpect(problema(406, "nao-aceitavel", URL + "/" + id));
  }

  /** AC-T015.3: o X-Request-Id do cliente volta em sucesso e erro. */
  @Test
  void requestIdVoltaEmSucessoEErro() throws Exception {
    mvc.perform(listar("").header("X-Request-Id", "rid-get-200"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", "rid-get-200"));
    mvc.perform(listar("size=0").header("X-Request-Id", "rid-get-400"))
        .andExpect(jsonPath("$.requestId").value("rid-get-400"));
    mvc.perform(obter(UUID.randomUUID()).header("X-Request-Id", "rid-get-404"))
        .andExpect(header().string("X-Request-Id", "rid-get-404"))
        .andExpect(jsonPath("$.requestId").value("rid-get-404"));
  }

  private String registrar(String corpo, String chave) throws Exception {
    return campo(
        mvc.perform(postar(corpo, chave)).andExpect(status().isCreated()).andReturn(), "id");
  }

  private static String comData(String tipo, String valor, String dataOcorrencia) {
    return "{\"tipo\":\""
        + tipo
        + "\",\"valor\":\""
        + valor
        + "\",\"dataOcorrencia\":\""
        + dataOcorrencia
        + "\"}";
  }

  private static JsonNode json(MvcResult resultado) throws Exception {
    return JSON.readTree(resultado.getResponse().getContentAsString());
  }

  private static List<String> ids(JsonNode pagina) {
    List<String> ids = new ArrayList<>();
    pagina.path("itens").forEach(item -> ids.add(item.path("id").asString()));
    return ids;
  }

  private static String menor(String a, String b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  private static String maior(String a, String b) {
    return a.compareTo(b) <= 0 ? b : a;
  }

  /** Corpo do problema sem os membros que variam por requisição. */
  private static String semVariaveis(MvcResult resultado) throws Exception {
    return resultado
        .getResponse()
        .getContentAsString()
        .replaceAll("\"(instance|requestId|traceId|timestamp)\":\"[^\"]*\"", "");
  }
}
