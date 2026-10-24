package br.com.fluxocaixa.consolidado.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.consolidado.application.port.out.ConsultaConsolidadoRepository;
import br.com.fluxocaixa.consolidado.application.port.out.LeituraDoPeriodo;
import br.com.fluxocaixa.consolidado.application.port.out.MovimentoGravado;
import br.com.fluxocaixa.consolidado.config.ConsultaConfig;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import br.com.fluxocaixa.consolidado.domain.TipoLancamento;
import br.com.fluxocaixa.consolidado.support.IntegracaoApiPostgres;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Leitura da projeção no banco real (T-023): soma anterior coluna a coluna, dias do intervalo,
 * filtro por comerciante e o mesmo <i>snapshot</i> nas duas consultas (AC-T023.1, Q-053).
 */
@Tag("RF-005")
@Tag("RF-006")
@Tag("RF-010")
class ConsultaConsolidadoJdbcRepositoryIT extends IntegracaoApiPostgres {

  private static final UUID A = UUID.randomUUID();
  private static final UUID B = UUID.randomUUID();

  @Autowired private ConsultaConsolidadoRepository leitura;
  @Autowired private DataSource dataSource;

  @Test
  void leASomaAnteriorEOsDiasDoComerciante() {
    credito(A, "2026-10-01", "100.00");
    debito(A, "2026-10-02", "30.00");
    estorno(A, TipoLancamento.DEBITO, "2026-10-03", "5.00");
    debito(A, "2026-10-05", "30.00");
    estorno(A, TipoLancamento.CREDITO, "2026-10-06", "10.00");
    credito(A, "2026-10-07", "1.00");
    credito(B, "2026-10-05", "999.00");
    credito(B, "2026-09-01", "888.00");

    LeituraDoPeriodo lido =
        leitura.ler(A, LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06"));

    assertThat(lido.anterior()).isEqualTo(movimento("100.00", "30.00", "0", "5.00", 3));
    assertThat(lido.anterior().saldoDia()).isEqualByComparingTo("75.00");
    assertThat(lido.dias())
        .containsOnly(
            Map.entry(
                LocalDate.parse("2026-10-05"),
                new MovimentoGravado(movimento("0", "30.00", "0", "0", 1), EM)),
            Map.entry(
                LocalDate.parse("2026-10-06"),
                new MovimentoGravado(movimento("0", "0", "10.00", "0", 1), EM)));
  }

  @Test
  void semHistoricoAnteriorEZeroESemDias() {
    LeituraDoPeriodo lido =
        leitura.ler(
            UUID.randomUUID(), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));

    assertThat(lido.anterior()).isEqualTo(MovimentoDiario.ZERO);
    assertThat(lido.dias()).isEmpty();
  }

  /**
   * Entre as duas consultas, outra {@code thread} aplica e confirma um evento antes e outro dentro
   * do intervalo. A leitura em curso não vê nenhum dos dois (mesmo <i>snapshot</i>); a seguinte vê
   * os dois. Em {@code READ COMMITTED}, a soma anterior veria o primeiro.
   */
  @Test
  void asDuasConsultasVemDoMesmoSnapshot() throws Exception {
    credito(A, "2026-10-01", "100.00");
    credito(A, "2026-10-05", "10.00");
    ExecutorService outra = Executors.newSingleThreadExecutor();
    try {
      Runnable gravarNoMeio =
          () -> {
            try {
              outra
                  .submit(
                      () -> {
                        credito(A, "2026-10-02", "50.00");
                        credito(A, "2026-10-05", "7.00");
                      })
                  .get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          };
      ConsultaConsolidadoJdbcRepository comGancho =
          new ConsultaConsolidadoJdbcRepository(
              JdbcClient.create(dataSource),
              ConsultaConfig.leituraConsistente(dataSource),
              gravarNoMeio);
      LocalDate inicio = LocalDate.parse("2026-10-05");

      LeituraDoPeriodo durante = comGancho.ler(A, inicio, inicio);
      LeituraDoPeriodo depois = leitura.ler(A, inicio, inicio);

      assertThat(durante.anterior().totalCreditos()).isEqualByComparingTo("100.00");
      assertThat(durante.dias().get(inicio).movimento().totalCreditos())
          .isEqualByComparingTo("10.00");
      assertThat(depois.anterior().totalCreditos()).isEqualByComparingTo("150.00");
      assertThat(depois.dias().get(inicio).movimento().totalCreditos())
          .isEqualByComparingTo("17.00");
    } finally {
      outra.shutdownNow();
    }
  }

  private static MovimentoDiario movimento(
      String creditos, String debitos, String estornosCredito, String estornosDebito, int qtd) {
    return new MovimentoDiario(
        new BigDecimal(creditos),
        new BigDecimal(debitos),
        new BigDecimal(estornosCredito),
        new BigDecimal(estornosDebito),
        qtd);
  }
}
