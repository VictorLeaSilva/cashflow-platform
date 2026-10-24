package br.com.fluxocaixa.lancamentos.adapter.out.observabilidade;

import br.com.fluxocaixa.lancamentos.application.port.out.MetricasDeLancamentos;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link MetricasDeLancamentos} com Micrometer (03 §8, Q-063): {@code
 * lancamentos.registrados{tipo}}, {@code idempotency.replays} e {@code idempotency.conflicts}. Os
 * medidores nascem no início, para a série existir (com zero) desde a primeira raspagem do
 * Prometheus.
 */
@Component
public final class MetricasMicrometer implements MetricasDeLancamentos {

  private final Map<TipoLancamento, Counter> registrados = new EnumMap<>(TipoLancamento.class);
  private final Counter replays;
  private final Counter conflitos;

  public MetricasMicrometer(MeterRegistry registro) {
    for (TipoLancamento tipo : TipoLancamento.values()) {
      registrados.put(
          tipo,
          Counter.builder("lancamentos.registrados")
              .description("Lançamentos novos confirmados, por tipo")
              .tag("tipo", tipo.name())
              .register(registro));
    }
    replays =
        Counter.builder("idempotency.replays")
            .description("Respostas reconstruídas de uma Idempotency-Key já usada")
            .register(registro);
    conflitos =
        Counter.builder("idempotency.conflicts")
            .description("Idempotency-Key reutilizada com outro comando")
            .register(registro);
  }

  @Override
  public void registrado(TipoLancamento tipo) {
    registrados.get(tipo).increment();
  }

  @Override
  public void replay() {
    replays.increment();
  }

  @Override
  public void conflito() {
    conflitos.increment();
  }
}
