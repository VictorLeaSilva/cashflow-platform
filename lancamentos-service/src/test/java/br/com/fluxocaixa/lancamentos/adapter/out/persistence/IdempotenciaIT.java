package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.lancamentos.application.IdempotencyKeyReuseException;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** AC-007.2, AC-007.3 e AC-007.5: replay, reuso de chave e escopo por comerciante. */
@Tag("RF-007")
class IdempotenciaIT extends IntegracaoPostgres {

  private static final String CHAVE = "pedido-2026-10-23-001";

  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private JdbcClient jdbc;

  @Test
  void mesmaChaveEMesmoComandoDevolvemOMesmoLancamento() {
    UUID comerciante = UUID.randomUUID();
    var comando = comando(comerciante, CHAVE, "100.00", "Venda", "req-1");

    var primeiro = registrarLancamento.registrar(comando);
    var segundo = registrarLancamento.registrar(comando);
    var terceiro = registrarLancamento.registrar(comando);

    assertThat(primeiro.replay()).isFalse();
    assertThat(segundo.replay()).isTrue();
    assertThat(terceiro.replay()).isTrue();
    assertThat(segundo.lancamento()).isEqualTo(primeiro.lancamento());
    assertThat(terceiro.lancamento()).isEqualTo(primeiro.lancamento());
    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(1, 1, 1));
  }

  @Test
  void comandoEquivalenteComOutroRequestIdEhReplay() {
    UUID comerciante = UUID.randomUUID();
    var original =
        new RegistrarLancamentoCommand(
            comerciante,
            CHAVE,
            TipoLancamento.CREDITO,
            new BigDecimal("100"),
            "Venda",
            OffsetDateTime.parse("2026-10-22T21:30:00-03:00").toInstant(),
            null,
            "req-1");
    var equivalente =
        new RegistrarLancamentoCommand(
            comerciante,
            CHAVE,
            TipoLancamento.CREDITO,
            new BigDecimal("100.00"),
            "Venda",
            OffsetDateTime.parse("2026-10-23T00:30:00Z").toInstant(),
            null,
            "req-2");

    var primeiro = registrarLancamento.registrar(original);
    var segundo = registrarLancamento.registrar(equivalente);

    assertThat(segundo.replay()).isTrue();
    assertThat(segundo.lancamento()).isEqualTo(primeiro.lancamento());
    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(1, 1, 1));
  }

  @Test
  void mesmaChaveComComandoDiferenteEhRejeitadaSemGravar() {
    UUID comerciante = UUID.randomUUID();
    registrarLancamento.registrar(comando(comerciante, CHAVE, "100.00", "Venda", null));

    assertThatThrownBy(
            () ->
                registrarLancamento.registrar(comando(comerciante, CHAVE, "100.01", "Venda", null)))
        .isInstanceOf(IdempotencyKeyReuseException.class);
    assertThatThrownBy(
            () ->
                registrarLancamento.registrar(
                    comando(comerciante, CHAVE, "100.00", "Outra venda", null)))
        .isInstanceOf(IdempotencyKeyReuseException.class);

    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(1, 1, 1));
  }

  @Test
  void mesmaChaveEmComerciantesDiferentesGeraLancamentosIndependentes() {
    UUID comercianteA = UUID.randomUUID();
    UUID comercianteB = UUID.randomUUID();

    var deA = registrarLancamento.registrar(comando(comercianteA, CHAVE, "10.00", "Venda", null));
    var deB = registrarLancamento.registrar(comando(comercianteB, CHAVE, "10.00", "Venda", null));

    assertThat(deA.replay()).isFalse();
    assertThat(deB.replay()).isFalse();
    assertThat(deB.lancamento().id()).isNotEqualTo(deA.lancamento().id());
    assertThat(deA.lancamento().comercianteId()).isEqualTo(comercianteA);
    assertThat(deB.lancamento().comercianteId()).isEqualTo(comercianteB);
    assertThat(Contagens.de(jdbc, comercianteA)).isEqualTo(new Contagens(1, 1, 1));
    assertThat(Contagens.de(jdbc, comercianteB)).isEqualTo(new Contagens(1, 1, 1));
  }

  private static RegistrarLancamentoCommand comando(
      UUID comerciante, String chave, String valor, String descricao, String requestId) {
    return new RegistrarLancamentoCommand(
        comerciante,
        chave,
        TipoLancamento.CREDITO,
        new BigDecimal(valor),
        descricao,
        null,
        null,
        requestId);
  }
}
