package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado.LeitorDaDlt;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado.MensagemDaDlt;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * AC-T031.4 (RNF-007, <i>follow-up</i> 4 da T-022): o evento fora do contrato é lido e vai direto
 * para o <i>recoverer</i> da DLT; no instante do envio à DLT o broker é pausado (um interceptador
 * de teste do produtor da DLT dispara a pausa, o que garante que o broker cai <b>depois</b> da
 * leitura). O envio falha dentro dos limites curtos do {@code application.yml} (Q-060), o registro
 * <b>não</b> é confirmado e o contador da DLT não sobe. Com o broker de volta, o registro é
 * entregue de novo e termina na DLT exatamente uma vez, o contador sobe 1 e o <i>offset</i> é
 * confirmado.
 */
@Tag("RNF-007")
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(
    properties =
        "spring.kafka.producer.properties[interceptor.classes]="
            + "br.com.fluxocaixa.consolidado.adapter.in.kafka.DltForaIT$PausaAoEnviarParaADlt")
class DltForaIT extends IntegracaoKafka {

  private static final Duration LIMITE = Duration.ofSeconds(120);

  /**
   * O envio à DLT falha pelos limites curtos (Q-060); com os padrões do Kafka levaria 60 s ou mais.
   */
  private static final Duration LIMITE_DO_ENVIO = Duration.ofSeconds(30);

  private static final String DIA = "2026-10-22";

  @Autowired private MeterRegistry metricas;

  /** Pausa o broker no primeiro envio à DLT depois de armado (instanciado pelo produtor Kafka). */
  public static final class PausaAoEnviarParaADlt implements ProducerInterceptor<String, String> {

    static final AtomicBoolean ARMADO = new AtomicBoolean();

    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> registro) {
      if (KafkaConsolidado.DLT.equals(registro.topic()) && ARMADO.compareAndSet(true, false)) {
        KafkaConsolidado.pausar();
      }
      return registro;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadados, Exception falha) {
      // nada a fazer: o interceptador só pausa o broker
    }

    @Override
    public void close() {
      // sem recursos
    }

    @Override
    public void configure(Map<String, ?> configuracao) {
      // sem configuração
    }
  }

  @Test
  void dltForaNaoConfirmaORegistroEDepoisTerminaNaDltUmaVez(CapturedOutput saida) {
    UUID comerciante = UUID.randomUUID();
    String chave = comerciante.toString();
    UUID evento = UUID.randomUUID();
    double dltAntes = contador("consolidado.events.dlt");
    int processadosAntes = TabelasDoConsolidado.eventosProcessados();
    String foraDoContrato =
        EventosJson.json(EventosJson.credito(evento, comerciante, "5.000", DIA));
    PausaAoEnviarParaADlt.ARMADO.set(true);

    RecordMetadata publicado;
    try {
      publicado = KafkaConsolidado.publicar(chave, foraDoContrato, "req-dlt-fora");
      // O envio à DLT falha com o broker fora e o tratamento de erro registra a falha.
      await()
          .atMost(LIMITE_DO_ENVIO)
          .until(() -> saida.getAll().contains("envio do evento para a DLT falhou"));
      assertThat(contador("consolidado.events.dlt") - dltAntes).as("nada na DLT").isZero();
    } finally {
      PausaAoEnviarParaADlt.ARMADO.set(false);
      KafkaConsolidado.retomar();
    }

    try (LeitorDaDlt dlt = KafkaConsolidado.leitorDaDlt()) {
      await()
          .atMost(LIMITE)
          .during(Duration.ofSeconds(5))
          .until(() -> dlt.ler(chave, Duration.ofMillis(200)).size() == 1);
      List<MensagemDaDlt> naDlt = dlt.ler(chave, Duration.ofMillis(200));
      assertThat(naDlt).hasSize(1);
      assertThat(naDlt.getFirst().valor()).isEqualTo(foraDoContrato);
      assertThat(naDlt.getFirst().cabecalho("x-request-id")).isEqualTo("req-dlt-fora");
    }
    await().atMost(LIMITE).until(() -> contador("consolidado.events.dlt") - dltAntes == 1.0);
    await()
        .atMost(LIMITE)
        .until(
            () ->
                KafkaConsolidado.offsetConfirmado(GRUPO, publicado.partition()).orElse(-1)
                    >= publicado.offset() + 1);
    assertThat(TabelasDoConsolidado.processadoEm(evento)).isEmpty();
    assertThat(TabelasDoConsolidado.eventosProcessados()).isEqualTo(processadosAntes);
  }

  private double contador(String nome) {
    return metricas.get(nome).counter().count();
  }
}
