package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.application.PublicarOutbox;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoKafka;
import br.com.fluxocaixa.lancamentos.support.KafkaLancamentos;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * AC-T016.8 (03 §8, Q-027): os medidores da outbox refletem o banco pelo Clock e registram falhas e
 * duração dos lotes. Nomes Micrometer com ponto; no Prometheus (T-032) viram {@code
 * outbox_pending_events}, {@code outbox_oldest_pending_age_seconds}, {@code
 * outbox_publish_failures_total} e {@code outbox_publish_duration_seconds}.
 */
@Tag("RNF-007")
@TestPropertySource(
    properties = {
      "spring.kafka.producer.properties[max.block.ms]=1000",
      "spring.kafka.producer.properties[request.timeout.ms]=1000",
      "spring.kafka.producer.properties[delivery.timeout.ms]=1500"
    })
class MetricasOutboxIT extends IntegracaoKafka {

  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private PublicarOutbox publicarOutbox;
  @Autowired private MeterRegistry metricas;
  @Autowired private RelogioFixo.Controlado relogio;

  @Test
  void pendentesEIdadeDoMaisAntigoVemDoBancoPeloClock() {
    assertThat(gauge("outbox.pending.events")).isZero();
    assertThat(gauge("outbox.oldest.pending.age")).isZero();

    Eventos.registrar(registrarLancamento, jdbc, UUID.randomUUID(), null);
    Eventos.registrar(registrarLancamento, jdbc, UUID.randomUUID(), null);
    relogio.ajustar(RelogioFixo.AGORA.plusSeconds(42));

    assertThat(gauge("outbox.pending.events")).isEqualTo(2.0);
    assertThat(gauge("outbox.oldest.pending.age")).isEqualTo(42.0);
    assertThat(metricas.get("outbox.oldest.pending.age").gauge().getId().getBaseUnit())
        .isEqualTo("seconds");
  }

  @Test
  void loteRegistraDuracaoEFalhaIncrementaOContador() {
    double falhasAntes = falhas();
    long lotesAntes = metricas.get("outbox.publish.duration").timer().count();
    Eventos.registrar(registrarLancamento, jdbc, UUID.randomUUID(), null);

    publicarOutbox.publicarLote();

    assertThat(metricas.get("outbox.publish.duration").timer().count()).isEqualTo(lotesAntes + 1);
    assertThat(metricas.get("outbox.publish.duration").timer().totalTime(TimeUnit.NANOSECONDS))
        .isPositive();
    assertThat(falhas()).isEqualTo(falhasAntes);

    Eventos.registrar(registrarLancamento, jdbc, UUID.randomUUID(), null);
    KafkaLancamentos.pausar();
    try {
      PublicarOutbox.ResultadoDoLote resultado = publicarOutbox.publicarLote();
      assertThat(resultado.falhas()).isEqualTo(1);
    } finally {
      KafkaLancamentos.retomar();
    }

    assertThat(falhas()).isEqualTo(falhasAntes + 1);
    assertThat(gauge("outbox.pending.events")).isEqualTo(1.0);
  }

  private double gauge(String nome) {
    return metricas.get(nome).gauge().value();
  }

  private double falhas() {
    return metricas.get("outbox.publish.failures").counter().count();
  }
}
