package br.com.fluxocaixa.lancamentos.config;

import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@code PoliticaLancamento} a partir de {@code lancamentos.*} (T-013). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PoliticaLancamentoProperties.class)
public class PoliticaLancamentoConfig {

  @Bean
  PoliticaLancamento politicaLancamento(PoliticaLancamentoProperties propriedades) {
    return propriedades.paraPolitica();
  }
}
