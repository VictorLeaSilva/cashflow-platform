package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import br.com.fluxocaixa.lancamentos.application.port.out.OutboxPendente;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicadorDeEventos;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * {@link PublicadorDeEventos} com o produtor Kafka (ADR-004; propriedades de 03 §7.1 em {@code
 * spring.kafka.producer}). Chave = {@code message_key} (comerciante), valor = payload da outbox sem
 * alteração e cabeçalho {@code x-request-id} quando houver (03 §5.2). Envia o lote inteiro e só
 * depois espera as confirmações. Registra {@code outbox.publish.duration} e {@code
 * outbox.publish.failures} (03 §8, Q-027).
 */
@Component
public final class PublicadorKafka implements PublicadorDeEventos {

  static final String CABECALHO_REQUEST_ID = "x-request-id";

  /** Padrão do Kafka para {@code delivery.timeout.ms}, se a configuração não informar. */
  static final Duration DELIVERY_TIMEOUT_PADRAO = Duration.ofMillis(120_000);

  /** Folga sobre o {@code delivery.timeout.ms}: depois dele o produtor já falhou o envio. */
  static final Duration FOLGA = Duration.ofSeconds(1);

  private final KafkaOperations<String, String> kafka;
  private final Duration espera;
  private final Timer duracao;
  private final Counter falhas;

  public PublicadorKafka(KafkaOperations<String, String> kafka, MeterRegistry metricas) {
    this.kafka = kafka;
    this.espera = deliveryTimeout(kafka).plus(FOLGA);
    this.duracao =
        Timer.builder("outbox.publish.duration")
            .description("Duração do envio de cada lote da outbox ao broker")
            .register(metricas);
    this.falhas =
        Counter.builder("outbox.publish.failures")
            .description("Eventos da outbox cujo envio falhou")
            .register(metricas);
  }

  /**
   * Prazo de espera pelas confirmações do lote: o {@code delivery.timeout.ms} configurado (03 §7.1:
   * 10 s) mais a folga, para o prazo acompanhar a configuração em vez de uma constante.
   */
  Duration espera() {
    return espera;
  }

  private static Duration deliveryTimeout(KafkaOperations<String, String> kafka) {
    Object configurado =
        kafka
            .getProducerFactory()
            .getConfigurationProperties()
            .get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG);
    return configurado == null
        ? DELIVERY_TIMEOUT_PADRAO
        : Duration.ofMillis(Long.parseLong(configurado.toString().strip()));
  }

  @Override
  public Map<UUID, String> publicar(List<OutboxPendente> lote) {
    return duracao.record(
        () -> {
          Map<UUID, String> falhasDoLote = new LinkedHashMap<>();
          Map<UUID, CompletableFuture<SendResult<String, String>>> envios =
              enviar(lote, falhasDoLote);
          esperar(envios, falhasDoLote);
          falhas.increment(falhasDoLote.size());
          return falhasDoLote;
        });
  }

  /**
   * Dispara os envios. Com o broker fora, cada {@code send} pode bloquear até {@code max.block.ms}
   * esperando metadados: depois do primeiro timeout síncrono, o resto do lote é dado como falho sem
   * novo bloqueio (falha rápida, 03 §7.1, Q-031). Outra falha síncrona afeta só o próprio evento,
   * para um evento problemático não travar a outbox. Assim o ciclo fica em torno de {@code
   * max.block.ms} + {@code delivery.timeout.ms}, dentro do timeout da transação, e as falhas são
   * registradas.
   */
  private Map<UUID, CompletableFuture<SendResult<String, String>>> enviar(
      List<OutboxPendente> lote, Map<UUID, String> falhasDoLote) {
    Map<UUID, CompletableFuture<SendResult<String, String>>> envios = new LinkedHashMap<>();
    String falhaImediata = null;
    for (OutboxPendente evento : lote) {
      if (falhaImediata != null) {
        falhasDoLote.put(evento.eventId(), falhaImediata);
        continue;
      }
      try {
        envios.put(evento.eventId(), kafka.send(mensagem(evento)));
      } catch (RuntimeException e) {
        String descricao = DescricaoDeFalha.de(e);
        falhasDoLote.put(evento.eventId(), descricao);
        if (DescricaoDeFalha.timeout(e)) {
          falhaImediata = descricao;
        }
      }
    }
    return envios;
  }

  /** Espera as confirmações dentro de um único prazo para o lote inteiro. */
  private void esperar(
      Map<UUID, CompletableFuture<SendResult<String, String>>> envios,
      Map<UUID, String> falhasDoLote) {
    long prazo = System.nanoTime() + espera.toNanos();
    for (Map.Entry<UUID, CompletableFuture<SendResult<String, String>>> envio : envios.entrySet()) {
      try {
        envio.getValue().get(Math.max(0, prazo - System.nanoTime()), TimeUnit.NANOSECONDS);
      } catch (ExecutionException | TimeoutException e) {
        falhasDoLote.put(envio.getKey(), DescricaoDeFalha.de(e));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        falhasDoLote.put(envio.getKey(), DescricaoDeFalha.de(e));
      }
    }
  }

  private static ProducerRecord<String, String> mensagem(OutboxPendente evento) {
    ProducerRecord<String, String> mensagem =
        new ProducerRecord<>(evento.topic(), evento.messageKey(), evento.payload());
    if (evento.requestId() != null) {
      mensagem
          .headers()
          .add(CABECALHO_REQUEST_ID, evento.requestId().getBytes(StandardCharsets.UTF_8));
    }
    return mensagem;
  }
}
