package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.domain.RegraDeNegocioException;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * AC-T012.6 (Q-011): o replay não reaplica as regras de data. Um comando aceito no último dia da
 * janela (A4) e repetido no dia seguinte devolve o original; um comando novo com a mesma data é
 * rejeitado sem gravar nada, nem a chave.
 */
@Tag("RF-007")
@Tag("RF-009")
class ReplayForaDaJanelaIT extends IntegracaoPostgres {

  /** 12:00 em São Paulo de 2026-09-23: hoje(SP) − 30 dias, ainda dentro da janela. */
  private static final Instant LIMITE_DA_JANELA = Instant.parse("2026-09-23T15:00:00Z");

  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private RelogioFixo.Controlado relogio;
  @Autowired private JdbcClient jdbc;

  @AfterEach
  void restaurarRelogio() {
    relogio.restaurar();
  }

  @Test
  void replayDepoisDaJanelaDevolveOOriginal() {
    UUID comerciante = UUID.randomUUID();
    var comando = comando(comerciante, "retroativo-" + UUID.randomUUID());
    var original = registrarLancamento.registrar(comando);
    assertThat(original.lancamento().dataReferencia()).isEqualTo(LocalDate.of(2026, 9, 23));

    relogio.ajustar(RelogioFixo.AGORA.plus(Duration.ofDays(1)));
    var replay = registrarLancamento.registrar(comando);

    assertThat(replay.replay()).isTrue();
    assertThat(replay.lancamento()).isEqualTo(original.lancamento());
    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(1, 1, 1));
  }

  @Test
  void comandoNovoForaDaJanelaEhRejeitadoSemGravarAChave() {
    UUID comerciante = UUID.randomUUID();
    relogio.ajustar(RelogioFixo.AGORA.plus(Duration.ofDays(1)));

    assertThatThrownBy(
            () -> registrarLancamento.registrar(comando(comerciante, "novo-" + UUID.randomUUID())))
        .isInstanceOfSatisfying(
            RegraDeNegocioException.class,
            erro ->
                assertThat(erro.codigo())
                    .isEqualTo(RegraDeNegocioException.DATA_RETROATIVA_EXCEDIDA));
    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(0, 0, 0));
  }

  private static RegistrarLancamentoCommand comando(UUID comerciante, String chave) {
    return new RegistrarLancamentoCommand(
        comerciante,
        chave,
        TipoLancamento.CREDITO,
        new BigDecimal("30.00"),
        "Venda retroativa",
        LIMITE_DA_JANELA,
        null,
        null);
  }
}
