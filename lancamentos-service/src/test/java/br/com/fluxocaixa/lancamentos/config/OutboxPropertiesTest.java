package br.com.fluxocaixa.lancamentos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** AC-T016.9 (Q-028): padrões de 03 §7.1 e valores alterados por propriedade. */
@Tag("RNF-007")
class OutboxPropertiesTest {

  @Test
  void padroesSaoOsDe03Secao7ponto1() {
    OutboxProperties padrao = vincular(Map.of());

    assertThat(padrao.retentionDays()).isEqualTo(7);
    assertThat(padrao.retencao()).isEqualTo(Duration.ofDays(7));
    assertThat(padrao.cleanupInterval()).isEqualTo(Duration.ofHours(1));
    assertThat(padrao.publisher().enabled()).isTrue();
    assertThat(padrao.publisher().interval()).isEqualTo(Duration.ofMillis(500));
    assertThat(padrao.publisher().maxBackoff()).isEqualTo(Duration.ofSeconds(30));
    assertThat(padrao.publisher().batchSize()).isEqualTo(100);
    assertThat(padrao.publisher().transactionTimeout()).isEqualTo(Duration.ofSeconds(15));
    assertThat(padrao.publisher().poolSize()).isEqualTo(2);
  }

  @Test
  void valoresAlteradosChegamAoPublicador() {
    OutboxProperties alterado =
        vincular(
            Map.of(
                "lancamentos.outbox.retention-days", "3",
                "lancamentos.outbox.cleanup-interval", "10m",
                "lancamentos.outbox.publisher.enabled", "false",
                "lancamentos.outbox.publisher.interval", "100ms",
                "lancamentos.outbox.publisher.max-backoff", "2s",
                "lancamentos.outbox.publisher.batch-size", "10",
                "lancamentos.outbox.publisher.transaction-timeout", "5s",
                "lancamentos.outbox.publisher.pool-size", "1"));

    assertThat(alterado.retencao()).isEqualTo(Duration.ofDays(3));
    assertThat(alterado.cleanupInterval()).isEqualTo(Duration.ofMinutes(10));
    assertThat(alterado.publisher())
        .isEqualTo(
            new OutboxProperties.Publisher(
                false,
                Duration.ofMillis(100),
                Duration.ofSeconds(2),
                10,
                Duration.ofSeconds(5),
                1));
  }

  private static OutboxProperties vincular(Map<String, String> valores) {
    return new Binder(new MapConfigurationPropertySource(valores))
        .bindOrCreate("lancamentos.outbox", OutboxProperties.class);
  }
}
