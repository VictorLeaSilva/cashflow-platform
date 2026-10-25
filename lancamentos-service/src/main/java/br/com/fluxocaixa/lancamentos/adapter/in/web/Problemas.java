package br.com.fluxocaixa.lancamentos.adapter.in.web;

import br.com.fluxocaixa.lancamentos.domain.LancamentoInvalidoException.ErroCampo;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Monta e escreve erros RFC 9457 (03 §5, §5.2): {@code type} do catálogo, {@code title}, {@code
 * status}, {@code detail}, {@code instance}, {@code errors[]} quando houver, {@code requestId},
 * {@code traceId} (com tracing ativo) e {@code timestamp} pelo {@code Clock}. Nunca leva <i>stack
 * trace</i> nem mensagem interna.
 */
@Component
public class Problemas {

  static final String BASE = "/erros/";

  /** {@code type} padrão do RFC 9457, usado nos erros fora do catálogo (404, 405). */
  static final String TIPO_PADRAO = "about:blank";

  /** Corpo de mapas e textos: não depende da configuração JSON da API. */
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Clock clock;
  private final Tracer tracer;

  public Problemas(Clock clock, Tracer tracer) {
    this.clock = clock;
    this.tracer = tracer;
  }

  /** {@code traceId} da requisição corrente, ou {@code null} sem trace ativo (03 §5.2). */
  private String traceIdAtual() {
    Span atual = tracer.currentSpan();
    return atual == null ? null : atual.context().traceId();
  }

  /** Corpo do problema, na ordem dos membros do RFC 9457 seguida das extensões. */
  Map<String, Object> corpo(
      HttpServletRequest requisicao,
      HttpStatus status,
      String tipo,
      String detalhe,
      List<ErroCampo> erros) {
    Map<String, Object> corpo = new LinkedHashMap<>();
    corpo.put("type", tipo.startsWith(TIPO_PADRAO) ? tipo : BASE + tipo);
    corpo.put("title", status.getReasonPhrase());
    corpo.put("status", status.value());
    corpo.put("detail", detalhe);
    corpo.put("instance", requisicao.getRequestURI());
    if (!erros.isEmpty()) {
      corpo.put(
          "errors",
          erros.stream()
              .map(
                  erro ->
                      Map.of(
                          "campo",
                          erro.campo(),
                          "codigo",
                          erro.codigo(),
                          "mensagem",
                          erro.mensagem()))
              .toList());
    }
    corpo.put("requestId", RequestIdFilter.requestIdDe(requisicao));
    String traceId = traceIdAtual();
    if (traceId != null) {
      corpo.put("traceId", traceId);
    }
    corpo.put("timestamp", clock.instant().toString());
    return corpo;
  }

  ResponseEntity<Object> resposta(
      HttpServletRequest requisicao, HttpStatus status, String tipo, String detalhe) {
    return resposta(requisicao, status, tipo, detalhe, List.of());
  }

  ResponseEntity<Object> resposta(
      HttpServletRequest requisicao,
      HttpStatus status,
      String tipo,
      String detalhe,
      List<ErroCampo> erros) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(corpo(requisicao, status, tipo, detalhe, erros));
  }

  /**
   * Escreve o problema direto na resposta, sem negociação de conteúdo: usado por filtros, pela
   * segurança e no 406 (quando o {@code Accept} não admitiria nenhum corpo).
   */
  public void escrever(
      HttpServletRequest requisicao,
      HttpServletResponse resposta,
      HttpStatus status,
      String tipo,
      String detalhe)
      throws IOException {
    resposta.setStatus(status.value());
    resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    resposta.setCharacterEncoding("UTF-8");
    JSON.writeValue(
        resposta.getOutputStream(), corpo(requisicao, status, tipo, detalhe, List.of()));
  }
}
