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
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
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
  private final Supplier<RequestMappingHandlerMapping> mapeamento;

  /**
   * @param mapeamento mapeamento dos controllers, usado quando a requisição não chegou a um handler
   *     (401, 403, 405, 406, 413, 415) para ainda assim registrar a rota padrão
   */
  public LogDeAcessoFilter(Clock clock, Supplier<RequestMappingHandlerMapping> mapeamento) {
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

  /**
   * Casa só o caminho com os padrões dos controllers (sem método, Content-Type nem Accept), para
   * que 401, 403, 405, 406, 413 e 415 numa rota existente registrem a rota padrão; sem padrão
   * compatível, a rota é desconhecida.
   */
  private String resolver(HttpServletRequest requisicao) {
    if (!ServletRequestPathUtils.hasParsedRequestPath(requisicao)) {
      ServletRequestPathUtils.parseAndCache(requisicao);
    }
    for (RequestMappingInfo info : mapeamento.get().getHandlerMethods().keySet()) {
      PathPatternsRequestCondition caminhos = info.getPathPatternsCondition();
      PathPatternsRequestCondition casou =
          caminhos != null ? caminhos.getMatchingCondition(requisicao) : null;
      if (casou != null) {
        return casou.getFirstPattern().getPatternString();
      }
    }
    return null;
  }
}
