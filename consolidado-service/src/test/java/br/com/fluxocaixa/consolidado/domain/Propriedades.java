package br.com.fluxocaixa.consolidado.domain;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Gerador reproduzível para testes de propriedade (Q-009), copiado do serviço de Lançamentos sem
 * dependência entre os módulos ({@code 01 §3}). Executa {@code tentativas} casos gerados a partir
 * de uma semente fixa; os casos de fronteira informados rodam primeiro. Ao falhar, informa semente,
 * tentativa e caso. Não faz <i>shrinking</i>: o caso reportado não é minimizado.
 *
 * <p>Reproduzir uma falha: {@code ./mvnw test -Dtest=<Classe> -Dpropriedades.semente=<semente>}.
 */
public final class Propriedades {

  public static final long SEMENTE_PADRAO = 20_261_023L;
  public static final int TENTATIVAS_PADRAO = 1_000;

  private Propriedades() {}

  public static long semente() {
    return Long.getLong("propriedades.semente", SEMENTE_PADRAO);
  }

  /**
   * Verifica {@code propriedade} para cada caso de fronteira e para {@link #TENTATIVAS_PADRAO}
   * casos gerados.
   */
  public static <T> void paraTodo(
      String nome,
      List<T> fronteiras,
      Function<SplittableRandom, T> gerador,
      Consumer<T> propriedade) {
    paraTodo(nome, TENTATIVAS_PADRAO, fronteiras, gerador, propriedade);
  }

  /**
   * Como {@link #paraTodo(String, List, Function, Consumer)}, com {@code tentativas} casos gerados
   * (ITs contra o banco usam menos tentativas, T-021).
   */
  public static <T> void paraTodo(
      String nome,
      int tentativas,
      List<T> fronteiras,
      Function<SplittableRandom, T> gerador,
      Consumer<T> propriedade) {
    long semente = semente();
    for (int i = 0; i < fronteiras.size(); i++) {
      verificar(nome, semente, "fronteira " + i, fronteiras.get(i), propriedade);
    }
    SplittableRandom aleatorio = new SplittableRandom(semente);
    for (int i = 0; i < tentativas; i++) {
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
