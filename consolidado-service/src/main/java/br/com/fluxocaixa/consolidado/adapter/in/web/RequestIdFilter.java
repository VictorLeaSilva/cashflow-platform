package br.com.fluxocaixa.consolidado.adapter.in.web;

import br.com.fluxocaixa.consolidado.application.port.out.IdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Primeiro filtro da cadeia (03 §5.2, RNF-012, Q-014): aceita o {@code X-Request-Id} válido ou gera
 * um pela porta {@link IdGenerator}; nunca rejeita a requisição por causa dele. O valor volta em
 * toda resposta, fica no MDC durante a requisição e segue para o comando (outbox).
 */
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String CABECALHO = "X-Request-Id";
  public static final String MDC_REQUEST_ID = "requestId";

  private static final String ATRIBUTO = RequestIdFilter.class.getName() + ".requestId";
  private static final int TAMANHO_MAXIMO = 64;
  private static final String PONTUACAO_PERMITIDA = "._-";

  private final IdGenerator ids;

  public RequestIdFilter(IdGenerator ids) {
    this.ids = ids;
  }

  /** Request ID da requisição corrente (definido por este filtro). */
  public static String requestIdDe(HttpServletRequest requisicao) {
    return (String) requisicao.getAttribute(ATRIBUTO);
  }

  /**
   * Cópia caractere a caractere do valor recebido se ele casar com {@code ^[A-Za-z0-9._-]{1,64}$};
   * senão, {@code null}. A cópia (e não o valor recebido) vai para o cabeçalho de resposta.
   */
  static String copiaValida(String recebido) {
    if (recebido == null || recebido.isEmpty() || recebido.length() > TAMANHO_MAXIMO) {
      return null;
    }
    StringBuilder copia = new StringBuilder(recebido.length());
    for (int i = 0; i < recebido.length(); i++) {
      char c = recebido.charAt(i);
      if (!permitido(c)) {
        return null;
      }
      copia.append(c);
    }
    return copia.toString();
  }

  private static boolean permitido(char c) {
    return (c >= 'A' && c <= 'Z')
        || (c >= 'a' && c <= 'z')
        || (c >= '0' && c <= '9')
        || PONTUACAO_PERMITIDA.indexOf(c) >= 0;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
      throws ServletException, IOException {
    String valido = copiaValida(requisicao.getHeader(CABECALHO));
    String requestId = valido != null ? valido : ids.gerar().toString();
    requisicao.setAttribute(ATRIBUTO, requestId);
    resposta.setHeader(CABECALHO, requestId);
    MDC.put(MDC_REQUEST_ID, requestId);
    try {
      cadeia.doFilter(requisicao, resposta);
    } finally {
      MDC.remove(MDC_REQUEST_ID);
    }
  }
}
