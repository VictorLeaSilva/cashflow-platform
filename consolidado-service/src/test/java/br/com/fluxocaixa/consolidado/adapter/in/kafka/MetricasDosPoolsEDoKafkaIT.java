package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AC-T032.1 (03 §8): a ocupação dos pools JDBC do Consolidado ({@code hikaricp_connections_active}
 * com {@code pool="api"} e {@code pool="consumidor"}) e o <i>lag</i> do consumidor Kafka ({@code
 * kafka_consumer_fetch_manager_records_lag_max}) são medidos. As séries nascem com o uso: o pool
 * depois da primeira conexão e o consumidor depois de ligado.
 */
@Tag("RNF-006")
class MetricasDosPoolsEDoKafkaIT extends IntegracaoKafka {

  @Autowired private MeterRegistry metricas;
  @Autowired private DataSource principal;

  @Test
  void poolsEConsumidorKafkaTemMetricas() throws SQLException {
    try (Connection ignorada = principal.getConnection()) {
      assertThat(ignorada.isValid(1)).isTrue();
    }
    UUID comerciante = UUID.randomUUID();
    KafkaConsolidado.publicar(
        comerciante.toString(),
        EventosJson.json(EventosJson.credito(UUID.randomUUID(), comerciante, "1.00", "2026-10-22")),
        null);
    await()
        .atMost(Duration.ofSeconds(40))
        .until(() -> TabelasDoConsolidado.eventosProcessados() > 0);

    for (String pool : new String[] {"api", "consumidor"}) {
      assertThat(metricas.find("hikaricp.connections.active").tag("pool", pool).gauge())
          .as("ocupação do pool %s", pool)
          .isNotNull();
    }
    assertThat(metricas.find("kafka.consumer.fetch.manager.records.lag.max").gauge())
        .as("lag do consumidor Kafka")
        .isNotNull();
  }
}
