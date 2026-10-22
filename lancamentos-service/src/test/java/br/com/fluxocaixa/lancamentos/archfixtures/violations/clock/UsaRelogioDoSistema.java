package br.com.fluxocaixa.lancamentos.archfixtures.violations.clock;

import java.time.Instant;

/** Fixture: viola "sem relógio do sistema". Existe só para provar que a regra falha. */
public class UsaRelogioDoSistema {

  public Instant agora() {
    return Instant.now();
  }

  public long milissegundos() {
    return System.currentTimeMillis();
  }
}
