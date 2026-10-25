package br.com.fluxocaixa.consolidado.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Predicate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cabeçalhos seguros em toda resposta da API, inclusive nas que nascem em filtros anteriores à
 * segurança (413, 405 do {@code OPTIONS}) e nos erros (Q-067, API8): {@code
 * X-Content-Type-Options}, {@code X-Frame-Options}, {@code Cache-Control: no-store}, {@code
 * Content-Security-Policy} e {@code Referrer-Policy}. Não envia HSTS (TLS termina na borda,
 * ADR-010) nem cabeçalhos CORS (CORS fechado). As páginas da documentação (só no profile {@code
 * local}) recebem a CSP que permite a própria Swagger UI (Q-078).
 */
public class CabecalhosSegurosFilter extends OncePerRequestFilter {

  static final String CSP_DA_API = "default-src 'none'; frame-ancestors 'none'";
  static final String CSP_DA_DOCUMENTACAO =
      "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline';"
          + " frame-ancestors 'none'";

  private final Predicate<HttpServletRequest> paginaDeDocumentacao;

  /**
   * @param paginaDeDocumentacao rotas da Swagger UI e do contrato; sem o profile {@code local}, não
   *     casa com nenhuma
   */
  public CabecalhosSegurosFilter(Predicate<HttpServletRequest> paginaDeDocumentacao) {
    this.paginaDeDocumentacao = paginaDeDocumentacao;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
      throws ServletException, IOException {
    resposta.setHeader("X-Content-Type-Options", "nosniff");
    resposta.setHeader("X-Frame-Options", "DENY");
    resposta.setHeader("Cache-Control", "no-store");
    resposta.setHeader("Referrer-Policy", "no-referrer");
    resposta.setHeader(
        "Content-Security-Policy",
        paginaDeDocumentacao.test(requisicao) ? CSP_DA_DOCUMENTACAO : CSP_DA_API);
    cadeia.doFilter(requisicao, resposta);
  }
}
