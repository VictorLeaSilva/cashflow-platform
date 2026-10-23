package br.com.fluxocaixa.lancamentos.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A outbox (D-02): a gravação acontece na transação do lançamento; a leitura, a marcação e a
 * limpeza são do publicador (T-016) e rodam na transação dele, no pool dedicado.
 */
public interface OutboxRepository {

  void inserir(EventoOutbox evento);

  /**
   * Trava até {@code limite} pendentes, as mais antigas primeiro, pulando as já travadas por outra
   * transação ({@code FOR UPDATE SKIP LOCKED}). Exige transação aberta.
   */
  List<OutboxPendente> travarPendentes(int limite);

  /** Marca {@code published_at} nos eventos confirmados pelo broker. */
  void marcarPublicados(Collection<UUID> eventIds, Instant publicadoEm);

  /** Incrementa {@code attempt_count} e grava {@code last_error} (sem payload, Q-030). */
  void registrarFalhas(Map<UUID, String> falhasPorEvento);

  /** Exclui até {@code limite} linhas publicadas antes de {@code instante}; devolve quantas. */
  int excluirPublicadosAntesDe(Instant instante, int limite);

  long contarPendentes();

  /** {@code created_at} da pendente mais antiga, se houver. */
  Optional<Instant> maisAntigoPendente();
}
