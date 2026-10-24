package br.com.fluxocaixa.lancamentos.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Porta de gestão (Q-062): tudo o que chega à porta separada passa sem token (só {@code health} e
 * {@code prometheus} estão expostos; o resto responde 404). A porta fica fora do alcance externo e
 * nunca é publicada no alvo (ADR-010). Com a gestão na mesma porta da API, o matcher nunca casa e a
 * cadeia da API vale para tudo.
 */
@Configuration(proxyBeanMethods = false)
public class GestaoSegurancaConfig {

  private static final String PORTA_DA_GESTAO = "local.management.port";
  private static final String PORTA_DA_API = "local.server.port";

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SecurityFilterChain gestao(HttpSecurity http, Environment ambiente) {
    http.securityMatcher(portaDeGestao(ambiente))
        .authorizeHttpRequests(rotas -> rotas.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable);
    return http.build();
  }

  private static RequestMatcher portaDeGestao(Environment ambiente) {
    return (HttpServletRequest requisicao) -> {
      Integer gestao = ambiente.getProperty(PORTA_DA_GESTAO, Integer.class);
      Integer api = ambiente.getProperty(PORTA_DA_API, Integer.class);
      return gestao != null && !gestao.equals(api) && requisicao.getLocalPort() == gestao;
    };
  }
}
