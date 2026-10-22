package br.com.fluxocaixa.lancamentos.archfixtures.violations.domain;

import org.springframework.util.StringUtils;

/** Fixture: viola "domain sem frameworks". Existe só para provar que a regra falha. */
public class DomainUsandoSpring {

  public boolean temTexto(String valor) {
    return StringUtils.hasText(valor);
  }
}
