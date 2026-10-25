package br.com.fluxocaixa.consolidado.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.consolidado.support.LeituraEmMemoria;
import br.com.fluxocaixa.consolidado.support.RelogioFixo;
import br.com.fluxocaixa.consolidado.support.TokensDeTeste;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.unit.DataSize;

/**
 * AC-T034.4 (RNF-005, API4/API8): cabeçalho acima de 8 KiB → 400 do contêiner (o Tomcat 11 não tem
 * 431, Q-080), <i>timeout</i> de conexão de 5 s e nenhum erro com <i>stack trace</i> ou nome de
 * classe. Precisa do Tomcat de verdade, por isso sobe o servidor numa porta aleatória (Q-077).
 */
@Tag("RNF-005")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"management.server.port=0", "server.port=0"})
@Import({RelogioFixo.class, LeituraEmMemoria.class})
class LimitesHttpWebTest {

  private static final HttpClient HTTP =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  private static final UUID COMERCIANTE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
  private static final List<String> VAZAMENTOS =
      List.of(
          "Exception",
          "stackTrace",
          "\"trace\"",
          "org.springframework",
          "br.com.fluxocaixa",
          "java.lang",
          "jakarta.",
          "\tat ");

  @Autowired private Environment ambiente;

  @DynamicPropertySource
  static void jwt(DynamicPropertyRegistry registro) {
    TokensDeTeste.registrarPropriedades(registro);
  }

  private URI api(String caminho) {
    return URI.create("http://localhost:" + ambiente.getProperty("local.server.port") + caminho);
  }

  private static HttpRequest.Builder autenticada(URI uri) {
    return HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(10))
        .header("Authorization", "Bearer " + TokensDeTeste.valido(COMERCIANTE, "consolidado:read"));
  }

  @Test
  void cabecalhoAcimaDe8KibEhRecusadoPeloContainerCom400() throws Exception {
    HttpRequest requisicao =
        HttpRequest.newBuilder(api("/api/v1/consolidado"))
            .timeout(Duration.ofSeconds(10))
            .header("X-Cabecalho-Grande", "a".repeat(9 * 1024))
            .GET()
            .build();

    assertThat(HTTP.send(requisicao, BodyHandlers.ofString()).statusCode()).isEqualTo(400);
  }

  @Test
  void cabecalhoAbaixoDoLimiteSegueParaAAutenticacao() throws Exception {
    HttpRequest requisicao =
        HttpRequest.newBuilder(api("/api/v1/consolidado"))
            .timeout(Duration.ofSeconds(10))
            .header("X-Cabecalho-Medio", "a".repeat(4 * 1024))
            .GET()
            .build();

    assertThat(HTTP.send(requisicao, BodyHandlers.ofString()).statusCode()).isEqualTo(401);
  }

  @Test
  void respostasNaoRevelamAVersaoDoServidor() throws Exception {
    HttpResponse<String> resposta =
        HTTP.send(
            HttpRequest.newBuilder(api("/api/v1/inexistente")).GET().build(),
            BodyHandlers.ofString());

    assertThat(resposta.headers().firstValue("Server").orElse("")).doesNotContainPattern("[0-9]");
    assertThat(resposta.headers().firstValue("X-Powered-By")).isEmpty();
  }

  @Test
  void limitesDoServidorEstaoNaConfiguracao() {
    assertThat(ambiente.getProperty("server.max-http-request-header-size", DataSize.class))
        .isEqualTo(DataSize.ofKilobytes(8));
    assertThat(ambiente.getProperty("server.error.include-exception", Boolean.class)).isFalse();
    assertThat(ambiente.getProperty("server.tomcat.connection-timeout", Duration.class))
        .isEqualTo(Duration.ofSeconds(5));
    for (String propriedade :
        List.of(
            "server.error.include-stacktrace",
            "server.error.include-message",
            "server.error.include-binding-errors")) {
      assertThat(ambiente.getProperty(propriedade)).as(propriedade).isEqualTo("never");
    }
  }

  @Test
  void nenhumErroDevolveStackTraceOuNomeDeClasse() throws Exception {
    List<HttpResponse<String>> erros =
        List.of(
            HTTP.send(
                HttpRequest.newBuilder(api("/api/v1/consolidado")).GET().build(),
                BodyHandlers.ofString()),
            HTTP.send(
                autenticada(api("/api/v1/consolidado?dataInicio=ontem&dataFim=hoje")).GET().build(),
                BodyHandlers.ofString()),
            HTTP.send(
                autenticada(api("/api/v1/inexistente")).GET().build(), BodyHandlers.ofString()),
            HTTP.send(
                autenticada(api("/api/v1/consolidado")).DELETE().build(), BodyHandlers.ofString()),
            HTTP.send(
                autenticada(api("/api/v1/consolidado"))
                    .header("Accept", "application/xml")
                    .GET()
                    .build(),
                BodyHandlers.ofString()),
            HTTP.send(
                HttpRequest.newBuilder(api("/api/v1/consolidado"))
                    .header("X-Cabecalho-Grande", "a".repeat(9 * 1024))
                    .GET()
                    .build(),
                BodyHandlers.ofString()));

    for (HttpResponse<String> erro : erros) {
      assertThat(erro.statusCode()).isGreaterThanOrEqualTo(400);
      for (String vazamento : VAZAMENTOS) {
        assertThat(erro.body())
            .as("resposta %d não deve conter %s", erro.statusCode(), vazamento)
            .doesNotContain(vazamento);
      }
    }
  }
}
