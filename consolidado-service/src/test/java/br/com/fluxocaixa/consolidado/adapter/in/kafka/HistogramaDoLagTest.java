package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-T032.1 e RNF-003 (03 §8, Q-062): {@code consolidado_event_lag_seconds} sai no Prometheus como
 * histograma, com <i>buckets</i> de 50 ms a 60 s, para o p95 sair no PromQL ({@code
 * histogram_quantile}). Duplicatas e eventos da DLT não entram (têm métricas próprias).
 */
@Tag("RNF-003")
@Tag("RNF-006")
class HistogramaDoLagTest {

  private static final Pattern BUCKET =
      Pattern.compile("^consolidado_event_lag_seconds_bucket\\{le=\"([^\"]+)\"\\} (\\S+)$");

  @Test
  void lagEhHistogramaComBucketsDe50MsA60s() {
    PrometheusMeterRegistry registro = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    MetricasDoConsumidor metricas = new MetricasDoConsumidor(registro);

    metricas.aplicado(Duration.ofMillis(120));
    metricas.aplicado(Duration.ofSeconds(3));
    metricas.aplicado(Duration.ofSeconds(90));

    List<Double> limites = limitesFinitos(registro.scrape());
    assertThat(limites).isNotEmpty().isSorted();
    assertThat(limites.getFirst()).isLessThanOrEqualTo(0.06);
    assertThat(limites.getLast()).isGreaterThanOrEqualTo(60.0);
    assertThat(registro.scrape())
        .contains("consolidado_event_lag_seconds_count 3")
        .contains("le=\"+Inf\"} 3");
  }

  @Test
  void duplicataEDltNaoEntramNoHistograma() {
    PrometheusMeterRegistry registro = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    MetricasDoConsumidor metricas = new MetricasDoConsumidor(registro);

    metricas.duplicado();
    metricas.enviadoParaDlt();

    assertThat(registro.scrape()).contains("consolidado_event_lag_seconds_count 0");
  }

  private static List<Double> limitesFinitos(String texto) {
    return texto
        .lines()
        .map(BUCKET::matcher)
        .filter(Matcher::matches)
        .map(m -> m.group(1))
        .filter(le -> !le.equals("+Inf"))
        .map(Double::parseDouble)
        .toList();
  }
}
