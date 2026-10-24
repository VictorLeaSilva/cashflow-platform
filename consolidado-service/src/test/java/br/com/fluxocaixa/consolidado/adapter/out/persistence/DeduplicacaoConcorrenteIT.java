package br.com.fluxocaixa.consolidado.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.consolidado.application.port.out.ProjecaoRepository;
import br.com.fluxocaixa.consolidado.domain.ChaveDiaria;
import br.com.fluxocaixa.consolidado.domain.Efeito;
import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import br.com.fluxocaixa.consolidado.domain.TipoLancamento;
import br.com.fluxocaixa.consolidado.support.IntegracaoPostgres;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AC-T021.4 e AC-T021.5: risco "deduplicação concorrente" de {@code 04 §2.7}. As {@code threads}
 * largam juntas num {@link CountDownLatch}; sem {@code sleep}.
 */
@Tag("RF-008")
@Tag("RNF-004")
class DeduplicacaoConcorrenteIT extends IntegracaoPostgres {

  private static final int THREADS = 8;
  private static final int EVENTOS_POR_THREAD = 25;
  private static final UUID COMERCIANTE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
  private static final LocalDate DIA = LocalDate.parse("2026-10-22");
  private static final ChaveDiaria CHAVE = new ChaveDiaria(COMERCIANTE, DIA);
  private static final Instant AGORA = Instant.parse("2026-10-22T13:30:00Z");

  @Autowired private ProjecaoRepository projecao;

  @Test
  void mesmoEventoEmOitoThreadsSomaUmaUnicaVez() throws Exception {
    UUID evento = new UUID(0L, 1L);
    Efeito efeito =
        new Efeito(
            CHAVE,
            new MovimentoDiario(
                new BigDecimal("42.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1));
    List<Callable<Boolean>> tarefas = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      tarefas.add(() -> projecao.aplicarSeNovo(evento, efeito, AGORA));
    }

    List<Boolean> resultados = executarJuntas(tarefas);

    assertThat(resultados).containsOnlyOnce(true).hasSize(THREADS);
    assertThat(TabelasDoConsolidado.eventosProcessados()).isEqualTo(1);
    assertThat(TabelasDoConsolidado.dias()).containsExactly(Map.entry(CHAVE, efeito.delta()));
  }

  @Test
  void upsertsConcorrentesNoMesmoDiaNaoPerdemSoma() throws Exception {
    List<List<LancamentoRegistrado>> lotes = new ArrayList<>();
    MovimentoDiario esperado = MovimentoDiario.ZERO;
    for (int t = 0; t < THREADS; t++) {
      List<LancamentoRegistrado> lote = new ArrayList<>();
      for (int i = 0; i < EVENTOS_POR_THREAD; i++) {
        LancamentoRegistrado evento = evento(t * EVENTOS_POR_THREAD + i);
        lote.add(evento);
        esperado = esperado.somar(Efeito.de(evento).delta());
      }
      lotes.add(lote);
    }
    List<Callable<Boolean>> tarefas = new ArrayList<>();
    for (List<LancamentoRegistrado> lote : lotes) {
      tarefas.add(
          () -> {
            for (LancamentoRegistrado evento : lote) {
              assertThat(projecao.aplicarSeNovo(evento.eventId(), Efeito.de(evento), AGORA))
                  .isTrue();
            }
            return true;
          });
    }

    executarJuntas(tarefas);

    assertThat(TabelasDoConsolidado.dias()).containsExactly(Map.entry(CHAVE, esperado));
    assertThat(esperado.qtdLancamentos()).isEqualTo(THREADS * EVENTOS_POR_THREAD);
    assertThat(TabelasDoConsolidado.eventosProcessados()).isEqualTo(THREADS * EVENTOS_POR_THREAD);
  }

  /** Crédito, débito, estorno de crédito e estorno de débito em rodízio, com valores distintos. */
  private static LancamentoRegistrado evento(int n) {
    TipoLancamento[] tipos = {TipoLancamento.CREDITO, TipoLancamento.DEBITO};
    boolean estorno = n % 4 >= 2;
    TipoLancamento natureza = tipos[n % 2];
    return new LancamentoRegistrado(
        new UUID(1L, n),
        COMERCIANTE,
        new UUID(2L, n),
        estorno ? TipoLancamento.ESTORNO : natureza,
        BigDecimal.valueOf(n + 1L, 2).add(BigDecimal.valueOf(n)),
        DIA,
        estorno ? natureza : null,
        estorno ? new UUID(3L, n) : null);
  }

  private static <T> List<T> executarJuntas(List<Callable<T>> tarefas) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(tarefas.size());
    try {
      CountDownLatch prontas = new CountDownLatch(tarefas.size());
      CountDownLatch largada = new CountDownLatch(1);
      List<Future<T>> futuros = new ArrayList<>();
      for (Callable<T> tarefa : tarefas) {
        futuros.add(
            executor.submit(
                () -> {
                  prontas.countDown();
                  largada.await();
                  return tarefa.call();
                }));
      }
      assertThat(prontas.await(30, TimeUnit.SECONDS)).isTrue();
      largada.countDown();
      List<T> resultados = new ArrayList<>();
      for (Future<T> futuro : futuros) {
        resultados.add(futuro.get(60, TimeUnit.SECONDS));
      }
      return resultados;
    } finally {
      executor.shutdownNow();
    }
  }
}
