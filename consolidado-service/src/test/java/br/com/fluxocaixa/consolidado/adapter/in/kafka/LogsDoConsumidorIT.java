package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado.LeitorDaDlt;
import br.com.fluxocaixa.consolidado.support.ProjecaoComFalhas;
import java.nio.charset.StandardCharsets;
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
 * AC-T022.3 ({@code x-request-id} no MDC, RNF-012) e AC-T022.6 (sem valor, comerciante nem payload
 * nos logs, inclusive durante <i>retry</i> e DLT). Os logs são JSON (Q-049).
 */
@Tag("RNF-012")
@ExtendWith(OutputCaptureExtension.class)
class LogsDoConsumidorIT extends IntegracaoKafka {

  private static final Duration ESPERA = Duration.ofSeconds(40);
  private static final String DIA = "2026-10-22";

  @Test
  void requestIdValidoDoCabecalhoVaiParaALinhaDoEvento(CapturedOutput saida) {
    UUID evento = UUID.randomUUID();
    UUID comerciante = UUID.randomUUID();

    KafkaConsolidado.publicar(
        comerciante.toString(),
        EventosJson.json(EventosJson.credito(evento, comerciante, "1.00", DIA)),
        "req-abc.123_X");

    String linha = linhaDoEvento(saida, evento);
    assertThat(linha)
        .contains("\"requestId\":\"req-abc.123_X\"")
        .contains("aplicado")
        .contains("CREDITO")
        .contains(DIA);
  }

  @Test
  void semCabecalhoOuComCabecalhoInvalidoALinhaNaoTemRequestId(CapturedOutput saida) {
    UUID semCabecalho = UUID.randomUUID();
    UUID invalido = UUID.randomUUID();
    UUID comerciante = UUID.randomUUID();

    KafkaConsolidado.publicar(
        comerciante.toString(),
        EventosJson.json(EventosJson.credito(semCabecalho, comerciante, "1.00", DIA)),
        null);
    KafkaConsolidado.publicar(
        KafkaConsolidado.TOPICO,
        comerciante.toString(),
        EventosJson.json(EventosJson.credito(invalido, comerciante, "1.00", DIA)),
        "req-ok\ninjetado".getBytes(StandardCharsets.UTF_8));

    assertThat(linhaDoEvento(saida, semCabecalho)).doesNotContain("requestId");
    assertThat(linhaDoEvento(saida, invalido)).doesNotContain("requestId");
    assertThat(saida.getAll()).doesNotContain("injetado");
  }

  @Test
  void retryEDltNaoRegistramValorComercianteNemPayload(CapturedOutput saida) {
    UUID comerciante = UUID.randomUUID();
    String chave = comerciante.toString();
    UUID persistente = UUID.randomUUID();
    falhas.falharAntes(persistente, ProjecaoComFalhas.SEMPRE);
    Map<String, Object> foraDoContrato =
        EventosJson.credito(UUID.randomUUID(), comerciante, "123.456", DIA);

    try (LeitorDaDlt dlt = KafkaConsolidado.leitorDaDlt()) {
      KafkaConsolidado.publicar(
          chave,
          EventosJson.json(EventosJson.credito(persistente, comerciante, "4321.98", DIA)),
          null);
      KafkaConsolidado.publicar(chave, EventosJson.json(foraDoContrato), null);
      await()
          .atMost(ESPERA)
          .until(() -> dlt.ler(chave, Duration.ofMillis(200)), mensagens -> mensagens.size() == 2);
    }

    assertThat(saida.getAll())
        .contains(KafkaConsolidado.TOPICO)
        .doesNotContain("4321.98", "123.456", chave);
  }

  private static String linhaDoEvento(CapturedOutput saida, UUID evento) {
    return await()
        .atMost(ESPERA)
        .until(
            () ->
                linhas(saida).stream()
                    .filter(l -> l.contains(evento.toString()))
                    .filter(l -> l.contains("consolidado.consumo"))
                    .findFirst()
                    .orElse(null),
            linha -> linha != null);
  }

  private static List<String> linhas(CapturedOutput saida) {
    return saida.getAll().lines().toList();
  }
}
