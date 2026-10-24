package br.com.fluxocaixa.consolidado.adapter.out.persistence;

import br.com.fluxocaixa.consolidado.application.port.out.ProjecaoRepository;
import br.com.fluxocaixa.consolidado.domain.Efeito;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ProjecaoRepository} com {@code JdbcClient} e SQL explícito (D-05). Deduplicação e
 * <i>upsert</i> aditivo são um único comando (Q-037): o CTE só devolve linha se o {@code eventId}
 * for novo, e o {@code INSERT ... ON CONFLICT DO UPDATE} soma os deltas sem ler antes. Estouro de
 * {@code NUMERIC(18,2)} ou {@code INTEGER} falha o comando inteiro, e a {@code DataAccessException}
 * sobe sem tradução (Q-040). Criado em {@code ConsumidorConfig}, com o pool do consumidor (T-022).
 */
public class ProjecaoJdbcRepository implements ProjecaoRepository {

  private static final String APLICAR_SE_NOVO =
      """
      WITH novo AS (
        INSERT INTO evento_processado (event_id, processado_em)
        VALUES (:eventId, :instante)
        ON CONFLICT (event_id) DO NOTHING
        RETURNING event_id)
      INSERT INTO consolidado_diario (comerciante_id, data_referencia, total_creditos,
        total_debitos, total_estornos_credito, total_estornos_debito, qtd_lancamentos,
        atualizado_em)
      SELECT :comercianteId, :dataReferencia, :creditos, :debitos, :estornosCredito,
        :estornosDebito, :qtd, :instante
      FROM novo
      ON CONFLICT (comerciante_id, data_referencia) DO UPDATE SET
        total_creditos = consolidado_diario.total_creditos + EXCLUDED.total_creditos,
        total_debitos = consolidado_diario.total_debitos + EXCLUDED.total_debitos,
        total_estornos_credito =
          consolidado_diario.total_estornos_credito + EXCLUDED.total_estornos_credito,
        total_estornos_debito =
          consolidado_diario.total_estornos_debito + EXCLUDED.total_estornos_debito,
        qtd_lancamentos = consolidado_diario.qtd_lancamentos + EXCLUDED.qtd_lancamentos,
        atualizado_em = GREATEST(consolidado_diario.atualizado_em, EXCLUDED.atualizado_em)
      """;

  private final JdbcClient jdbc;

  public ProjecaoJdbcRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean aplicarSeNovo(UUID eventId, Efeito efeito, Instant instante) {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(efeito, "efeito");
    Objects.requireNonNull(instante, "instante");
    MovimentoDiario delta = efeito.delta();
    int linhas =
        jdbc.sql(APLICAR_SE_NOVO)
            .param("eventId", eventId)
            .param("instante", instante.atOffset(ZoneOffset.UTC))
            .param("comercianteId", efeito.chave().comercianteId())
            .param("dataReferencia", efeito.chave().dataReferencia())
            .param("creditos", delta.totalCreditos())
            .param("debitos", delta.totalDebitos())
            .param("estornosCredito", delta.totalEstornosCredito())
            .param("estornosDebito", delta.totalEstornosDebito())
            .param("qtd", delta.qtdLancamentos())
            .update();
    return linhas == 1;
  }
}
