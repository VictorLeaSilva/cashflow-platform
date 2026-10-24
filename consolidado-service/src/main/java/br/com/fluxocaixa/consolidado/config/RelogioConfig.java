package br.com.fluxocaixa.consolidado.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio da aplicação (constituição §4). */
@Configuration(proxyBeanMethods = false)
public class RelogioConfig {

  /**
   * {@code Clock.systemUTC()} com resolução de microssegundos, a mesma do {@code TIMESTAMPTZ}: o
   * instante em memória é igual ao que volta do banco (como no Lançamentos).
   */
  @Bean
  Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
  }
}
