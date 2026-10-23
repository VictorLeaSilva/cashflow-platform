package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.COMERCIANTE;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.URL;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.campo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.credito;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.erroDeCampo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.estornar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.estornarComo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.problema;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.semContentLength;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.urlDoEstorno;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.util.Locale;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * POST /api/v1/lancamentos/{id}/estornos na fatia web (T-014): sucesso, replay, 404 sem revelar
 * existência, 409, 422, validação de caminho, cabeçalho e corpo (Q-017, Q-018), segurança e request
 * ID (AC-003.x, AC-007.1..3, AC-010.x, AC-T014.4..6, AC-T014.8, AC-T014.12).
 */
@Tag("RF-003")
@Tag("RF-007")
@Tag("RF-010")
@Tag("RNF-005")
@Tag("RNF-012")
@Tag("RNF-013")
class PostEstornoWebTest extends IntegracaoWeb {

  private static final int LIMITE = 16 * 1024;

  @Test
  void estornoDeCreditoResponde201ComOsDadosDoOriginal() throws Exception {
    UUID original = registrar(credito("100.00"), "original-credito");
    Lancamento antes = portas.lancamentos().getFirst();

    MvcResult resultado =
        mvc.perform(estornar(original, "estorno-credito"))
            .andExpect(status().isCreated())
            .andExpect(header().doesNotExist("Idempotent-Replayed"))
            .andExpect(jsonPath("$.comercianteId").value(COMERCIANTE.toString()))
            .andExpect(jsonPath("$.tipo").value("ESTORNO"))
            .andExpect(jsonPath("$.valor").value("100.00"))
            .andExpect(jsonPath("$.referenciaLancamentoId").value(original.toString()))
            .andExpect(jsonPath("$.tipoOriginal").value("CREDITO"))
            .andExpect(jsonPath("$.dataReferencia").value("2026-10-23"))
            .andExpect(jsonPath("$.dataOcorrencia").value(RelogioFixo.AGORA.toString()))
            .andExpect(jsonPath("$.criadoEm").value(RelogioFixo.AGORA.toString()))
            .andExpect(jsonPath("$.descricao").value(Matchers.nullValue()))
            .andExpect(jsonPath("$.identificadorExterno").value(Matchers.nullValue()))
            .andReturn();

    String estornoId = campo(resultado, "id");
    assertThat(estornoId).isNotEqualTo(original.toString());
    assertThat(resultado.getResponse().getHeader("Location")).isEqualTo(URL + "/" + estornoId);
    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(2);
    assertThat(portas.quantidadeDeEventos()).isEqualTo(2);
    assertThat(portas.lancamentos().getFirst()).isEqualTo(antes);
  }

