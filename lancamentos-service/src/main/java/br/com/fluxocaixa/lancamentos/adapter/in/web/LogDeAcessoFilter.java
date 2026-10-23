package br.com.fluxocaixa.lancamentos.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Uma linha de log de acesso por requisição (03 §5.2): {@code requestId} (MDC), método, rota padrão
 * (nunca a URL com IDs ou query), status e duração. Sem corpo, token, {@code Idempotency-Key} nem
 * comerciante.
 */
public class LogDeAcessoFilter extends OncePerRequestFilter {

  static final String LOGGER = "br.com.fluxocaixa.lancamentos.acesso";
  static final String ROTA_DESCONHECIDA = "desconhecida";

  private static final Logger ACESSO = LoggerFactory.getLogger(LOGGER);

  private final Clock clock;
  private final Supplier<HandlerMapping> mapeamento;

  /**
   * @param mapeamento mapeamento dos controllers, usado quando a requisição foi respondida antes de
   *     chegar ao Spring MVC (401, 403, 413) para ainda assim registrar a rota padrão
   */
  public LogDeAcessoFilter(Clock clock, Supplier<HandlerMapping> mapeamento) {
    this.clock = clock;
    this.mapeamento = mapeamento;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
      throws ServletException, IOException {
    long inicio = clock.millis();
    try {
      cadeia.doFilter(requisicao, resposta);
    } finally {
      ACESSO
          .atInfo()
          .addKeyValue("metodo", requisicao.getMethod())
          .addKeyValue("rota", rota(requisicao))
          .addKeyValue("status", resposta.getStatus())
          .addKeyValue("duracaoMs", clock.millis() - inicio)
          .log("acesso");
    }
  }

  private String rota(HttpServletRequest requisicao) {
    Object rota = requisicao.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (rota == null) {
      rota = resolver(requisicao);
    }
    return rota != null ? rota.toString() : ROTA_DESCONHECIDA;
  }

  /** Pergunta ao mapeamento qual handler atenderia; sem handler, a rota é desconhecida. */
  private Object resolver(HttpServletRequest requisicao) {
    try {
      if (!ServletRequestPathUtils.hasParsedRequestPath(requisicao)) {
        ServletRequestPathUtils.parseAndCache(requisicao);
      }
      return mapeamento.get().getHandler(requisicao) != null
          ? requisicao.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
          : null;
    } catch (Exception semHandler) {
      // Ex.: Content-Type sem handler compatível; o acesso ainda é registrado.
      return null;
    }
  }
}
