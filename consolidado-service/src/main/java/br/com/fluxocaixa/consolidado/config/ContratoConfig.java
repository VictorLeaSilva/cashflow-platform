package br.com.fluxocaixa.consolidado.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.resource.LiteWebJarsResourceResolver;

/**
 * Contrato e Swagger UI só no profile {@code local} (03 §5): {@code /v3/api-docs} devolve o próprio
 * YAML versionado (sem springdoc) e {@code /swagger-ui/index.html} o exibe. Sem o profile, nada é
 * registrado e a segurança não libera as rotas.
 */
@Configuration(proxyBeanMethods = false)
@Profile(ContratoConfig.PROFILE_LOCAL)
public class ContratoConfig implements WebMvcConfigurer {

  public static final String PROFILE_LOCAL = "local";
  static final String[] ROTAS_PUBLICAS = {
    "/swagger-ui/**", "/webjars/swagger-ui/**", "/v3/api-docs"
  };

  private static final MediaType YAML = MediaType.parseMediaType("application/yaml");

  @Bean
  RouterFunction<ServerResponse> contratoOpenApi() {
    ClassPathResource contrato = new ClassPathResource("openapi/consolidado-v1.yaml");
    return RouterFunctions.route(
        RequestPredicates.GET("/v3/api-docs"),
        requisicao -> ServerResponse.ok().contentType(YAML).body(contrato));
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registro) {
    registro.addResourceHandler("/swagger-ui/**").addResourceLocations("classpath:/swagger-ui/");
    // O mapeamento padrão de /webjars/** está desligado (spring.web.resources.add-mappings).
    registro
        .addResourceHandler("/webjars/**")
        .addResourceLocations("classpath:/META-INF/resources/webjars/")
        .resourceChain(true)
        .addResolver(new LiteWebJarsResourceResolver());
  }
}
