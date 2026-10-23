package br.com.fluxocaixa.lancamentos.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Tokens JWT de teste (Q-012): o par RSA é gerado em memória a cada execução e só a chave pública
 * vai para um arquivo temporário, lido pela configuração real de segurança. Nenhuma chave é
 * versionada. Os instantes partem de {@link RelogioFixo#AGORA}, o relógio que valida o token.
 */
public final class TokensDeTeste {

  public static final String EMISSOR = "https://teste.fluxocaixa.local";
  public static final String AUDIENCIA = "lancamentos-api";
  public static final String ESCRITA = "lancamentos:write";
  public static final String LEITURA = "lancamentos:read";

  private static final KeyPair PAR = gerarPar();
  private static final Path CHAVE_PUBLICA = gravarChavePublica(PAR);
  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private TokensDeTeste() {}

  /** Aponta a validação de JWT para a chave pública desta execução. */
  public static void registrarPropriedades(DynamicPropertyRegistry registro) {
    registro.add(
        "spring.security.oauth2.resourceserver.jwt.public-key-location",
        () -> CHAVE_PUBLICA.toUri().toString());
    registro.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> EMISSOR);
    registro.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCIA);
  }

  /** Token válido para o comerciante, com os scopes informados. */
  public static String valido(UUID comerciante, String... scopes) {
    return novo().comerciante(comerciante.toString()).scopes(scopes).emitir();
  }

  public static Construtor novo() {
    return new Construtor();
  }

  /** Monta tokens válidos ou deliberadamente inválidos. */
  public static final class Construtor {

    private final Map<String, Object> claims = new LinkedHashMap<>();
    private String algoritmo = "RS256";
    private PrivateKey chave = PAR.getPrivate();
    private byte[] segredoHmac;

    private Construtor() {
      claims.put("iss", EMISSOR);
      claims.put("aud", AUDIENCIA);
      claims.put("sub", "teste");
      claims.put("iat", RelogioFixo.AGORA.getEpochSecond());
      claims.put("exp", RelogioFixo.AGORA.plus(Duration.ofHours(1)).getEpochSecond());
    }

    public Construtor comerciante(String merchantId) {
      return claim("merchant_id", merchantId);
    }

    public Construtor scopes(String... scopes) {
      return claim("scope", String.join(" ", scopes));
    }

    public Construtor emissor(String emissor) {
      return claim("iss", emissor);
    }

    public Construtor audiencia(String audiencia) {
      return claim("aud", audiencia);
    }

    public Construtor expiraEm(Instant instante) {
      return claim("exp", instante.getEpochSecond());
    }

    public Construtor naoAntesDe(Instant instante) {
      return claim("nbf", instante.getEpochSecond());
    }

    /** {@code null} remove o claim. */
    public Construtor claim(String nome, Object valor) {
      if (valor == null) {
        claims.remove(nome);
      } else {
        claims.put(nome, valor);
      }
      return this;
    }

    /** Assina com outra chave RSA (assinatura inválida para a chave configurada). */
    public Construtor assinadoComOutraChave() {
      chave = gerarPar().getPrivate();
      return this;
    }

    /** {@code alg: none}, sem assinatura. */
    public Construtor semAssinatura() {
      algoritmo = "none";
      return this;
    }

    /** HS256 com um segredo qualquer (algoritmo simétrico, proibido pelo ADR-007). */
    public Construtor hs256() {
      algoritmo = "HS256";
      segredoHmac = "segredo-de-teste-com-32-bytes-ok!".getBytes(StandardCharsets.UTF_8);
      return this;
    }

    public String emitir() {
      String cabecalho = base64(json(Map.of("alg", algoritmo, "typ", "JWT")));
      String corpo = base64(json(claims));
      String assinado = cabecalho + "." + corpo;
      return assinado + "." + assinar(assinado);
    }

    private String assinar(String conteudo) {
      byte[] bytes = conteudo.getBytes(StandardCharsets.US_ASCII);
      try {
        return switch (algoritmo) {
          case "none" -> "";
          case "HS256" -> {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredoHmac, "HmacSHA256"));
            yield BASE64_URL.encodeToString(mac.doFinal(bytes));
          }
          default -> {
            Signature assinatura = Signature.getInstance("SHA256withRSA");
            assinatura.initSign(chave);
            assinatura.update(bytes);
            yield BASE64_URL.encodeToString(assinatura.sign());
          }
        };
      } catch (GeneralSecurityException e) {
        throw new IllegalStateException("falha ao assinar o token de teste", e);
      }
    }
  }

  private static String base64(String json) {
    return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  /** JSON mínimo para claims de texto e número (sem dependência de mapper). */
  private static String json(Map<String, Object> valores) {
    List<String> pares = new ArrayList<>();
    valores.forEach(
        (nome, valor) ->
            pares.add(
                "\"" + nome + "\":" + (valor instanceof Number ? valor : "\"" + valor + "\"")));
    return "{" + String.join(",", pares) + "}";
  }

  private static KeyPair gerarPar() {
    try {
      KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
      gerador.initialize(2048);
      return gerador.generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("RSA é obrigatório em toda JVM", e);
    }
  }

  private static Path gravarChavePublica(KeyPair par) {
    String pem =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(par.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----\n";
    try {
      Path arquivo = Files.createTempFile("jwt-teste-", ".pem");
      arquivo.toFile().deleteOnExit();
      return Files.writeString(arquivo, pem, StandardCharsets.US_ASCII);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
