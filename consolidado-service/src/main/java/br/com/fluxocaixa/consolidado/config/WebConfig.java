package br.com.fluxocaixa.consolidado.config;

import br.com.fluxocaixa.consolidado.adapter.in.web.LimiteDeCorpoFilter;
import br.com.fluxocaixa.consolidado.adapter.in.web.LogDeAcessoFilter;
import br.com.fluxocaixa.consolidado.adapter.in.web.Problemas;
import br.com.fluxocaixa.consolidado.adapter.in.web.RequestIdFilter;
import br.com.fluxocaixa.consolidado.application.port.out.IdGenerator;
import jakarta.servlet.Filter;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Filtros HTTP antes da cadeia do Spring Security, nesta ordem: request ID (para que 401 e 403 já o
 * tenham), log de acesso (mede a requisição inteira) e limite de corpo (03 §5.2).
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig {

  @Bean
  FilterRegistrationBean<RequestIdFilter> filtroRequestId(IdGenerator ids) {
    return registrar(new RequestIdFilter(ids), Ordered.HIGHEST_PRECEDENCE);
  }

  @Bean
  FilterRegistrationBean<LogDeAcessoFilter> filtroLogDeAcesso(
      Clock clock,
      @Qualifier("requestMappingHandlerMapping")
          ObjectProvider<RequestMappingHandlerMapping> mapeamento) {
    return registrar(
        new LogDeAcessoFilter(clock, mapeamento::getObject), Ordered.HIGHEST_PRECEDENCE + 1);
  }

  @Bean
  FilterRegistrationBean<LimiteDeCorpoFilter> filtroLimiteDeCorpo(Problemas problemas) {
    return registrar(new LimiteDeCorpoFilter(problemas), Ordered.HIGHEST_PRECEDENCE + 2);
  }

  private static <F extends Filter> FilterRegistrationBean<F> registrar(F filtro, int ordem) {
    FilterRegistrationBean<F> registro = new FilterRegistrationBean<>(filtro);
    registro.setOrder(ordem);
    return registro;
  }
}
