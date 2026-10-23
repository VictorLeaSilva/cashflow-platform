package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.application.EstornarLancamento;
import br.com.fluxocaixa.lancamentos.application.EstornarLancamentoCommand;
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

/**
 * AC-007.4 no estorno: N chamadas simultâneas com a mesma chave e o mesmo original → 1 estorno, 1
 * evento e o mesmo id para todas, exatamente uma sem replay.
 */
@Tag("RF-007")
@Tag("RNF-004")
class EstornoIdempotenteConcorrenteIT extends IntegracaoPostgres {

  private static final int N = 10;

  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private EstornarLancamento estornarLancamento;
  @Autowired private JdbcClient jdbc;

  @Test
  void mesmaChaveSimultaneaGeraUmUnicoEstorno() throws Exception {
    UUID comerciante = UUID.randomUUID();
    UUID original =
        registrarLancamento
            .registrar(
                new RegistrarLancamentoCommand(
                    comerciante,
                    "idem-original",
                    TipoLancamento.DEBITO,
                    new BigDecimal("8.00"),
                    null,
                    null,
                    null,
                    null))
            .lancamento()
            .id();
    var comando = new EstornarLancamentoCommand(comerciante, "idem-estorno", original, null);

    CountDownLatch prontas = new CountDownLatch(N);
    CountDownLatch largada = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(N);
    List<EstornarLancamento.Resultado> resultados = new ArrayList<>();
    try {
      List<Future<EstornarLancamento.Resultado>> futuras = new ArrayList<>();
      for (int i = 0; i < N; i++) {
        futuras.add(
            executor.submit(
                () -> {
                  prontas.countDown();
                  largada.await();
                  return estornarLancamento.estornar(comando);
                }));
      }
      assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
      largada.countDown();
      for (Future<EstornarLancamento.Resultado> futura : futuras) {
        resultados.add(futura.get(30, TimeUnit.SECONDS));
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(resultados)
        .extracting(r -> r.lancamento().id())
        .containsOnly(resultados.getFirst().lancamento().id());
    assertThat(resultados).filteredOn(r -> !r.replay()).hasSize(1);
    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(2, 2, 2));
  }
}
