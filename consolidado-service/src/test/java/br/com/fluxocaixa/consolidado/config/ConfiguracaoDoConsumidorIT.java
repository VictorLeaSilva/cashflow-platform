package br.com.fluxocaixa.consolidado.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.adapter.in.kafka.ConsumidorDeLancamentos;
import br.com.fluxocaixa.consolidado.application.port.out.ProjecaoRepository;
import br.com.fluxocaixa.consolidado.domain.ChaveDiaria;
import br.com.fluxocaixa.consolidado.domain.Efeito;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.IntegracaoPostgres;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * AC-T022.5, Q-044 e Q-049: com {@code consolidado.consumer.enabled=false} (padrão dos testes) o
 * {@code listener} não roda e nada é aplicado (D-10); os pools têm os limites de {@code 03 §7.1}, e
 * a porta de projeção usa o pool do consumidor.
 */
@Tag("RF-008")
class ConfiguracaoDoConsumidorIT extends IntegracaoPostgres {

  @Autowired private KafkaListenerEndpointRegistry listeners;
  @Autowired private ProjecaoRepository projecao;
  @Autowired private HikariDataSource dataSource;
  @Autowired private ConsumerFactory<?, ?> consumidores;

  @Autowired
  @Qualifier(ConsumidorConfig.CONSUMIDOR)
  private HikariDataSource dataSourceDoConsumidor;

  @DynamicPropertySource
  static void kafka(DynamicPropertyRegistry registro) {
    KafkaConsolidado.registrarPropriedades(registro);
  }

  @Test
  void desligadoOListenerNaoRodaENadaEAplicado() {
    MessageListenerContainer container =
        listeners.getListenerContainer(ConsumidorDeLancamentos.ID_DO_LISTENER);
    assertThat(container).isNotNull();
    assertThat(container.isRunning()).isFalse();

    UUID comerciante = UUID.randomUUID();
    KafkaConsolidado.publicar(
        comerciante.toString(),
        EventosJson.json(EventosJson.credito(UUID.randomUUID(), comerciante, "1.00", "2026-10-22")),
        null);

    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(5))
        .until(() -> TabelasDoConsolidado.dias().isEmpty());
  }

  @Test
  void poolsTemOsLimitesEAProjecaoUsaOPoolDoConsumidor() {
    assertThat(dataSource.getPoolName()).isEqualTo("api");
    assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
    assertThat(dataSourceDoConsumidor.getPoolName()).isEqualTo("consumidor");
    assertThat(dataSourceDoConsumidor.getMaximumPoolSize()).isEqualTo(4);
    int conexoesAntes = conexoes(dataSourceDoConsumidor);

    projecao.aplicarSeNovo(
        UUID.randomUUID(),
        new Efeito(
            new ChaveDiaria(UUID.randomUUID(), LocalDate.parse("2026-10-22")),
            new MovimentoDiario(
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1)),
        Instant.parse("2026-10-22T13:30:00Z"));

    assertThat(conexoesAntes).isZero();
    assertThat(conexoes(dataSourceDoConsumidor)).isPositive();
  }

  /**
   * Q-049: sem o {@code Detail} do servidor na mensagem do driver, nos dois pools. Uma violação de
   * {@code CHECK} traria a linha inteira ({@code Failing row contains (...)}), com o comerciante.
   */
  @Test
  void erroDoDriverNaoTrazALinhaNosDoisPools() throws SQLException {
    for (HikariDataSource pool : List.of(dataSource, dataSourceDoConsumidor)) {
      assertThat(pool.getDataSourceProperties().getProperty("logServerErrorDetail"))
          .as(pool.getPoolName())
          .isEqualTo("false");
      UUID comerciante = UUID.randomUUID();
      try (Connection conexao = pool.getConnection();
          PreparedStatement comando =
              conexao.prepareStatement(
                  "INSERT INTO consolidado_diario (comerciante_id, data_referencia,"
                      + " total_creditos, atualizado_em) VALUES (?, DATE '2026-10-22', -1.00,"
                      + " now())")) {
        comando.setObject(1, comerciante);
        assertThatThrownBy(comando::executeUpdate)
            .as(pool.getPoolName())
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_totais_nao_negativos")
            .message()
            .doesNotContain("Failing row", comerciante.toString(), "-1.00");
      }
    }
  }

  /** Q-044: o offset de cada registro sai depois que o listener retorna, sem auto-commit. */
  @Test
  void confirmacaoPorRegistroSemAutoCommit() {
    MessageListenerContainer container =
        listeners.getListenerContainer(ConsumidorDeLancamentos.ID_DO_LISTENER);

    assertThat(container.getContainerProperties().getAckMode())
        .isEqualTo(ContainerProperties.AckMode.RECORD);
    assertThat(consumidores.getConfigurationProperties())
        .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
        .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        .containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
  }

  /** Conexões abertas no pool; zero enquanto o pool não foi usado (o Hikari inicia sob demanda). */
  private static int conexoes(HikariDataSource pool) {
    return pool.getHikariPoolMXBean() == null
        ? 0
        : pool.getHikariPoolMXBean().getTotalConnections();
  }
}
