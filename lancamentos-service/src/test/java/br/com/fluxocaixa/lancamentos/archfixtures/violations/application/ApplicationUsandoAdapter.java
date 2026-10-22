package br.com.fluxocaixa.lancamentos.archfixtures.violations.application;

import br.com.fluxocaixa.lancamentos.archfixtures.violations.adapter.AdapterQualquer;

/** Fixture: viola "application sem adapters". Existe só para provar que a regra falha. */
public class ApplicationUsandoAdapter {

  private final AdapterQualquer adapter = new AdapterQualquer();

  public AdapterQualquer adapter() {
    return adapter;
  }
}
