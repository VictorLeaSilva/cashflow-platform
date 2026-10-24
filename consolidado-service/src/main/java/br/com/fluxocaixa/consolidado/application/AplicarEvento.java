package br.com.fluxocaixa.consolidado.application;

import br.com.fluxocaixa.consolidado.application.port.out.ProjecaoRepository;
import br.com.fluxocaixa.consolidado.domain.Efeito;
import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import java.time.Clock;
import java.util.Objects;

/**
 * Aplica um evento na projeção (03 §2.2, T-022): o efeito do domínio, deduplicado por {@code
 * eventId} num único comando atômico da porta (Q-037), com o instante do {@code Clock} (Q-038). Não
 * abre transação: o comando da porta já é a transação (Q-044).
 */
public final class AplicarEvento {

  /** O que aconteceu com o evento. */
  public enum Resultado {
    APLICADO,
    DUPLICADO
  }

  private final ProjecaoRepository projecao;
  private final Clock clock;

  public AplicarEvento(ProjecaoRepository projecao, Clock clock) {
    this.projecao = Objects.requireNonNull(projecao, "projecao");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Resultado aplicar(LancamentoRegistrado evento) {
    Objects.requireNonNull(evento, "evento");
    boolean novo = projecao.aplicarSeNovo(evento.eventId(), Efeito.de(evento), clock.instant());
    return novo ? Resultado.APLICADO : Resultado.DUPLICADO;
  }
}
