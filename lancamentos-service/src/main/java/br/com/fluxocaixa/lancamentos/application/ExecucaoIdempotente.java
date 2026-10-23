package br.com.fluxocaixa.lancamentos.application;

import br.com.fluxocaixa.lancamentos.application.port.out.IdempotencyKeyRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.IdempotencyKeyRepository.ChaveRegistrada;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Passos 2 a 5 de 03 §3.1.1, genérico pela operação (registro na T-012, estorno na T-014). Numa
 * transação: registra a chave com {@code ON CONFLICT DO NOTHING}; se a chave é nossa, executa a
 * operação e confirma; se já existe, desfaz e devolve o recurso existente (mesmo hash) ou rejeita
 * (hash diferente). A FK diferida garante que nenhuma chave é confirmada sem o recurso.
 */
public class ExecucaoIdempotente {

  /** Tentativas do passo 3 quando a linha concorrente desaparece (rollback da outra transação). */
  public static final int MAX_TENTATIVAS = 3;

  /**
   * Resultado da execução.
   *
   * @param replay {@code true} se a chave já existia e o recurso devolvido é o original
   */
  public record Resultado<T>(T valor, boolean replay) {}

  /** O que a transação do passo 3 decidiu. */
  private sealed interface Desfecho<T> {}

  private record Criado<T>(T valor) implements Desfecho<T> {}

  private record Existente<T>(UUID recursoId) implements Desfecho<T> {}

  private record Ausente<T>() implements Desfecho<T> {}

  private final IdempotencyKeyRepository chaves;
  private final TransactionOperations transacao;

  public ExecucaoIdempotente(IdempotencyKeyRepository chaves, TransactionOperations transacao) {
    this.chaves = chaves;
    this.transacao = transacao;
  }

  /**
   * Executa {@code criar} uma única vez por (comerciante, chave).
   *
   * @param recursoId id gerado antes da transação; gravado na chave e usado por {@code criar}
   * @param carregarExistente lê o recurso original pelo id gravado na chave (replay)
   * @throws IdempotencyKeyReuseException se a chave já foi usada com outro {@code requestHash}
   * @throws IdempotencyConflictException se o passo 3 não se resolver em {@link #MAX_TENTATIVAS}
   */
  public <T> Resultado<T> executar(
      UUID comercianteId,
      String chave,
      String requestHash,
      UUID recursoId,
      Supplier<T> criar,
      Function<UUID, T> carregarExistente) {
    for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
      Desfecho<T> desfecho =
          transacao.execute(
              status -> {
                if (chaves.tentarRegistrar(comercianteId, chave, requestHash, recursoId)) {
                  return new Criado<>(criar.get());
                }
                status.setRollbackOnly();
                Optional<ChaveRegistrada> registrada = chaves.buscar(comercianteId, chave);
                if (registrada.isEmpty()) {
                  return new Ausente<>();
                }
                if (!mesmoHash(registrada.get().requestHash(), requestHash)) {
                  throw new IdempotencyKeyReuseException();
                }
                return new Existente<>(registrada.get().lancamentoId());
              });
      if (desfecho instanceof Criado<T> criado) {
        return new Resultado<>(criado.valor(), false);
      }
      if (desfecho instanceof Existente<T> existente) {
        // Lido depois do rollback: o recurso está confirmado (a chave só existe com ele).
        return new Resultado<>(carregarExistente.apply(existente.recursoId()), true);
      }
      // Ausente: a transação concorrente desfez a chave; repetir o passo 3.
    }
    throw new IdempotencyConflictException(MAX_TENTATIVAS);
  }

  /** Comparação em tempo constante. */
  private static boolean mesmoHash(String registrado, String recebido) {
    return MessageDigest.isEqual(
        registrado.getBytes(StandardCharsets.UTF_8), recebido.getBytes(StandardCharsets.UTF_8));
  }
}
