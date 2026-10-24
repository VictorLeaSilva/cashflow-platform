package br.com.fluxocaixa.consolidado.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Efeito de um evento na projeção: um delta aditivo sobre uma única chave ({@code 03 §3.2}). É o
 * que o <i>upsert</i> aditivo da T-021 recebe como {@code EXCLUDED}.
 */
public record Efeito(ChaveDiaria chave, MovimentoDiario delta) {

  public Efeito {
    Objects.requireNonNull(chave, "chave");
    Objects.requireNonNull(delta, "delta");
  }

  /**
   * Regra de efeito de {@code 03 §3.2}. A chave usa a {@code dataReferencia} do evento, nunca uma
   * data recalculada; o estorno soma no dia dele, não no do original (D-07), e não depende de o
   * original ter sido aplicado (ADR-005).
   */
  public static Efeito de(LancamentoRegistrado evento) {
    Objects.requireNonNull(evento, "evento");
    BigDecimal valor = evento.valor();
    BigDecimal zero = BigDecimal.ZERO;
    MovimentoDiario delta =
        switch (evento.tipo()) {
          case CREDITO -> new MovimentoDiario(valor, zero, zero, zero, 1);
          case DEBITO -> new MovimentoDiario(zero, valor, zero, zero, 1);
          case ESTORNO ->
              evento.tipoOriginal() == TipoLancamento.CREDITO
                  ? new MovimentoDiario(zero, zero, valor, zero, 1)
                  : new MovimentoDiario(zero, zero, zero, valor, 1);
        };
    return new Efeito(new ChaveDiaria(evento.comercianteId(), evento.dataReferencia()), delta);
  }
}
