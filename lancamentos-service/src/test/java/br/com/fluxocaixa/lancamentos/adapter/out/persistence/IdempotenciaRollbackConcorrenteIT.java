package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.inserirIdempotencyKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.PostgresLancamentos;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * AC-T012.2: A insere a chave e fica aberta; B, com a mesma chave, espera no {@code INSERT ... ON
 * CONFLICT}; A faz rollback; B prossegue e cria o lançamento.
 */
@Tag("RF-007")
@Tag("RNF-004")
class IdempotenciaRollbackConcorrenteIT extends IntegracaoPostgres {

  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private JdbcClient jdbc;

  @Test
  void rollbackDaTransacaoConcorrenteLiberaAChave() throws Exception {
    UUID comerciante = UUID.randomUUID();
    String chave = "rollback-" + UUID.randomUUID();
    var comando =
        new RegistrarLancamentoCommand(
            comerciante,
            chave,
            TipoLancamento.CREDITO,
            new BigDecimal("42.00"),
            null,
            null,
            null,
            null);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (Connection transacaoA = PostgresLancamentos.conectarComoApp();
        Connection observador = PostgresLancamentos.conectarComoOwner()) {
      transacaoA.setAutoCommit(false);
      long pidA = pid(transacaoA);
      inserirIdempotencyKey(transacaoA, comerciante, chave, UUID.randomUUID());

      CountDownLatch bComecou = new CountDownLatch(1);
      CompletableFuture<RegistrarLancamento.Resultado> transacaoB =
          CompletableFuture.supplyAsync(
              () -> {
                bComecou.countDown();
                return registrarLancamento.registrar(comando);
              },
              executor);

      assertThat(bComecou.await(10, TimeUnit.SECONDS)).isTrue();
      await().atMost(Duration.ofSeconds(10)).until(() -> alguemEsperaPor(observador, pidA));
      assertThat(transacaoB).as("B aguarda a decisão de A").isNotDone();

      transacaoA.rollback();

      RegistrarLancamento.Resultado resultado = transacaoB.get(10, TimeUnit.SECONDS);
      assertThat(resultado.replay()).isFalse();
      assertThat(resultado.lancamento().comercianteId()).isEqualTo(comerciante);
      assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(1, 1, 1));
      assertThat(lancamentoDaChave(comerciante, chave)).isEqualTo(resultado.lancamento().id());
    } finally {
      executor.shutdownNow();
    }
  }

  private UUID lancamentoDaChave(UUID comerciante, String chave) {
    return jdbc.sql(
            "SELECT lancamento_id FROM idempotency_key WHERE comerciante_id = ? AND chave = ?")
        .params(comerciante, chave)
        .query(UUID.class)
        .single();
  }

  private static long pid(Connection conexao) throws SQLException {
    try (var statement = conexao.createStatement();
        var rs = statement.executeQuery("SELECT pg_backend_pid()")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  /** Há alguma sessão com lock não concedido bloqueada pela sessão {@code pid}. */
  private static boolean alguemEsperaPor(Connection observador, long pid) throws SQLException {
    try (var ps =
        observador.prepareStatement(
            "SELECT count(*) FROM pg_locks WHERE NOT granted"
                + " AND ?::int = ANY (pg_blocking_pids(pid))")) {
      ps.setLong(1, pid);
      try (var rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong(1) > 0;
      }
    }
  }
}
