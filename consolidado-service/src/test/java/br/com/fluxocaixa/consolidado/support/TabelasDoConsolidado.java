package br.com.fluxocaixa.consolidado.support;

import br.com.fluxocaixa.consolidado.domain.ChaveDiaria;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Leitura direta das tabelas nos ITs, com SQL próprio e como {@code consolidado_owner}: a leitura
 * de produção da projeção é da T-023, e o teste não deve depender do código que ele verifica.
 */
public final class TabelasDoConsolidado {

  private TabelasDoConsolidado() {}

  /** Todas as linhas de {@code consolidado_diario}. */
  public static Map<ChaveDiaria, MovimentoDiario> dias() {
    Map<ChaveDiaria, MovimentoDiario> dias = new HashMap<>();
    consultar(
        "SELECT comerciante_id, data_referencia, total_creditos, total_debitos,"
            + " total_estornos_credito, total_estornos_debito, qtd_lancamentos"
            + " FROM consolidado_diario",
        linhas -> {
          while (linhas.next()) {
            dias.put(
                new ChaveDiaria(
                    linhas.getObject("comerciante_id", UUID.class),
                    linhas.getObject("data_referencia", LocalDate.class)),
                new MovimentoDiario(
                    linhas.getBigDecimal("total_creditos"),
                    linhas.getBigDecimal("total_debitos"),
                    linhas.getBigDecimal("total_estornos_credito"),
                    linhas.getBigDecimal("total_estornos_debito"),
                    linhas.getInt("qtd_lancamentos")));
          }
          return null;
        });
    return Map.copyOf(dias);
  }

  /** {@code atualizado_em} da linha, se existir. */
  public static Optional<Instant> atualizadoEm(ChaveDiaria chave) {
    return instante(
        "SELECT atualizado_em FROM consolidado_diario"
            + " WHERE comerciante_id = ? AND data_referencia = ?",
        chave.comercianteId(),
        chave.dataReferencia());
  }

  /** {@code processado_em} do evento, se ele estiver em {@code evento_processado}. */
  public static Optional<Instant> processadoEm(UUID eventId) {
    return instante("SELECT processado_em FROM evento_processado WHERE event_id = ?", eventId);
  }

  public static int eventosProcessados() {
    return consultar(
        "SELECT count(*) FROM evento_processado",
        linhas -> {
          linhas.next();
          return linhas.getInt(1);
        });
  }

  /** Executa um comando como {@code consolidado_owner} (preparação de cenário). */
  public static void executarComoOwner(String sql, Object... parametros) {
    try (Connection owner = PostgresConsolidado.conectarComoOwner();
        PreparedStatement comando = owner.prepareStatement(sql)) {
      vincular(comando, parametros);
      comando.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("falha ao preparar o cenário", e);
    }
  }

  private static Optional<Instant> instante(String sql, Object... parametros) {
    return consultar(
        sql,
        linhas ->
            linhas.next()
                ? Optional.of(linhas.getObject(1, OffsetDateTime.class).toInstant())
                : Optional.empty(),
        parametros);
  }

  private interface Leitor<T> {
    T ler(ResultSet linhas) throws SQLException;
  }

  private static <T> T consultar(String sql, Leitor<T> leitor, Object... parametros) {
    try (Connection owner = PostgresConsolidado.conectarComoOwner();
        PreparedStatement consulta = owner.prepareStatement(sql)) {
      vincular(consulta, parametros);
      try (ResultSet linhas = consulta.executeQuery()) {
        return leitor.ler(linhas);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("falha ao ler as tabelas do consolidado", e);
    }
  }

  private static void vincular(PreparedStatement comando, Object... parametros)
      throws SQLException {
    for (int i = 0; i < parametros.length; i++) {
      comando.setObject(i + 1, parametros[i]);
    }
  }
}
