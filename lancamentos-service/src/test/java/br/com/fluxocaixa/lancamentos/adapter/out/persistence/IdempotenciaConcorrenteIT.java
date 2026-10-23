package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** AC-007.4: N = 20 chamadas simultâneas com a mesma chave criam exatamente 1 lançamento. */
@Tag("RF-007")
@Tag("RNF-004")
class IdempotenciaConcorrenteIT extends IntegracaoPostgres {

  private static final int N = 20;

  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private JdbcClient jdbc;

  @Test
  void chamadasSimultaneasComAMesmaChaveCriamUmUnicoLancamento() throws Exception {
    UUID comerciante = UUID.randomUUID();
    var comando =
        new RegistrarLancamentoCommand(
            comerciante,
            "concorrente-" + UUID.randomUUID(),
            TipoLancamento.CREDITO,
            new BigDecimal("250.00"),
            "Venda simultânea",
            null,
            null,
            null);

    CountDownLatch prontas = new CountDownLatch(N);
    CountDownLatch largada = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(N);
    List<Future<RegistrarLancamento.Resultado>> futuros = new ArrayList<>();
    try {
      for (int i = 0; i < N; i++) {
        futuros.add(
            executor.submit(
                () -> {
                  prontas.countDown();
                  largada.await();
                  return registrarLancamento.registrar(comando);
                }));
      }
      assertThat(prontas.await(30, TimeUnit.SECONDS)).isTrue();
      largada.countDown();

      List<RegistrarLancamento.Resultado> resultados = new ArrayList<>();
      for (Future<RegistrarLancamento.Resultado> futuro : futuros) {
        resultados.add(futuro.get(60, TimeUnit.SECONDS));
      }

      assertThat(resultados).hasSize(N);
      assertThat(resultados.stream().map(r -> r.lancamento().id()).distinct()).hasSize(1);
      assertThat(resultados.stream().map(RegistrarLancamento.Resultado::lancamento).distinct())
          .hasSize(1);
      assertThat(resultados.stream().filter(r -> !r.replay())).hasSize(1);
      assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(1, 1, 1));
    } finally {
      executor.shutdownNow();
    }
  }
}
