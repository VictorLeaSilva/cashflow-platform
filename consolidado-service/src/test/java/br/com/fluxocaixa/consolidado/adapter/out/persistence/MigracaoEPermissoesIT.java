package br.com.fluxocaixa.consolidado.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.consolidado.support.IntegracaoPostgres;
import br.com.fluxocaixa.consolidado.support.PostgresConsolidado;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-T021.1 (migração, roles e grants mínimos) e AC-T021.2 (CHECK de não negatividade, Q-039). */
@Tag("RNF-004")
class MigracaoEPermissoesIT extends IntegracaoPostgres {

  private static final String SEM_PERMISSAO = "42501";
  private static final String CHECK_VIOLADO = "23514";
  // Montado em partes para que este próprio arquivo não seja encontrado pela busca.
  private static final String CRIACAO_DE_ROLE = "CREATE" + " ROLE";
  private static final String LINHA =
      "INSERT INTO consolidado_diario (comerciante_id, data_referencia, total_creditos,"
          + " total_debitos, total_estornos_credito, total_estornos_debito, qtd_lancamentos,"
          + " atualizado_em) VALUES ('00000000-0000-0000-0000-0000000000aa', DATE '2026-10-22',"
          + " %s, %s, %s, %s, %s, now())";

  @Test
  void v1AplicadaComoOwnerQueEDonoDasTabelas() throws SQLException {
    try (Connection owner = PostgresConsolidado.conectarComoOwner();
        Statement consulta = owner.createStatement()) {
      assertThat(
              valores(
                  consulta,
                  "SELECT version || ':' || installed_by || ':' || success"
                      + " FROM flyway_schema_history WHERE version IS NOT NULL"))
          .containsExactly("1:consolidado_owner:true");
      assertThat(
              valores(
                  consulta,
                  "SELECT tablename || ':' || tableowner FROM pg_tables"
                      + " WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'"
                      + " ORDER BY tablename"))
          .containsExactly(
              "consolidado_diario:consolidado_owner", "evento_processado:consolidado_owner");
    }
  }

  @Test
  void appNaoExecutaDdl() {
    assertSemPermissao("CREATE TABLE intrusa (id INTEGER)");
    assertSemPermissao("ALTER TABLE consolidado_diario ADD COLUMN intrusa INTEGER");
    assertSemPermissao("DROP TABLE evento_processado");
  }

  @Test
  void appNaoApagaAProjecao() {
    assertSemPermissao("DELETE FROM consolidado_diario");
    assertSemPermissao("TRUNCATE consolidado_diario");
  }

  @Test
  void appNaoAlteraNemApagaEventoProcessado() {
    assertSemPermissao("UPDATE evento_processado SET processado_em = now()");
    assertSemPermissao("DELETE FROM evento_processado");
    assertSemPermissao("TRUNCATE evento_processado");
  }

  @Test
  void appLeInsereEAtualizaAProjecaoEInsereEventoProcessado() throws SQLException {
    try (Connection app = PostgresConsolidado.conectarComoApp();
        Statement comando = app.createStatement()) {
      comando.executeUpdate(LINHA.formatted("1.00", "0", "0", "0", "1"));
      comando.executeUpdate("UPDATE consolidado_diario SET total_creditos = 2.00");
      comando.executeUpdate(
          "INSERT INTO evento_processado (event_id, processado_em)"
              + " VALUES ('00000000-0000-0000-0000-000000000001', now())");

      assertThat(valores(comando, "SELECT total_creditos::text FROM consolidado_diario"))
          .containsExactly("2.00");
      assertThat(valores(comando, "SELECT count(*)::text FROM evento_processado"))
          .containsExactly("1");
    }
  }

  @Test
  void checkRejeitaTotalOuQuantidadeNegativos() {
    assertCheckViolado(LINHA.formatted("-0.01", "0", "0", "0", "0"));
    assertCheckViolado(LINHA.formatted("0", "-0.01", "0", "0", "0"));
    assertCheckViolado(LINHA.formatted("0", "0", "-0.01", "0", "0"));
    assertCheckViolado(LINHA.formatted("0", "0", "0", "-0.01", "0"));
    assertCheckViolado(LINHA.formatted("0", "0", "0", "0", "-1"));
  }

  @Test
  void rolesVemDoDeployInitSemSqlDeRolesNosTestes() throws IOException {
    assertThat(PostgresConsolidado.INIT_SQL.resolve("01-bancos-e-roles.sql")).exists();
    try (Stream<Path> arquivos = Files.walk(Path.of("src", "test"))) {
      List<Path> comCreateRole =
          arquivos
              .filter(Files::isRegularFile)
              .filter(arquivo -> conteudo(arquivo).toUpperCase().contains(CRIACAO_DE_ROLE))
              .toList();
      assertThat(comCreateRole).isEmpty();
    }
  }

  private static void assertSemPermissao(String sql) {
    assertThatThrownBy(
            () -> {
              try (Connection app = PostgresConsolidado.conectarComoApp();
                  Statement comando = app.createStatement()) {
                comando.execute(sql);
              }
            })
        .isInstanceOf(SQLException.class)
        .extracting(e -> ((SQLException) e).getSQLState())
        .isEqualTo(SEM_PERMISSAO);
  }

  private static void assertCheckViolado(String sql) {
    assertThatThrownBy(
            () -> {
              try (Connection owner = PostgresConsolidado.conectarComoOwner();
                  Statement comando = owner.createStatement()) {
                comando.execute(sql);
              }
            })
        .isInstanceOf(SQLException.class)
        .extracting(e -> ((SQLException) e).getSQLState())
        .isEqualTo(CHECK_VIOLADO);
  }

  private static List<String> valores(Statement consulta, String sql) throws SQLException {
    try (ResultSet linhas = consulta.executeQuery(sql)) {
      List<String> valores = new ArrayList<>();
      while (linhas.next()) {
        valores.add(linhas.getString(1));
      }
      return valores;
    }
  }

  private static String conteudo(Path arquivo) {
    try {
      return Files.readString(arquivo);
    } catch (IOException e) {
      return "";
    }
  }
}
