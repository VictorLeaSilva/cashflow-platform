package br.com.fluxocaixa.consolidado.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.consolidado.application.port.out.ProjecaoRepository;
import br.com.fluxocaixa.consolidado.domain.ChaveDiaria;
import br.com.fluxocaixa.consolidado.domain.Efeito;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import br.com.fluxocaixa.consolidado.support.IntegracaoPostgres;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AC-008.1 e AC-008.2 (parte de banco), AC-T021.3 (exatidão), AC-T021.6 (falha atômica, Q-040) e
 * AC-T021.7 ({@code atualizado_em}, Q-038).
 */
@Tag("RF-008")
@Tag("RNF-004")
class ProjecaoJdbcRepositoryIT extends IntegracaoPostgres {

  private static final UUID COMERCIANTE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
  private static final LocalDate DIA = LocalDate.parse("2026-10-22");
  private static final ChaveDiaria CHAVE = new ChaveDiaria(COMERCIANTE, DIA);
  private static final Instant T1 = Instant.parse("2026-10-22T13:30:00.123456Z");
  private static final Instant T2 = T1.plusSeconds(5);

  @Autowired private ProjecaoRepository projecao;
  @Autowired private TransactionTemplate transacao;

  @Test
  void eventoNovoRegistraOEventIdESomaOEfeito() {
    UUID evento = id(1);

    boolean aplicado = projecao.aplicarSeNovo(evento, credito(CHAVE, "150.75"), T1);

    assertThat(aplicado).isTrue();
    assertThat(TabelasDoConsolidado.processadoEm(evento)).contains(T1);
    assertThat(TabelasDoConsolidado.dias())
        .containsExactly(Map.entry(CHAVE, movimento("150.75", "0", "0", "0", 1)));
    assertThat(TabelasDoConsolidado.atualizadoEm(CHAVE)).contains(T1);
  }

  @Test
  void rollbackDaTransacaoDeQuemChamaDesfazAsDuasTabelas() {
    transacao.executeWithoutResult(
        status -> {
          assertThat(projecao.aplicarSeNovo(id(1), credito(CHAVE, "10.00"), T1)).isTrue();
          status.setRollbackOnly();
        });

    assertThat(TabelasDoConsolidado.dias()).isEmpty();
    assertThat(TabelasDoConsolidado.eventosProcessados()).isZero();
  }

  @Test
  void mesmoEventIdComOutroEfeitoNaoMudaNada() {
    UUID evento = id(1);
    projecao.aplicarSeNovo(evento, credito(CHAVE, "10.00"), T1);
    Map<ChaveDiaria, MovimentoDiario> antes = TabelasDoConsolidado.dias();

    boolean reaplicado = projecao.aplicarSeNovo(evento, credito(CHAVE, "99.00"), T2);
    boolean outroDia =
        projecao.aplicarSeNovo(
            evento, credito(new ChaveDiaria(COMERCIANTE, DIA.plusDays(1)), "5.00"), T2);

    assertThat(reaplicado).isFalse();
    assertThat(outroDia).isFalse();
    assertThat(TabelasDoConsolidado.dias()).isEqualTo(antes);
    assertThat(TabelasDoConsolidado.atualizadoEm(CHAVE)).contains(T1);
    assertThat(TabelasDoConsolidado.processadoEm(evento)).contains(T1);
    assertThat(TabelasDoConsolidado.eventosProcessados()).isEqualTo(1);
  }

  @Test
  void valoresVoltamExatosComEscala2() {
    ChaveDiaria outroDia = new ChaveDiaria(COMERCIANTE, DIA.minusDays(30));
    projecao.aplicarSeNovo(id(1), credito(CHAVE, "0.01"), T1);
    projecao.aplicarSeNovo(id(2), credito(CHAVE, "10000000.00"), T1);
    projecao.aplicarSeNovo(id(3), efeito(CHAVE, "0", "0.10", "0", "0"), T1);
    projecao.aplicarSeNovo(id(4), efeito(CHAVE, "0", "0", "0.01", "0"), T1);
    projecao.aplicarSeNovo(id(5), efeito(CHAVE, "0", "0", "0", "9999999.99"), T1);
    projecao.aplicarSeNovo(id(6), credito(outroDia, "0.30"), T1);

    Map<ChaveDiaria, MovimentoDiario> dias = TabelasDoConsolidado.dias();

    assertThat(dias)
        .containsOnly(
            Map.entry(CHAVE, movimento("10000000.01", "0.10", "0.01", "9999999.99", 5)),
            Map.entry(outroDia, movimento("0.30", "0", "0", "0", 1)));
    // Lido direto do ResultSet, sem passar pelo construtor de MovimentoDiario (que normaliza a
    // escala): BigDecimal.equals compara também a escala.
    assertThat(TabelasDoConsolidado.totalCreditosComoGravado(CHAVE))
        .isEqualTo(new BigDecimal("10000000.01"));
    assertThat(dias.get(CHAVE).saldoDia()).isEqualTo(new BigDecimal("19999999.89"));
  }

