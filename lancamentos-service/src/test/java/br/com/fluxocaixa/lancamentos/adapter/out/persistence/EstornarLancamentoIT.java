package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.lancamentos.application.EstornarLancamento;
import br.com.fluxocaixa.lancamentos.application.EstornarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.application.LancamentoJaEstornadoException;
import br.com.fluxocaixa.lancamentos.application.LancamentoNaoEncontradoException;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.RegraDeNegocioException;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Caso de uso do estorno com PostgreSQL real: AC-003.1 (dados do original, original intacto, evento
 * na mesma transação), AC-003.2/AC-009.2 (data do estorno), AC-003.3/AC-010.1 (404 sem gravar) e
 * AC-003.4 (já estornado, estorno de estorno).
 */
@Tag("RF-003")
@Tag("RF-009")
@Tag("RF-010")
class EstornarLancamentoIT extends IntegracaoPostgres {

  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private EstornarLancamento estornarLancamento;
  @Autowired private JdbcClient jdbc;
  @Autowired private RelogioFixo.Controlado relogio;

  @Test
  void estornoCopiaValorETipoEDeixaOOriginalIntacto() {
    UUID comerciante = UUID.randomUUID();
    Lancamento original = registrar(comerciante, TipoLancamento.CREDITO, "250.75", null);
    Map<String, Object> linhaAntes = linha(original.id());

    var resultado = estornarLancamento.estornar(comando(comerciante, original.id(), "est-1"));

    Lancamento estorno = resultado.lancamento();
    assertThat(resultado.replay()).isFalse();
    assertThat(estorno.tipo()).isEqualTo(TipoLancamento.ESTORNO);
    assertThat(estorno.valor()).isEqualTo(original.valor());
    assertThat(estorno.tipoOriginal()).isEqualTo(TipoLancamento.CREDITO);
    assertThat(estorno.referenciaLancamentoId()).isEqualTo(original.id());
    assertThat(estorno.comercianteId()).isEqualTo(comerciante);
    assertThat(linha(original.id())).isEqualTo(linhaAntes);
    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(2, 2, 2));
    assertThat(
            jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?")
                .param(estorno.id())
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  /** Última linha da tabela de 03 §4.1: estorno às 00:10 de 22/10 de um lançamento de 15/10. */
  @Test
  void dataDeReferenciaEhADoEstornoNaoADoOriginal() {
    UUID comerciante = UUID.randomUUID();
    relogio.ajustar(Instant.parse("2026-10-22T03:10:00Z"));
    Lancamento original =
        registrar(
            comerciante, TipoLancamento.DEBITO, "40.00", Instant.parse("2026-10-15T15:00:00Z"));

    Lancamento estorno =
        estornarLancamento.estornar(comando(comerciante, original.id(), "est-data")).lancamento();

    assertThat(original.dataReferencia()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(estorno.dataReferencia()).isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(linha(original.id()))
        .containsEntry("data_referencia", java.sql.Date.valueOf("2026-10-15"));
  }

  @Test
  void originalInexistenteOuDeOutroComercianteNaoGravaNada() {
    UUID comerciante = UUID.randomUUID();
    UUID outro = UUID.randomUUID();
    Lancamento doOutro = registrar(outro, TipoLancamento.CREDITO, "10.00", null);

    assertThatThrownBy(
            () -> estornarLancamento.estornar(comando(comerciante, UUID.randomUUID(), "est-404")))
        .isInstanceOf(LancamentoNaoEncontradoException.class);
    assertThatThrownBy(
            () -> estornarLancamento.estornar(comando(comerciante, doOutro.id(), "est-cruzado")))
        .isInstanceOf(LancamentoNaoEncontradoException.class)
        .hasMessageNotContaining(doOutro.id().toString());

    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(0, 0, 0));
    assertThat(Contagens.de(jdbc, outro)).isEqualTo(new Contagens(1, 1, 1));
  }

  @Test
  void jaEstornadoEEstornoDeEstornoNaoGravamNada() {
    UUID comerciante = UUID.randomUUID();
    Lancamento original = registrar(comerciante, TipoLancamento.CREDITO, "10.00", null);
    Lancamento estorno =
        estornarLancamento.estornar(comando(comerciante, original.id(), "est-a")).lancamento();

    assertThatThrownBy(
            () -> estornarLancamento.estornar(comando(comerciante, original.id(), "est-b")))
        .isInstanceOf(LancamentoJaEstornadoException.class);
    assertThatThrownBy(
            () -> estornarLancamento.estornar(comando(comerciante, estorno.id(), "est-c")))
        .isInstanceOfSatisfying(
            RegraDeNegocioException.class,
            erro ->
                assertThat(erro.codigo()).isEqualTo(RegraDeNegocioException.ESTORNO_DE_ESTORNO));

    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(2, 2, 2));
  }

  private Lancamento registrar(
      UUID comerciante, TipoLancamento tipo, String valor, Instant ocorrencia) {
    return registrarLancamento
        .registrar(
            new RegistrarLancamentoCommand(
                comerciante,
                "orig-" + UUID.randomUUID(),
                tipo,
                new BigDecimal(valor),
                null,
                ocorrencia,
                null,
                null))
        .lancamento();
  }

  private static EstornarLancamentoCommand comando(UUID comerciante, UUID original, String chave) {
    return new EstornarLancamentoCommand(comerciante, chave, original, null);
  }

  private Map<String, Object> linha(UUID id) {
    return jdbc.sql("SELECT * FROM lancamento WHERE id = ?").param(id).query().singleRow();
  }
}
