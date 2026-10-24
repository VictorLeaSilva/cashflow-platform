package br.com.fluxocaixa.consolidado.application.port.out;

import br.com.fluxocaixa.consolidado.domain.Efeito;
import java.time.Instant;
import java.util.UUID;

/** Projeção diária do Consolidado com deduplicação por {@code eventId} (03 §3.2, ADR-005). */
public interface ProjecaoRepository {

  /**
   * Registra o evento e soma o efeito, numa única operação atômica (Q-037). Chamada dentro de uma
   * transação, faz parte dela: o <i>rollback</i> desfaz as duas coisas.
   *
   * @param instante instante do processamento, pelo {@code Clock} de quem chama (Q-038)
   * @return {@code true} se o evento era novo e o efeito foi somado; {@code false} se o {@code
   *     eventId} já tinha sido processado, sem mudar nada
   */
  boolean aplicarSeNovo(UUID eventId, Efeito efeito, Instant instante);
}
