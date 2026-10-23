package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import br.com.fluxocaixa.lancamentos.application.IdentificadorExternoDuplicadoException;
import br.com.fluxocaixa.lancamentos.application.LancamentoJaEstornadoException;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.domain.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link LancamentoRepository} com {@code JdbcClient} e SQL explícito (D-05). Só insere e lê: o
 * role da aplicação não tem UPDATE/DELETE em {@code lancamento} (A8).
 */
@Repository
public class LancamentoJdbcRepository implements LancamentoRepository {

  private static final String INSERIR =
      """
      INSERT INTO lancamento (id, comerciante_id, tipo, valor, descricao, data_ocorrencia,
                              data_referencia, referencia_lancamento_id, tipo_original,
                              identificador_externo, criado_em)
      VALUES (:id, :comercianteId, :tipo, :valor, :descricao, :dataOcorrencia,
              :dataReferencia, :referenciaLancamentoId, :tipoOriginal,
              :identificadorExterno, :criadoEm)
      """;

  private static final String BUSCAR_POR_ID =
      """
      SELECT id, comerciante_id, tipo, valor, descricao, data_ocorrencia, data_referencia,
             referencia_lancamento_id, tipo_original, identificador_externo, criado_em
        FROM lancamento
       WHERE comerciante_id = :comercianteId AND id = :id
      """;

  private static final String UX_IDENTIFICADOR_EXTERNO = "ux_identificador_externo";
  private static final String UX_ESTORNO_UNICO = "ux_estorno_unico";

  private final JdbcClient jdbc;

  public LancamentoJdbcRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void inserir(Lancamento lancamento) {
    try {
      gravar(lancamento.dados());
    } catch (DuplicateKeyException e) {
      if (violou(e, UX_IDENTIFICADOR_EXTERNO)) {
        // A mensagem do banco traz o identificador e o comerciante: não é propagada (Q-015).
        throw new IdentificadorExternoDuplicadoException();
      }
      if (violou(e, UX_ESTORNO_UNICO)) {
        // Um estorno por lançamento (A5): a mensagem do banco traz o id do original (T-014).
        throw new LancamentoJaEstornadoException();
      }
      throw e;
    }
  }

  private void gravar(Lancamento.Dados dados) {
    jdbc.sql(INSERIR)
        .param("id", dados.id())
        .param("comercianteId", dados.comercianteId())
        .param("tipo", dados.tipo().name())
        .param("valor", dados.valor().valor())
        .param("descricao", dados.descricao())
        .param("dataOcorrencia", emUtc(dados.dataOcorrencia()))
        .param("dataReferencia", dados.dataReferencia())
        .param("referenciaLancamentoId", dados.referenciaLancamentoId())
        .param("tipoOriginal", nome(dados.tipoOriginal()))
        .param("identificadorExterno", dados.identificadorExterno())
        .param("criadoEm", emUtc(dados.criadoEm()))
        .update();
  }

  @Override
  public Optional<Lancamento> buscarPorId(UUID comercianteId, UUID id) {
    return jdbc.sql(BUSCAR_POR_ID)
        .param("comercianteId", comercianteId)
        .param("id", id)
        .query((rs, linha) -> reconstituir(rs))
        .optional();
  }

  private static boolean violou(DuplicateKeyException erro, String constraint) {
    String causa = NestedExceptionUtils.getMostSpecificCause(erro).getMessage();
    return causa != null && causa.contains(constraint);
  }

  private static Lancamento reconstituir(ResultSet rs) throws SQLException {
    String tipoOriginal = rs.getString("tipo_original");
    return Lancamento.reconstituir(
        new Lancamento.Dados(
            rs.getObject("id", UUID.class),
            rs.getObject("comerciante_id", UUID.class),
            TipoLancamento.valueOf(rs.getString("tipo")),
            Dinheiro.reconstituir(rs.getBigDecimal("valor")),
            rs.getString("descricao"),
            instante(rs, "data_ocorrencia"),
            rs.getObject("data_referencia", LocalDate.class),
            rs.getObject("referencia_lancamento_id", UUID.class),
            tipoOriginal != null ? TipoLancamento.valueOf(tipoOriginal) : null,
            rs.getString("identificador_externo"),
            instante(rs, "criado_em")));
  }

  /**
   * O driver PostgreSQL mapeia {@code TIMESTAMPTZ} para {@link OffsetDateTime}, não para Instant.
   */
  private static OffsetDateTime emUtc(Instant instante) {
    return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
  }

  private static Instant instante(ResultSet rs, String coluna) throws SQLException {
    return rs.getObject(coluna, OffsetDateTime.class).toInstant();
  }

  private static String nome(TipoLancamento tipo) {
    return tipo != null ? tipo.name() : null;
  }
}
