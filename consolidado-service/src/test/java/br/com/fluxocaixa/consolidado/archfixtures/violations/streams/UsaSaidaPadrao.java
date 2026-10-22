package br.com.fluxocaixa.consolidado.archfixtures.violations.streams;

/** Fixture: viola "sem saída padrão". Existe só para provar que a regra falha. */
public class UsaSaidaPadrao {

  public void imprime() {
    System.out.println("fixture");
  }
}
