package br.com.fluxocaixa.lancamentos.application;

import br.com.fluxocaixa.lancamentos.application.port.out.ContextoDeTrace;
import br.com.fluxocaixa.lancamentos.application.port.out.EventoOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.IdGenerator;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.MetricasDeLancamentos;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Estorna um lançamento (RF-003, A5, D-07) com a mesma idempotência e a mesma transação com outbox
 * do registro (03 §3.1.1). Dentro da transação: busca o original do comerciante (404), cria o
 * estorno no domínio (422 para estorno de estorno) e o insere; o índice único parcial {@code
 * ux_estorno_unico} é a única garantia de "um estorno por lançamento", inclusive sob concorrência
 * (409). Um replay devolve o estorno original sem reaplicar regra nenhuma (Q-011).
 */
public class EstornarLancamento {

  /** Logger de auditoria dos estornos (03 §6, Q-021). */
  static final String AUDITORIA = "br.com.fluxocaixa.lancamentos.auditoria";

  private static final Logger LOG_AUDITORIA = LoggerFactory.getLogger(AUDITORIA);

  /**
   * Resultado do estorno.
   *
   * @param replay {@code true} se a chave já tinha sido usada para estornar o mesmo original
   */
  public record Resultado(Lancamento lancamento, boolean replay) {}

  private final ExecucaoIdempotente execucaoIdempotente;
  private final LancamentoRepository lancamentos;
  private final OutboxRepository outbox;
  private final IdGenerator ids;
  private final PoliticaLancamento politica;
  private final Clock clock;
  private final MetricasDeLancamentos metricas;
  private final ContextoDeTrace trace;

  public EstornarLancamento(
      ExecucaoIdempotente execucaoIdempotente,
      LancamentoRepository lancamentos,
      OutboxRepository outbox,
      Ambiente ambiente,
      Observabilidade observabilidade) {
    this.execucaoIdempotente = execucaoIdempotente;
    this.lancamentos = lancamentos;
    this.outbox = outbox;
    this.ids = ambiente.ids();
    this.politica = ambiente.politica();
    this.clock = ambiente.clock();
    this.metricas = observabilidade.metricas();
    this.trace = observabilidade.trace();
  }

  /**
   * Passo 1 (sem banco): gera o id do estorno e calcula o hash; o resto roda dentro da execução
   * idempotente. A auditoria é registrada só depois do commit de um estorno novo.
   *
   * @throws LancamentoNaoEncontradoException original inexistente ou de outro comerciante
   * @throws br.com.fluxocaixa.lancamentos.domain.RegraDeNegocioException o original é um estorno
   * @throws LancamentoJaEstornadoException o original já tem estorno
   * @throws IdempotencyKeyReuseException chave já usada com outra operação
   * @throws IdempotencyConflictException chave em disputa após as tentativas
   */
  public Resultado estornar(EstornarLancamentoCommand comando) {
    UUID comercianteId = comando.comercianteId();
    UUID estornoId = ids.gerar();
    try {
      var resultado =
          execucaoIdempotente.executar(
              comercianteId,
              comando.chave(),
              RequestFingerprint.deEstorno(comando),
              estornoId,
              () -> gravar(criar(comando, estornoId), comando.requestId()),
              existente -> carregar(comercianteId, existente));
      if (resultado.replay()) {
        metricas.replay();
      } else {
        metricas.registrado(resultado.valor().tipo());
        auditar(resultado.valor());
      }
      return new Resultado(resultado.valor(), resultado.replay());
    } catch (IdempotencyKeyReuseException e) {
      metricas.conflito();
      throw e;
    }
  }

  /** Passo 4: o original só é visível para o próprio comerciante (RF-010). */
  private Lancamento criar(EstornarLancamentoCommand comando, UUID estornoId) {
    Lancamento original =
        lancamentos
            .buscarPorId(comando.comercianteId(), comando.lancamentoOriginalId())
            .orElseThrow(LancamentoNaoEncontradoException::new);
    return Lancamento.estornar(estornoId, original, politica, clock);
  }

  /** Estorno e evento na transação aberta pela execução idempotente. */
  private Lancamento gravar(Lancamento estorno, String requestId) {
    lancamentos.inserir(estorno);
    LancamentoRegistradoV1 evento =
        LancamentoRegistradoV1.de(estorno, ids.gerar(), clock.instant(), trace.traceIdAtual());
    outbox.inserir(
        new EventoOutbox(
            evento.eventId(),
            estorno.id(),
            LancamentoRegistradoV1.TOPICO,
            estorno.comercianteId().toString(),
            LancamentoRegistradoV1.EVENT_TYPE,
            LancamentoRegistradoV1.SCHEMA_VERSION,
            evento,
            requestId));
    return estorno;
  }

  /** Replay: a resposta é reconstruída do estorno original (D-08). */
  private Lancamento carregar(UUID comercianteId, UUID estornoId) {
    return lancamentos
        .buscarPorId(comercianteId, estornoId)
        .orElseThrow(() -> new IllegalStateException("chave confirmada sem lançamento"));
  }

  /** Sem valor nem comerciante; o {@code requestId} vem do MDC (03 §5.2, Q-021). */
  private static void auditar(Lancamento estorno) {
    LOG_AUDITORIA
        .atInfo()
        .addKeyValue("evento", "estorno-registrado")
        .addKeyValue("estornoId", estorno.id().toString())
        .addKeyValue("lancamentoOriginalId", estorno.referenciaLancamentoId().toString())
        .addKeyValue("tipoOriginal", estorno.tipoOriginal().name())
        .addKeyValue("dataReferencia", estorno.dataReferencia().toString())
        .log("estorno registrado");
  }
}
