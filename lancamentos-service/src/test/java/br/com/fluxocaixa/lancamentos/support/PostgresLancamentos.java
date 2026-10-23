package br.com.fluxocaixa.lancamentos.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL compartilhado pelos ITs de persistência (T-011). Usa a mesma tag do compose e os
 * mesmos scripts de {@code deploy/init/} (fonte única dos roles, AC-T011.9); as senhas dos roles
 * são geradas a cada execução e entregues ao script por variável de ambiente ({@code \getenv}).
 */
public final class PostgresLancamentos {

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

  public static final String BANCO = "lancamentos";
  public static final String OWNER = "lancamentos_owner";
  public static final String APP = "lancamentos_app";

  private static final String OWNER_PASSWORD = UUID.randomUUID().toString();
  private static final String APP_PASSWORD = UUID.randomUUID().toString();

  @SuppressWarnings("resource") // encerrado pelo Ryuk ao fim da JVM (container compartilhado)
  private static final PostgreSQLContainer CONTAINER = iniciar();

  private PostgresLancamentos() {}

  private static PostgreSQLContainer iniciar() {
    var container =
        new PostgreSQLContainer(IMAGEM)
            .withCopyFileToContainer(
                MountableFile.forHostPath(INIT_SQL), "/docker-entrypoint-initdb.d/")
            .withEnv("LANCAMENTOS_OWNER_PASSWORD", OWNER_PASSWORD)
            .withEnv("LANCAMENTOS_APP_PASSWORD", APP_PASSWORD)
            .withEnv("CONSOLIDADO_OWNER_PASSWORD", UUID.randomUUID().toString())
            .withEnv("CONSOLIDADO_APP_PASSWORD", UUID.randomUUID().toString());
    container.start();
    return container;
  }

  /** URL JDBC do banco {@code lancamentos} no container. */
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

  /**
   * Aponta datasource e Flyway para o container. Os usuários vêm do {@code application.yml}; aqui
   * só entram URL e senhas, como no ambiente local.
   */
  public static void registrarPropriedades(DynamicPropertyRegistry registro) {
    registro.add("spring.datasource.url", PostgresLancamentos::jdbcUrl);
    registro.add("spring.datasource.password", () -> APP_PASSWORD);
    registro.add("spring.flyway.password", () -> OWNER_PASSWORD);
    registro.add("spring.flyway.enabled", () -> "true");
  }
}
