package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/**
 * Medidores do consumidor (03 §8, Q-048). As três contagens não se sobrepõem: aplicados, duplicados
 * e publicados na DLT. A exposição Prometheus é da T-032.
 */
public final class MetricasDoConsumidor {

  private final Counter aplicados;
  private final Counter duplicados;
  private final Counter dlt;
  private final Timer lag;

  public MetricasDoConsumidor(MeterRegistry registro) {
    aplicados =
        Counter.builder("consolidado.events.processed")
            .description("Eventos aplicados na projeção")
            .register(registro);
    duplicados =
        Counter.builder("consolidado.events.duplicates")
            .description("Eventos ignorados por eventId já processado")
            .register(registro);
    dlt =
        Counter.builder("consolidado.events.dlt")
            .description("Eventos publicados na DLT")
            .register(registro);
    lag =
        Timer.builder("consolidado.event.lag")
            .description("Commit da projeção menos occurredAt do evento (ponta a ponta)")
            .publishPercentileHistogram()
            .register(registro);
  }

  /** Evento aplicado, com o <i>lag</i> ponta a ponta; negativo conta como zero (Q-048). */
  public void aplicado(Duration atraso) {
    aplicados.increment();
    lag.record(atraso.isNegative() ? Duration.ZERO : atraso);
  }

  public void duplicado() {
    duplicados.increment();
  }

  public void enviadoParaDlt() {
    dlt.increment();
  }
}
