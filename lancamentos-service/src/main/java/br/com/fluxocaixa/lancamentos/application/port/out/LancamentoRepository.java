package br.com.fluxocaixa.lancamentos.application.port.out;

import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import java.util.Optional;
import java.util.UUID;

/** Porta de saída para persistir e ler lançamentos imutáveis (sem alteração nem exclusão, A8). */
public interface LancamentoRepository {

  /**
   * Insere um lançamento. Violações de unicidade ({@code ux_estorno_unico}, {@code
   * ux_identificador_externo}) chegam como exceção de chave duplicada do Spring.
   */
  void inserir(Lancamento lancamento);

  /** Busca pelo id, sempre restrito ao comerciante dono (RF-010): de outro comerciante, vazio. */
  Optional<Lancamento> buscarPorId(UUID comercianteId, UUID id);
}
