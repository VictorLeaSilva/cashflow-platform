package br.com.fluxocaixa.consolidado.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Linha da projeção: um comerciante num dia de referência ({@code 03 §3.2}, chave primária). */
public record ChaveDiaria(UUID comercianteId, LocalDate dataReferencia) {

  public ChaveDiaria {
    Objects.requireNonNull(comercianteId, "comercianteId");
    Objects.requireNonNull(dataReferencia, "dataReferencia");
  }
}
