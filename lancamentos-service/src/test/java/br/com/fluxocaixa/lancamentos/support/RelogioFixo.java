package br.com.fluxocaixa.lancamentos.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Relógio dos ITs (05 §7): substitui o {@code Clock} do sistema no contexto de teste. Fica parado
 * em {@link #AGORA}; um IT pode avançá-lo e deve restaurá-lo ao terminar.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RelogioFixo {

  /** 12:00 em São Paulo; a data de referência de um lançamento sem data é 2026-10-23. */
  public static final Instant AGORA = Instant.parse("2026-10-23T15:00:00Z");

  @Bean
  @Primary
  Controlado relogioFixo() {
    return new Controlado();
  }

  /** Relógio parado, ajustável pelo teste. */
  public static final class Controlado extends Clock {

    private volatile Instant instante = AGORA;

    public void ajustar(Instant novo) {
      instante = novo;
    }

    public void restaurar() {
      instante = AGORA;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
      return Clock.fixed(instante, zona);
    }

    @Override
    public Instant instant() {
      return instante;
    }
  }
}
