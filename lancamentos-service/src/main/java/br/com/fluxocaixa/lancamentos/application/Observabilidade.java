package br.com.fluxocaixa.lancamentos.application;

import br.com.fluxocaixa.lancamentos.application.port.out.ContextoDeTrace;
import br.com.fluxocaixa.lancamentos.application.port.out.MetricasDeLancamentos;

/** As duas portas de observabilidade dos casos de uso de escrita (03 §8, Q-061, Q-063). */
public record Observabilidade(MetricasDeLancamentos metricas, ContextoDeTrace trace) {}
