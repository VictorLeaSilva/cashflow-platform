package br.com.fluxocaixa.consolidado.config;

import br.com.fluxocaixa.consolidado.adapter.out.persistence.ConsultaConsolidadoJdbcRepository;
import br.com.fluxocaixa.consolidado.application.ConsultarConsolidado;
import br.com.fluxocaixa.consolidado.application.port.out.ConsultaConsolidadoRepository;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consulta do consolidado (T-023): a leitura usa o pool {@code api} (o primário, máx. 10), nunca o
 * do consumidor, numa transação somente leitura {@code REPEATABLE READ}, para que o saldo anterior
 * e os dias venham do mesmo <i>snapshot</i> (Q-053).
 */
@Configuration(proxyBeanMethods = false)
public class ConsultaConfig {

  @Bean
  ConsultaConsolidadoRepository consultaConsolidadoRepository(DataSource dataSource) {
    return new ConsultaConsolidadoJdbcRepository(
        JdbcClient.create(dataSource), leituraConsistente(dataSource));
  }

  /** Somente leitura, {@code REPEATABLE READ}: um <i>snapshot</i> por consulta (Q-053). */
  public static TransactionTemplate leituraConsistente(DataSource dataSource) {
    TransactionTemplate leitura =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    leitura.setReadOnly(true);
    leitura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return leitura;
  }

  @Bean
  ConsultarConsolidado consultarConsolidado(ConsultaConsolidadoRepository leitura) {
    return new ConsultarConsolidado(leitura);
  }
}
