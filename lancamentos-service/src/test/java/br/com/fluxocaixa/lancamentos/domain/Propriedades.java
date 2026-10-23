package br.com.fluxocaixa.lancamentos.domain;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Gerador reproduzível para testes de propriedade (Q-009). Executa {@code tentativas} casos gerados
 * a partir de uma semente fixa; os casos de fronteira informados rodam primeiro. Ao falhar, informa
 * semente, tentativa e caso. Não faz <i>shrinking</i>: o caso reportado não é minimizado.
 *
 * <p>Reproduzir uma falha: {@code ./mvnw test -Dtest=<Classe> -Dpropriedades.semente=<semente>}.
 */
final class Propriedades {

  static final long SEMENTE_PADRAO = 20_261_023L;
  static final int TENTATIVAS_PADRAO = 1_000;

  private Propriedades() {}

  static long semente() {
    return Long.getLong("propriedades.semente", SEMENTE_PADRAO);
  }

  /**
   * Verifica {@code propriedade} para cada caso de fronteira e para {@link #TENTATIVAS_PADRAO}
   * casos gerados.
   */
  static <T> void paraTodo(
      String nome,
      List<T> fronteiras,
      Function<SplittableRandom, T> gerador,
      Consumer<T> propriedade) {
    long semente = semente();
    for (int i = 0; i < fronteiras.size(); i++) {
      verificar(nome, semente, "fronteira " + i, fronteiras.get(i), propriedade);
    }
    SplittableRandom aleatorio = new SplittableRandom(semente);
    for (int i = 0; i < TENTATIVAS_PADRAO; i++) {
      verificar(nome, semente, "tentativa " + i, gerador.apply(aleatorio), propriedade);
    }
  }

  private static <T> void verificar(
      String nome, long semente, String origem, T caso, Consumer<T> propriedade) {
    try {
      propriedade.accept(caso);
    } catch (AssertionError | RuntimeException falha) {
      throw new AssertionError(
          "Propriedade '"
              + nome
              + "' falhou na "
              + origem
              + " (semente="
              + semente
              + "; reproduzir com -Dpropriedades.semente="
              + semente
              + "). Caso: "
              + caso,
          falha);
    }
  }
}
