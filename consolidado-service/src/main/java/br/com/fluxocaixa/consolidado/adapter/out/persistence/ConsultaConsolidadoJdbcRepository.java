package br.com.fluxocaixa.consolidado.adapter.out.persistence;

import br.com.fluxocaixa.consolidado.application.port.out.ConsultaConsolidadoRepository;
import br.com.fluxocaixa.consolidado.application.port.out.LeituraDoPeriodo;
import br.com.fluxocaixa.consolidado.application.port.out.MovimentoGravado;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link ConsultaConsolidadoRepository} com {@code JdbcClient} e SQL explícito (D-05), no pool
 * {@code api}. As duas consultas (os dias do intervalo, depois a soma de tudo antes dele) rodam
 * numa única transação somente leitura {@code REPEATABLE READ}: o <i>snapshot</i> nasce na
 * primeira, então a segunda não vê o que o consumidor confirmar entre elas (Q-053). A soma anterior
 * é coluna a coluna; o {@code saldoDia} fica no domínio (Q-035).
 */
public final class ConsultaConsolidadoJdbcRepository implements ConsultaConsolidadoRepository {

  private static final String DIAS =
      """
      SELECT data_referencia, total_creditos, total_debitos, total_estornos_credito,
        total_estornos_debito, qtd_lancamentos, atualizado_em
      FROM consolidado_diario
      WHERE comerciante_id = :comercianteId AND data_referencia BETWEEN :inicio AND :fim
      """;

  private static final String ANTERIOR =
      """
      SELECT COALESCE(sum(total_creditos), 0) AS total_creditos,
        COALESCE(sum(total_debitos), 0) AS total_debitos,
        COALESCE(sum(total_estornos_credito), 0) AS total_estornos_credito,
        COALESCE(sum(total_estornos_debito), 0) AS total_estornos_debito,
        COALESCE(sum(qtd_lancamentos), 0) AS qtd_lancamentos
      FROM consolidado_diario
      WHERE comerciante_id = :comercianteId AND data_referencia < :inicio
      """;

  private final JdbcClient jdbc;
  private final TransactionTemplate leituraConsistente;
  private final Runnable entreConsultas;

  public ConsultaConsolidadoJdbcRepository(
      JdbcClient jdbc, TransactionTemplate leituraConsistente) {
    this(jdbc, leituraConsistente, () -> {});
  }

  /** Só para o IT da leitura consistente (AC-T023.1): roda entre as duas consultas. */
  ConsultaConsolidadoJdbcRepository(
      JdbcClient jdbc, TransactionTemplate leituraConsistente, Runnable entreConsultas) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    this.leituraConsistente = Objects.requireNonNull(leituraConsistente, "leituraConsistente");
    this.entreConsultas = Objects.requireNonNull(entreConsultas, "entreConsultas");
  }

  @Override
  public LeituraDoPeriodo ler(UUID comercianteId, LocalDate inicio, LocalDate fim) {
    return leituraConsistente.execute(
        status -> {
          Map<LocalDate, MovimentoGravado> dias = new HashMap<>();
          jdbc.sql(DIAS)
              .param("comercianteId", comercianteId)
              .param("inicio", inicio)
              .param("fim", fim)
              .query(
                  (linha, numero) ->
                      dias.put(
                          linha.getObject("data_referencia", LocalDate.class),
                          new MovimentoGravado(
                              movimento(linha),
                              linha.getObject("atualizado_em", OffsetDateTime.class).toInstant())))
              .list();
          entreConsultas.run();
          MovimentoDiario anterior =
              jdbc.sql(ANTERIOR)
                  .param("comercianteId", comercianteId)
                  .param("inicio", inicio)
                  .query((linha, numero) -> movimento(linha))
                  .single();
          return new LeituraDoPeriodo(anterior, dias);
        });
  }

  /**
   * A soma de {@code INTEGER} no PostgreSQL é {@code BIGINT}; acima de {@code int} lança, como a
   * soma do domínio (Q-040).
   */
  private static MovimentoDiario movimento(ResultSet linha) throws SQLException {
    return new MovimentoDiario(
        linha.getBigDecimal("total_creditos"),
        linha.getBigDecimal("total_debitos"),
        linha.getBigDecimal("total_estornos_credito"),
        linha.getBigDecimal("total_estornos_debito"),
        Math.toIntExact(linha.getLong("qtd_lancamentos")));
  }
}
