package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.support.PortasEmMemoria;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * AC-T032.1 (Lançamentos): a porta de gestão separada serve {@code health} e {@code prometheus} sem
 * token, com as métricas de {@code 03 §8}; a porta da API não serve o Actuator (Q-062).
 */
@Tag("RNF-006")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"management.server.port=0", "server.port=0"})
@AutoConfigureMetrics
@Import({RelogioFixo.class, PortasEmMemoria.class})
class GestaoActuatorWebTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @Autowired private Environment ambiente;

  @DynamicPropertySource
  static void jwt(DynamicPropertyRegistry registro) {
    TokensDeTeste.registrarPropriedades(registro);
  }

  @Test
  void prometheusNaPortaDeGestaoSemTokenTrazAsMetricasDoServico() throws Exception {
    // http_server_requests só existe depois da primeira requisição à API.
    get(api("/api/v1/lancamentos"), null);
    HttpResponse<String> resposta = get(gestao("/actuator/prometheus"), null);

    assertThat(resposta.statusCode()).isEqualTo(200);
    for (String metrica :
        List.of(
            "lancamentos_registrados_total{",
            "idempotency_replays_total",
            "idempotency_conflicts_total",
            "outbox_pending_events",
            "outbox_oldest_pending_age_seconds",
            "outbox_publish_failures_total",
            "outbox_publish_duration_seconds",
            "http_server_requests_seconds",
            "jvm_memory_used_bytes")) {
      assertThat(resposta.body()).as("métrica %s", metrica).contains(metrica);
    }
  }

  @Test
  void healthNaoMostraDetalhes() throws Exception {
    HttpResponse<String> resposta = get(gestao("/actuator/health"), null);

    assertThat(resposta.body()).contains("\"status\"").doesNotContain("components", "details");
  }

  @Test
  void soHealthEPrometheusEstaoExpostos() throws Exception {
    assertThat(get(gestao("/actuator/env"), null).statusCode()).isEqualTo(404);
    assertThat(get(gestao("/actuator/info"), null).statusCode()).isEqualTo(404);
    assertThat(get(gestao("/actuator/metrics"), null).statusCode()).isEqualTo(404);
  }

  @Test
  void aPortaDaApiNaoServeOActuator() throws Exception {
    String token = TokensDeTeste.valido(UUID.randomUUID(), TokensDeTeste.LEITURA);

    assertThat(get(api("/actuator/prometheus"), token).statusCode()).isEqualTo(404);
    assertThat(get(api("/actuator/health"), token).statusCode()).isEqualTo(404);
    // Sem token a cadeia de segurança responde antes do MVC: 401, sem revelar rota nenhuma.
    assertThat(get(api("/actuator/prometheus"), null).statusCode()).isEqualTo(401);
  }

  private URI gestao(String caminho) {
    return URI.create(
        "http://localhost:" + ambiente.getProperty("local.management.port") + caminho);
  }

  private URI api(String caminho) {
    return URI.create("http://localhost:" + ambiente.getProperty("local.server.port") + caminho);
  }

  private static HttpResponse<String> get(URI uri, String token)
      throws IOException, InterruptedException {
    HttpRequest.Builder requisicao = HttpRequest.newBuilder(uri).GET();
    if (token != null) {
      requisicao.header("Authorization", "Bearer " + token);
    }
    return HTTP.send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
  }
}
