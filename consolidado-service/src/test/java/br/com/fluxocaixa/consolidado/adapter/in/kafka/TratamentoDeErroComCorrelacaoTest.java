package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Follow-up 5 da T-022 (T-032, RNF-012), sem Kafka: enquanto o tratamento de erro recupera um
 * registro, o MDC leva a correlação do registro (caminho {@code handleOne}); depois, o MDC volta ao
 * que era. O caminho {@code handleRemaining}, o usado pelo contêiner, é coberto por {@code
 * RastroDoConsumoIT}.
 */
@Tag("RNF-012")
class TratamentoDeErroComCorrelacaoTest {

  private static final String EVENTO = "4daef025-8e03-4452-b150-b1f0e3a5ca9d";
  private static final String TRACE_DE_ORIGEM = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACE_DO_PUBLICADOR = "0af7651916cd43dd8448eb211c80319c";

  @Test
  void handleOnePoeACorrelacaoNoMdcDuranteARecuperacaoERestaura() {
    Map<String, String> vistoNaRecuperacao = new HashMap<>();
    TratamentoDeErroComCorrelacao tratamento =
        new TratamentoDeErroComCorrelacao(
            (registro, erro) -> {
              if (MDC.getCopyOfContextMap() != null) {
                vistoNaRecuperacao.putAll(MDC.getCopyOfContextMap());
              }
            },
            new FixedBackOff(0, 0));
    ConsumerRecord<String, String> registro =
        new ConsumerRecord<>(
            "cashflow.lancamentos.v1",
            0,
            7,
            "comerciante",
            "{\"eventId\":\"" + EVENTO + "\",\"traceId\":\"" + TRACE_DE_ORIGEM + "\"}");
    registro.headers().add("x-request-id", "req-handle-one".getBytes(StandardCharsets.UTF_8));
    registro
        .headers()
        .add(
            "traceparent",
            ("00-" + TRACE_DO_PUBLICADOR + "-b7ad6b7169203331-01")
                .getBytes(StandardCharsets.UTF_8));

    tratamento.handleOne(
        new IllegalStateException("falha"),
        registro,
        new MockConsumer<String, String>("earliest"),
        null);

    assertThat(vistoNaRecuperacao)
        .containsEntry("eventId", EVENTO)
        .containsEntry("requestId", "req-handle-one")
        .containsEntry("traceIdOrigem", TRACE_DE_ORIGEM)
        .containsEntry("traceId", TRACE_DO_PUBLICADOR);
    assertThat(MDC.getCopyOfContextMap())
        .satisfiesAnyOf(mapa -> assertThat(mapa).isNull(), mapa -> assertThat(mapa).isEmpty());
  }
}
