package br.com.fluxocaixa.lancamentos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fluxocaixa.lancamentos.application.port.out.IdempotencyKeyRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.IdempotencyKeyRepository.ChaveRegistrada;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Ramos do passo 3 de 03 §3.1.1, com a porta de chaves simulada (sem banco). */
@Tag("RF-007")
class ExecucaoIdempotenteTest {

  private static final UUID COMERCIANTE = UUID.randomUUID();
  private static final String CHAVE = "chave-1";
  private static final String HASH = "a".repeat(64);

  private final IdempotencyKeyRepository chaves = mock(IdempotencyKeyRepository.class);
  private final TransacoesRegistradas transacoes = new TransacoesRegistradas();
  private final ExecucaoIdempotente execucao =
      new ExecucaoIdempotente(chaves, new TransactionTemplate(transacoes));
  private final AtomicInteger criacoes = new AtomicInteger();

  @Test
  void chaveNovaExecutaAOperacaoEConfirma() {
    UUID recurso = UUID.randomUUID();
    when(chaves.tentarRegistrar(COMERCIANTE, CHAVE, HASH, recurso)).thenReturn(true);

    var resultado = executar(recurso);

    assertThat(resultado.valor()).isEqualTo("criado");
    assertThat(resultado.replay()).isFalse();
    assertThat(criacoes).hasValue(1);
    assertThat(transacoes.commits).hasValue(1);
    assertThat(transacoes.rollbacks).hasValue(0);
  }

  @Test
  void chaveExistenteComMesmoHashDevolveOExistenteSemCriar() {
    UUID recurso = UUID.randomUUID();
    UUID existente = UUID.randomUUID();
    when(chaves.tentarRegistrar(COMERCIANTE, CHAVE, HASH, recurso)).thenReturn(false);
    when(chaves.buscar(COMERCIANTE, CHAVE))
        .thenReturn(Optional.of(new ChaveRegistrada(HASH, existente)));

    var resultado = executar(recurso);

    assertThat(resultado.valor()).isEqualTo("existente " + existente);
    assertThat(resultado.replay()).isTrue();
    assertThat(criacoes).hasValue(0);
    assertThat(transacoes.rollbacks).hasValue(1);
  }

  @Test
  void chaveExistenteComOutroHashLancaReuso() {
    UUID recurso = UUID.randomUUID();
    when(chaves.tentarRegistrar(COMERCIANTE, CHAVE, HASH, recurso)).thenReturn(false);
    when(chaves.buscar(COMERCIANTE, CHAVE))
        .thenReturn(Optional.of(new ChaveRegistrada("b".repeat(64), UUID.randomUUID())));

    assertThatThrownBy(() -> executar(recurso))
        .isInstanceOf(IdempotencyKeyReuseException.class)
        .hasMessageNotContaining(CHAVE);
    assertThat(criacoes).hasValue(0);
    assertThat(transacoes.rollbacks).hasValue(1);
  }

  @Test
  void linhaAusenteRepeteOPassoTres() {
    UUID recurso = UUID.randomUUID();
    when(chaves.tentarRegistrar(COMERCIANTE, CHAVE, HASH, recurso)).thenReturn(false, true);
    when(chaves.buscar(COMERCIANTE, CHAVE)).thenReturn(Optional.empty());

    var resultado = executar(recurso);

    assertThat(resultado.replay()).isFalse();
    assertThat(criacoes).hasValue(1);
    verify(chaves, times(2)).tentarRegistrar(COMERCIANTE, CHAVE, HASH, recurso);
  }

  @Test
  void tresTentativasSemResolverLancamConflito() {
    UUID recurso = UUID.randomUUID();
    when(chaves.tentarRegistrar(COMERCIANTE, CHAVE, HASH, recurso)).thenReturn(false);
    when(chaves.buscar(COMERCIANTE, CHAVE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> executar(recurso))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessageNotContaining(CHAVE);
    verify(chaves, times(ExecucaoIdempotente.MAX_TENTATIVAS))
        .tentarRegistrar(COMERCIANTE, CHAVE, HASH, recurso);
    assertThat(criacoes).hasValue(0);
  }

  private ExecucaoIdempotente.Resultado<String> executar(UUID recurso) {
    return execucao.executar(
        COMERCIANTE,
        CHAVE,
        HASH,
        recurso,
        () -> {
          criacoes.incrementAndGet();
          return "criado";
        },
        existente -> "existente " + existente);
  }

  /** Gerenciador de transação em memória que só conta commits e rollbacks. */
  private static final class TransacoesRegistradas implements PlatformTransactionManager {

    private final AtomicInteger commits = new AtomicInteger();
    private final AtomicInteger rollbacks = new AtomicInteger();

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definicao) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      if (status.isRollbackOnly()) {
        rollbacks.incrementAndGet();
      } else {
        commits.incrementAndGet();
      }
    }

    @Override
    public void rollback(TransactionStatus status) {
      rollbacks.incrementAndGet();
    }
  }
}
