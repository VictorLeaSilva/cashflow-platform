package br.com.fluxocaixa.lancamentos.application.port.out;

import java.util.UUID;

/** Gera identificadores (constituição §4); testes podem fixar a sequência. */
public interface IdGenerator {

  UUID gerar();
}
