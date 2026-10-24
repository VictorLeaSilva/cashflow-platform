package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import br.com.fluxocaixa.consolidado.application.AplicarEvento;
import br.com.fluxocaixa.consolidado.application.AplicarEvento.Resultado;
import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Consumidor de {@code LancamentoRegistrado.v1} (03 §2.2, T-022). Um registro por vez: lê e valida
 * a mensagem, aplica o evento e registra as métricas. O <i>offset</i> só é confirmado depois que
 * este método retorna, isto é, depois do <i>commit</i> no banco ({@code AckMode.RECORD}, Q-044).
 * Exceções sobem para o tratamento de erro ({@code ConsumidorConfig}): <i>retry</i> e DLT (Q-045).
 *
 * <p>MDC (Q-046, Q-061): {@code requestId} só com {@code x-request-id} válido (03 §5.2); {@code
 * eventId} e {@code traceIdOrigem} (o {@code traceId} do payload, se válido) depois da leitura; o
 * {@code traceId} é o do <i>trace</i> que a observação do Kafka continua a partir do {@code
 * traceparent}. Uma linha por evento no logger {@value #LOGGER_DE_CONSUMO}, sem valor nem
 * comerciante.
 */
public final class ConsumidorDeLancamentos {

  public static final String TOPICO = "cashflow.lancamentos.v1";
  public static final String DLT = TOPICO + ".DLT";
  public static final String ID_DO_LISTENER = "consolidado-consumidor";
  public static final String CABECALHO_REQUEST_ID = "x-request-id";
  static final String LOGGER_DE_CONSUMO = "br.com.fluxocaixa.consolidado.consumo";

  private static final Logger CONSUMO = LoggerFactory.getLogger(LOGGER_DE_CONSUMO);

  private final LeitorDeEvento leitor;
  private final AplicarEvento aplicarEvento;
  private final MetricasDoConsumidor metricas;
  private final Clock clock;

  public ConsumidorDeLancamentos(
      LeitorDeEvento leitor,
      AplicarEvento aplicarEvento,
      MetricasDoConsumidor metricas,
      Clock clock) {
    this.leitor = Objects.requireNonNull(leitor, "leitor");
    this.aplicarEvento = Objects.requireNonNull(aplicarEvento, "aplicarEvento");
    this.metricas = Objects.requireNonNull(metricas, "metricas");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @KafkaListener(
      id = ID_DO_LISTENER,
      topics = TOPICO,
      groupId = "${consolidado.consumer.group-id}",
      concurrency = "${consolidado.consumer.concurrency}",
      autoStartup = "${consolidado.consumer.enabled}")
  public void consumir(ConsumerRecord<String, String> registro) {
    try {
      CorrelacaoDoRegistro.requestId(registro)
          .ifPresent(id -> MDC.put(CorrelacaoDoRegistro.REQUEST_ID, id));
      EventoLido lido = leitor.ler(registro.value());
      LancamentoRegistrado evento = lido.evento();
      MDC.put(CorrelacaoDoRegistro.EVENT_ID, evento.eventId().toString());
      CorrelacaoDoRegistro.traceIdDeOrigemValido(lido.traceId())
          .ifPresent(id -> MDC.put(CorrelacaoDoRegistro.TRACE_ID_ORIGEM, id));
      Resultado resultado = aplicarEvento.aplicar(evento);
      if (resultado == Resultado.APLICADO) {
        metricas.aplicado(Duration.between(lido.occurredAt(), clock.instant()));
      } else {
        metricas.duplicado();
      }
      CONSUMO.info(
          "evento {} resultado={} tipo={} dataReferencia={}",
          evento.eventId(),
          resultado == Resultado.APLICADO ? "aplicado" : "duplicado",
          evento.tipo(),
          evento.dataReferencia());
    } finally {
      MDC.remove(CorrelacaoDoRegistro.REQUEST_ID);
      MDC.remove(CorrelacaoDoRegistro.EVENT_ID);
      MDC.remove(CorrelacaoDoRegistro.TRACE_ID_ORIGEM);
    }
  }
}
