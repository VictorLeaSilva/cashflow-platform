package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.credito;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.erroDeCampo;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postar;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.problema;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.semContentLength;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoWeb;
import java.text.Normalizer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

/** AC-T013.3 (RNF-013): 415, 406, 413, texto livre estrito e NFC antes do hash. */
@Tag("RNF-013")
class ValidacaoEstritaWebTest extends IntegracaoWeb {

  private static final int LIMITE = 16 * 1024;

  @Test
  void contentTypeDiferenteDeJsonResponde415() throws Exception {
    mvc.perform(postar(credito("10.00"), "chave-415").contentType(MediaType.TEXT_PLAIN))
        .andExpect(problema(415, "tipo-de-midia-nao-suportado"));
  }

  @Test
  void jsonComCharsetEhAceito() throws Exception {
    mvc.perform(
            postar(credito("10.00"), "chave-charset").contentType("application/json;charset=UTF-8"))
        .andExpect(status().isCreated());
  }

  @Test
  void acceptIncompativelResponde406EmProblemJson() throws Exception {
    mvc.perform(postar(credito("10.00"), "chave-406").accept(MediaType.APPLICATION_XML))
        .andExpect(problema(406, "nao-aceitavel"));
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }

  @Test
  void corpoAcimaDe16KibResponde413() throws Exception {
    mvc.perform(postar(corpoComTamanho(LIMITE + 1), "chave-413"))
        .andExpect(problema(413, "payload-muito-grande"));
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }

  @Test
  void corpoAcimaDe16KibSemContentLengthResponde413() throws Exception {
    mvc.perform(postar(corpoComTamanho(LIMITE + 1), "chave-413-chunked").with(semContentLength()))
        .andExpect(problema(413, "payload-muito-grande"));
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }

  @Test
  void corpoDeExatamente16KibPassaPeloLimite() throws Exception {
    mvc.perform(postar(corpoComTamanho(LIMITE), "chave-16k"))
        .andExpect(problema(400, "requisicao-invalida"));
  }

  @ParameterizedTest
  @CsvSource({
    "descricao, 'linha\\u0001', caractere-de-controle",
    "descricao, 'com\\ttab', caractere-de-controle",
    "descricao, ' antes', espacos-nas-pontas",
    "descricao, 'depois ', espacos-nas-pontas",
    "descricao, '', vazio",
    "identificadorExterno, 'nf\\r1', caractere-de-controle",
    "identificadorExterno, ' nf-1', espacos-nas-pontas",
    "identificadorExterno, '', vazio"
  })
  void textoLivreInvalidoResponde400(String campo, String valorJson, String codigo)
      throws Exception {
    mvc.perform(
            postar(
                "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\",\""
                    + campo
                    + "\":\""
                    + valorJson
                    + "\"}",
                "chave-texto"))
        .andExpect(problema(400, "requisicao-invalida"))
        .andExpect(erroDeCampo(campo, codigo));
    assertThat(portas.quantidadeDeLancamentos()).isZero();
  }

  @Test
  void textoEmNfdEhGravadoEmNfcEReplayEquivalenteEhReconhecido() throws Exception {
    String nfd = Normalizer.normalize("São João", Normalizer.Form.NFD);
    String nfc = Normalizer.normalize("São João", Normalizer.Form.NFC);

    mvc.perform(postar(comDescricao(nfd), "chave-nfc"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.descricao").value(nfc));
    assertThat(portas.lancamentos().getFirst().descricao()).isEqualTo(nfc);

    mvc.perform(postar(comDescricao(nfc), "chave-nfc"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Idempotent-Replayed", "true"));
    mvc.perform(postar(comDescricao(nfd), "chave-nfc"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Idempotent-Replayed", "true"));
    assertThat(portas.quantidadeDeLancamentos()).isEqualTo(1);
  }

  private static String comDescricao(String descricao) {
    return "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\",\"descricao\":\"" + descricao + "\"}";
  }

  /** JSON válido com exatamente {@code tamanho} bytes, com propriedade desconhecida de recheio. */
  private static String corpoComTamanho(int tamanho) {
    String inicio = "{\"tipo\":\"CREDITO\",\"valor\":\"10.00\",\"recheio\":\"";
    String fim = "\"}";
    return inicio + "x".repeat(tamanho - inicio.length() - fim.length()) + fim;
  }
}
