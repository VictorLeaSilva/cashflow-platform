package br.com.fluxocaixa.lancamentos.application;

import br.com.fluxocaixa.lancamentos.application.port.out.EventoOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.IdGenerator;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
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

  public RegistrarLancamento(
      ExecucaoIdempotente execucaoIdempotente,
      LancamentoRepository lancamentos,
      OutboxRepository outbox,
      IdGenerator ids,
      PoliticaLancamento politica,
      Clock clock) {
    this.execucaoIdempotente = execucaoIdempotente;
    this.lancamentos = lancamentos;
    this.outbox = outbox;
    this.ids = ids;
    this.politica = politica;
    this.clock = clock;
  }

  /**
   * Passo 1 (sem banco): valida pelo domínio, gera o id e calcula o hash; depois, a execução
   * idempotente.
   *
   * @throws br.com.fluxocaixa.lancamentos.domain.LancamentoInvalidoException campos inválidos
   * @throws br.com.fluxocaixa.lancamentos.domain.RegraDeNegocioException data fora dos limites
   * @throws IdempotencyKeyReuseException chave já usada com outro comando
   * @throws IdempotencyConflictException chave em disputa após as tentativas
   */
  public Resultado registrar(RegistrarLancamentoCommand comando) {
    UUID comercianteId = comando.comercianteId();
    Lancamento novo =
        Lancamento.registrar(
            ids.gerar(),
            new Lancamento.Novo(
                comercianteId,
                comando.tipo(),
                comando.valor(),
                comando.descricao(),
                comando.dataOcorrencia(),
                comando.identificadorExterno()),
            politica,
            clock);
    var resultado =
        execucaoIdempotente.executar(
            comercianteId,
            comando.chave(),
            RequestFingerprint.deRegistro(comando),
            novo.id(),
            () -> gravar(novo, comando.requestId()),
            existente -> carregar(comercianteId, existente));
    return new Resultado(resultado.valor(), resultado.replay());
  }

  /** Passo 4: lançamento e evento na transação aberta pela execução idempotente. */
  private Lancamento gravar(Lancamento lancamento, String requestId) {
    lancamentos.inserir(lancamento);
    LancamentoRegistradoV1 evento =
        LancamentoRegistradoV1.de(lancamento, ids.gerar(), clock.instant());
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

  /** Replay: a resposta é reconstruída do lançamento original (D-08). */
  private Lancamento carregar(UUID comercianteId, UUID lancamentoId) {
    return lancamentos
        .buscarPorId(comercianteId, lancamentoId)
        .orElseThrow(() -> new IllegalStateException("chave confirmada sem lançamento"));
  }
}
