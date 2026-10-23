package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.application.port.out.OutboxPendente;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Envio do lote (T-016, Q-031). Falha rápida só quando o broker está indisponível (timeout
 * síncrono, que custaria {@code max.block.ms} por evento); outra falha síncrona afeta só o próprio
 * evento. A espera pelas confirmações vem do {@code delivery.timeout.ms} configurado.
 */
@Tag("RNF-007")
class PublicadorKafkaTest {

  private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();

  @Test
  void timeoutSincronoEncerraOLoteSemTentarOResto() {
    AtomicInteger tentativas = new AtomicInteger();
    PublicadorKafka publicador =
        publicador(
            Map.of(),
            registro -> {
              tentativas.incrementAndGet();
              throw new KafkaException(
                  "Send failed",
                  new TimeoutException("Topic not present in metadata after 3000 ms."));
            });
    List<OutboxPendente> lote = List.of(pendente(), pendente(), pendente());

    Map<UUID, String> falhas = publicador.publicar(lote);

    assertThat(tentativas).hasValue(1);
    assertThat(falhas).containsOnlyKeys(lote.stream().map(OutboxPendente::eventId).toList());
    assertThat(falhas.values())
        .allMatch(falha -> falha.startsWith("org.apache.kafka.common.errors.TimeoutException"));
    assertThat(metricas.get("outbox.publish.failures").counter().count()).isEqualTo(3.0);
  }

  /** Um evento com problema próprio na cabeça da fila não trava o resto da outbox. */
  @Test
  void outraFalhaSincronaAfetaSoOProprioEvento() {
    List<OutboxPendente> lote = List.of(pendente(), pendente(), pendente());
    UUID problematico = lote.getFirst().eventId();
    AtomicInteger tentativas = new AtomicInteger();
    PublicadorKafka publicador =
        publicador(
            Map.of(),
            registro -> {
              tentativas.incrementAndGet();
              if (registro.value().equals(problematico.toString())) {
                throw new KafkaException("Send failed", new RecordTooLargeException("grande"));
              }
              return confirmado(registro);
            });

    Map<UUID, String> falhas = publicador.publicar(lote);

    assertThat(tentativas).hasValue(3);
    assertThat(falhas).containsOnlyKeys(problematico);
    assertThat(falhas.get(problematico)).contains("RecordTooLargeException");
  }

  @Test
  void esperaVemDoDeliveryTimeoutComFolga() {
    PublicadorKafka configurado =
        publicador(
            Map.of(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "1500"),
            PublicadorKafkaTest::confirmado);
    PublicadorKafka padrao = publicador(Map.of(), PublicadorKafkaTest::confirmado);

    assertThat(configurado.espera()).isEqualTo(Duration.ofMillis(2_500));
    assertThat(padrao.espera()).isEqualTo(Duration.ofMillis(120_000 + 1_000));
  }

  private PublicadorKafka publicador(
      Map<String, Object> configuracao,
      Function<ProducerRecord<String, String>, CompletableFuture<SendResult<String, String>>>
          envio) {
    KafkaTemplate<String, String> kafka =
        new KafkaTemplate<>(new DefaultKafkaProducerFactory<String, String>(configuracao)) {
          @Override
          public CompletableFuture<SendResult<String, String>> send(
              ProducerRecord<String, String> registro) {
            return envio.apply(registro);
          }
        };
    return new PublicadorKafka(kafka, metricas);
  }

  private static CompletableFuture<SendResult<String, String>> confirmado(
      ProducerRecord<String, String> registro) {
    RecordMetadata metadados =
        new RecordMetadata(new TopicPartition(registro.topic(), 0), 0, 0, 0, 0, 0);
    return CompletableFuture.completedFuture(new SendResult<>(registro, metadados));
  }

  /** O payload é o próprio eventId, para o envio de teste reconhecer o evento. */
  private static OutboxPendente pendente() {
    UUID eventId = UUID.randomUUID();
    return new OutboxPendente(
        eventId, "cashflow.lancamentos.v1", "chave", eventId.toString(), null, Instant.EPOCH);
  }
}
