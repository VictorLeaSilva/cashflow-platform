package br.com.fluxocaixa.lancamentos.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

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

  public LogDeAcessoFilter(Clock clock) {
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
      throws ServletException, IOException {
    long inicio = clock.millis();
    try {
      cadeia.doFilter(requisicao, resposta);
    } finally {
      Object rota = requisicao.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      ACESSO
          .atInfo()
          .addKeyValue("metodo", requisicao.getMethod())
          .addKeyValue("rota", rota != null ? rota.toString() : ROTA_DESCONHECIDA)
          .addKeyValue("status", resposta.getStatus())
          .addKeyValue("duracaoMs", clock.millis() - inicio)
          .log("acesso");
    }
  }
}
