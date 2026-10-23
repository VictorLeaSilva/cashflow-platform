package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.application.port.out.OutboxPendente;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Falha rápida (03 §7.1): com o broker fora, cada {@code send} pode bloquear até {@code
 * max.block.ms}. Depois da primeira falha síncrona, o resto do lote é dado como falho sem novo
 * bloqueio, para o ciclo caber no timeout da transação e as falhas serem registradas (Q-030).
 */
@Tag("RNF-007")
class PublicadorKafkaTest {

  @Test
  void primeiraFalhaSincronaEncerraOLoteSemTentarOResto() {
    AtomicInteger tentativas = new AtomicInteger();
    KafkaTemplate<String, String> kafka =
        new KafkaTemplate<>(new DefaultKafkaProducerFactory<String, String>(Map.of())) {
          @Override
          public CompletableFuture<SendResult<String, String>> send(
              ProducerRecord<String, String> registro) {
            tentativas.incrementAndGet();
            throw new TimeoutException("Topic not present in metadata after 3000 ms.");
          }
        };
    SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    PublicadorKafka publicador = new PublicadorKafka(kafka, metricas);
    List<OutboxPendente> lote = List.of(pendente(), pendente(), pendente());

    Map<UUID, String> falhas = publicador.publicar(lote);

    assertThat(tentativas).hasValue(1);
    assertThat(falhas).containsOnlyKeys(lote.stream().map(OutboxPendente::eventId).toList());
    assertThat(falhas.get(lote.getFirst().eventId()))
        .startsWith("org.apache.kafka.common.errors.TimeoutException");
    assertThat(falhas.get(lote.getLast().eventId())).contains("TimeoutException");
    assertThat(metricas.get("outbox.publish.failures").counter().count()).isEqualTo(3.0);
  }

  private static OutboxPendente pendente() {
    return new OutboxPendente(
        UUID.randomUUID(), "cashflow.lancamentos.v1", "chave", "{}", null, Instant.EPOCH);
  }
}
