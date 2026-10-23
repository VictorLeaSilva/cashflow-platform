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
 * §3.1). Formato: {@code operação|tipo|valor com escala 2|descricao|instante UTC|identificador
 * externo}, com {@code -} para campo ausente. Textos entram em NFC (03 §5.2); chave e {@code
 * requestId} não entram.
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
        OPERACAO_REGISTRO,
        comando.tipo().name(),
        valor(comando.valor()),
        texto(comando.descricao()),
        instante(comando.dataOcorrencia()),
        texto(comando.identificadorExterno()));
  }

  /** O comando já foi validado pelo domínio (no máximo 2 casas), então não há arredondamento. */
  private static String valor(BigDecimal valor) {
    return valor.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }

  private static String texto(String texto) {
    return texto == null ? AUSENTE : Normalizer.normalize(texto, Normalizer.Form.NFC);
  }

  private static String instante(Instant instante) {
    return instante == null ? AUSENTE : instante.toString();
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
