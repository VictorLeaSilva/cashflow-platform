package br.com.fluxocaixa.lancamentos.adapter.in.web;

import br.com.fluxocaixa.lancamentos.application.EstornarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.domain.LancamentoInvalidoException.ErroCampo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectReader;

/**
 * Validação estrita da entrada do estorno (03 §5.2, RNF-013; Q-017, Q-018): rejeita, nunca corrige.
 * O corpo é opcional; quando presente, exige JSON (415) e só o objeto vazio (400). Os erros de
 * cabeçalho e de caminho voltam juntos em {@code errors[]}.
 */
final class EntradaDeEstorno {

  /** Forma canônica: 8-4-4-4-12 hexadecimais (Q-018). */
  private static final int TAMANHO_UUID = 36;

  private EntradaDeEstorno() {}

  /**
   * Corpo, quando presente: {@code Content-Type} JSON e o objeto vazio.
   *
   * @throws HttpMediaTypeNotSupportedException corpo com {@code Content-Type} que não é JSON (415)
   * @throws HttpMessageNotReadableException JSON malformado ou com propriedade (400)
   * @throws RequisicaoInvalidaException JSON válido que não é um objeto (400)
   */
  static void validarCorpo(byte[] corpo, HttpServletRequest requisicao, ObjectReader leitor)
      throws HttpMediaTypeNotSupportedException {
    if (corpo == null || corpo.length == 0) {
      return;
    }
    if (!ehJson(requisicao.getContentType())) {
      throw new HttpMediaTypeNotSupportedException(
          "envie o corpo como application/json ou não envie corpo");
    }
    EstornarLancamentoRequest lido;
    try {
      lido = leitor.readValue(corpo);
    } catch (JacksonException e) {
      throw new HttpMessageNotReadableException(
          "corpo do estorno inválido", e, new ServletServerHttpRequest(requisicao));
    }
    if (lido == null) {
      throw new RequisicaoInvalidaException(
          List.of(
              EntradaDeLancamento.erro(
                  "corpo",
                  "json-malformado",
                  "o corpo, quando enviado, deve ser o objeto vazio {}")));
    }
  }

  /**
   * @throws RequisicaoInvalidaException com todos os erros de cabeçalho e de caminho
   */
  static EstornarLancamentoCommand comando(
      UUID comercianteId, String chave, String id, String requestId) {
    List<ErroCampo> erros = new ArrayList<>();
    EntradaDeLancamento.validarChave(chave, erros);
    UUID original = id(id, erros);
    if (!erros.isEmpty()) {
      throw new RequisicaoInvalidaException(erros);
    }
    return new EstornarLancamentoCommand(comercianteId, chave, original, requestId);
  }

  private static UUID id(String id, List<ErroCampo> erros) {
    if (!uuidCanonico(id)) {
      erros.add(
          EntradaDeLancamento.erro(
              "id",
              "formato-invalido",
              "id deve ser um UUID na forma 8-4-4-4-12, como 7d2f6c1a-3b4e-4f5a-9c8d-1e2f3a4b5c6d"));
      return null;
    }
    return UUID.fromString(id);
  }

  /** Verificado sem expressão regular; formas abreviadas aceitas por {@code UUID} são recusadas. */
  private static boolean uuidCanonico(String id) {
    if (id == null || id.length() != TAMANHO_UUID) {
      return false;
    }
    for (int i = 0; i < TAMANHO_UUID; i++) {
      char c = id.charAt(i);
      boolean hifen = i == 8 || i == 13 || i == 18 || i == 23;
      if (hifen ? c != '-' : Character.digit(c, 16) < 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean ehJson(String contentType) {
    if (contentType == null) {
      return false;
    }
    try {
      return MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(contentType));
    } catch (InvalidMediaTypeException e) {
      return false;
    }
  }
}
