package br.com.fluxocaixa.consolidado.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Modelo de referência só de teste da projeção (Q-033): imita o algoritmo de {@code 03 §2.2}
 * (registra o {@code eventId}; se novo, soma o efeito do domínio). A deduplicação atômica no banco
 * é da T-021; aqui só a semântica de deduplicar por {@code eventId}, e só por ele (ADR-005).
 */
final class ProjecaoDeReferencia {

  private final Set<UUID> aplicados = new HashSet<>();
  private final Map<ChaveDiaria, MovimentoDiario> dias = new HashMap<>();

  static ProjecaoDeReferencia aplicandoTodos(List<LancamentoRegistrado> entrega) {
    ProjecaoDeReferencia projecao = new ProjecaoDeReferencia();
    entrega.forEach(projecao::aplicar);
    return projecao;
  }

  /** Devolve {@code true} se o evento foi aplicado e {@code false} se era duplicata. */
  boolean aplicar(LancamentoRegistrado evento) {
    if (!aplicados.add(evento.eventId())) {
      return false;
    }
    Efeito efeito = Efeito.de(evento);
    dias.merge(efeito.chave(), efeito.delta(), MovimentoDiario::somar);
    return true;
  }

  Map<ChaveDiaria, MovimentoDiario> dias() {
    return Map.copyOf(dias);
  }

  MovimentoDiario dia(ChaveDiaria chave) {
    return dias.getOrDefault(chave, MovimentoDiario.ZERO);
  }
}
