package br.com.fluxocaixa.consolidado.adapter.in.web;

import java.util.Objects;

/** Item de {@code errors[]} do problema 400: nome do campo, código estável e mensagem legível. */
public record ErroDeCampo(String campo, String codigo, String mensagem) {

  public ErroDeCampo {
    Objects.requireNonNull(campo, "campo");
    Objects.requireNonNull(codigo, "codigo");
    Objects.requireNonNull(mensagem, "mensagem");
  }
}
