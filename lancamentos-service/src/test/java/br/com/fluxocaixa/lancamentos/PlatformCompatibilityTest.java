package br.com.fluxocaixa.lancamentos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Prova de compatibilidade (AC-T001.4): Java, Spring Boot e Testcontainers funcionando juntos com
 * Docker. Descartável: removido quando existirem testes de integração reais (T-011/T-016). As tags
 * de imagem são provisórias; a T-004 fixa as definitivas.
 */
@Tag("RNF-008")
class PlatformCompatibilityTest {

  private static final String POSTGRES_IMAGE = "postgres:17-alpine";
  private static final String KAFKA_IMAGE = "apache/kafka:3.9.1";

  @Test
  @SuppressWarnings("rawtypes") // a assinatura genérica varia entre versões do Testcontainers
  void postgresContainerStarts() {
    try (var postgres = new PostgreSQLContainer(POSTGRES_IMAGE)) {
      postgres.start();
      assertThat(postgres.isRunning()).isTrue();
      assertThat(postgres.getFirstMappedPort()).isPositive();
    }
  }

  @Test
  void kafkaContainerStarts() {
    try (var kafka = new KafkaContainer(KAFKA_IMAGE)) {
      kafka.start();
      assertThat(kafka.isRunning()).isTrue();
      assertThat(kafka.getBootstrapServers()).isNotBlank();
    }
  }
}
