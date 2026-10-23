package br.com.fluxocaixa.lancamentos.adapter.in.web;

/**
 * Corpo de {@code POST /api/v1/lancamentos} (03 §5). Tudo chega como texto e é validado em {@link
 * EntradaDeLancamento}: {@code valor} é string decimal (constituição §4) e {@code dataOcorrencia} é
 * ISO-8601 com offset. Propriedades desconhecidas (inclusive {@code comercianteId}) são rejeitadas
 * pelo mapper.
 */
public record RegistrarLancamentoRequest(
    String tipo,
    String valor,
    String descricao,
    String dataOcorrencia,
    String identificadorExterno) {

  /** Sem payload financeiro em log. */
  @Override
  public String toString() {
    return "RegistrarLancamentoRequest[tipo=" + tipo + "]";
  }
}
