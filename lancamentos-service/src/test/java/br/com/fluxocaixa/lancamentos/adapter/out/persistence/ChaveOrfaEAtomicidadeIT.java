package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * AC-007.6 e AC-T012.1: falha na gravação da outbox, depois de inserir chave e lançamento, desfaz
 * tudo; o retry com a mesma chave é processado normalmente.
 */
@Tag("RF-007")
@Tag("RNF-004")
class ChaveOrfaEAtomicidadeIT extends IntegracaoPostgres {

  @MockitoSpyBean private OutboxRepository outbox;
  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private JdbcClient jdbc;

  @AfterEach
  void restaurarOutbox() {
    reset(outbox);
  }

  @Test
  void falhaNaOutboxNaoDeixaChaveLancamentoNemEvento() {
    UUID comerciante = UUID.randomUUID();
    var comando =
        new RegistrarLancamentoCommand(
            comerciante,
            "orfa-" + UUID.randomUUID(),
            TipoLancamento.DEBITO,
            new BigDecimal("75.30"),
            "Fornecedor",
            null,
            "nf-orfa",
            null);
    doThrow(new IllegalStateException("falha injetada na outbox"))
        .doCallRealMethod()
        .when(outbox)
        .inserir(any());

    assertThatThrownBy(() -> registrarLancamento.registrar(comando))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("falha injetada na outbox");

    assertThat(Contagens.de(jdbc, comerciante).lancamentos())
        .as("o lançamento inserido antes da falha foi desfeito")
        .isZero();
    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(0, 0, 0));

    var retry = registrarLancamento.registrar(comando);

    assertThat(retry.replay()).isFalse();
    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(1, 1, 1));
    assertThat(
            jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?")
                .param(retry.lancamento().id())
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void semFalhaInjetadaOSpyDelegaAoAdapterReal() {
    UUID comerciante = UUID.randomUUID();
    doCallRealMethod().when(outbox).inserir(any());

    registrarLancamento.registrar(
        new RegistrarLancamentoCommand(
            comerciante,
            "controle-" + UUID.randomUUID(),
            TipoLancamento.CREDITO,
            new BigDecimal("1.00"),
            null,
            null,
            null,
            null));

    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(1, 1, 1));
  }
}
