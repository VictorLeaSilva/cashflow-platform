package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.EntradaDeLancamento.erro;

import br.com.fluxocaixa.lancamentos.application.port.out.FiltroDeHistorico;
import br.com.fluxocaixa.lancamentos.domain.LancamentoInvalidoException.ErroCampo;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.util.MultiValueMap;

/**
 * Validação estrita da query das consultas (03 §5.2, RNF-013; Q-023..Q-026): parâmetro desconhecido
 * ou repetido é rejeitado, inclusive {@code comercianteId} (AC-010.2), e todos os erros voltam
 * juntos em {@code errors[]}.
 */
final class EntradaDeConsulta {

  static final int PAGE_PADRAO = 0;
  static final int SIZE_PADRAO = 20;
  static final int SIZE_MAXIMO = 100;

  private static final String DATA_INICIO = "dataInicio";
  private static final String DATA_FIM = "dataFim";
  private static final String TIPO = "tipo";
  private static final String PAGE = "page";
  private static final String SIZE = "size";
  private static final Set<String> PARAMETROS_DO_HISTORICO =
      Set.of(DATA_INICIO, DATA_FIM, TIPO, PAGE, SIZE);
  private static final String FORMATO_INVALIDO = "formato-invalido";
  private static final Pattern AAAA_MM_DD = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");

  private EntradaDeConsulta() {}

  /**
   * Id do detalhe; o detalhe não aceita parâmetros de query.
   *
   * @throws RequisicaoInvalidaException com todos os erros
   */
  static UUID id(String id, MultiValueMap<String, String> parametros) {
    List<ErroCampo> erros = new ArrayList<>();
    UUID valido = EntradaDeEstorno.id(id, erros);
    conferirNomes(parametros, Set.of(), erros);
    exigirSemErros(erros);
    return valido;
  }

  /**
   * Filtro do histórico, com os padrões de Q-024 para {@code page} e {@code size}.
   *
   * @throws RequisicaoInvalidaException com todos os erros
   */
  static FiltroDeHistorico filtro(UUID comercianteId, MultiValueMap<String, String> parametros) {
    List<ErroCampo> erros = new ArrayList<>();
    conferirNomes(parametros, PARAMETROS_DO_HISTORICO, erros);
    LocalDate inicio = data(DATA_INICIO, unico(parametros, DATA_INICIO), erros);
    LocalDate fim = data(DATA_FIM, unico(parametros, DATA_FIM), erros);
    if (inicio != null && fim != null && inicio.isAfter(fim)) {
      erros.add(
          erro(DATA_INICIO, "intervalo-invalido", "dataInicio não pode ser posterior a dataFim"));
    }
    TipoLancamento tipo = tipo(unico(parametros, TIPO), erros);
    int page = page(unico(parametros, PAGE), erros);
    int size = size(unico(parametros, SIZE), erros);
    exigirSemErros(erros);
    return new FiltroDeHistorico(comercianteId, inicio, fim, tipo, page, size);
  }

  /** Desconhecido → {@code parametro-desconhecido}; repetido → {@code parametro-repetido}. */
  private static void conferirNomes(
      MultiValueMap<String, String> parametros, Set<String> permitidos, List<ErroCampo> erros) {
    for (Map.Entry<String, List<String>> parametro : parametros.entrySet()) {
      String nome = parametro.getKey();
      if (!permitidos.contains(nome)) {
        erros.add(erro(nome, "parametro-desconhecido", "parâmetro não previsto no contrato"));
      } else if (parametro.getValue().size() > 1) {
        erros.add(erro(nome, "parametro-repetido", "informe " + nome + " uma única vez"));
      }
    }
  }

  /** Valor de um parâmetro permitido; ausente ou repetido (já acusado) vira {@code null}. */
  private static String unico(MultiValueMap<String, String> parametros, String nome) {
    List<String> valores = parametros.get(nome);
    return valores == null || valores.size() != 1 ? null : valores.getFirst();
  }

  private static LocalDate data(String campo, String valor, List<ErroCampo> erros) {
    if (valor == null) {
      return null;
    }
    // AAAA-MM-DD é exigido antes do parse: o ISO do JDK também aceita ano com sinal ou com mais de
    // 4 dígitos (N1 da T-023, RNF-013).
    if (AAAA_MM_DD.matcher(valor).matches()) {
      try {
        return LocalDate.parse(valor, DateTimeFormatter.ISO_LOCAL_DATE);
      } catch (DateTimeParseException e) {
        // data inexistente (2026-02-30): cai no erro abaixo
      }
    }
    erros.add(erro(campo, FORMATO_INVALIDO, campo + " deve ser uma data AAAA-MM-DD"));
    return null;
  }

  private static TipoLancamento tipo(String valor, List<ErroCampo> erros) {
    if (valor == null) {
      return null;
    }
    try {
      return TipoLancamento.valueOf(valor);
    } catch (IllegalArgumentException e) {
      erros.add(erro(TIPO, "valor-nao-permitido", "tipo deve ser CREDITO, DEBITO ou ESTORNO"));
      return null;
    }
  }

  /** Até {@link Integer#MAX_VALUE} (declarado no OpenAPI); acima disso, formato inválido. */
  private static int page(String valor, List<ErroCampo> erros) {
    if (valor == null) {
      return PAGE_PADRAO;
    }
    if (!soDigitos(valor) || acimaDe(valor, Integer.MAX_VALUE)) {
      erros.add(
          erro(PAGE, FORMATO_INVALIDO, "page deve ser um inteiro de 0 a " + Integer.MAX_VALUE));
      return PAGE_PADRAO;
    }
    return Integer.parseInt(valor);
  }

  /** Qualquer número acima de 100, por maior que seja, é {@code tamanho-maximo}. */
  private static int size(String valor, List<ErroCampo> erros) {
    if (valor == null) {
      return SIZE_PADRAO;
    }
    if (!soDigitos(valor) || new BigInteger(valor).signum() == 0) {
      erros.add(erro(SIZE, FORMATO_INVALIDO, "size deve ser um inteiro de 1 a " + SIZE_MAXIMO));
      return SIZE_PADRAO;
    }
    if (acimaDe(valor, SIZE_MAXIMO)) {
      erros.add(erro(SIZE, "tamanho-maximo", "size deve ser no máximo " + SIZE_MAXIMO));
      return SIZE_PADRAO;
    }
    return Integer.parseInt(valor);
  }

  /** Só dígitos: sem sinal, espaço nem separador. */
  private static boolean soDigitos(String valor) {
    return !valor.isEmpty() && valor.chars().allMatch(c -> c >= '0' && c <= '9');
  }

  /** Comparação exata, sem transbordo, de um texto só de dígitos. */
  private static boolean acimaDe(String digitos, long limite) {
    return new BigInteger(digitos).compareTo(BigInteger.valueOf(limite)) > 0;
  }

  private static void exigirSemErros(List<ErroCampo> erros) {
    if (!erros.isEmpty()) {
      throw new RequisicaoInvalidaException(erros);
    }
  }
}
