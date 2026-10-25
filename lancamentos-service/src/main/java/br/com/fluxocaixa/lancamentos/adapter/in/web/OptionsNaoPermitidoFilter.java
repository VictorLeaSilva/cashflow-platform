package br.com.fluxocaixa.lancamentos.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code OPTIONS} em {@code /api/v1/**} → 405 em {@code problem+json} (Q-067, Q-075): o Spring MVC
 * responderia sozinho (200 com {@code Allow}, ou 403 num <i>preflight</i> CORS), então o 405 vem
 * antes. Coerente com o inventário estrito (API9) e com o CORS fechado. Não exige token: o
 * <i>preflight</i> nunca leva credenciais e a resposta não expõe dado algum.
 */
public class OptionsNaoPermitidoFilter extends OncePerRequestFilter {

  static final String TIPO = "metodo-nao-permitido";
  static final String PREFIXO_DA_API = "/api/v1/";

  private final Problemas problemas;

  public OptionsNaoPermitidoFilter(Problemas problemas) {
    this.problemas = problemas;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
      throws ServletException, IOException {
    if (HttpMethod.OPTIONS.matches(requisicao.getMethod())
        && requisicao.getRequestURI().startsWith(PREFIXO_DA_API)) {
      problemas.escrever(
          requisicao,
          resposta,
          HttpStatus.METHOD_NOT_ALLOWED,
          TIPO,
          "OPTIONS não é permitido: a API não responde a preflight nem expõe CORS");
      return;
    }
    cadeia.doFilter(requisicao, resposta);
  }
}
