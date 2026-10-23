package br.com.fluxocaixa.lancamentos.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Publicador e limpeza da outbox (03 §7.1, Q-028, Q-029). Os padrões são os valores iniciais de 03
 * §7.1, revisados com a medição da T-033.
 *
 * @param retentionDays {@code lancamentos.outbox.retention-days}: idade mínima, em dias, de uma
 *     linha publicada para ser removida
 * @param cleanupInterval {@code lancamentos.outbox.cleanup-interval}: intervalo entre limpezas
 */
@ConfigurationProperties("lancamentos.outbox")
public record OutboxProperties(
    @DefaultValue("7") int retentionDays,
    @DefaultValue("1h") Duration cleanupInterval,
    @DefaultValue Publisher publisher) {

  /**
   * @param enabled liga o laço do publicador (desligado nos testes sem Kafka)
   * @param interval intervalo entre ciclos sem falha
   * @param maxBackoff teto do intervalo com falhas seguidas
   * @param batchSize linhas travadas por ciclo
   * @param transactionTimeout tempo máximo da transação do lote
   * @param poolSize conexões do pool JDBC dedicado
   */
  public record Publisher(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("500ms") Duration interval,
      @DefaultValue("30s") Duration maxBackoff,
      @DefaultValue("100") int batchSize,
      @DefaultValue("15s") Duration transactionTimeout,
      @DefaultValue("2") int poolSize) {}

  public Duration retencao() {
    return Duration.ofDays(retentionDays);
  }
}
