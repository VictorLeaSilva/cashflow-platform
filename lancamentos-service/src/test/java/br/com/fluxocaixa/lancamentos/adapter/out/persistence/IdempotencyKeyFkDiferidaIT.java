package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.VIOLACAO_FK;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.contar;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.falhaCom;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.inserir;
import static br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.inserirIdempotencyKey;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.adapter.out.persistence.LinhasSql.Linha;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.PostgresLancamentos;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-T011.8: a FK diferida impede confirmar chave de idempotência sem lançamento (03 §3.1.1). */
@Tag("RF-007")
@Tag("RNF-004")
class IdempotencyKeyFkDiferidaIT extends IntegracaoPostgres {

  private static final String CONTAR_CHAVES =
      "SELECT count(*) FROM idempotency_key WHERE lancamento_id = ?";

  @Test
  void commitFalhaQuandoOLancamentoNaoExiste() throws SQLException {
    Linha nuncaInserida = Linha.credito(UUID.randomUUID());
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      app.setAutoCommit(false);

      // O INSERT da chave passa: a verificação da FK fica para o COMMIT.
      inserirIdempotencyKey(app, nuncaInserida.comercianteId(), nuncaInserida.id());

      falhaCom(VIOLACAO_FK, "fk_idem_lancamento", app::commit);
      app.rollback();
      app.setAutoCommit(true);
      assertThat(contar(app, CONTAR_CHAVES, nuncaInserida.id())).isZero();
    }
  }

  @Test
  void commitPassaQuandoOLancamentoEhInseridoAntesDoCommit() throws SQLException {
    Linha linha = Linha.credito(UUID.randomUUID());
    try (Connection app = PostgresLancamentos.conectarComoApp()) {
      app.setAutoCommit(false);

      // Ordem de 03 §3.1.1: chave primeiro, lançamento depois, na mesma transação.
      inserirIdempotencyKey(app, linha.comercianteId(), linha.id());
      inserir(app, linha);
      app.commit();

      app.setAutoCommit(true);
      assertThat(contar(app, CONTAR_CHAVES, linha.id())).isEqualTo(1);
    }
  }
}
