package br.com.fluxocaixa.lancamentos.application;

import br.com.fluxocaixa.lancamentos.application.port.out.OutboxPendente;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicadorDeEventos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Publicação da outbox (D-02, D-09, ADR-003; T-016). Um ciclo trava um lote de pendentes ({@code
 * SKIP LOCKED}), envia ao broker e, na mesma transação, marca os confirmados e registra as falhas
 * (Q-030). Queda depois do envio e antes do {@code COMMIT} republica o evento com o mesmo {@code
 * eventId}: a entrega é at-least-once e o consumidor deduplica (ADR-005).
 */
public class PublicarOutbox {

  /** Linhas removidas por transação na limpeza (Q-029). */
  public static final int BLOCO_DE_LIMPEZA = 1_000;

  /**
   * Resultado de um ciclo.
   *
   * @param cheio {@code true} se o lote veio com o tamanho máximo (pode haver mais pendentes)
   */
  public record ResultadoDoLote(int publicados, int falhas, boolean cheio) {}

  private final OutboxRepository outbox;
  private final PublicadorDeEventos publicador;
  private final TransactionOperations transacao;
  private final Clock clock;
  private final int tamanhoDoLote;
  private final Duration retencao;

  /**
   * @param transacao transação do pool dedicado do publicador, com o timeout de 03 §7.1
   */
  public PublicarOutbox(
      OutboxRepository outbox,
      PublicadorDeEventos publicador,
      TransactionOperations transacao,
      Clock clock,
      int tamanhoDoLote,
      Duration retencao) {
    this.outbox = outbox;
    this.publicador = publicador;
    this.transacao = transacao;
    this.clock = clock;
    this.tamanhoDoLote = tamanhoDoLote;
    this.retencao = retencao;
  }

  /**
   * Um ciclo. Uma exceção fora do envio (banco, timeout da transação) desfaz o lote inteiro, que
   * volta no próximo ciclo.
   */
  public ResultadoDoLote publicarLote() {
    return transacao.execute(status -> publicarNaTransacao());
  }

  private ResultadoDoLote publicarNaTransacao() {
    List<OutboxPendente> lote = outbox.travarPendentes(tamanhoDoLote);
    if (lote.isEmpty()) {
      return new ResultadoDoLote(0, 0, false);
    }
    Map<UUID, String> falhas = publicador.publicar(lote);
    List<UUID> confirmados =
        lote.stream().map(OutboxPendente::eventId).filter(id -> !falhas.containsKey(id)).toList();
    if (!confirmados.isEmpty()) {
      outbox.marcarPublicados(confirmados, clock.instant());
    }
    if (!falhas.isEmpty()) {
      outbox.registrarFalhas(falhas);
    }
    return new ResultadoDoLote(confirmados.size(), falhas.size(), lote.size() == tamanhoDoLote);
  }

  /**
   * Remove, em blocos e cada bloco na sua transação, as linhas publicadas antes de {@code Clock −
   * retenção}; pendentes nunca (Q-029).
   *
   * @return quantas linhas foram removidas
   */
  public int limpar() {
    Instant corte = clock.instant().minus(retencao);
    int total = 0;
    int removidas;
    do {
      Integer bloco =
          transacao.execute(s -> outbox.excluirPublicadosAntesDe(corte, BLOCO_DE_LIMPEZA));
      removidas = bloco == null ? 0 : bloco;
      total += removidas;
    } while (removidas == BLOCO_DE_LIMPEZA);
    return total;
  }
}
