package br.com.fluxocaixa.lancamentos.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Relógio fixo dos ITs (05 §7): substitui o {@code Clock} do sistema no contexto de teste. */
@TestConfiguration(proxyBeanMethods = false)
public class RelogioFixo {

  /** 12:00 em São Paulo; a data de referência de um lançamento sem data é 2026-10-23. */
  public static final Instant AGORA = Instant.parse("2026-10-23T15:00:00Z");

  @Bean
  @Primary
  Clock relogioFixo() {
    return Clock.fixed(AGORA, ZoneOffset.UTC);
  }
}
