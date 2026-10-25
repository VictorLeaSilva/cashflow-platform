package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.IntegracaoKafka;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AC-T031.1 (RNF-001, risco "Consolidado parado" de 04 §2.7, lado do Lançamentos): sem nenhum
 * consumidor no grupo {@code consolidado-projecao}, N lançamentos seguem 201, a outbox drena
 * ({@code outbox_pending_events} volta a 0) e os N eventos ficam no tópico, cada um pelo menos uma
 * vez. A leitura do tópico usa outro {@code group.id} e vem depois das asserções do POST e da
 * outbox. O lado "Kafka fora" é o {@code KafkaForaIT} (T-016); a prova cruzada, com os dois
 * serviços, é a demo da T-035.
 */
@Tag("RNF-001")
@AutoConfigureMockMvc
@AutoConfigureMetrics
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "lancamentos.outbox.publisher.enabled=true",
      "lancamentos.outbox.publisher.interval=100ms"
    })
class ConsolidadoParadoIT extends IntegracaoKafka {

  private static final int LANCAMENTOS = 12;
  private static final Duration LIMITE = Duration.ofSeconds(60);

  @Autowired private MockMvc mvc;
  @Autowired private MeterRegistry metricas;

  @Test
  void postsSeguem201SemConsumidorEOsEventosFicamNoTopico() throws Exception {
    UUID comerciante = UUID.randomUUID();
    String token = TokensDeTeste.valido(comerciante, TokensDeTeste.ESCRITA);

    for (int i = 0; i < LANCAMENTOS; i++) {
      mvc.perform(registro(token, "consolidado-parado-" + i)).andExpect(status().isCreated());
    }

    await()
        .atMost(LIMITE)
        .untilAsserted(
            () -> {
              assertThat(pendentes(comerciante)).as("outbox do comerciante").isZero();
              assertThat(metricas.get("outbox.pending.events").gauge().value())
                  .as("outbox_pending_events")
                  .isZero();
            });
    Set<UUID> eventos = eventosDoComerciante(comerciante);
    assertThat(eventos).hasSize(LANCAMENTOS);
    assertThat(Eventos.lerAte(eventos, LIMITE)).extracting(Eventos::eventId).containsAll(eventos);
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      registro(String token, String chave) {
    return post("/api/v1/lancamentos")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", chave)
        .content("{\"tipo\":\"CREDITO\",\"valor\":\"10.00\"}");
  }

  private long pendentes(UUID comerciante) {
    return jdbc.sql(
            "SELECT count(*) FROM outbox_event WHERE message_key = ? AND published_at IS NULL")
        .param(comerciante.toString())
        .query(Long.class)
        .single();
  }

  private Set<UUID> eventosDoComerciante(UUID comerciante) {
    List<UUID> ids =
        jdbc.sql("SELECT event_id FROM outbox_event WHERE message_key = ?")
            .param(comerciante.toString())
            .query(UUID.class)
            .list();
    return ids.stream().collect(Collectors.toSet());
  }
}
