package br.com.fluxocaixa.lancamentos.archfixtures.violations.application;

import io.micrometer.core.instrument.Counter;

/** Viola a regra: {@code application} não conhece o Micrometer (Q-063). */
public final class ApplicationUsandoMicrometer {

  private Counter contador;

  public void contar() {
    contador.increment();
  }
}
