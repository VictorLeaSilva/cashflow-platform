package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import br.com.fluxocaixa.consolidado.application.AplicarEvento;
import br.com.fluxocaixa.consolidado.application.AplicarEvento.Resultado;
import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
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
 * <p>MDC (Q-046): {@code requestId} só com {@code x-request-id} válido (03 §5.2); {@code eventId}
 * depois da leitura. Uma linha por evento no logger {@value #LOGGER_DE_CONSUMO}, sem valor nem
 * comerciante.
 */
public final class ConsumidorDeLancamentos {

  public static final String TOPICO = "cashflow.lancamentos.v1";
  public static final String DLT = TOPICO + ".DLT";
  public static final String ID_DO_LISTENER = "consolidado-consumidor";
  public static final String CABECALHO_REQUEST_ID = "x-request-id";
  static final String LOGGER_DE_CONSUMO = "br.com.fluxocaixa.consolidado.consumo";

  /** Mesma regra do {@code X-Request-Id} HTTP (03 §5.2). */
  private static final Pattern REQUEST_ID_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

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
      requestIdValido(registro).ifPresent(id -> MDC.put("requestId", id));
      EventoLido lido = leitor.ler(registro.value());
      LancamentoRegistrado evento = lido.evento();
      MDC.put("eventId", evento.eventId().toString());
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
      MDC.remove("requestId");
      MDC.remove("eventId");
    }
  }

  private static Optional<String> requestIdValido(ConsumerRecord<String, String> registro) {
    Header cabecalho = registro.headers().lastHeader(CABECALHO_REQUEST_ID);
    if (cabecalho == null || cabecalho.value() == null) {
      return Optional.empty();
    }
    String valor = new String(cabecalho.value(), StandardCharsets.UTF_8);
    return REQUEST_ID_VALIDO.matcher(valor).matches() ? Optional.of(valor) : Optional.empty();
  }
}
