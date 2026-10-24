package br.com.fluxocaixa.lancamentos.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AC-T016.9 (03 §7.1, D-09): o publicador tem pool próprio (máx. 2, timeout de conexão curto) e
 * transação com timeout de 15 s; a API mantém o pool principal (máx. 10). Os dois apontam para o
 * mesmo banco, com o mesmo role da aplicação.
 */
@Tag("RNF-007")
class PoolsJdbcIT extends IntegracaoPostgres {

  @Autowired private DataSource principal;
  @Autowired private JdbcClient jdbc;

  @Autowired
  @Qualifier("publicador")
  private DataSource doPublicador;

  @Autowired
  @Qualifier("publicador")
  private TransactionTemplate transacaoDoPublicador;

  @Test
  void poolsSaoSeparadosComOsLimitesDe03() {
    HikariDataSource api = (HikariDataSource) principal;
    HikariDataSource publicador = (HikariDataSource) doPublicador;

    assertThat(api).isNotSameAs(publicador);
    assertThat(api.getPoolName()).isEqualTo("api");
    assertThat(api.getMaximumPoolSize()).isEqualTo(10);
    assertThat(publicador.getPoolName()).isEqualTo("publicador");
    assertThat(publicador.getMaximumPoolSize()).isEqualTo(2);
    assertThat(Duration.ofMillis(publicador.getConnectionTimeout()))
        .isEqualTo(Duration.ofSeconds(3));
    assertThat(publicador.getJdbcUrl()).isEqualTo(api.getJdbcUrl());
    assertThat(publicador.getUsername()).isEqualTo("lancamentos_app").isEqualTo(api.getUsername());
    assertThat(transacaoDoPublicador.getTimeout()).isEqualTo(15);
  }

  /** A transação do publicador ocupa uma conexão do pool dele e nenhuma do pool da API. */
  @Test
  void transacaoDoPublicadorUsaSoOProprioPool() {
    HikariDataSource api = (HikariDataSource) principal;
    HikariDataSource publicador = (HikariDataSource) doPublicador;
    // O pool da API inicia na primeira conexão; o MXBean só existe depois disso.
    assertThat(jdbc.sql("SELECT current_user").query(String.class).single())
        .isEqualTo("lancamentos_app");

    int[] ativasDentro =
        transacaoDoPublicador.execute(
            s -> {
              JdbcClient.create(doPublicador).sql("SELECT 1").query(Integer.class).single();
              return new int[] {
                publicador.getHikariPoolMXBean().getActiveConnections(),
                api.getHikariPoolMXBean().getActiveConnections()
              };
            });

    assertThat(ativasDentro).containsExactly(1, 0);
  }
}
