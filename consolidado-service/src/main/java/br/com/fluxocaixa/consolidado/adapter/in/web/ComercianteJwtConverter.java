package br.com.fluxocaixa.consolidado.adapter.in.web;

import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

/**
 * Autenticação a partir do JWT já validado: o claim {@code scope} vira autoridades {@code SCOPE_*}
 * e o {@code merchant_id} é o principal (ADR-007; A1). O comerciante nunca vem de corpo, query ou
 * path (RF-010, AC-010.2).
 */
@Component
public class ComercianteJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  public static final String CLAIM_COMERCIANTE = "merchant_id";

  private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    return new JwtAuthenticationToken(
        jwt, scopes.convert(jwt), jwt.getClaimAsString(CLAIM_COMERCIANTE));
  }

  /** O validador do decoder já garantiu que o claim é um UUID. */
  static UUID comerciante(JwtAuthenticationToken autenticacao) {
    return UUID.fromString(autenticacao.getName());
  }
}
