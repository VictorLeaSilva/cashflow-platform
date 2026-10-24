package br.com.fluxocaixa.consolidado.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Corpo acima de 16 KiB → 413 (03 §5.2, API4). Com {@code Content-Length} declarado, rejeita antes
 * de ler; sem ele (<i>chunked</i>), conta os bytes lidos e interrompe a leitura ao passar do
 * limite.
 */
public class LimiteDeCorpoFilter extends OncePerRequestFilter {

  static final int LIMITE_BYTES = 16 * 1024;
  static final String TIPO = "payload-muito-grande";
  static final String DETALHE = "o corpo da requisição passa do limite de 16 KiB";

  private final Problemas problemas;

  public LimiteDeCorpoFilter(Problemas problemas) {
    this.problemas = problemas;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
      throws ServletException, IOException {
    if (requisicao.getContentLengthLong() > LIMITE_BYTES) {
      problemas.escrever(requisicao, resposta, HttpStatus.CONTENT_TOO_LARGE, TIPO, DETALHE);
      return;
    }
    cadeia.doFilter(new CorpoLimitado(requisicao), resposta);
  }

  /** Leitura do corpo passou do limite (corpo sem tamanho declarado). */
  static final class CorpoMuitoGrandeException extends IOException {

    private static final long serialVersionUID = 1L;

    CorpoMuitoGrandeException() {
      super(DETALHE);
    }
  }

  /** Conta os bytes lidos do corpo; a contagem é da requisição, não de cada stream devolvido. */
  private static final class CorpoLimitado extends HttpServletRequestWrapper {

    private long lidos;

    CorpoLimitado(HttpServletRequest requisicao) {
      super(requisicao);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      return new Contador(super.getInputStream());
    }

    private void contar(int bytes) throws CorpoMuitoGrandeException {
      lidos += bytes;
      if (lidos > LIMITE_BYTES) {
        throw new CorpoMuitoGrandeException();
      }
    }

    private final class Contador extends ServletInputStream {

      private final ServletInputStream original;

      Contador(ServletInputStream original) {
        this.original = original;
      }

      @Override
      public int read() throws IOException {
        int lido = original.read();
        if (lido >= 0) {
          contar(1);
        }
        return lido;
      }

      @Override
      public int read(byte[] destino, int inicio, int tamanho) throws IOException {
        int lidosAgora = original.read(destino, inicio, tamanho);
        if (lidosAgora > 0) {
          contar(lidosAgora);
        }
        return lidosAgora;
      }

      @Override
      public boolean isFinished() {
        return original.isFinished();
      }

      @Override
      public boolean isReady() {
        return original.isReady();
      }

      @Override
      public void setReadListener(ReadListener ouvinte) {
        original.setReadListener(ouvinte);
      }
    }
  }
}