  @Test
  void estouroDeNumericFalhaInteiroSemRegistrarOEvento() {
    TabelasDoConsolidado.executarComoOwner(
        "INSERT INTO consolidado_diario (comerciante_id, data_referencia, total_creditos,"
            + " total_debitos, total_estornos_credito, total_estornos_debito, qtd_lancamentos,"
            + " atualizado_em) VALUES (?, ?, 9999999999999999.99, 0, 0, 0, 1, ?)",
        COMERCIANTE,
        DIA,
        T1.atOffset(ZoneOffset.UTC));
    Map<ChaveDiaria, MovimentoDiario> antes = TabelasDoConsolidado.dias();
    UUID evento = id(1);

    assertThatThrownBy(() -> projecao.aplicarSeNovo(evento, credito(CHAVE, "0.01"), T2))
        .isInstanceOf(DataAccessException.class)
        .satisfies(ProjecaoJdbcRepositoryIT::foiEstouroNumerico);

    assertThat(TabelasDoConsolidado.dias()).isEqualTo(antes);
    assertThat(TabelasDoConsolidado.processadoEm(evento)).isEmpty();
    ChaveDiaria outroDia = new ChaveDiaria(COMERCIANTE, DIA.plusDays(1));
    assertThat(projecao.aplicarSeNovo(evento, credito(outroDia, "0.01"), T2)).isTrue();
    assertThat(TabelasDoConsolidado.dias().get(outroDia))
        .isEqualTo(movimento("0.01", "0", "0", "0", 1));
  }

  @Test
  void estouroDaQuantidadeFalhaInteiroSemRegistrarOEvento() {
    TabelasDoConsolidado.executarComoOwner(
        "INSERT INTO consolidado_diario (comerciante_id, data_referencia, total_creditos,"
            + " total_debitos, total_estornos_credito, total_estornos_debito, qtd_lancamentos,"
            + " atualizado_em) VALUES (?, ?, 1.00, 0, 0, 0, 2147483647, ?)",
        COMERCIANTE,
        DIA,
        T1.atOffset(ZoneOffset.UTC));
    Map<ChaveDiaria, MovimentoDiario> antes = TabelasDoConsolidado.dias();

    assertThatThrownBy(() -> projecao.aplicarSeNovo(id(1), credito(CHAVE, "1.00"), T2))
        .isInstanceOf(DataAccessException.class)
        .satisfies(ProjecaoJdbcRepositoryIT::foiEstouroNumerico);

    assertThat(TabelasDoConsolidado.dias()).isEqualTo(antes);
    assertThat(TabelasDoConsolidado.eventosProcessados()).isZero();
  }

  @Test
  void atualizadoEmAvancaENuncaRecua() {
    projecao.aplicarSeNovo(id(1), credito(CHAVE, "1.00"), T1);
    assertThat(TabelasDoConsolidado.atualizadoEm(CHAVE)).contains(T1);

    projecao.aplicarSeNovo(id(2), credito(CHAVE, "1.00"), T2);
    assertThat(TabelasDoConsolidado.atualizadoEm(CHAVE)).contains(T2);

    projecao.aplicarSeNovo(id(3), credito(CHAVE, "1.00"), T1.minusSeconds(60));
    assertThat(TabelasDoConsolidado.atualizadoEm(CHAVE)).contains(T2);
    assertThat(TabelasDoConsolidado.dias().get(CHAVE).qtdLancamentos()).isEqualTo(3);
  }

  private static Efeito credito(ChaveDiaria chave, String valor) {
    return efeito(chave, valor, "0", "0", "0");
  }

  private static Efeito efeito(
      ChaveDiaria chave,
      String creditos,
      String debitos,
      String estornosCredito,
      String estornosDebito) {
    return new Efeito(chave, movimento(creditos, debitos, estornosCredito, estornosDebito, 1));
  }

  private static MovimentoDiario movimento(
      String creditos, String debitos, String estornosCredito, String estornosDebito, int qtd) {
    return new MovimentoDiario(
        new BigDecimal(creditos),
        new BigDecimal(debitos),
        new BigDecimal(estornosCredito),
        new BigDecimal(estornosDebito),
        qtd);
  }

  /** SQLState 22003 ({@code numeric_value_out_of_range}) na cadeia de causas. */
  private static void foiEstouroNumerico(Throwable erro) {
    Throwable causa = erro;
    while (causa != null && !(causa instanceof SQLException)) {
      causa = causa.getCause();
    }
    assertThat(causa).as("SQLException na cadeia de causas de %s", erro).isNotNull();
    assertThat(((SQLException) causa).getSQLState()).isEqualTo("22003");
  }

  private static UUID id(int n) {
    return new UUID(0L, n);
  }
}
