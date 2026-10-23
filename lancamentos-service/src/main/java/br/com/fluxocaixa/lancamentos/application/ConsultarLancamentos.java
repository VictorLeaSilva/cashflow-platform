package br.com.fluxocaixa.lancamentos.application;

import br.com.fluxocaixa.lancamentos.application.port.out.FiltroDeHistorico;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import java.util.UUID;

/**
 * Consultas de lançamentos (RF-004): o detalhe e o histórico paginado, sempre restritos ao
 * comerciante do token (RF-010). Só leitura: sem idempotência nem outbox.
 */
public class ConsultarLancamentos {

  private final LancamentoRepository lancamentos;

  public ConsultarLancamentos(LancamentoRepository lancamentos) {
    this.lancamentos = lancamentos;
  }

  /**
   * @throws LancamentoNaoEncontradoException inexistente ou de outro comerciante (indistinguíveis)
   */
  public Lancamento detalhar(UUID comercianteId, UUID id) {
    return lancamentos
        .buscarPorId(comercianteId, id)
        .orElseThrow(LancamentoNaoEncontradoException::new);
  }

  /**
   * Página e total em duas consultas independentes: sob escrita concorrente, o total pode diferir
   * em alguns itens do que a página mostra (T-015).
   */
  public PaginaDeLancamentos listar(FiltroDeHistorico filtro) {
    return new PaginaDeLancamentos(
        lancamentos.listar(filtro), filtro.page(), filtro.size(), lancamentos.contar(filtro));
  }
}
