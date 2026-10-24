package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado.LeitorDaDlt;
import br.com.fluxocaixa.consolidado.support.ProjecaoComFalhas;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * AC-T032.4 (lado do Consolidado, Q-061) e <i>follow-up</i> 5 da T-022: com os cabeçalhos reais
 * ({@code x-request-id} e {@code traceparent}), a linha do evento aplicado traz {@code
 * traceIdOrigem} (o {@code traceId} do payload) e o {@code traceId} do trace que o consumidor
 * continuou; as linhas de <i>retry</i> e de DLT trazem {@code eventId}, {@code requestId} e os dois
 * {@code traceId}.
 */
@Tag("RNF-012")
@Tag("RNF-006")
@ExtendWith(OutputCaptureExtension.class)
class RastroDoConsumoIT extends IntegracaoKafka {

  private static final Duration ESPERA = Duration.ofSeconds(40);
  private static final String DIA = "2026-10-22";
  private static final String TRACE_DE_ORIGEM = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACE_DO_PUBLICADOR = "0af7651916cd43dd8448eb211c80319c";
  private static final String TRACEPARENT = "00-" + TRACE_DO_PUBLICADOR + "-b7ad6b7169203331-01";

  @Test
  void linhaDoEventoAplicadoTrazOTraceIdDeOrigemEOTraceContinuado(CapturedOutput saida) {
    UUID evento = UUID.randomUUID();
    UUID comerciante = UUID.randomUUID();
    Map<String, Object> campos = EventosJson.credito(evento, comerciante, "1.00", DIA);
    campos.put("traceId", TRACE_DE_ORIGEM);

    KafkaConsolidado.publicarComCabecalhos(
        comerciante.toString(),
        EventosJson.json(campos),
        Map.of("x-request-id", "req-rastro-1", "traceparent", TRACEPARENT));

    assertThat(linhaDe(saida, evento, "aplicado"))
        .contains("\"eventId\":\"" + evento + "\"")
        .contains("\"requestId\":\"req-rastro-1\"")
        .contains("\"traceIdOrigem\":\"" + TRACE_DE_ORIGEM + "\"")
        .contains("\"traceId\":\"" + TRACE_DO_PUBLICADOR + "\"");
  }

  @Test
  void semTraceIdNoPayloadNaoHaTraceIdOrigem(CapturedOutput saida) {
    UUID evento = UUID.randomUUID();
    UUID comerciante = UUID.randomUUID();

    KafkaConsolidado.publicar(
        comerciante.toString(),
        EventosJson.json(EventosJson.credito(evento, comerciante, "1.00", DIA)),
        "req-rastro-2");

    assertThat(linhaDe(saida, evento, "aplicado")).doesNotContain("traceIdOrigem");
  }

  @Test
  void retryEDltTrazemEventIdRequestIdETraceIds(CapturedOutput saida) {
    UUID evento = UUID.randomUUID();
    UUID comerciante = UUID.randomUUID();
    String chave = comerciante.toString();
    falhas.falharAntes(evento, ProjecaoComFalhas.SEMPRE);
    Map<String, Object> campos = EventosJson.credito(evento, comerciante, "5.00", DIA);
    campos.put("traceId", TRACE_DE_ORIGEM);

    try (LeitorDaDlt dlt = KafkaConsolidado.leitorDaDlt()) {
      KafkaConsolidado.publicarComCabecalhos(
          chave,
          EventosJson.json(campos),
          Map.of("x-request-id", "req-rastro-3", "traceparent", TRACEPARENT));
      await()
          .atMost(ESPERA)
          .until(() -> dlt.ler(chave, Duration.ofMillis(200)), mensagens -> !mensagens.isEmpty());
    }

    List<String> tentativas = linhasDoErro(saida, evento, "tentativa");
    assertThat(tentativas).hasSize(4);
    List<String> daDlt = linhasDoErro(saida, evento, "DLT");
    assertThat(daDlt).hasSize(1);
    for (String linha : List.of(tentativas.getFirst(), tentativas.getLast(), daDlt.getFirst())) {
      assertThat(linha)
          .contains("\"eventId\":\"" + evento + "\"")
          .contains("\"requestId\":\"req-rastro-3\"")
          .contains("\"traceIdOrigem\":\"" + TRACE_DE_ORIGEM + "\"")
          .contains("\"traceId\":\"" + TRACE_DO_PUBLICADOR + "\"");
    }
  }

  private static String linhaDe(CapturedOutput saida, UUID evento, String palavra) {
    return await()
        .atMost(ESPERA)
        .until(
            () ->
                saida
                    .getAll()
                    .lines()
                    .filter(l -> l.contains("consolidado.consumo"))
                    .filter(l -> l.contains(evento.toString()) && l.contains(palavra))
                    .findFirst()
                    .orElse(null),
            linha -> linha != null);
  }

  private static List<String> linhasDoErro(CapturedOutput saida, UUID evento, String palavra) {
    return saida
        .getAll()
        .lines()
        .filter(l -> l.contains("consolidado.consumo"))
        .filter(l -> l.contains("\"eventId\":\"" + evento + "\""))
        .filter(l -> l.contains(palavra))
        .toList();
  }
}
