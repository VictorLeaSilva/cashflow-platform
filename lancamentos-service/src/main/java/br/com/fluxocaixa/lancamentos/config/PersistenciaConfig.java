package br.com.fluxocaixa.lancamentos.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Relógio e transação usados pelos casos de uso (constituição §4; 03 §3.1.1). */
@Configuration(proxyBeanMethods = false)
public class PersistenciaConfig {

  /**
   * {@code Clock.systemUTC()} com resolução de microssegundos, a mesma do {@code TIMESTAMPTZ}: o
   * instante em memória é igual ao que volta do banco (follow-up da T-011).
   */
  @Bean
  Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
  }

  /** Isolamento {@code READ COMMITTED} explícito, como pede 03 §3.1.1. */
  @Bean
  TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    return template;
  }
}
