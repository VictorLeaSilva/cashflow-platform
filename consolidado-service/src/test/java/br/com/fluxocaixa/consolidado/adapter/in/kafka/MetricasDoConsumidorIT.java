package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado.LeitorDaDlt;
import br.com.fluxocaixa.consolidado.support.RelogioFixo;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AC-T022.4 (RNF-003): {@code consolidado.event.lag} = relógio depois do commit − {@code
 * occurredAt}, só para eventos aplicados (03 §8, Q-048).
 */
@Tag("RNF-003")
class MetricasDoConsumidorIT extends IntegracaoKafka {

  private static final Duration ESPERA = Duration.ofSeconds(30);
  private static final String DIA = "2026-10-23";

  @Autowired private MeterRegistry metricas;

  @Test
  void eventoAplicadoRegistraOLagExatoPeloRelogio() {
    UUID evento = UUID.randomUUID();
    UUID comerciante = UUID.randomUUID();
    Map<String, Object> campos = EventosJson.credito(evento, comerciante, "1.00", DIA);
    Instant ocorrencia = RelogioFixo.AGORA.minusMillis(2_500);
    campos.put("occurredAt", ocorrencia.toString());
    long contagemAntes = lag().count();
    double totalAntes = lag().totalTime(TimeUnit.MILLISECONDS);

    KafkaConsolidado.publicar(comerciante.toString(), EventosJson.json(campos), null);

    await().atMost(ESPERA).until(() -> lag().count() == contagemAntes + 1);
    assertThat(lag().totalTime(TimeUnit.MILLISECONDS) - totalAntes).isEqualTo(2_500.0);
  }

  @Test
  void duplicataEDltNaoRegistramLag() {
    UUID comerciante = UUID.randomUUID();
    String chave = comerciante.toString();
    UUID evento = UUID.randomUUID();
    String valido = EventosJson.json(EventosJson.credito(evento, comerciante, "1.00", DIA));
    long contagemAntes = lag().count();
    KafkaConsolidado.publicar(chave, valido, null);
    await().atMost(ESPERA).until(() -> lag().count() == contagemAntes + 1);
    long contagemDepoisDoPrimeiro = lag().count();
    double duplicadosAntes = contador("consolidado.events.duplicates");
    double dltAntes = contador("consolidado.events.dlt");

    try (LeitorDaDlt dlt = KafkaConsolidado.leitorDaDlt()) {
      KafkaConsolidado.publicar(chave, valido, null);
      KafkaConsolidado.publicar(chave, "{ fora do contrato", null);
      await().atMost(ESPERA).until(() -> dlt.ler(chave, Duration.ofMillis(200)), l -> !l.isEmpty());
    }

    await()
        .atMost(ESPERA)
        .until(() -> contador("consolidado.events.duplicates") - duplicadosAntes >= 1.0);
    assertThat(contador("consolidado.events.dlt") - dltAntes).isEqualTo(1.0);
    assertThat(lag().count()).isEqualTo(contagemDepoisDoPrimeiro);
  }

  @Test
  void lagNegativoPorDiferencaDeRelogioContaComoZero() {
    UUID evento = UUID.randomUUID();
    UUID comerciante = UUID.randomUUID();
    Map<String, Object> campos = EventosJson.credito(evento, comerciante, "1.00", DIA);
    campos.put("occurredAt", RelogioFixo.AGORA.plusSeconds(10).toString());
    long contagemAntes = lag().count();
    double totalAntes = lag().totalTime(TimeUnit.MILLISECONDS);

    KafkaConsolidado.publicar(comerciante.toString(), EventosJson.json(campos), null);

    await().atMost(ESPERA).until(() -> lag().count() == contagemAntes + 1);
    assertThat(lag().totalTime(TimeUnit.MILLISECONDS) - totalAntes).isZero();
  }

  private Timer lag() {
    return metricas.get("consolidado.event.lag").timer();
  }

  private double contador(String nome) {
    return metricas.get(nome).counter().count();
  }
}
