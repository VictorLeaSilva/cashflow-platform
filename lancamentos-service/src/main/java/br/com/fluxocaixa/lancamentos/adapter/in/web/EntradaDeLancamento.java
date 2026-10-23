package br.com.fluxocaixa.lancamentos.adapter.in.web;

import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.LancamentoInvalidoException;
import br.com.fluxocaixa.lancamentos.domain.LancamentoInvalidoException.ErroCampo;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validação estrita da entrada HTTP (03 §5.2, RNF-013): rejeita, nunca corrige. Acumula os erros de
 * cabeçalho e de campo, inclusive os do domínio ({@link Lancamento#validarCampos}), e monta o
 * comando com o texto livre já em NFC.
 */
final class EntradaDeLancamento {

  static final String IDEMPOTENCY_KEY = "Idempotency-Key";

  /** Precisão do TIMESTAMPTZ (Q-016). */
  private static final int MAX_CASAS_NOS_SEGUNDOS = 6;

  private static final Pattern CHAVE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
  private static final Pattern CONTROLE = Pattern.compile("\\p{Cc}");

  private EntradaDeLancamento() {}

  /**
   * @throws RequisicaoInvalidaException com todos os erros encontrados
   */
  static RegistrarLancamentoCommand comando(
      UUID comercianteId,
      String chave,
      RegistrarLancamentoRequest corpo,
      String requestId,
      PoliticaLancamento politica) {
    List<ErroCampo> erros = new ArrayList<>();
    validarChave(chave, erros);
    RegistrarLancamentoRequest dados =
        corpo != null ? corpo : new RegistrarLancamentoRequest(null, null, null, null, null);
    TipoLancamento tipo = tipo(dados.tipo(), erros);
    BigDecimal valor = valor(dados.valor(), erros);
    Instant ocorrencia = ocorrencia(dados.dataOcorrencia(), erros);
    String descricao = texto("descricao", dados.descricao(), erros);
    String identificador = texto("identificadorExterno", dados.identificadorExterno(), erros);
    validarNoDominio(
        new Lancamento.Novo(comercianteId, tipo, valor, descricao, ocorrencia, identificador),
        erros,
        politica);
    if (!erros.isEmpty()) {
      throw new RequisicaoInvalidaException(erros);
    }
    return new RegistrarLancamentoCommand(
        comercianteId, chave, tipo, valor, descricao, ocorrencia, identificador, requestId);
  }

  private static void validarChave(String chave, List<ErroCampo> erros) {
    if (chave == null) {
      erros.add(erro(IDEMPOTENCY_KEY, "obrigatorio", "o cabeçalho Idempotency-Key é obrigatório"));
    } else if (!CHAVE.matcher(chave).matches()) {
      erros.add(
          erro(
              IDEMPOTENCY_KEY,
              "formato-invalido",
              "Idempotency-Key deve ter de 1 a 64 caracteres entre A-Z, a-z, 0-9, '.', '_' e '-'"));
    }
  }

  /** {@code ESTORNO} e ausência ficam para o domínio, que tem os códigos deles. */
  private static TipoLancamento tipo(String tipo, List<ErroCampo> erros) {
    if (tipo == null) {
      return null;
    }
    try {
      return TipoLancamento.valueOf(tipo);
    } catch (IllegalArgumentException e) {
      erros.add(erro("tipo", "valor-nao-permitido", "tipo deve ser CREDITO ou DEBITO"));
      return null;
    }
  }

  private static BigDecimal valor(String valor, List<ErroCampo> erros) {
    if (valor == null) {
      return null;
    }
    if (!decimalEmTexto(valor)) {
      erros.add(
          erro("valor", "formato-invalido", "valor deve ser um decimal em texto, como \"100.00\""));
      return null;
    }
    return new BigDecimal(valor);
  }

  /** {@code ^[0-9]+(\.[0-9]+)?$}, verificado sem expressão regular (sem risco de ReDoS). */
  private static boolean decimalEmTexto(String valor) {
    int ponto = valor.indexOf('.');
    String inteira = ponto < 0 ? valor : valor.substring(0, ponto);
    String fracao = ponto < 0 ? null : valor.substring(ponto + 1);
    return apenasDigitos(inteira) && (fracao == null || apenasDigitos(fracao));
  }

  private static boolean apenasDigitos(String texto) {
    return !texto.isEmpty() && texto.chars().allMatch(c -> c >= '0' && c <= '9');
  }

  /**
   * Casas da fração de segundo de um instante ISO-8601 já validado (Q-016): o {@code TIMESTAMPTZ}
   * guarda microssegundos, e arredondar em silêncio mudaria o dado e o replay.
   */
  private static int casasNosSegundos(String data) {
    int ponto = data.indexOf('.', data.indexOf('T'));
    if (ponto < 0) {
      return 0;
    }
    int fim = ponto + 1;
    while (fim < data.length() && Character.isDigit(data.charAt(fim))) {
      fim++;
    }
    return fim - ponto - 1;
  }

  private static Instant ocorrencia(String data, List<ErroCampo> erros) {
    if (data == null) {
      return null;
    }
    try {
      Instant instante =
          OffsetDateTime.parse(data, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
      if (casasNosSegundos(data) > MAX_CASAS_NOS_SEGUNDOS) {
        erros.add(
            erro(
                "dataOcorrencia",
                "formato-invalido",
                "dataOcorrencia aceita no máximo 6 casas decimais nos segundos (microssegundos)"));
        return null;
      }
      return instante;
    } catch (DateTimeParseException e) {
      erros.add(
          erro(
              "dataOcorrencia",
              "formato-invalido",
              "dataOcorrencia deve ser ISO-8601 com offset ou Z, como 2026-10-21T14:03:11-03:00"));
      return null;
    }
  }

  /** Controle, espaço nas pontas e vazio são rejeitados; o resto segue em NFC. */
  private static String texto(String campo, String texto, List<ErroCampo> erros) {
    if (texto == null) {
      return null;
    }
    if (texto.isEmpty()) {
      erros.add(erro(campo, "vazio", campo + " vazio: omita o campo para não informá-lo"));
      return null;
    }
    if (CONTROLE.matcher(texto).find()) {
      erros.add(
          erro(campo, "caractere-de-controle", campo + " não pode ter caracteres de controle"));
      return null;
    }
    if (!texto.equals(texto.strip())) {
      erros.add(
          erro(campo, "espacos-nas-pontas", campo + " não pode começar nem terminar com espaço"));
      return null;
    }
    return Normalizer.normalize(texto, Normalizer.Form.NFC);
  }

  /**
   * Regras de campo do domínio (obrigatórios, valor, tamanhos). Um campo que a web já rejeitou
   * chega nulo ao domínio; o erro de "obrigatório" dele não é repetido.
   */
  private static void validarNoDominio(
      Lancamento.Novo novo, List<ErroCampo> erros, PoliticaLancamento politica) {
    try {
      Lancamento.validarCampos(novo, politica);
    } catch (LancamentoInvalidoException e) {
      for (ErroCampo erro : e.erros()) {
        if (erros.stream().noneMatch(outro -> outro.campo().equals(erro.campo()))) {
          erros.add(erro);
        }
      }
    }
  }

  private static ErroCampo erro(String campo, String codigo, String mensagem) {
    return new ErroCampo(campo, codigo, mensagem);
  }
}
