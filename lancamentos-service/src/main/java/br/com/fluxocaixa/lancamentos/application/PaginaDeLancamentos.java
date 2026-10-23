package br.com.fluxocaixa.lancamentos.application;

import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import java.util.List;

/**
 * Uma página do histórico (03 §5.1): os itens, a página e o tamanho aplicados e o total que atende
 * aos filtros. O total vem de uma consulta à parte e pode diferir em alguns itens sob escrita
 * concorrente (T-015).
 */
public record PaginaDeLancamentos(List<Lancamento> itens, int page, int size, long total) {

  public PaginaDeLancamentos {
    itens = List.copyOf(itens);
  }
}
