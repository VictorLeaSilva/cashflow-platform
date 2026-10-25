package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.application.PublicarOutbox;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoKafka;
import br.com.fluxocaixa.lancamentos.support.KafkaLancamentos.Mensagem;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-T032.4 (lado do Lançamentos, Q-061): o payload do evento leva o {@code traceId} da requisição
 * de origem, e a mensagem publicada leva o cabeçalho {@code traceparent} (W3C) do envio, com
 * PostgreSQL e Kafka reais. A requisição é simulada por um <i>span</i> aberto em volta do caso de
 * uso, como o filtro de observação HTTP faz.
 */
@Tag("RNF-012")
@Tag("RNF-006")
class RastroDoEventoIT extends IntegracaoKafka {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private PublicarOutbox publicarOutbox;
  @Autowired private Tracer tracer;

  @Test
  void payloadLevaOTraceIdDaRequisicaoEAMensagemLevaOTraceparent() {
    UUID comerciante = UUID.randomUUID();
    Span requisicao = tracer.nextSpan().name("requisicao-de-teste").start();
    UUID evento;
    try (Tracer.SpanInScope ignorado = tracer.withSpan(requisicao)) {
      evento = Eventos.registrar(registrarLancamento, jdbc, comerciante, "rid-rastro-1");
    } finally {
      requisicao.end();
    }
    String traceIdDaRequisicao = requisicao.context().traceId();

    publicarOutbox.publicarLote();

    Mensagem mensagem =
        Eventos.lerAte(Set.of(evento), Duration.ofSeconds(30)).stream()
            .filter(m -> m.valor().contains(evento.toString()))
            .findFirst()
            .orElseThrow();
    assertThat(JSON.readTree(mensagem.valor()).path("traceId").asString())
        .isEqualTo(traceIdDaRequisicao);
    assertThat(mensagem.traceparent()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    assertThat(mensagem.requestId()).isEqualTo("rid-rastro-1");
  }
}
