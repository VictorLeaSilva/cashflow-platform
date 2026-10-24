package br.com.fluxocaixa.consolidado.support;

import br.com.fluxocaixa.consolidado.application.port.out.ProjecaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Base dos ITs do consumidor (T-022): PostgreSQL e Kafka reais, com o consumidor ligado, o relógio
 * de {@link RelogioFixo} e a porta de projeção decorada por {@link ProjecaoComFalhas}. Todos os ITs
 * do consumidor compartilham este contexto (mesmas propriedades), então o {@code listener} é um só;
 * cada teste usa comerciantes e {@code eventId}s próprios e espera pelos próprios eventos.
 */
@TestPropertySource(properties = "consolidado.consumer.enabled=true")
@Import({RelogioFixo.class, IntegracaoKafka.Falhas.class})
public abstract class IntegracaoKafka extends IntegracaoPostgres {

  /** Grupo de consumo do {@code application.yml}. */
  protected static final String GRUPO = "consolidado-projecao";

  @Autowired protected ProjecaoComFalhas falhas;
  @Autowired private RelogioFixo.Controlado relogio;

  @DynamicPropertySource
  static void kafka(DynamicPropertyRegistry registro) {
    KafkaConsolidado.registrarPropriedades(registro);
  }

  @BeforeEach
  void semFalhasERelogioEmAgora() {
    falhas.limpar();
    relogio.restaurar();
  }

  protected RelogioFixo.Controlado relogio() {
    return relogio;
  }

  /** Decora a porta real (a do {@code ConsumidorConfig}) para injetar falhas. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Falhas {

    @Bean
    @Primary
    ProjecaoComFalhas projecaoComFalhas(@Qualifier("projecaoRepository") ProjecaoRepository real) {
      return new ProjecaoComFalhas(real);
    }
  }
}
