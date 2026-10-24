package br.com.fluxocaixa.consolidado.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Relógio dos ITs (05 §7), copiado do Lançamentos: substitui o {@code Clock} do sistema no contexto
 * de teste. Fica parado em {@link #AGORA}; um IT pode ajustá-lo, e {@link IntegracaoKafka} o
 * restaura antes de cada teste.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RelogioFixo {

  /** 12:00 em São Paulo de 2026-10-23. */
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
