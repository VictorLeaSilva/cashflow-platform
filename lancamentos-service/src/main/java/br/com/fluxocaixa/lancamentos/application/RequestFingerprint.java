package br.com.fluxocaixa.lancamentos.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HexFormat;

/**
 * {@code request_hash} da chave de idempotência: SHA-256 (hex) da representação canônica da
 * operação, calculada do comando já validado e não do JSON bruto (T-012; "corpo canônico" de 03
 * §3.1). Campos, nesta ordem e separados por {@code |}: operação, tipo, valor com escala 2,
 * descricao, instante UTC e identificador externo. Sem ambiguidade (Q-010): campo presente como
 * {@code <tamanho>:<texto>} (code points, após NFC) e ausente como {@code -}. Ex.: {@code 17:POST
 * /lancamentos|7:CREDITO|6:100.00|-|-|-}. Chave e {@code requestId} não entram.
 */
public final class RequestFingerprint {

  static final String OPERACAO_REGISTRO = "POST /lancamentos";
  private static final String SEPARADOR = "|";
  private static final String AUSENTE = "-";

  private RequestFingerprint() {}

  public static String deRegistro(RegistrarLancamentoCommand comando) {
    return sha256(canonico(comando));
  }

  static String canonico(RegistrarLancamentoCommand comando) {
    return String.join(
        SEPARADOR,
        campo(OPERACAO_REGISTRO),
        campo(comando.tipo().name()),
        campo(valor(comando.valor())),
        campo(nfc(comando.descricao())),
        campo(instante(comando.dataOcorrencia())),
        campo(nfc(comando.identificadorExterno())));
  }

  /** O comando já foi validado pelo domínio (no máximo 2 casas), então não há arredondamento. */
  private static String valor(BigDecimal valor) {
    return valor.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }

  /** Prefixo de tamanho: nenhum texto, nem {@code "-"} nem um com {@code |}, imita outro campo. */
  private static String campo(String texto) {
    return texto == null ? AUSENTE : texto.codePointCount(0, texto.length()) + ":" + texto;
  }

  private static String nfc(String texto) {
    return texto == null ? null : Normalizer.normalize(texto, Normalizer.Form.NFC);
  }

  private static String instante(Instant instante) {
    return instante == null ? null : instante.toString();
  }

  private static String sha256(String canonico) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", e);
    }
  }
}
