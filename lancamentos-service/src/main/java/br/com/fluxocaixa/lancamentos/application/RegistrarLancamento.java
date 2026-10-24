package br.com.fluxocaixa.lancamentos.application;

import br.com.fluxocaixa.lancamentos.application.port.out.ContextoDeTrace;
import br.com.fluxocaixa.lancamentos.application.port.out.EventoOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.IdGenerator;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.MetricasDeLancamentos;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.time.Clock;
import java.util.UUID;

/**
 * Registra um crédito ou débito com idempotência por {@code Idempotency-Key} e o evento na outbox
 * na mesma transação (03 §3.1.1, D-02, D-08). Em replay, a resposta é reconstruída do lançamento
 * imutável; nada da resposta é armazenado.
 */
public class RegistrarLancamento {

  /**
   * Resultado do registro.
   *
   * @param replay {@code true} se a chave já tinha sido usada com o mesmo comando
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

  public RegistrarLancamento(
      ExecucaoIdempotente execucaoIdempotente,
      LancamentoRepository lancamentos,
      OutboxRepository outbox,
      IdGenerator ids,
      PoliticaLancamento politica,
      Clock clock,
      MetricasDeLancamentos metricas,
      ContextoDeTrace trace) {
    this.execucaoIdempotente = execucaoIdempotente;
    this.lancamentos = lancamentos;
    this.outbox = outbox;
    this.ids = ids;
    this.politica = politica;
    this.clock = clock;
    this.metricas = metricas;
    this.trace = trace;
  }

  /**
   * Passo 1 (sem banco): valida só os campos, gera o id e calcula o hash. As regras de data ficam
   * no passo 4, dentro da execução idempotente: um replay devolve o original sem reaplicá-las, e
   * uma rejeição desfaz a chave junto (03 §3.1.1, Q-011).
   *
   * @throws br.com.fluxocaixa.lancamentos.domain.LancamentoInvalidoException campos inválidos
   * @throws br.com.fluxocaixa.lancamentos.domain.RegraDeNegocioException data fora dos limites (só
   *     em comandos novos)
   * @throws IdempotencyKeyReuseException chave já usada com outro comando
   * @throws IdempotencyConflictException chave em disputa após as tentativas
   */
  public Resultado registrar(RegistrarLancamentoCommand comando) {
    UUID comercianteId = comando.comercianteId();
    Lancamento.Novo novo =
        new Lancamento.Novo(
            comercianteId,
            comando.tipo(),
            comando.valor(),
            comando.descricao(),
            comando.dataOcorrencia(),
            comando.identificadorExterno());
    Lancamento.validarCampos(novo, politica);
    UUID lancamentoId = ids.gerar();
    try {
      var resultado =
          execucaoIdempotente.executar(
              comercianteId,
              comando.chave(),
              RequestFingerprint.deRegistro(comando),
              lancamentoId,
              () ->
                  gravar(
                      Lancamento.registrar(lancamentoId, novo, politica, clock),
                      comando.requestId()),
              existente -> carregar(comercianteId, existente));
      contar(resultado.valor().tipo(), resultado.replay());
      return new Resultado(resultado.valor(), resultado.replay());
    } catch (IdempotencyKeyReuseException e) {
      metricas.conflito();
      throw e;
    }
  }

  /** Passo 4: lançamento e evento na transação aberta pela execução idempotente. */
  private Lancamento gravar(Lancamento lancamento, String requestId) {
    lancamentos.inserir(lancamento);
    LancamentoRegistradoV1 evento =
        LancamentoRegistradoV1.de(lancamento, ids.gerar(), clock.instant(), trace.traceIdAtual());
    outbox.inserir(
        new EventoOutbox(
            evento.eventId(),
            lancamento.id(),
            LancamentoRegistradoV1.TOPICO,
            lancamento.comercianteId().toString(),
            LancamentoRegistradoV1.EVENT_TYPE,
            LancamentoRegistradoV1.SCHEMA_VERSION,
            evento,
            requestId));
    return lancamento;
  }

  /** Depois do commit: lançamento novo conta como registrado; replay, como replay (Q-063). */
  private void contar(TipoLancamento tipo, boolean replay) {
    if (replay) {
      metricas.replay();
    } else {
      metricas.registrado(tipo);
    }
  }

  /** Replay: a resposta é reconstruída do lançamento original (D-08). */
  private Lancamento carregar(UUID comercianteId, UUID lancamentoId) {
    return lancamentos
        .buscarPorId(comercianteId, lancamentoId)
        .orElseThrow(() -> new IllegalStateException("chave confirmada sem lançamento"));
  }
}
