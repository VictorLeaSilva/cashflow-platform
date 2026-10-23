package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.config.LacoDoPublicador;
import br.com.fluxocaixa.lancamentos.support.IntegracaoKafka;
import br.com.fluxocaixa.lancamentos.support.KafkaLancamentos;
import br.com.fluxocaixa.lancamentos.support.TokensDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AC-T016.4 (RNF-007, risco de 04 §2.7 na parte de Lançamentos) com o laço do publicador ligado e
 * tempos curtos (AC-T016.9): com o Kafka pausado, o POST segue 201 e rápido, o pool da API fica
 * livre, a outbox cresce com tentativas registradas e o intervalo sobe até o teto; com o Kafka de
 * volta, tudo é publicado e o intervalo volta à base. O contexto é descartado ao fim para que o
 * laço não continue rodando nos outros testes.
 */
@Tag("RNF-007")
@Tag("RNF-001")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "lancamentos.outbox.publisher.enabled=true",
      "lancamentos.outbox.publisher.interval=100ms",
      "lancamentos.outbox.publisher.max-backoff=800ms",
      "spring.kafka.producer.properties[request.timeout.ms]=1000",
      "spring.kafka.producer.properties[delivery.timeout.ms]=1500"
    })
class KafkaForaIT extends IntegracaoKafka {

  private static final Duration TETO = Duration.ofMillis(800);
  private static final Duration BASE = Duration.ofMillis(100);

  @Autowired private MockMvc mvc;
  @Autowired private LacoDoPublicador laco;
  @Autowired private DataSource poolDaApi;

  @Autowired
  @Qualifier("publicador")
  private DataSource poolDoPublicador;

  @Test
  void kafkaForaNaoAfetaAApiEAOutboxDrenaQuandoEleVolta(CapturedOutput saida) throws Exception {
    UUID comerciante = UUID.randomUUID();
    String token = TokensDeTeste.valido(comerciante, TokensDeTeste.ESCRITA);
    Set<String> requestIds = new HashSet<>();
    aquecer(token, comerciante);

    KafkaLancamentos.pausar();
    try {
      for (int i = 0; i < 5; i++) {
        String requestId = "rid-kafka-fora-" + i;
        requestIds.add(requestId);
        long inicio = System.nanoTime();
        mvc.perform(registro(token, "kafka-fora-" + i, requestId)).andExpect(status().isCreated());
        assertThat(Duration.ofNanos(System.nanoTime() - inicio))
            .as("o POST não toca no Kafka")
            .isLessThan(Duration.ofMillis(1_500));
        assertThat(ativas(poolDaApi)).as("o publicador não usa o pool da API").isZero();
      }
      await()
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                assertThat(laco.intervaloAtual()).isEqualTo(TETO);
                assertThat(tentativasMinimas(comerciante)).isGreaterThanOrEqualTo(2);
              });
      assertThat(pendentes(comerciante)).isEqualTo(5);
      assertThat(ultimoErro(comerciante)).doesNotContain(comerciante.toString(), "rid-kafka-fora");
    } finally {
      KafkaLancamentos.retomar();
    }

    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              assertThat(pendentes(comerciante)).isZero();
              assertThat(laco.intervaloAtual()).isEqualTo(BASE);
            });
    assertThat(hikari(poolDoPublicador).getMaximumPoolSize()).isEqualTo(2);
    verificarLogsDoPublicador(saida, comerciante, requestIds);
  }

  /** Aquecimento com o Kafka no ar: o tempo dos POSTs medidos não inclui a primeira requisição. */
  private void aquecer(String token, UUID comerciante) throws Exception {
    mvc.perform(registro(token, "kafka-fora-aquecimento", "rid-aquecimento"))
        .andExpect(status().isCreated());
    await().atMost(Duration.ofSeconds(30)).until(() -> pendentes(comerciante) == 0);
  }

  /**
   * AC-T016.10: as linhas do publicador não levam payload, chave nem request ID dos eventos (o log
   * de acesso registra o request ID da requisição HTTP, por desenho, RNF-012).
   */
  private static void verificarLogsDoPublicador(
      CapturedOutput saida, UUID comerciante, Set<String> requestIds) {
    List<String> doPublicador =
        saida.getAll().lines().filter(linha -> linha.contains("Publicador")).toList();
    assertThat(doPublicador).isNotEmpty();
    assertThat(doPublicador)
        .noneMatch(
            linha ->
                linha.contains(comerciante.toString())
                    || linha.contains("\"valor\"")
                    || requestIds.stream().anyMatch(linha::contains));
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      registro(String token, String chave, String requestId) {
    return post("/api/v1/lancamentos")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", chave)
        .header("X-Request-Id", requestId)
        .content("{\"tipo\":\"CREDITO\",\"valor\":\"10.00\"}");
  }

  private long pendentes(UUID comerciante) {
    return jdbc.sql(
            "SELECT count(*) FROM outbox_event WHERE message_key = ? AND published_at IS NULL")
        .param(comerciante.toString())
        .query(Long.class)
        .single();
  }

  private int tentativasMinimas(UUID comerciante) {
    // Só as pendentes: o evento do aquecimento foi publicado sem falha.
    return jdbc.sql(
            "SELECT min(attempt_count) FROM outbox_event WHERE message_key = ?"
                + " AND published_at IS NULL")
        .param(comerciante.toString())
        .query(Integer.class)
        .single();
  }

  private String ultimoErro(UUID comerciante) {
    return jdbc.sql(
            "SELECT last_error FROM outbox_event WHERE message_key = ? AND last_error IS NOT NULL"
                + " LIMIT 1")
        .param(comerciante.toString())
        .query(String.class)
        .single();
  }

  private static int ativas(DataSource dataSource) {
    return hikari(dataSource).getHikariPoolMXBean().getActiveConnections();
  }

  private static HikariDataSource hikari(DataSource dataSource) {
    return (HikariDataSource) dataSource;
  }
}
