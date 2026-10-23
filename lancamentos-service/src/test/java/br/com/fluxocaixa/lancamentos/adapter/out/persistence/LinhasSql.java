package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** SQL cru para os ITs que exercitam o banco abaixo do repositório (constraints e permissões). */
final class LinhasSql {

  static final String PERMISSAO_NEGADA = "42501";
  static final String VIOLACAO_CHECK = "23514";
  static final String VIOLACAO_UNICIDADE = "23505";
  static final String VIOLACAO_FK = "23503";

  private static final String INSERT_LANCAMENTO =
      """
      INSERT INTO lancamento (id, comerciante_id, tipo, valor, data_ocorrencia, data_referencia,
                              referencia_lancamento_id, tipo_original, identificador_externo)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private LinhasSql() {}

  /** Uma linha de {@code lancamento}; os campos não informados recebem valores válidos fixos. */
  record Linha(
      UUID id,
      UUID comercianteId,
      String tipo,
      BigDecimal valor,
      UUID referencia,
      String tipoOriginal,
      String identificadorExterno) {

    static Linha credito(UUID comercianteId) {
      return new Linha(
          UUID.randomUUID(), comercianteId, "CREDITO", new BigDecimal("10.00"), null, null, null);
    }

    static Linha estornoDe(Linha original) {
      return new Linha(
          UUID.randomUUID(),
          original.comercianteId(),
          "ESTORNO",
          original.valor(),
          original.id(),
          original.tipo(),
          null);
    }

    Linha comTipo(String novoTipo) {
      return new Linha(
          id, comercianteId, novoTipo, valor, referencia, tipoOriginal, identificadorExterno);
    }

    Linha comValor(BigDecimal novoValor) {
      return new Linha(
          id, comercianteId, tipo, novoValor, referencia, tipoOriginal, identificadorExterno);
    }

    Linha comReferencia(UUID novaReferencia, String novoTipoOriginal) {
      return new Linha(
          id, comercianteId, tipo, valor, novaReferencia, novoTipoOriginal, identificadorExterno);
    }

    Linha comIdentificadorExterno(String novoIdentificador) {
      return new Linha(id, comercianteId, tipo, valor, referencia, tipoOriginal, novoIdentificador);
    }

    Linha comNovoId() {
      return new Linha(
          UUID.randomUUID(),
          comercianteId,
          tipo,
          valor,
          referencia,
          tipoOriginal,
          identificadorExterno);
    }
  }

  static void inserir(Connection conexao, Linha linha) throws SQLException {
    try (PreparedStatement ps = conexao.prepareStatement(INSERT_LANCAMENTO)) {
      ps.setObject(1, linha.id());
      ps.setObject(2, linha.comercianteId());
      ps.setString(3, linha.tipo());
      ps.setBigDecimal(4, linha.valor());
      ps.setObject(5, OffsetDateTime.of(2026, 10, 22, 12, 0, 0, 0, ZoneOffset.UTC));
      ps.setObject(6, LocalDate.of(2026, 10, 22));
      ps.setObject(7, linha.referencia());
      ps.setString(8, linha.tipoOriginal());
      ps.setString(9, linha.identificadorExterno());
      ps.executeUpdate();
    }
  }

  static void inserirIdempotencyKey(Connection conexao, UUID comercianteId, UUID lancamentoId)
      throws SQLException {
    inserirIdempotencyKey(conexao, comercianteId, UUID.randomUUID().toString(), lancamentoId);
  }

  static void inserirIdempotencyKey(
      Connection conexao, UUID comercianteId, String chave, UUID lancamentoId) throws SQLException {
    try (PreparedStatement ps =
        conexao.prepareStatement(
            "INSERT INTO idempotency_key (comerciante_id, chave, request_hash, lancamento_id)"
                + " VALUES (?, ?, ?, ?)")) {
      ps.setObject(1, comercianteId);
      ps.setString(2, chave);
      ps.setString(3, "a".repeat(64));
      ps.setObject(4, lancamentoId);
      ps.executeUpdate();
    }
  }

  static void executar(Connection conexao, String sql) throws SQLException {
    try (var statement = conexao.createStatement()) {
      statement.execute(sql);
    }
  }

  static long contar(Connection conexao, String sql, UUID parametro) throws SQLException {
    try (PreparedStatement ps = conexao.prepareStatement(sql)) {
      ps.setObject(1, parametro);
      try (var rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  /** Verifica que a operação falha com o SQLSTATE esperado. */
  static void falhaCom(String sqlState, ThrowingCallable operacao) {
    assertThatThrownBy(operacao)
        .isInstanceOfSatisfying(
            SQLException.class, erro -> assertThat(erro.getSQLState()).isEqualTo(sqlState));
  }

  /** Verifica que a operação falha com o SQLSTATE e a constraint esperados. */
  static void falhaCom(String sqlState, String constraint, ThrowingCallable operacao) {
    assertThatThrownBy(operacao)
        .isInstanceOfSatisfying(
            SQLException.class,
            erro -> {
              assertThat(erro.getSQLState()).isEqualTo(sqlState);
              assertThat(erro.getMessage()).contains(constraint);
            });
  }
}
