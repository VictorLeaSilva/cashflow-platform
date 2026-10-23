package br.com.fluxocaixa.lancamentos.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base dos ITs de persistência: contexto Spring completo contra o {@link PostgresLancamentos}, com
 * o Flyway aplicando as migrações como {@code lancamentos_owner} na subida do contexto e o relógio
 * de {@link RelogioFixo}, devolvido a {@link RelogioFixo#AGORA} antes de cada teste (o contexto é
 * compartilhado entre ITs).
 */
@SpringBootTest
@Import(RelogioFixo.class)
public abstract class IntegracaoPostgres {

  @Autowired private RelogioFixo.Controlado relogioDosTestes;

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registro) {
    PostgresLancamentos.registrarPropriedades(registro);
  }

  @BeforeEach
  void relogioEmAgora() {
    relogioDosTestes.restaurar();
  }
}
