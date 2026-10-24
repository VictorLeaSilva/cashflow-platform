package br.com.fluxocaixa.lancamentos.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Porta de saída para o broker (ADR-004): envia um lote e espera as confirmações. */
public interface PublicadorDeEventos {

  /**
   * Envia os eventos do lote e espera a confirmação de cada um. Com o broker indisponível, a
   * implementação pode dar o resto do lote como falho sem tentar enviá-lo (falha rápida, Q-031);
   * todo evento do lote volta confirmado ou com falha, nunca omitido.
   *
   * @return as falhas, por {@code eventId}, com uma descrição sem payload (Q-030); vazio quando
   *     todos foram confirmados
   */
  Map<UUID, String> publicar(List<OutboxPendente> lote);
}
