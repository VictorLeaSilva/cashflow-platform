package br.com.fluxocaixa.lancamentos.adapter.in.web;

import br.com.fluxocaixa.lancamentos.application.IdempotencyConflictException;
import br.com.fluxocaixa.lancamentos.application.IdempotencyKeyReuseException;
import br.com.fluxocaixa.lancamentos.application.IdentificadorExternoDuplicadoException;
import br.com.fluxocaixa.lancamentos.application.LancamentoJaEstornadoException;
import br.com.fluxocaixa.lancamentos.application.LancamentoNaoEncontradoException;
import br.com.fluxocaixa.lancamentos.domain.LancamentoInvalidoException;
import br.com.fluxocaixa.lancamentos.domain.LancamentoInvalidoException.ErroCampo;
import br.com.fluxocaixa.lancamentos.domain.RegraDeNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Erros da API em RFC 9457 com o catálogo da T-013: cada exceção vira um {@code type} estável, sem
 * <i>stack trace</i> nem mensagem interna (03 §5, §5.2; API8).
 */
@RestControllerAdvice
public class ProblemaHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemaHandler.class);
  private static final String REQUISICAO_INVALIDA = "requisicao-invalida";
  private static final String DETALHE_400 = "a requisição tem erros; veja errors[]";
  private static final Pattern DUPLICADA = Pattern.compile("Duplicate [A-Za-z ]+\"([^\"]+)\"");

  private final Problemas problemas;

  public ProblemaHandler(Problemas problemas) {
    this.problemas = problemas;
  }

  @ExceptionHandler(RequisicaoInvalidaException.class)
  ResponseEntity<Object> requisicaoInvalida(
      RequisicaoInvalidaException erro, HttpServletRequest requisicao) {
    return invalida(requisicao, erro.erros());
  }

  @ExceptionHandler(LancamentoInvalidoException.class)
  ResponseEntity<Object> lancamentoInvalido(
      LancamentoInvalidoException erro, HttpServletRequest requisicao) {
    return invalida(requisicao, erro.erros());
  }

  @ExceptionHandler(RegraDeNegocioException.class)
  ResponseEntity<Object> regraDeNegocio(
      RegraDeNegocioException erro, HttpServletRequest requisicao) {
    return problemas.resposta(
        requisicao, HttpStatus.UNPROCESSABLE_CONTENT, erro.codigo(), erro.getMessage());
  }

  @ExceptionHandler(IdempotencyKeyReuseException.class)
  ResponseEntity<Object> reusoDeChave(HttpServletRequest requisicao) {
    return problemas.resposta(
        requisicao,
        HttpStatus.UNPROCESSABLE_CONTENT,
        "idempotency-key-reuse",
        "a Idempotency-Key já foi usada com outro corpo");
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  ResponseEntity<Object> disputaDeChave(HttpServletRequest requisicao) {
    ResponseEntity<Object> resposta =
        problemas.resposta(
            requisicao,
            HttpStatus.CONFLICT,
            "idempotency-conflict",
            "outra requisição com a mesma Idempotency-Key está em processamento; repita");
    return ResponseEntity.status(resposta.getStatusCode())
        .headers(resposta.getHeaders())
        .header(HttpHeaders.RETRY_AFTER, "1")
        .body(resposta.getBody());
  }

  @ExceptionHandler(IdentificadorExternoDuplicadoException.class)
  ResponseEntity<Object> identificadorDuplicado(HttpServletRequest requisicao) {
    return problemas.resposta(
        requisicao,
        HttpStatus.CONFLICT,
        "identificador-externo-duplicado",
        "já existe um lançamento deste comerciante com este identificadorExterno");
  }

  /** Inexistente e de outro comerciante respondem igual, sem o id no detalhe (RF-010). */
  @ExceptionHandler(LancamentoNaoEncontradoException.class)
  ResponseEntity<Object> naoEncontrado(HttpServletRequest requisicao) {
    return problemas.resposta(
        requisicao,
        HttpStatus.NOT_FOUND,
        "lancamento-nao-encontrado",
        "lançamento não encontrado para este comerciante");
  }

  @ExceptionHandler(LancamentoJaEstornadoException.class)
  ResponseEntity<Object> jaEstornado(HttpServletRequest requisicao) {
    return problemas.resposta(
        requisicao,
        HttpStatus.CONFLICT,
        "lancamento-ja-estornado",
        "este lançamento já foi estornado; o estorno é único");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> inesperado(Exception erro, HttpServletRequest requisicao) {
    // Só as classes da cadeia de causas: a mensagem (ex.: erro do driver) pode trazer dados da
    // linha.
    LOG.error("erro inesperado ao processar a requisição: {}", classes(erro));
    return problemas.resposta(
        requisicao, HttpStatus.INTERNAL_SERVER_ERROR, "interno", "erro interno; tente novamente");
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException erro,
      HttpHeaders cabecalhos,
      HttpStatusCode status,
      WebRequest requisicao) {
    HttpServletRequest http = servlet(requisicao);
    if (causa(erro, LimiteDeCorpoFilter.CorpoMuitoGrandeException.class) != null) {
      return problemas.resposta(
          http,
          HttpStatus.CONTENT_TOO_LARGE,
          LimiteDeCorpoFilter.TIPO,
          LimiteDeCorpoFilter.DETALHE);
    }
    return invalida(http, List.of(erroDeLeitura(erro)));
  }

  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException erro,
      HttpHeaders cabecalhos,
      HttpStatusCode status,
      WebRequest requisicao) {
    return problemas.resposta(
        servlet(requisicao),
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "tipo-de-midia-nao-suportado",
        "envie o corpo como application/json");
  }

  /** O {@code Accept} não admite JSON: o problema é escrito sem negociação de conteúdo. */
  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
      HttpMediaTypeNotAcceptableException erro,
      HttpHeaders cabecalhos,
      HttpStatusCode status,
      WebRequest requisicao) {
    HttpServletResponse resposta =
        ((NativeWebRequest) requisicao).getNativeResponse(HttpServletResponse.class);
    if (resposta == null) {
      return problemas.resposta(
          servlet(requisicao),
          HttpStatus.NOT_ACCEPTABLE,
          "nao-aceitavel",
          "esta API responde application/json");
    }
    try {
      problemas.escrever(
          servlet(requisicao),
          resposta,
          HttpStatus.NOT_ACCEPTABLE,
          "nao-aceitavel",
          "esta API responde application/json");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return null;
  }

  /**
   * Demais erros do Spring MVC no mesmo formato; 404 e 405 com o {@code type} padrão (fora do
   * catálogo).
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception erro,
      Object corpo,
      HttpHeaders cabecalhos,
      HttpStatusCode status,
      WebRequest requisicao) {
    HttpStatus http = HttpStatus.valueOf(status.value());
    String tipo =
        switch (http) {
          case NOT_FOUND, METHOD_NOT_ALLOWED -> Problemas.TIPO_PADRAO;
          default -> http.is4xxClientError() ? REQUISICAO_INVALIDA : "interno";
        };
    ResponseEntity<Object> resposta =
        problemas.resposta(servlet(requisicao), http, tipo, http.getReasonPhrase());
    return ResponseEntity.status(http)
        .headers(cabecalhos)
        .contentType(resposta.getHeaders().getContentType())
        .body(resposta.getBody());
  }

  private ResponseEntity<Object> invalida(HttpServletRequest requisicao, List<ErroCampo> erros) {
    return problemas.resposta(
        requisicao, HttpStatus.BAD_REQUEST, REQUISICAO_INVALIDA, DETALHE_400, erros);
  }

  /** Erro de leitura do JSON traduzido para um item de {@code errors[]}. */
  private static ErroCampo erroDeLeitura(HttpMessageNotReadableException erro) {
    UnrecognizedPropertyException desconhecida = causa(erro, UnrecognizedPropertyException.class);
    if (desconhecida != null) {
      return new ErroCampo(
          desconhecida.getPropertyName(),
          "propriedade-desconhecida",
          "propriedade não prevista no contrato");
    }
    MismatchedInputException tipoErrado = causa(erro, MismatchedInputException.class);
    if (tipoErrado != null && !tipoErrado.getPath().isEmpty()) {
      return new ErroCampo(
          campo(tipoErrado), "tipo-invalido", "tipo JSON inválido; use texto (string)");
    }
    StreamReadException leitura = causa(erro, StreamReadException.class);
    if (leitura != null) {
      Matcher duplicada = DUPLICADA.matcher(String.valueOf(leitura.getOriginalMessage()));
      if (duplicada.find()) {
        return new ErroCampo(
            duplicada.group(1), "propriedade-duplicada", "propriedade repetida no corpo");
      }
    }
    return new ErroCampo("corpo", "json-malformado", "o corpo não é um JSON válido");
  }

  private static String campo(DatabindException erro) {
    return erro.getPath().getLast().getPropertyName();
  }

  private static String classes(Throwable erro) {
    StringBuilder cadeia = new StringBuilder();
    for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
      if (!cadeia.isEmpty()) {
        cadeia.append(" <- ");
      }
      cadeia.append(atual.getClass().getName());
    }
    return cadeia.toString();
  }

  private static <T extends Throwable> T causa(Throwable erro, Class<T> tipo) {
    for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
      if (tipo.isInstance(atual)) {
        return tipo.cast(atual);
      }
    }
    return null;
  }

  private static HttpServletRequest servlet(WebRequest requisicao) {
    return ((NativeWebRequest) requisicao).getNativeRequest(HttpServletRequest.class);
  }
}
