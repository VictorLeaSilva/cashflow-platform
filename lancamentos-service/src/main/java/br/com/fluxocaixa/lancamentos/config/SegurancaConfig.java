package br.com.fluxocaixa.lancamentos.config;

import br.com.fluxocaixa.lancamentos.adapter.in.web.ComercianteJwtConverter;
import br.com.fluxocaixa.lancamentos.adapter.in.web.Problemas;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Resource Server JWT (ADR-007, Q-008, Q-012): só RS256, chave pública de {@code
 * public-key-location}, {@code iss}, {@code aud}, {@code exp} obrigatório, {@code nbf} e {@code
 * merchant_id} UUID; tolerância de relógio padrão (60 s) medida pelo {@code Clock}. 401 e 403 em
 * {@code problem+json}, com {@code WWW-Authenticate}.
 */
@Configuration(proxyBeanMethods = false)
public class SegurancaConfig {

  static final String ESCRITA = "SCOPE_lancamentos:write";
  static final String LEITURA = "SCOPE_lancamentos:read";

  /** Q-008: 60 s, o valor histórico do Spring Security, fixado aqui para não depender do padrão. */
  static final Duration TOLERANCIA_DE_RELOGIO = Duration.ofSeconds(60);

  private static final String PROPRIEDADE = "spring.security.oauth2.resourceserver.jwt.";

  @Bean
  SecurityFilterChain api(
      HttpSecurity http,
      Environment ambiente,
      ComercianteJwtConverter conversor,
      AuthenticationEntryPoint naoAutenticado,
      AccessDeniedHandler acessoNegado) {
    boolean local = ambiente.acceptsProfiles(Profiles.of(ContratoConfig.PROFILE_LOCAL));
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            rotas -> {
              if (local) {
                rotas.requestMatchers(ContratoConfig.ROTAS_PUBLICAS).permitAll();
              }
              rotas.requestMatchers(HttpMethod.POST, "/api/v1/lancamentos").hasAuthority(ESCRITA);
              rotas
                  .requestMatchers(HttpMethod.POST, "/api/v1/lancamentos/*/estornos")
                  .hasAuthority(ESCRITA);
              // Escrita não inclui leitura (ADR-007, T-015).
              rotas
                  .requestMatchers(HttpMethod.GET, "/api/v1/lancamentos", "/api/v1/lancamentos/*")
                  .hasAuthority(LEITURA);
              rotas.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            recurso ->
                recurso
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(conversor))
                    .authenticationEntryPoint(naoAutenticado)
                    .accessDeniedHandler(acessoNegado))
        .exceptionHandling(
            excecoes ->
                excecoes
                    .authenticationEntryPoint(naoAutenticado)
                    .accessDeniedHandler(acessoNegado));
    return http.build();
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${" + PROPRIEDADE + "public-key-location:}") String localDaChave,
      @Value("${" + PROPRIEDADE + "issuer-uri:}") String emissor,
      @Value("${" + PROPRIEDADE + "audiences:}") String audiencias,
      ResourceLoader recursos,
      Clock clock) {
    exigir(localDaChave, "public-key-location");
    exigir(emissor, "issuer-uri");
    exigir(audiencias, "audiences");
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(chavePublica(recursos, localDaChave))
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    JwtTimestampValidator tempo = new JwtTimestampValidator(TOLERANCIA_DE_RELOGIO);
    tempo.setClock(clock);
    List<String> aceitas = Arrays.stream(audiencias.split(",")).map(String::strip).toList();
    List<OAuth2TokenValidator<Jwt>> validadores =
        List.of(
            tempo,
            new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
            new JwtIssuerValidator(emissor),
            new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.stream().anyMatch(aceitas::contains)),
            new JwtClaimValidator<Object>(
                ComercianteJwtConverter.CLAIM_COMERCIANTE, SegurancaConfig::ehUuid));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validadores));
    return decoder;
  }

  @Bean
  AuthenticationEntryPoint naoAutenticado(Problemas problemas) {
    BearerTokenAuthenticationEntryPoint cabecalho = new BearerTokenAuthenticationEntryPoint();
    return (requisicao, resposta, falha) -> {
      cabecalho.commence(requisicao, resposta, falha);
      problemas.escrever(
          requisicao,
          resposta,
          HttpStatus.UNAUTHORIZED,
          "nao-autenticado",
          "token ausente ou inválido");
    };
  }

  @Bean
  AccessDeniedHandler acessoNegado(Problemas problemas) {
    BearerTokenAccessDeniedHandler cabecalho = new BearerTokenAccessDeniedHandler();
    return (requisicao, resposta, falha) -> {
      cabecalho.handle(requisicao, resposta, falha);
      problemas.escrever(
          requisicao,
          resposta,
          HttpStatus.FORBIDDEN,
          "acesso-negado",
          "o token não tem o scope exigido por esta operação");
    };
  }

  private static boolean ehUuid(Object valor) {
    if (!(valor instanceof String texto)) {
      return false;
    }
    try {
      return UUID.fromString(texto).toString().equalsIgnoreCase(texto);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static RSAPublicKey chavePublica(ResourceLoader recursos, String local) {
    try (InputStream pem = recursos.getResource(local).getInputStream()) {
      return RsaKeyConverters.x509().convert(pem);
    } catch (IOException e) {
      throw new UncheckedIOException("chave pública JWT ilegível em " + local, e);
    }
  }

  private static void exigir(String valor, String nome) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException(
          "configure " + PROPRIEDADE + nome + " (variáveis LANCAMENTOS_JWT_*; Q-012)");
    }
  }
}