  @Test
  void estornoDeDebitoTrazTipoOriginalDebito() throws Exception {
    UUID original = registrar("{\"tipo\":\"DEBITO\",\"valor\":\"35.10\"}", "original-debito");

    mvc.perform(estornar(original, "estorno-debito"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.valor").value("35.10"))
        .andExpect(jsonPath("$.tipoOriginal").value("DEBITO"));
  }

  /** AC-T014.6 (Q-020): o registro comum traz o campo novo como null. */
  @Test
  void registroComumTrazTipoOriginalNulo() throws Exception {
    mvc.perform(postar(credito("1.00"), "registro-tipo-original"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tipoOriginal").value(Matchers.nullValue()))
        // exists() trata null como ausente: o campo tem de estar no corpo, com null.
        .andExpect(
            content()
                .string(Matchers.containsString("\"tipoOriginal\":null,\"identificadorExterno\"")));
  }

  @Test
  void replayDevolveOMesmoCorpoSemGravarNada() throws Exception {
    UUID original = registrar(credito("10.00"), "original-replay");
    MvcResult primeiro =
        mvc.perform(estornar(original, "estorno-replay"))
            .andExpect(status().isCreated())
            .andReturn();

    MvcResult replay =
        mvc.perform(estornar(original, "estorno-replay"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andReturn();

    assertThat(replay.getResponse().getContentAsString())
        .isEqualTo(primeiro.getResponse().getContentAsString());
    assertThat(replay.getResponse().getHeader("Location"))
        .isEqualTo(primeiro.getResponse().getHeader("Location"));
    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(2);
    assertThat(portas.quantidadeDeEventos()).isEqualTo(2);
    assertThat(portas.quantidadeDeChaves()).isEqualTo(2);
  }

  @Test
  void originalInexistenteResponde404SemGravarNada() throws Exception {
    UUID inexistente = UUID.randomUUID();

    mvc.perform(estornar(inexistente, "estorno-404"))
        .andExpect(problema(404, "lancamento-nao-encontrado", urlDoEstorno(inexistente)));

    assertThat(portas.quantidadeDeChaves()).isZero();
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }

  /** AC-003.3 / AC-010.1: de outro comerciante é indistinguível de inexistente. */
  @Test
  void originalDeOutroComercianteResponde404IgualAoInexistente() throws Exception {
    UUID doOutro = registrar(credito("10.00"), "original-outro");
    String outroComerciante = TokensDeTeste.valido(UUID.randomUUID(), TokensDeTeste.ESCRITA);
    UUID inexistente = UUID.randomUUID();

    MvcResult cruzado =
        mvc.perform(estornarComo(outroComerciante, doOutro, "estorno-cruzado"))
            .andExpect(problema(404, "lancamento-nao-encontrado", urlDoEstorno(doOutro)))
            .andReturn();
    MvcResult ausente =
        mvc.perform(estornarComo(outroComerciante, inexistente, "estorno-ausente"))
            .andExpect(status().isNotFound())
            .andReturn();

    assertThat(semVariaveis(cruzado)).isEqualTo(semVariaveis(ausente));
    // O id só aparece em instance, que é o caminho enviado pelo próprio cliente.
    assertThat(semVariaveis(cruzado)).doesNotContain(doOutro.toString());
    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(1);
    assertThat(portas.quantidadeDeChaves()).isEqualTo(1);
  }

  @Test
  void originalJaEstornadoPorOutraChaveResponde409() throws Exception {
    UUID original = registrar(credito("10.00"), "original-409");
    mvc.perform(estornar(original, "estorno-409-a")).andExpect(status().isCreated());

    mvc.perform(estornar(original, "estorno-409-b"))
        .andExpect(problema(409, "lancamento-ja-estornado", urlDoEstorno(original)));

    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(2);
    assertThat(portas.quantidadeDeChaves()).isEqualTo(2);
  }

  @Test
  void estornoDeEstornoResponde422() throws Exception {
    UUID original = registrar(credito("10.00"), "original-422");
    MvcResult estorno =
        mvc.perform(estornar(original, "estorno-422-a"))
            .andExpect(status().isCreated())
            .andReturn();
    String estornoId = campo(estorno, "id");

    mvc.perform(estornar(estornoId, "estorno-422-b"))
        .andExpect(problema(422, "estorno-de-estorno", urlDoEstorno(estornoId)));

    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(2);
  }

  @Test
  void chaveAusenteEIdInvalidoVoltamJuntosEm400() throws Exception {
    mvc.perform(estornar("abc", null))
        .andExpect(problema(400, "requisicao-invalida", urlDoEstorno("abc")))
        .andExpect(erroDeCampo("Idempotency-Key", "obrigatorio"))
        .andExpect(erroDeCampo("id", "formato-invalido"));
  }

  @Test
  void chaveForaDoFormatoResponde400() throws Exception {
    UUID original = registrar(credito("10.00"), "original-chave");

    mvc.perform(estornar(original, "chave com espaço"))
        .andExpect(problema(400, "requisicao-invalida", urlDoEstorno(original)))
        .andExpect(erroDeCampo("Idempotency-Key", "formato-invalido"));
  }

  /** AC-T014.4 (Q-018): só a forma canônica de 36 caracteres. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "abc",
        "1-1-1-1-1",
        "0f8fad5b-d9cb-469f-a165-70867728950e0",
        "0f8fad5bd9cb469fa165"
      })
  void idForaDaFormaCanonicaResponde400(String id) throws Exception {
    mvc.perform(estornar(id, "estorno-id-invalido"))
        .andExpect(problema(400, "requisicao-invalida", urlDoEstorno(id)))
        .andExpect(erroDeCampo("id", "formato-invalido"));

    assertThat(portas.quantidadeDeChaves()).isZero();
  }

  @Test
  void idEmMaiusculasEhAMesmaOperacaoQueEmMinusculas() throws Exception {
    UUID original = registrar(credito("10.00"), "original-caixa");
    String maiusculo = original.toString().toUpperCase(Locale.ROOT);

    mvc.perform(estornar(maiusculo, "estorno-caixa"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.referenciaLancamentoId").value(original.toString()));
    mvc.perform(estornar(original, "estorno-caixa"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Idempotent-Replayed", "true"));
  }

  @Test
  void mesmaChaveComOutroOriginalResponde422() throws Exception {
    UUID primeiro = registrar(credito("10.00"), "original-reuso-1");
    UUID segundo = registrar(credito("20.00"), "original-reuso-2");
    mvc.perform(estornar(primeiro, "estorno-reuso")).andExpect(status().isCreated());

    mvc.perform(estornar(segundo, "estorno-reuso"))
        .andExpect(problema(422, "idempotency-key-reuse", urlDoEstorno(segundo)));

    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(3);
  }

  @Test
  void chaveDeRegistroNaoServeParaEstornoEViceVersa() throws Exception {
    UUID original = registrar(credito("10.00"), "chave-compartilhada");

    mvc.perform(estornar(original, "chave-compartilhada"))
        .andExpect(problema(422, "idempotency-key-reuse", urlDoEstorno(original)));
    mvc.perform(estornar(original, "chave-do-estorno")).andExpect(status().isCreated());
    mvc.perform(postar(credito("10.00"), "chave-do-estorno"))
        .andExpect(problema(422, "idempotency-key-reuse"));
  }

  /** AC-T014.5 (Q-017): sem corpo e {@code {}} são a mesma operação. */
  @Test
  void corpoVazioEhOMesmoQueSemCorpo() throws Exception {
    UUID original = registrar(credito("10.00"), "original-corpo");

    mvc.perform(estornar(original, "estorno-corpo")).andExpect(status().isCreated());
    mvc.perform(
            estornar(original, "estorno-corpo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Idempotent-Replayed", "true"));
  }

  @Test
  void objetoVazioComJsonEhAceito() throws Exception {
    UUID original = registrar(credito("10.00"), "original-objeto");

    mvc.perform(
            estornar(original, "estorno-objeto")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isCreated());
  }

  @Test
  void semCorpoComContentTypeQualquerEhAceito() throws Exception {
    UUID original = registrar(credito("10.00"), "original-sem-corpo");

    mvc.perform(estornar(original, "estorno-sem-corpo").contentType(MediaType.TEXT_PLAIN))
        .andExpect(status().isCreated());
  }

  @ParameterizedTest
  @ValueSource(strings = {"comercianteId", "motivo"})
  void propriedadeNoCorpoResponde400(String propriedade) throws Exception {
    UUID original = registrar(credito("10.00"), "original-prop");

    mvc.perform(
            estornar(original, "estorno-prop")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + propriedade + "\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(problema(400, "requisicao-invalida", urlDoEstorno(original)))
        .andExpect(erroDeCampo(propriedade, "propriedade-desconhecida"));

    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "[]|corpo|json-malformado",
        "null|corpo|json-malformado",
        "\"texto\"|corpo|json-malformado",
        "{|corpo|json-malformado",
        "{\"a\":1,\"a\":2}|a|propriedade-desconhecida"
      })
  void corpoQueNaoEhObjetoVazioResponde400(String corpo, String campo, String codigo)
      throws Exception {
    UUID original = registrar(credito("10.00"), "original-malformado");

    mvc.perform(
            estornar(original, "estorno-malformado")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo))
        .andExpect(problema(400, "requisicao-invalida", urlDoEstorno(original)))
        .andExpect(erroDeCampo(campo, codigo));

    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(1);
  }

  /** Erros de cabeçalho, caminho e corpo voltam juntos (T-014, precedência dos erros). */
  @Test
  void errosDeCabecalhoCaminhoECorpoVoltamJuntos() throws Exception {
    mvc.perform(
            estornar("abc", null).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":1}"))
        .andExpect(problema(400, "requisicao-invalida", urlDoEstorno("abc")))
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(erroDeCampo("Idempotency-Key", "obrigatorio"))
        .andExpect(erroDeCampo("id", "formato-invalido"))
        .andExpect(erroDeCampo("motivo", "propriedade-desconhecida"));
  }

  @Test
  void corpoComContentTypeDiferenteDeJsonResponde415() throws Exception {
    UUID original = registrar(credito("10.00"), "original-415");

    mvc.perform(estornar(original, "estorno-415").contentType(MediaType.TEXT_PLAIN).content("{}"))
        .andExpect(problema(415, "tipo-de-midia-nao-suportado", urlDoEstorno(original)));
  }

  @Test
  void acceptIncompativelResponde406() throws Exception {
    UUID original = registrar(credito("10.00"), "original-406");

    mvc.perform(estornar(original, "estorno-406").accept(MediaType.APPLICATION_XML))
        .andExpect(problema(406, "nao-aceitavel", urlDoEstorno(original)));
  }

  @Test
  void corpoAcimaDe16KibResponde413ComESemContentLength() throws Exception {
    UUID original = registrar(credito("10.00"), "original-413");
    String grande = "{\"recheio\":\"" + "x".repeat(LIMITE) + "\"}";

    mvc.perform(
            estornar(original, "estorno-413")
                .contentType(MediaType.APPLICATION_JSON)
                .content(grande))
        .andExpect(problema(413, "payload-muito-grande", urlDoEstorno(original)));
    mvc.perform(
            estornar(original, "estorno-413-chunked")
                .contentType(MediaType.APPLICATION_JSON)
                .content(grande)
                .with(semContentLength()))
        .andExpect(problema(413, "payload-muito-grande", urlDoEstorno(original)));

    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(1);
  }

  @Test
  void semTokenResponde401EComLeituraResponde403() throws Exception {
    UUID original = registrar(credito("10.00"), "original-seguranca");
    String leitura = TokensDeTeste.valido(COMERCIANTE, TokensDeTeste.LEITURA);

    mvc.perform(estornarComo(null, original, "estorno-401"))
        .andExpect(problema(401, "nao-autenticado", urlDoEstorno(original)));
    mvc.perform(estornarComo(leitura, original, "estorno-403"))
        .andExpect(problema(403, "acesso-negado", urlDoEstorno(original)));

    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(1);
    assertThat(portas.quantidadeDeChaves()).isEqualTo(1);
  }

  /** AC-T014.12: o X-Request-Id do cliente volta em sucesso e erro, igual ao do problem+json. */
  @Test
  void requestIdVoltaEmSucessoEErro() throws Exception {
    UUID original = registrar(credito("10.00"), "original-rid");

    mvc.perform(estornar(original, "estorno-rid").header("X-Request-Id", "rid-estorno-201"))
        .andExpect(status().isCreated())
        .andExpect(header().string("X-Request-Id", "rid-estorno-201"));
    mvc.perform(estornar(original, "estorno-rid-409").header("X-Request-Id", "rid-estorno-409"))
        .andExpect(problema(409, "lancamento-ja-estornado", urlDoEstorno(original)))
        .andExpect(jsonPath("$.requestId").value("rid-estorno-409"));
    mvc.perform(estornar("abc", "estorno-rid-400").header("X-Request-Id", "rid-estorno-400"))
        .andExpect(jsonPath("$.requestId").value("rid-estorno-400"));
  }

  private UUID registrar(String corpo, String chave) throws Exception {
    MvcResult resultado =
        mvc.perform(postar(corpo, chave)).andExpect(status().isCreated()).andReturn();
    return UUID.fromString(campo(resultado, "id"));
  }

  /** Corpo do problema sem os membros que variam por requisição. */
  private static String semVariaveis(MvcResult resultado) throws Exception {
    return resultado
        .getResponse()
        .getContentAsString()
        .replaceAll("\"(instance|requestId|timestamp)\":\"[^\"]*\"", "");
  }
}
