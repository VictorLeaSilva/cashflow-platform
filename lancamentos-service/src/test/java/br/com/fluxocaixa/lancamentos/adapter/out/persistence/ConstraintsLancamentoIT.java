package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.VIOLACAO_CHECK;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.VIOLACAO_UNICIDADE;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.contar;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.falhaCom;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.inserir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.Linha;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.PostgresLancamentos;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-T011.3, AC-T011.4 e AC-T011.5: constraints de `lancamento` (03 §3.1). */
@Tag("RF-003")
@Tag("RNF-004")
class ConstraintsLancamentoIT extends IntegracaoPostgres {

  private static final String CK_ESTORNO = "ck_estorno";
  private static final String UX_ESTORNO_UNICO = "ux_estorno_unico";
  private static final String UX_IDENTIFICADOR_EXTERNO = "ux_identificador_externo";
  private static final String CONTAR_ESTORNOS =
      "SELECT count(*) FROM lancamento WHERE tipo = 'ESTORNO' AND referencia_lancamento_id = ?";

  @Test
  void estornoSemReferenciaEhRejeitado() throws SQLException {
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      Linha original = Linha.credito(UUID.randomUUID());
      inserir(app, original);
      Linha semReferencia = Linha.estornoDe(original).comReferencia(null, "CREDITO");

      falhaCom(VIOLACAO_CHECK, CK_ESTORNO, () -> inserir(app, semReferencia));
    }
  }

  @Test
  void estornoSemTipoOriginalEhRejeitado() throws SQLException {
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      Linha original = Linha.credito(UUID.randomUUID());
      inserir(app, original);
      Linha semTipoOriginal = Linha.estornoDe(original).comReferencia(original.id(), null);

      falhaCom(VIOLACAO_CHECK, CK_ESTORNO, () -> inserir(app, semTipoOriginal));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREDITO", "DEBITO"})
  void creditoOuDebitoComReferenciaEhRejeitado(String tipo) throws SQLException {
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      Linha original = Linha.credito(UUID.randomUUID());
      inserir(app, original);
      Linha comReferencia =
          Linha.credito(original.comercianteId()).comTipo(tipo).comReferencia(original.id(), null);
      Linha comTipoOriginal =
          Linha.credito(original.comercianteId()).comTipo(tipo).comReferencia(null, "CREDITO");

      falhaCom(VIOLACAO_CHECK, CK_ESTORNO, () -> inserir(app, comReferencia));
      falhaCom(VIOLACAO_CHECK, CK_ESTORNO, () -> inserir(app, comTipoOriginal));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.00", "-0.01", "-10.00"})
  void valorNaoPositivoEhRejeitado(String valor) throws SQLException {
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      Linha linha = Linha.credito(UUID.randomUUID()).comValor(new BigDecimal(valor));

      falhaCom(VIOLACAO_CHECK, "lancamento_valor_check", () -> inserir(app, linha));
    }
  }

  @Test
  void linhasValidasSaoAceitas() throws SQLException {
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      Linha credito = Linha.credito(UUID.randomUUID()).comValor(new BigDecimal("0.01"));
      Linha debito = Linha.credito(credito.comercianteId()).comTipo("DEBITO");
      inserir(app, credito);
      inserir(app, debito);
      inserir(app, Linha.estornoDe(debito));

      assertThat(contar(app, CONTAR_ESTORNOS, debito.id())).isEqualTo(1);
    }
  }

  @Test
  void segundoEstornoDoMesmoOriginalEhRejeitado() throws SQLException {
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      Linha original = Linha.credito(UUID.randomUUID());
      inserir(app, original);
      inserir(app, Linha.estornoDe(original));

      falhaCom(VIOLACAO_UNICIDADE, UX_ESTORNO_UNICO, () -> inserir(app, Linha.estornoDe(original)));
      assertThat(contar(app, CONTAR_ESTORNOS, original.id())).isEqualTo(1);
    }
  }

  /**
   * Duas transações sobrepostas estornam o mesmo original: a segunda fica bloqueada no índice único
   * até a primeira confirmar e então falha. Exatamente um estorno é confirmado.
   */
  @Test
  void estornosConcorrentesConfirmamExatamenteUm() throws Exception {
    Linha original = Linha.credito(UUID.randomUUID());
    try (Connection setup = PostgresLancamentos.conectarComoApp()) {
      inserir(setup, original);
    }

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (Connection primeira = PostgresLancamentos.conectarComoApp();
        Connection segunda = PostgresLancamentos.conectarComoApp();
        Connection observador = PostgresLancamentos.conectarComoOwner()) {
      primeira.setAutoCommit(false);
      segunda.setAutoCommit(false);
      long pidSegunda = pid(segunda);

      inserir(primeira, Linha.estornoDe(original));

      CountDownLatch segundaVaiInserir = new CountDownLatch(1);
      CompletableFuture<Void> resultadoSegunda =
          CompletableFuture.runAsync(
              () -> {
                segundaVaiInserir.countDown();
                try {
                  inserir(segunda, Linha.estornoDe(original));
                  segunda.commit();
                } catch (SQLException e) {
                  throw new EstornoRejeitado(e);
                }
              },
              executor);

      assertThat(segundaVaiInserir.await(10, TimeUnit.SECONDS)).isTrue();
      await().atMost(Duration.ofSeconds(10)).until(() -> aguardandoLock(observador, pidSegunda));
      assertThat(resultadoSegunda).as("a segunda transação está bloqueada").isNotDone();

      primeira.commit();

      falhouPorEstornoUnico(resultadoSegunda);
      segunda.rollback();
      assertThat(contar(observador, CONTAR_ESTORNOS, original.id())).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void identificadorExternoRepetidoNoMesmoComercianteEhRejeitado() throws SQLException {
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      Linha primeira = Linha.credito(UUID.randomUUID()).comIdentificadorExterno("pedido-123");
      inserir(app, primeira);

      falhaCom(
          VIOLACAO_UNICIDADE, UX_IDENTIFICADOR_EXTERNO, () -> inserir(app, primeira.comNovoId()));
    }
  }

  @Test
  void identificadorExternoRepetidoEmOutroComercianteEhAceito() throws SQLException {
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      Linha primeira = Linha.credito(UUID.randomUUID()).comIdentificadorExterno("pedido-456");
      Linha outroComerciante =
          Linha.credito(UUID.randomUUID()).comIdentificadorExterno("pedido-456");
      inserir(app, primeira);
      inserir(app, outroComerciante);

      assertThat(
              contar(
                  app,
                  "SELECT count(*) FROM lancamento WHERE identificador_externo = 'pedido-456'"
                      + " AND comerciante_id = ?",
                  outroComerciante.comercianteId()))
          .isEqualTo(1);
    }
  }

  @Test
  void semIdentificadorExternoNaoColide() throws SQLException {
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      Linha primeira = Linha.credito(UUID.randomUUID());
      inserir(app, primeira);
      inserir(app, primeira.comNovoId());

      assertThat(
              contar(
                  app,
                  "SELECT count(*) FROM lancamento WHERE comerciante_id = ?",
                  primeira.comercianteId()))
          .isEqualTo(2);
    }
  }

  private static void falhouPorEstornoUnico(CompletableFuture<Void> resultado) {
    assertThat(resultado)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableThat()
        .isInstanceOf(ExecutionException.class)
        .havingCause()
        .isInstanceOfSatisfying(
            EstornoRejeitado.class,
            erro -> {
              assertThat(erro.getCause().getSQLState()).isEqualTo(VIOLACAO_UNICIDADE);
              assertThat(erro.getCause().getMessage()).contains(UX_ESTORNO_UNICO);
            });
  }

  private static long pid(Connection conexao) throws SQLException {
    try (var statement = conexao.createStatement();
        var rs = statement.executeQuery("SELECT pg_backend_pid()")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static boolean aguardandoLock(Connection observador, long pid) throws SQLException {
    try (var ps =
        observador.prepareStatement(
            "SELECT count(*) FROM pg_locks WHERE pid = ? AND NOT granted")) {
      ps.setLong(1, pid);
      try (var rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong(1) > 0;
      }
    }
  }

  /** Transporta a falha SQL da transação concorrente para fora da thread. */
  private static final class EstornoRejeitado extends RuntimeException {

    private static final long serialVersionUID = 1L;

    EstornoRejeitado(SQLException causa) {
      super(causa);
    }

    @Override
    public synchronized SQLException getCause() {
      return (SQLException) super.getCause();
    }
  }
}
