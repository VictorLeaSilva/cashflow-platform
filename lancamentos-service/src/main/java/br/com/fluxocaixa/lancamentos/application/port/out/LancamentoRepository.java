package br.com.fluxocaixa.lancamentos.application.port.out;

import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de saída para persistir e ler lançamentos imutáveis (sem alteração nem exclusão, A8). */
public interface LancamentoRepository {

  /** Insere um lançamento. */
  void inserir(Lancamento lancamento);

  /** Busca pelo id, sempre restrito ao comerciante dono (RF-010): de outro comerciante, vazio. */
  Optional<Lancamento> buscarPorId(UUID comercianteId, UUID id);

  /**
   * Página do histórico do comerciante do filtro, por {@code dataReferencia} decrescente e {@code
   * id} crescente (Q-024).
   */
  List<Lancamento> listar(FiltroDeHistorico filtro);

  /** Quantidade de lançamentos que atendem ao filtro (sem paginação). */
  long contar(FiltroDeHistorico filtro);
}
