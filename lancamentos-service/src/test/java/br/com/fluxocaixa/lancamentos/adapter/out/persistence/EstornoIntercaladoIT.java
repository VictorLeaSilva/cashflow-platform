package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import br.com.fluxocaixa.lancamentos.application.EstornarLancamento;
import br.com.fluxocaixa.lancamentos.application.EstornarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.application.LancamentoJaEstornadoException;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * AC-T014.1: intercalação forçada do estorno concorrente. A insere o estorno e fica retida na
 * gravação da outbox (porta espiã com <i>latch</i>); B, com outra chave, é confirmada esperando o
 * lock do índice único ({@code pg_stat_activity}, sem {@code sleep}); A confirma → B recebe 409 e
 * nada dela persiste. Variante: A faz rollback → B cria o estorno.
 */
@Tag("RF-003")
@Tag("RNF-004")
class EstornoIntercaladoIT extends IntegracaoPostgres {

  @MockitoSpyBean private OutboxRepository outbox;
  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private EstornarLancamento estornarLancamento;
  @Autowired private JdbcClient jdbc;

  @AfterEach
  void restaurarOutbox() {
    reset(outbox);
  }

  @Test
  void segundaTransacaoEsperaAPrimeiraEReceberConflito() throws Exception {
    Cenario cenario = new Cenario(false);

    cenario.executar();

    assertThat(cenario.a.get(10, TimeUnit.SECONDS).replay()).isFalse();
    assertThat(cenario.b)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(LancamentoJaEstornadoException.class);
    assertThat(Contagens.de(jdbc, cenario.comerciante)).isEqualTo(new Contagens(2, 2, 2));
    assertThat(chaveExiste(cenario.comerciante, "intercalado-b")).isFalse();
  }

  @Test
  void rollbackDaPrimeiraLiberaASegunda() throws Exception {
    Cenario cenario = new Cenario(true);

    cenario.executar();

    assertThat(cenario.a)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(IllegalStateException.class);
    var resultadoB = cenario.b.get(10, TimeUnit.SECONDS);
    assertThat(resultadoB.replay()).isFalse();
    assertThat(resultadoB.lancamento().referenciaLancamentoId()).isEqualTo(cenario.originalId);
    assertThat(Contagens.de(jdbc, cenario.comerciante)).isEqualTo(new Contagens(2, 2, 2));
    assertThat(chaveExiste(cenario.comerciante, "intercalado-a")).isFalse();
  }

  /** Duas transações de estorno do mesmo original, com A retida dentro da transação. */
  private final class Cenario {

    private final boolean aDesfaz;
    private final UUID comerciante = UUID.randomUUID();
    private UUID originalId;
    private CompletableFuture<EstornarLancamento.Resultado> a;
    private CompletableFuture<EstornarLancamento.Resultado> b;

    Cenario(boolean aDesfaz) {
      this.aDesfaz = aDesfaz;
    }

    void executar() throws Exception {
      originalId =
          registrarLancamento
              .registrar(
                  new RegistrarLancamentoCommand(
                      comerciante,
                      "intercalado-original",
                      TipoLancamento.CREDITO,
                      new BigDecimal("33.00"),
                      null,
                      null,
                      null,
                      null))
              .lancamento()
              .id();
      CountDownLatch aInseriu = new CountDownLatch(1);
      CountDownLatch liberarA = new CountDownLatch(1);
      AtomicInteger chamadas = new AtomicInteger();
      doAnswer(
              invocacao -> {
                if (chamadas.incrementAndGet() == 1) {
                  aInseriu.countDown();
                  assertThat(liberarA.await(20, TimeUnit.SECONDS)).isTrue();
                  if (aDesfaz) {
                    throw new IllegalStateException("A desfaz de propósito");
                  }
                }
                return invocacao.callRealMethod();
              })
          .when(outbox)
          .inserir(any());

      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        a = CompletableFuture.supplyAsync(() -> estornar("intercalado-a"), executor);
        assertThat(aInseriu.await(10, TimeUnit.SECONDS)).isTrue();
        b = CompletableFuture.supplyAsync(() -> estornar("intercalado-b"), executor);

        await().atMost(Duration.ofSeconds(10)).until(this::alguemEsperaLock);
        assertThat(b).as("B aguarda a decisão de A").isNotDone();
        liberarA.countDown();
        CompletableFuture.allOf(a, b).exceptionally(erro -> null).get(20, TimeUnit.SECONDS);
      } finally {
        liberarA.countDown();
        executor.shutdown();
      }
    }

    private EstornarLancamento.Resultado estornar(String chave) {
      return estornarLancamento.estornar(
          new EstornarLancamentoCommand(comerciante, chave, originalId, null));
    }

    /** Uma sessão bloqueada no INSERT de lançamento (o índice único), não qualquer espera. */
    private boolean alguemEsperaLock() {
      return jdbc.sql(
                  "SELECT count(*) FROM pg_stat_activity"
                      + " WHERE datname = current_database() AND wait_event_type = 'Lock'"
                      + " AND query ILIKE '%INSERT INTO lancamento %'")
              .query(Long.class)
              .single()
          > 0;
    }
  }

  private boolean chaveExiste(UUID comerciante, String chave) {
    return jdbc.sql("SELECT count(*) FROM idempotency_key WHERE comerciante_id = ? AND chave = ?")
            .params(comerciante, chave)
            .query(Long.class)
            .single()
        > 0;
  }
}
