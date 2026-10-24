package br.com.fluxocaixa.consolidado.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Erros da API em RFC 9457 com o catálogo do Lançamentos (03 §5, §5.2; API8), duplicado de
 * propósito (01 §3). A consulta não tem corpo, então só há 400, 406, 500 e os erros padrão do
 * Spring MVC (404, 405), sem <i>stack trace</i> nem mensagem interna.
 */
@RestControllerAdvice
public class ProblemaHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemaHandler.class);
  private static final String REQUISICAO_INVALIDA = "requisicao-invalida";
  private static final String DETALHE_400 = "a requisição tem erros; veja errors[]";
  private static final String NAO_ACEITAVEL = "nao-aceitavel";
  private static final String DETALHE_406 = "esta API responde application/json";

  private final Problemas problemas;

  public ProblemaHandler(Problemas problemas) {
    this.problemas = problemas;
  }

  @ExceptionHandler(RequisicaoInvalidaException.class)
  ResponseEntity<Object> requisicaoInvalida(
      RequisicaoInvalidaException erro, HttpServletRequest requisicao) {
    return problemas.resposta(
        requisicao, HttpStatus.BAD_REQUEST, REQUISICAO_INVALIDA, DETALHE_400, erro.erros());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> inesperado(Exception erro, HttpServletRequest requisicao) {
    // Só as classes da cadeia de causas: a mensagem (ex.: erro do driver) pode trazer dados.
    LOG.error("erro inesperado ao processar a requisição: {}", classes(erro));
    return problemas.resposta(
        requisicao, HttpStatus.INTERNAL_SERVER_ERROR, "interno", "erro interno; tente novamente");
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
          servlet(requisicao), HttpStatus.NOT_ACCEPTABLE, NAO_ACEITAVEL, DETALHE_406);
    }
    try {
      problemas.escrever(
          servlet(requisicao), resposta, HttpStatus.NOT_ACCEPTABLE, NAO_ACEITAVEL, DETALHE_406);
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

  private static HttpServletRequest servlet(WebRequest requisicao) {
    return ((NativeWebRequest) requisicao).getNativeRequest(HttpServletRequest.class);
  }
}
