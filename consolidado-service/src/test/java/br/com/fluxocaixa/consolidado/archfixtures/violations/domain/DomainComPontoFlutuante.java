package br.com.fluxocaixa.consolidado.archfixtures.violations.domain;

/** Fixture: viola "sem ponto flutuante em domain". Existe só para provar que a regra falha. */
public class DomainComPontoFlutuante {

  private double valor;

  public double valor() {
    return valor;
  }
}
