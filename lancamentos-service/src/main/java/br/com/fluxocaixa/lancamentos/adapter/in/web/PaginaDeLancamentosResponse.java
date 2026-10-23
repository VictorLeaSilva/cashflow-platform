package br.com.fluxocaixa.lancamentos.adapter.in.web;

import br.com.fluxocaixa.lancamentos.application.PaginaDeLancamentos;
import java.util.List;

/**
 * Página do histórico na resposta (03 §5.1): {@code { itens, page, size, total }}, com cada item na
 * mesma representação do 201.
 */
public record PaginaDeLancamentosResponse(
    List<LancamentoResponse> itens, int page, int size, long total) {

  public PaginaDeLancamentosResponse {
    itens = List.copyOf(itens);
  }

  static PaginaDeLancamentosResponse de(PaginaDeLancamentos pagina) {
    return new PaginaDeLancamentosResponse(
        pagina.itens().stream().map(LancamentoResponse::de).toList(),
        pagina.page(),
        pagina.size(),
        pagina.total());
  }

  /** Sem os itens (sem payload financeiro em log). */
  @Override
  public String toString() {
    return "PaginaDeLancamentosResponse[page=" + page + ", size=" + size + ", total=" + total + "]";
  }
}
