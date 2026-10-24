package br.com.fluxocaixa.lancamentos.adapter.out.observabilidade;

import br.com.fluxocaixa.lancamentos.application.port.out.ContextoDeTrace;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

/** {@link ContextoDeTrace} com o Micrometer Tracing (ponte OpenTelemetry, Q-061). */
@Component
public final class TraceMicrometer implements ContextoDeTrace {

  private final Tracer tracer;

  public TraceMicrometer(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public String traceIdAtual() {
    Span atual = tracer.currentSpan();
    return atual == null ? null : atual.context().traceId();
  }
}
