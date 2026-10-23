package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.PERMISSAO_NEGADA;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.contar;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.executar;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.falhaCom;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.inserir;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.Linha;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.PostgresLancamentos;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-T011.1, AC-T011.2 e AC-T011.9: migração como owner, app sem DDL e sem UPDATE/DELETE. */
@Tag("RNF-004")
class MigracaoEPermissoesIT extends IntegracaoPostgres {

  private static final String CREATE_ROLE = "CREATE ROLE";

  @Test
  void v1AplicadaComSucessoPeloOwner() throws SQLException {
    try (Connection owner = PostgresLancamentos.conectarComoOwner();
        var ps =
            owner.prepareStatement(
                "SELECT installed_by, success FROM flyway_schema_history WHERE version = '1'");
        var rs = ps.executeQuery()) {
      assertThat(rs.next()).as("V1 registrada no histórico do Flyway").isTrue();
      assertThat(rs.getString("installed_by")).isEqualTo(PostgresLancamentos.OWNER);
      assertThat(rs.getBoolean("success")).isTrue();
    }
  }

  @Test
  void tabelasPertencemAoOwner() throws SQLException {
    List<String> donos = new ArrayList<>();
    try (Connection owner = PostgresLancamentos.conectarComoOwner();
        var ps =
            owner.prepareStatement(
                "SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'public'"
                    + " AND tablename IN ('lancamento', 'outbox_event', 'idempotency_key')");
        var rs = ps.executeQuery()) {
      while (rs.next()) {
        donos.add(rs.getString("tablename") + ":" + rs.getString("tableowner"));
      }
    }
    assertThat(donos)
        .containsExactlyInAnyOrder(
            "lancamento:lancamentos_owner",
            "outbox_event:lancamentos_owner",
            "idempotency_key:lancamentos_owner");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CREATE TABLE intrusa (id INTEGER)",
        "ALTER TABLE lancamento ADD COLUMN intrusa INTEGER",
        "DROP TABLE outbox_event",
        "TRUNCATE lancamento",
        "CREATE INDEX ix_intruso ON lancamento (descricao)"
      })
  void appNaoExecutaDdl(String ddl) throws SQLException {
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      falhaCom(PERMISSAO_NEGADA, () -> executar(app, ddl));
    }
  }

  @Test
  void appNaoAlteraNemExcluiLancamento() throws SQLException {
    Linha linha = Linha.credito(UUID.randomUUID());
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      inserir(app, linha);

      falhaCom(
          PERMISSAO_NEGADA,
          () ->
              executar(app, "UPDATE lancamento SET valor = 99.99 WHERE id = '" + linha.id() + "'"));
      falhaCom(
          PERMISSAO_NEGADA,
          () -> executar(app, "DELETE FROM lancamento WHERE id = '" + linha.id() + "'"));

      assertThat(
              contar(
                  app,
                  "SELECT count(*) FROM lancamento WHERE id = ? AND valor = 10.00",
                  linha.id()))
          .isEqualTo(1);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "lancamento,      SELECT, true",
    "lancamento,      INSERT, true",
    "lancamento,      UPDATE, false",
    "lancamento,      DELETE, false",
    "idempotency_key, SELECT, true",
    "idempotency_key, INSERT, true",
    "idempotency_key, UPDATE, false",
    "idempotency_key, DELETE, false",
    "outbox_event,    SELECT, true",
    "outbox_event,    INSERT, true",
    "outbox_event,    UPDATE, true",
    "outbox_event,    DELETE, true"
  })
  void grantsMinimosDoApp(String tabela, String privilegio, boolean esperado) throws SQLException {
    try (Connection owner = PostgresLancamentos.conectarComoOwner();
        var ps = owner.prepareStatement("SELECT has_table_privilege(?, ?, ?)")) {
      ps.setString(1, PostgresLancamentos.APP);
      ps.setString(2, tabela);
      ps.setString(3, privilegio);
      try (var rs = ps.executeQuery()) {
        rs.next();
        assertThat(rs.getBoolean(1)).as("%s em %s", privilegio, tabela).isEqualTo(esperado);
      }
    }
  }

  @Test
  void rolesVemDoInitSqlDoAmbienteLocal() throws IOException, SQLException {
    String initSql;
    try (Stream<Path> arquivos = Files.list(PostgresLancamentos.INIT_SQL)) {
      initSql =
          arquivos
              .filter(arquivo -> arquivo.toString().endsWith(".sql"))
              .map(LinhasSqlArquivos::ler)
              .reduce("", String::concat);
    }
    assertThat(initSql)
        .contains(CREATE_ROLE + " " + PostgresLancamentos.OWNER)
        .contains(CREATE_ROLE + " " + PostgresLancamentos.APP);

    try (Stream<Path> arquivos =
        Files.walk(
            PostgresLancamentos.INIT_SQL
                .resolve("../../lancamentos-service/src/test")
                .normalize())) {
      List<Path> sqlDeRolesNoTeste =
          arquivos
              .filter(arquivo -> arquivo.toString().endsWith(".sql"))
              .filter(
                  arquivo ->
                      LinhasSqlArquivos.ler(arquivo).toUpperCase(Locale.ROOT).contains(CREATE_ROLE))
              .toList();
      assertThat(sqlDeRolesNoTeste).as("SQL de roles duplicado em src/test").isEmpty();
    }

    try (Connection owner = PostgresLancamentos.conectarComoOwner();
        var ps =
            owner.prepareStatement(
                "SELECT count(*) FROM pg_roles WHERE rolname IN (?, ?)"
                    + " AND rolcanlogin AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole")) {
      ps.setString(1, PostgresLancamentos.OWNER);
      ps.setString(2, PostgresLancamentos.APP);
      try (var rs = ps.executeQuery()) {
        rs.next();
        assertThat(rs.getLong(1)).isEqualTo(2);
      }
    }
  }

  /** Leitura de arquivos de texto sem exceção checada, para uso em streams. */
  private static final class LinhasSqlArquivos {

    private LinhasSqlArquivos() {}

    static String ler(Path arquivo) {
      try {
        return Files.readString(arquivo, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new IllegalStateException("não foi possível ler " + arquivo, e);
      }
    }
  }
}
