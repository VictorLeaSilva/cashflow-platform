package br.com.fluxocaixa.consolidado.config;

import br.com.fluxocaixa.consolidado.adapter.in.kafka.ConsumidorDeLancamentos;
import br.com.fluxocaixa.consolidado.adapter.in.kafka.EventoForaDoContratoException;
import br.com.fluxocaixa.consolidado.adapter.in.kafka.LeitorDeEvento;
import br.com.fluxocaixa.consolidado.adapter.in.kafka.MetricasDoConsumidor;
import br.com.fluxocaixa.consolidado.adapter.in.kafka.TratamentoDeErroComCorrelacao;
import br.com.fluxocaixa.consolidado.adapter.out.persistence.ProjecaoJdbcRepository;
import br.com.fluxocaixa.consolidado.application.AplicarEvento;
import br.com.fluxocaixa.consolidado.application.port.out.ProjecaoRepository;
import br.com.fluxocaixa.consolidado.domain.EventoInvalidoException;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import javax.sql.DataSource;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.LoggingProducerListener;
import org.springframework.kafka.support.ProducerListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor do Consolidado (T-022): pools JDBC separados para a API e para o consumidor (03 §7.1;
 * o consumidor nunca usa conexões da API), o caso de uso, o <i>listener</i> e o tratamento de erro
 * com <i>retry</i> e DLT (Q-045). Os beans do consumidor que não são candidatos padrão só são
 * injetados com o qualificador {@value #CONSUMIDOR}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConsumidorProperties.class)
public class ConsumidorConfig {

  public static final String CONSUMIDOR = "consumidor";

  /** 1 tentativa + 3 novas, com intervalos de 1, 2 e 4 s (03 §7, ADR-005, Q-045). */
  static final int NOVAS_TENTATIVAS = 3;

  static final long PRIMEIRO_INTERVALO_MS = 1_000;
  static final double MULTIPLICADOR = 2.0;

  /** Pool da API, configurado por {@code spring.datasource.hikari} (máx. 10, 03 §7.1). */
  @Bean
  @Primary
  @ConfigurationProperties("spring.datasource.hikari")
  HikariDataSource dataSource(DataSourceProperties propriedades) {
    return semDetalheDoServidor(
        propriedades.initializeDataSourceBuilder().type(HikariDataSource.class).build());
  }

  /** Pool dedicado do consumidor: mesmo banco e mesmo role da aplicação (D-15), máx. 4. */
  @Bean(defaultCandidate = false)
  @Qualifier(CONSUMIDOR)
  HikariDataSource dataSourceDoConsumidor(
      DataSourceProperties propriedades, ConsumidorProperties consumidor) {
    HikariDataSource dataSource =
        propriedades.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    dataSource.setPoolName(CONSUMIDOR);
    dataSource.setMaximumPoolSize(consumidor.poolSize());
    return semDetalheDoServidor(dataSource);
  }

  /** A projeção é escrita só pelo consumidor, com o pool dele (follow-up da T-021). */
  @Bean
  ProjecaoRepository projecaoRepository(@Qualifier(CONSUMIDOR) DataSource dataSource) {
    return new ProjecaoJdbcRepository(JdbcClient.create(dataSource));
  }

  @Bean
  AplicarEvento aplicarEvento(ProjecaoRepository projecao, Clock clock) {
    return new AplicarEvento(projecao, clock);
  }

  @Bean
  LeitorDeEvento leitorDeEvento(JsonMapper json) {
    return new LeitorDeEvento(json);
  }

  @Bean
  MetricasDoConsumidor metricasDoConsumidor(MeterRegistry registro) {
    return new MetricasDoConsumidor(registro);
  }

  @Bean
  ConsumidorDeLancamentos consumidorDeLancamentos(
      LeitorDeEvento leitor,
      AplicarEvento aplicarEvento,
      MetricasDoConsumidor metricas,
      Clock clock) {
    return new ConsumidorDeLancamentos(leitor, aplicarEvento, metricas, clock);
  }

  /**
   * <i>Retry</i> e DLT (03 §7, §7.2, Q-045). O que está fora do contrato ou do domínio vai direto
   * para a DLT; o resto tenta de novo com <i>backoff</i>. A DLT recebe a mesma partição, chave,
   * valor e cabeçalhos, mais os cabeçalhos de origem e de exceção do spring-kafka. Se a publicação
   * na DLT falhar, o registro não é confirmado e volta a ser tentado; o contador só sobe depois da
   * confirmação.
   */
  @Bean
  DefaultErrorHandler tratamentoDeErroDoConsumidor(
      KafkaOperations<?, ?> kafka, MetricasDoConsumidor metricas) {
    DeadLetterPublishingRecoverer dlt =
        new DeadLetterPublishingRecoverer(
            kafka,
            (registro, erro) ->
                new TopicPartition(ConsumidorDeLancamentos.DLT, registro.partition()));
    ConsumerRecordRecoverer publicarEContar =
        (registro, erro) -> {
          dlt.accept(registro, erro);
          metricas.enviadoParaDlt();
        };
    ExponentialBackOffWithMaxRetries backoff =
        new ExponentialBackOffWithMaxRetries(NOVAS_TENTATIVAS);
    backoff.setInitialInterval(PRIMEIRO_INTERVALO_MS);
    backoff.setMultiplier(MULTIPLICADOR);
    DefaultErrorHandler tratamento = new TratamentoDeErroComCorrelacao(publicarEContar, backoff);
    tratamento.addNotRetryableExceptions(
        EventoForaDoContratoException.class, EventoInvalidoException.class);
    return tratamento;
  }

  /**
   * Substitui o {@code LoggingProducerListener} padrão do Boot, que grava chave (comerciante) e
   * payload no log de erro do produtor da DLT (lição da T-016; AC-T022.6).
   */
  @Bean
  ProducerListener<Object, Object> kafkaProducerListener() {
    LoggingProducerListener<Object, Object> semConteudo = new LoggingProducerListener<>();
    semConteudo.setIncludeContents(false);
    return semConteudo;
  }

  /**
   * Sem o {@code Detail} do servidor na mensagem da exceção do driver: numa violação de {@code
   * CHECK}, ele traz a linha inteira (comerciante e valores), que o <i>error handler</i>
   * registraria no log (Q-049).
   */
  private static HikariDataSource semDetalheDoServidor(HikariDataSource dataSource) {
    dataSource.addDataSourceProperty("logServerErrorDetail", "false");
    return dataSource;
  }
}
