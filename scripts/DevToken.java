import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Token JWT de desenvolvimento (somente local; 03 §5.1, ADR-007, Q-012).
 *
 * <pre>
 * java scripts/DevToken.java --merchant &lt;uuid&gt; --scopes lancamentos:write,lancamentos:read --ttl 3600
 * </pre>
 *
 * <p>Na primeira execução, gera o par RSA 2048 em {@code deploy/dev-keys/} (relativo ao diretório
 * atual; a pasta é ignorada pelo Git): {@code jwt-private.pem} (PKCS#8) e {@code jwt-public.pem}
 * (X.509), que o profile {@code local} do serviço lê. Imprime só o token em {@code stdout}; erros de
 * uso vão para {@code stderr} com código de saída 2. Emissor e audiências são os do profile {@code
 * local} dos dois serviços: o mesmo token vale no Lançamentos e no Consolidado (Q-050), e os scopes
 * separam o que cada um permite. Usa apenas o JDK.
 */
public final class DevToken {

  static final String EMISSOR = "https://dev.fluxocaixa.local";
  /** Os dois serviços (Q-050); os scopes separam o que cada um permite. */
  static final String AUDIENCIAS = "[\"lancamentos-api\",\"consolidado-api\"]";
  static final Path CHAVES = Path.of("deploy", "dev-keys");
  static final Path PRIVADA = CHAVES.resolve("jwt-private.pem");
  static final Path PUBLICA = CHAVES.resolve("jwt-public.pem");

  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private DevToken() {}

  public static void main(String[] argumentos) throws IOException, GeneralSecurityException {
    Map<String, String> opcoes;
    try {
      opcoes = opcoes(argumentos);
    } catch (IllegalArgumentException e) {
      System.err.println("erro: " + e.getMessage());
      System.err.println(
          "uso: java scripts/DevToken.java --merchant <uuid> --scopes a,b,c --ttl <segundos>");
      System.exit(2);
      return;
    }
    PrivateKey chave = chavePrivada();
    Instant agora = Instant.now();
    long ttl = Long.parseLong(opcoes.get("--ttl"));
    String scopes =
        Arrays.stream(opcoes.get("--scopes").split(","))
            .map(String::strip)
            .filter(scope -> !scope.isEmpty())
            .collect(Collectors.joining(" "));
    String claims =
        "{\"iss\":\""
            + EMISSOR
            + "\",\"aud\":"
            + AUDIENCIAS
            + ",\"sub\":\""
            + opcoes.get("--merchant")
            + "\",\"merchant_id\":\""
            + opcoes.get("--merchant")
            + "\",\"scope\":\""
            + scopes
            + "\",\"iat\":"
            + agora.getEpochSecond()
            + ",\"exp\":"
            + agora.plusSeconds(ttl).getEpochSecond()
            + ",\"jti\":\""
            + UUID.randomUUID()
            + "\"}";
    String conteudo =
        base64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
            + "."
            + base64(claims.getBytes(StandardCharsets.UTF_8));
    Signature assinatura = Signature.getInstance("SHA256withRSA");
    assinatura.initSign(chave);
    assinatura.update(conteudo.getBytes(StandardCharsets.US_ASCII));
    System.out.println(conteudo + "." + base64(assinatura.sign()));
  }

  private static Map<String, String> opcoes(String[] argumentos) {
    Map<String, String> opcoes = new LinkedHashMap<>();
    for (int i = 0; i < argumentos.length; i += 2) {
      if (i + 1 >= argumentos.length) {
        throw new IllegalArgumentException("opção sem valor: " + argumentos[i]);
      }
      opcoes.put(argumentos[i], argumentos[i + 1]);
    }
    for (String obrigatoria : new String[] {"--merchant", "--scopes", "--ttl"}) {
      if (!opcoes.containsKey(obrigatoria)) {
        throw new IllegalArgumentException("opção obrigatória ausente: " + obrigatoria);
      }
    }
    if (opcoes.size() != 3) {
      throw new IllegalArgumentException("opções aceitas: --merchant, --scopes, --ttl");
    }
    try {
      UUID merchant = UUID.fromString(opcoes.get("--merchant"));
      if (!merchant.toString().equalsIgnoreCase(opcoes.get("--merchant"))) {
        throw new IllegalArgumentException("--merchant deve ser um UUID");
      }
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("--merchant deve ser um UUID", e);
    }
    if (opcoes.get("--scopes").isBlank()) {
      throw new IllegalArgumentException("--scopes não pode ser vazio");
    }
    long ttl;
    try {
      ttl = Long.parseLong(opcoes.get("--ttl"));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("--ttl deve ser um número de segundos", e);
    }
    if (ttl <= 0) {
      throw new IllegalArgumentException("--ttl deve ser positivo");
    }
    return opcoes;
  }

  /** Lê a chave privada local ou gera o par na primeira execução. */
  private static PrivateKey chavePrivada() throws IOException, GeneralSecurityException {
    if (!Files.exists(PRIVADA)) {
      KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
      gerador.initialize(2048);
      KeyPair par = gerador.generateKeyPair();
      Files.createDirectories(CHAVES);
      Files.writeString(PRIVADA, pem("PRIVATE KEY", par.getPrivate().getEncoded()));
      Files.writeString(PUBLICA, pem("PUBLIC KEY", par.getPublic().getEncoded()));
      System.err.println("par de chaves gerado em " + CHAVES.toAbsolutePath());
    }
    String conteudo =
        Files.readString(PRIVADA)
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    return KeyFactory.getInstance("RSA")
        .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(conteudo)));
  }

  private static String pem(String tipo, byte[] chave) {
    return "-----BEGIN "
        + tipo
        + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(chave)
        + "\n-----END "
        + tipo
        + "-----\n";
  }

  private static String base64(byte[] bytes) {
    return BASE64_URL.encodeToString(bytes);
  }
}
