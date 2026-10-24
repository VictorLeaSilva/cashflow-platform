package br.com.fluxocaixa.lancamentos.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * AC-T032.1 (03 §8): a ocupação de cada pool JDBC do Lançamentos é medida, com o nome do pool como
 * <i>tag</i> ({@code hikaricp_connections_active{pool="api"}} e {@code pool="publicador"} no
 * Prometheus). O Hikari só publica a série depois da primeira conexão do pool.
 */
@Tag("RNF-006")
class PoolsMetricasIT extends IntegracaoPostgres {

  @Autowired private MeterRegistry metricas;
  @Autowired private DataSource principal;

  @Autowired
  @Qualifier("publicador")
  private DataSource doPublicador;

  @Test
  void cadaPoolTemASuaOcupacaoMedida() throws SQLException {
    abrirEFechar(principal);
    abrirEFechar(doPublicador);

    for (String pool : new String[] {"api", "publicador"}) {
      assertThat(metricas.find("hikaricp.connections.active").tag("pool", pool).gauge())
          .as("ocupação do pool %s", pool)
          .isNotNull();
      assertThat(metricas.find("hikaricp.connections.max").tag("pool", pool).gauge())
          .as("limite do pool %s", pool)
          .isNotNull();
    }
  }

  private static void abrirEFechar(DataSource dataSource) throws SQLException {
    try (Connection ignorada = dataSource.getConnection()) {
      assertThat(ignorada.isValid(1)).isTrue();
    }
  }
}
