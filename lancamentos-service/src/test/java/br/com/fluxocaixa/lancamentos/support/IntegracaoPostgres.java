package br.com.fluxocaixa.lancamentos.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base dos ITs de persistência: contexto Spring completo contra o {@link PostgresLancamentos}, com
 * o Flyway aplicando as migrações como {@code lancamentos_owner} na subida do contexto e o relógio
 * fixo de {@link RelogioFixo}.
 */
@SpringBootTest
@Import(RelogioFixo.class)
public abstract class IntegracaoPostgres {

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registro) {
    PostgresLancamentos.registrarPropriedades(registro);
  }
}
