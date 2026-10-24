package br.com.fluxocaixa.lancamentos.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base dos ITs do publicador (T-016): PostgreSQL e Kafka reais. O laço do publicador continua
 * desligado (configuração de teste); os testes chamam o caso de uso diretamente, a não ser que
 * liguem o laço. Cada teste começa com a outbox vazia, porque o publicador trava as pendentes mais
 * antigas de todos os comerciantes.
 */
public abstract class IntegracaoKafka extends IntegracaoPostgres {

  @Autowired protected JdbcClient jdbc;

  @DynamicPropertySource
  static void kafka(DynamicPropertyRegistry registro) {
    KafkaLancamentos.registrarPropriedades(registro);
  }

  @BeforeEach
  void outboxVazia() {
    jdbc.sql("DELETE FROM outbox_event").update();
  }
}
