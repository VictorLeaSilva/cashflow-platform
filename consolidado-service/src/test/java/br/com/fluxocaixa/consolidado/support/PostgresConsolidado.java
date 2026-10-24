package br.com.fluxocaixa.consolidado.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL compartilhado pelos ITs de persistência (T-021). Usa a mesma tag do compose e os
 * mesmos scripts de {@code deploy/init/} (fonte única dos roles, como na T-011); as senhas dos
 * roles são geradas a cada execução e entregues ao script por variável de ambiente ({@code
 * \getenv}).
 */
public final class PostgresConsolidado {

  /** Mesma tag de {@code deploy/docker-compose.yml} (T-004). */
  public static final String IMAGEM = "postgres:17.11-alpine";

  /**
   * Scripts de inicialização do ambiente local. Resolvido a partir do diretório do módulo (Maven)
   * ou da raiz do repositório (IDE).
   */
  public static final Path INIT_SQL =
      Files.isDirectory(Path.of("deploy", "init"))
          ? Path.of("deploy", "init")
          : Path.of("..", "deploy", "init");

  public static final String BANCO = "consolidado";
  public static final String OWNER = "consolidado_owner";
  public static final String APP = "consolidado_app";

  private static final String OWNER_PASSWORD = UUID.randomUUID().toString();
  private static final String APP_PASSWORD = UUID.randomUUID().toString();

  @SuppressWarnings("resource") // encerrado pelo Ryuk ao fim da JVM (container compartilhado)
  private static final PostgreSQLContainer CONTAINER = iniciar();

  private PostgresConsolidado() {}

  private static PostgreSQLContainer iniciar() {
    var container =
        new PostgreSQLContainer(IMAGEM)
            .withCopyFileToContainer(
                MountableFile.forHostPath(INIT_SQL), "/docker-entrypoint-initdb.d/")
            .withEnv("LANCAMENTOS_OWNER_PASSWORD", UUID.randomUUID().toString())
            .withEnv("LANCAMENTOS_APP_PASSWORD", UUID.randomUUID().toString())
            .withEnv("CONSOLIDADO_OWNER_PASSWORD", OWNER_PASSWORD)
            .withEnv("CONSOLIDADO_APP_PASSWORD", APP_PASSWORD);
    container.start();
    return container;
  }

  /** URL JDBC do banco {@code consolidado} no container. */
  public static String jdbcUrl() {
    return "jdbc:postgresql://"
        + CONTAINER.getHost()
        + ":"
        + CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
        + "/"
        + BANCO;
  }

  /** Conexão como o role da aplicação. */
  public static Connection conectarComoApp() throws SQLException {
    return DriverManager.getConnection(jdbcUrl(), APP, APP_PASSWORD);
  }

  /** Conexão como o role dono do banco (o mesmo usado pelo Flyway). */
  public static Connection conectarComoOwner() throws SQLException {
    return DriverManager.getConnection(jdbcUrl(), OWNER, OWNER_PASSWORD);
  }

  /** Esvazia as duas tabelas como {@code consolidado_owner} (o app não tem {@code DELETE}). */
  public static void esvaziar() {
    try (Connection owner = conectarComoOwner();
        Statement comando = owner.createStatement()) {
      comando.execute("TRUNCATE consolidado_diario, evento_processado");
    } catch (SQLException e) {
      throw new IllegalStateException("não foi possível esvaziar as tabelas do consolidado", e);
    }
  }

  /**
   * Aponta datasource e Flyway para o container. Os usuários vêm do {@code application.yml}; aqui
   * só entram URL e senhas, como no ambiente local.
   */
  public static void registrarPropriedades(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", PostgresConsolidado::jdbcUrl);
    registro.add("spring.datasource.password", () -> APP_PASSWORD);
    registro.add("spring.flyway.password", () -> OWNER_PASSWORD);
    registro.add("spring.flyway.enabled", () -> "true");
  }
}
