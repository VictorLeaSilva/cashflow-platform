package br.com.fluxocaixa.lancamentos.config;

import br.com.fluxocaixa.lancamentos.application.PublicarOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicadorDeEventos;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publicador da outbox (D-09, 03 §7.1, T-016): pools JDBC separados para a API e para o publicador
 * (o publicador nunca usa conexões da API), transação do lote com timeout, o laço e as métricas da
 * outbox (03 §8, Q-027). Os beans do publicador não são candidatos padrão: só são injetados com o
 * qualificador {@value #PUBLICADOR}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxProperties.class)
public class PublicadorConfig {

  public static final String PUBLICADOR = "publicador";

  /** Timeout curto para obter conexão: com o banco degradado, o ciclo falha cedo e recua. */
  static final Duration TIMEOUT_DE_CONEXAO = Duration.ofSeconds(3);

  /** Pool da API, configurado por {@code spring.datasource.hikari} (máx. 10, 03 §7.1). */
  @Bean
  @Primary
  @ConfigurationProperties("spring.datasource.hikari")
  HikariDataSource dataSource(DataSourceProperties propriedades) {
    return propriedades.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }

  /** Pool dedicado do publicador: mesmo banco e mesmo role da aplicação (D-15), máx. 2. */
  @Bean(defaultCandidate = false)
  @Qualifier(PUBLICADOR)
  HikariDataSource dataSourceDoPublicador(
      DataSourceProperties propriedades, OutboxProperties outbox) {
    HikariDataSource dataSource =
        propriedades.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    dataSource.setPoolName(PUBLICADOR);
    dataSource.setMaximumPoolSize(outbox.publisher().poolSize());
    dataSource.setConnectionTimeout(TIMEOUT_DE_CONEXAO.toMillis());
    return dataSource;
  }

  @Bean(defaultCandidate = false)
  @Qualifier(PUBLICADOR)
  TransactionTemplate transacaoDoPublicador(
      @Qualifier(PUBLICADOR) DataSource dataSource, OutboxProperties outbox) {
    TransactionTemplate transacao =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    transacao.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transacao.setTimeout(Math.toIntExact(outbox.publisher().transactionTimeout().toSeconds()));
    return transacao;
  }

  @Bean
  PublicarOutbox publicarOutbox(
      OutboxRepository outbox,
      PublicadorDeEventos publicador,
      @Qualifier(PUBLICADOR) TransactionTemplate transacao,
      Clock clock,
      OutboxProperties propriedades) {
    return new PublicarOutbox(
        outbox,
        publicador,
        transacao,
        clock,
        propriedades.publisher().batchSize(),
        propriedades.retencao());
  }

  @Bean
  LacoDoPublicador lacoDoPublicador(
      PublicarOutbox publicarOutbox, OutboxProperties propriedades, Clock clock) {
    return new LacoDoPublicador(publicarOutbox, propriedades, clock);
  }

  /**
   * {@code outbox.pending.events} e {@code outbox.oldest.pending.age} (segundos, pelo Clock), lidos
   * pelo pool do publicador a cada coleta; no Prometheus (T-032) viram {@code
   * outbox_pending_events} e {@code outbox_oldest_pending_age_seconds}.
   */
  @Bean
  MeterBinder metricasDaOutbox(OutboxRepository outbox, Clock clock) {
    return registro -> {
      Gauge.builder("outbox.pending.events", outbox, OutboxRepository::contarPendentes)
          .description("Eventos da outbox ainda não publicados")
          .register(registro);
      Gauge.builder("outbox.oldest.pending.age", outbox, o -> idadeDoMaisAntigo(o, clock))
          .description("Idade do evento pendente mais antigo da outbox")
          .baseUnit("seconds")
          .register(registro);
    };
  }

  private static double idadeDoMaisAntigo(OutboxRepository outbox, Clock clock) {
    return outbox
        .maisAntigoPendente()
        .map(criado -> Math.max(0, Duration.between(criado, clock.instant()).toMillis()) / 1000.0)
        .orElse(0.0);
  }
}
