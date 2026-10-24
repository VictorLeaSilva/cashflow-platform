package br.com.fluxocaixa.consolidado.adapter.in.web;

import br.com.fluxocaixa.consolidado.application.ConsultarConsolidado;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.MultiValueMap;

/**
 * Validação estrita da query do consolidado (03 §5.2, RNF-013; Q-052): {@code dataInicio} e {@code
 * dataFim} obrigatórios, em data ISO estrita, no máximo 366 dias contando os dois extremos;
 * parâmetro desconhecido ou repetido é rejeitado, inclusive {@code comercianteId} (AC-010.2). Todos
 * os erros voltam juntos em {@code errors[]}. Nada é corrigido em silêncio.
 */
final class EntradaDeConsolidado {

  private static final String DATA_INICIO = "dataInicio";
  private static final String DATA_FIM = "dataFim";
  private static final Set<String> PARAMETROS_DO_PERIODO = Set.of(DATA_INICIO, DATA_FIM);
  private static final String FORMATO_INVALIDO = "formato-invalido";

  private EntradaDeConsolidado() {}

  /** Período validado do {@code GET /consolidado}. */
  record Periodo(LocalDate inicio, LocalDate fim) {}

  /**
   * @throws RequisicaoInvalidaException com todos os erros
   */
  static Periodo periodo(MultiValueMap<String, String> parametros) {
    List<ErroDeCampo> erros = new ArrayList<>();
    conferirNomes(parametros, PARAMETROS_DO_PERIODO, erros);
    LocalDate inicio = obrigatoria(DATA_INICIO, parametros, erros);
    LocalDate fim = obrigatoria(DATA_FIM, parametros, erros);
    if (inicio != null && fim != null) {
      conferirPeriodo(inicio, fim, erros);
    }
    exigirSemErros(erros);
    return new Periodo(inicio, fim);
  }

  /**
   * Data do caminho; o dia não aceita parâmetros de query.
   *
   * @throws RequisicaoInvalidaException com todos os erros
   */
  static LocalDate data(String data, MultiValueMap<String, String> parametros) {
    List<ErroDeCampo> erros = new ArrayList<>();
    LocalDate valida = data("data", data, erros);
    conferirNomes(parametros, Set.of(), erros);
    exigirSemErros(erros);
    return valida;
  }

  private static void conferirPeriodo(LocalDate inicio, LocalDate fim, List<ErroDeCampo> erros) {
    if (inicio.isAfter(fim)) {
      erros.add(
          erro(DATA_INICIO, "intervalo-invalido", "dataInicio não pode ser posterior a dataFim"));
    } else if (fim.toEpochDay() - inicio.toEpochDay() + 1 > ConsultarConsolidado.MAXIMO_DE_DIAS) {
      erros.add(
          erro(
              DATA_FIM,
              "intervalo-maximo",
              "o período tem no máximo "
                  + ConsultarConsolidado.MAXIMO_DE_DIAS
                  + " dias, contando os dois extremos"));
    }
  }

  /** Desconhecido → {@code parametro-desconhecido}; repetido → {@code parametro-repetido}. */
  private static void conferirNomes(
      MultiValueMap<String, String> parametros, Set<String> permitidos, List<ErroDeCampo> erros) {
    for (Map.Entry<String, List<String>> parametro : parametros.entrySet()) {
      String nome = parametro.getKey();
      if (!permitidos.contains(nome)) {
        erros.add(erro(nome, "parametro-desconhecido", "parâmetro não previsto no contrato"));
      } else if (parametro.getValue().size() > 1) {
        erros.add(erro(nome, "parametro-repetido", "informe " + nome + " uma única vez"));
      }
    }
  }

  /** Ausente → {@code obrigatorio}; repetido já foi acusado e vira {@code null}. */
  private static LocalDate obrigatoria(
      String nome, MultiValueMap<String, String> parametros, List<ErroDeCampo> erros) {
    List<String> valores = parametros.get(nome);
    if (valores == null || valores.isEmpty()) {
      erros.add(erro(nome, "obrigatorio", nome + " é obrigatório"));
      return null;
    }
    return valores.size() == 1 ? data(nome, valores.getFirst(), erros) : null;
  }

  /** ISO estrita: {@code 2026-02-30} e {@code 2026-10-2} são rejeitadas, nunca ajustadas. */
  private static LocalDate data(String campo, String valor, List<ErroDeCampo> erros) {
    try {
      return LocalDate.parse(valor, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (DateTimeParseException e) {
      erros.add(erro(campo, FORMATO_INVALIDO, campo + " deve ser uma data AAAA-MM-DD"));
      return null;
    }
  }

  private static ErroDeCampo erro(String campo, String codigo, String mensagem) {
    return new ErroDeCampo(campo, codigo, mensagem);
  }

  private static void exigirSemErros(List<ErroDeCampo> erros) {
    if (!erros.isEmpty()) {
      throw new RequisicaoInvalidaException(erros);
    }
  }
}
