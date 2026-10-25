package br.com.fluxocaixa.consolidado.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base dos ITs de persistência: contexto Spring completo contra o {@link PostgresConsolidado}, com
 * o Flyway aplicando as migrações como {@code consolidado_owner} na subida do contexto. As tabelas
 * são esvaziadas antes de cada teste (o contexto e o container são compartilhados entre ITs).
 */
@SpringBootTest
public abstract class IntegracaoPostgres {

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registro) {
    PostgresConsolidado.registrarPropriedades(registro);
  }

  @BeforeEach
  protected void tabelasVazias() {
    PostgresConsolidado.esvaziar();
  }
}
