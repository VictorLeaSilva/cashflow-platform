package br.com.fluxocaixa.consolidado.support;

import br.com.fluxocaixa.consolidado.application.port.out.ProjecaoRepository;
import br.com.fluxocaixa.consolidado.domain.Efeito;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import org.springframework.dao.TransientDataAccessResourceException;

/**
 * Decorador de teste da porta {@link ProjecaoRepository} para injetar falhas recuperáveis num
 * {@code eventId} escolhido: antes de delegar (nada é gravado) ou depois (o {@code commit} já
 * aconteceu). Registra o instante (relógio do sistema) de cada chamada, para conferir o
 * <i>backoff</i>.
 */
public final class ProjecaoComFalhas implements ProjecaoRepository {

  /** Falhas sem limite, até {@link #limpar()}. */
  public static final int SEMPRE = Integer.MAX_VALUE;

  private final ProjecaoRepository real;
  private final Map<UUID, Integer> falhasAntes = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> falhasDepois = new ConcurrentHashMap<>();
  private final Map<UUID, List<Long>> chamadas = new ConcurrentHashMap<>();
  private final Map<UUID, CountDownLatch> primeiraFalha = new ConcurrentHashMap<>();
  private volatile Duration atraso = Duration.ZERO;

  public ProjecaoComFalhas(ProjecaoRepository real) {
    this.real = real;
  }

  /** O {@code eventId} falha {@code vezes} antes de delegar. */
  public CountDownLatch falharAntes(UUID eventId, int vezes) {
    falhasAntes.put(eventId, vezes);
    return primeiraFalha.computeIfAbsent(eventId, id -> new CountDownLatch(1));
  }

  /** O {@code eventId} falha {@code vezes} depois de delegar (depois do {@code commit}). */
  public void falharDepois(UUID eventId, int vezes) {
    falhasDepois.put(eventId, vezes);
  }

  /** Cada chamada demora {@code atraso} antes de delegar (cadência controlada do consumidor). */
  public void atrasar(Duration novoAtraso) {
    atraso = novoAtraso;
  }

  public void limpar() {
    falhasAntes.clear();
    falhasDepois.clear();
  }

  public int chamadas(UUID eventId) {
    return chamadas.getOrDefault(eventId, List.of()).size();
  }

  /** Instantes das chamadas, em milissegundos do relógio do sistema (só para medir intervalos). */
  public List<Long> instantesDasChamadas(UUID eventId) {
    return new ArrayList<>(chamadas.getOrDefault(eventId, List.of()));
  }

  @Override
  public boolean aplicarSeNovo(UUID eventId, Efeito efeito, Instant instante) {
    chamadas.computeIfAbsent(eventId, id -> new CopyOnWriteArrayList<>()).add(milissegundos());
    if (!atraso.isZero()) {
      LockSupport.parkNanos(atraso.toNanos());
    }
    if (consumir(falhasAntes, eventId)) {
      primeiraFalha.computeIfAbsent(eventId, id -> new CountDownLatch(1)).countDown();
      throw new TransientDataAccessResourceException("falha injetada antes do commit");
    }
    boolean aplicado = real.aplicarSeNovo(eventId, efeito, instante);
    if (consumir(falhasDepois, eventId)) {
      throw new TransientDataAccessResourceException("falha injetada depois do commit");
    }
    return aplicado;
  }

  /** Gasta uma falha programada; a entrada some quando chega a zero ({@code null} remove). */
  private static boolean consumir(Map<UUID, Integer> falhas, UUID eventId) {
    boolean[] falhar = {false};
    falhas.computeIfPresent(
        eventId,
        (id, restantes) -> {
          falhar[0] = true;
          if (restantes == SEMPRE) {
            return restantes;
          }
          return restantes > 1 ? restantes - 1 : null;
        });
    return falhar[0];
  }

  /** Intervalos de {@code backoff} medidos com o relógio do sistema, nunca com o {@code Clock}. */
  private static long milissegundos() {
    return System.nanoTime() / 1_000_000;
  }
}
