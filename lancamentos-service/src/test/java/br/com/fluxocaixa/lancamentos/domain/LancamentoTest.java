package br.com.fluxocaixa.lancamentos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("RF-001")
@Tag("RF-002")
@Tag("RNF-004")
class LancamentoTest {

  static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  static final UUID COMERCIANTE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
  static final PoliticaLancamento POLITICA = PoliticaLancamento.padrao();
  static final Clock AGORA = DataReferenciaTest.relogio("2026-10-22T12:00:00Z");

  @Test
  void registraCreditoComDadosDoComandoEDoRelogio() {
    Instant ocorrencia = Instant.parse("2026-10-21T15:00:00Z");

    Lancamento lancamento =
        Lancamento.registrar(
            ID,
            novo(TipoLancamento.CREDITO, "100", "Venda balcão", ocorrencia, "PDV-1"),
            POLITICA,
            AGORA);

    assertThat(lancamento.id()).isEqualTo(ID);
    assertThat(lancamento.comercianteId()).isEqualTo(COMERCIANTE);
    assertThat(lancamento.tipo()).isEqualTo(TipoLancamento.CREDITO);
    assertThat(lancamento.valor().valor()).isEqualByComparingTo("100.00");
    assertThat(lancamento.descricao()).isEqualTo("Venda balcão");
    assertThat(lancamento.dataOcorrencia()).isEqualTo(ocorrencia);
    assertThat(lancamento.dataReferencia()).isEqualTo(LocalDate.parse("2026-10-21"));
    assertThat(lancamento.identificadorExterno()).isEqualTo("PDV-1");
    assertThat(lancamento.referenciaLancamentoId()).isNull();
    assertThat(lancamento.tipoOriginal()).isNull();
    assertThat(lancamento.criadoEm()).isEqualTo(AGORA.instant());
  }

  @Test
  void dataOcorrenciaOmitidaUsaOInstanteDoRelogio() {
    Lancamento lancamento =
        Lancamento.registrar(
            ID, novo(TipoLancamento.CREDITO, "1.00", null, null, null), POLITICA, AGORA);

    assertThat(lancamento.dataOcorrencia()).isEqualTo(AGORA.instant());
    assertThat(lancamento.dataReferencia()).isEqualTo(LocalDate.parse("2026-10-22"));
  }

  @Test
  void debitoNaoDependeDeSaldo() {
    Lancamento debito =
        Lancamento.registrar(
            ID, novo(TipoLancamento.DEBITO, "10000000.00", null, null, null), POLITICA, AGORA);

    assertThat(debito.tipo()).isEqualTo(TipoLancamento.DEBITO);
    assertThat(debito.valor().valor()).isEqualByComparingTo("10000000.00");
  }

  @Test
  void registrarNaoAceitaEstorno() {
    assertThatThrownBy(
            () ->
                Lancamento.registrar(
                    ID, novo(TipoLancamento.ESTORNO, "1.00", null, null, null), POLITICA, AGORA))
        .isInstanceOfSatisfying(
            LancamentoInvalidoException.class,
            e ->
                assertThat(e.erros())
                    .extracting("campo", "codigo")
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("tipo", "tipo-invalido")));
  }

  @Test
  void reuneTodosOsErrosDeCampo() {
    String descricaoLonga = "x".repeat(256);
    String identificadorLongo = "y".repeat(101);

    assertThatThrownBy(
            () ->
                Lancamento.registrar(
                    ID,
                    new Lancamento.Novo(
                        COMERCIANTE, null, null, descricaoLonga, null, identificadorLongo),
                    POLITICA,
                    AGORA))
        .isInstanceOfSatisfying(
            LancamentoInvalidoException.class,
            e ->
                assertThat(e.erros())
                    .extracting("campo")
                    .containsExactlyInAnyOrder(
                        "tipo", "valor", "descricao", "identificadorExterno"));
  }

  @Test
  void aceitaTextosNoLimiteDeTamanho() {
    Lancamento lancamento =
        Lancamento.registrar(
            ID,
            novo(TipoLancamento.CREDITO, "1.00", "x".repeat(255), null, "y".repeat(100)),
            POLITICA,
            AGORA);

    assertThat(lancamento.descricao()).hasSize(255);
    assertThat(lancamento.identificadorExterno()).hasSize(100);
  }

  @Test
  void errosDeCampoVemAntesDasRegrasDeData() {
    Instant futuro = Instant.parse("2027-01-01T00:00:00Z");

    assertThatThrownBy(
            () ->
                Lancamento.registrar(
                    ID, novo(TipoLancamento.CREDITO, "0", null, futuro, null), POLITICA, AGORA))
        .isInstanceOf(LancamentoInvalidoException.class);
  }

  @Test
  void dataForaDaJanelaGeraRegraDeNegocio() {
    Instant futuro = Instant.parse("2027-01-01T00:00:00Z");

    assertThatThrownBy(
            () ->
                Lancamento.registrar(
                    ID, novo(TipoLancamento.CREDITO, "1.00", null, futuro, null), POLITICA, AGORA))
        .isInstanceOfSatisfying(
            RegraDeNegocioException.class,
            e -> assertThat(e.codigo()).isEqualTo(RegraDeNegocioException.DATA_FUTURA));
  }

  @Test
  void reconstituirAceitaLancamentoAntigoForaDaJanela() {
    LocalDate quarentaDiasAtras = LocalDate.parse("2026-09-12");

    Lancamento antigo =
        Lancamento.reconstituir(dados(TipoLancamento.CREDITO, null, null, quarentaDiasAtras));

    assertThat(antigo.dataReferencia()).isEqualTo(quarentaDiasAtras);
  }

  @Test
  void reconstituirRejeitaEstornoSemReferencia() {
    assertThatThrownBy(
            () ->
                Lancamento.reconstituir(
                    dados(TipoLancamento.ESTORNO, null, TipoLancamento.CREDITO, hoje())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Lancamento.reconstituir(
                    dados(TipoLancamento.ESTORNO, UUID.randomUUID(), null, hoje())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituirRejeitaCreditoOuDebitoComReferencia() {
    assertThatThrownBy(
            () ->
                Lancamento.reconstituir(
                    dados(TipoLancamento.CREDITO, UUID.randomUUID(), null, hoje())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Lancamento.reconstituir(
                    dados(TipoLancamento.DEBITO, null, TipoLancamento.CREDITO, hoje())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconstituirRejeitaEstornoComTipoOriginalEstorno() {
    assertThatThrownBy(
            () ->
                Lancamento.reconstituir(
                    dados(
                        TipoLancamento.ESTORNO, UUID.randomUUID(), TipoLancamento.ESTORNO, hoje())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void igualdadePorValorEToStringSemDadosFinanceiros() {
    Lancamento.Dados dados = dados(TipoLancamento.CREDITO, null, null, hoje());

    Lancamento a = Lancamento.reconstituir(dados);
    Lancamento b = Lancamento.reconstituir(dados);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a.toString()).doesNotContain("123.45").doesNotContain("Venda");
  }

  static Lancamento.Novo novo(
      TipoLancamento tipo, String valor, String descricao, Instant ocorrencia, String externo) {
    return new Lancamento.Novo(
        COMERCIANTE, tipo, new BigDecimal(valor), descricao, ocorrencia, externo);
  }

  static Lancamento.Dados dados(
      TipoLancamento tipo, UUID referencia, TipoLancamento tipoOriginal, LocalDate dataReferencia) {
    return new Lancamento.Dados(
        ID,
        COMERCIANTE,
        tipo,
        Dinheiro.reconstituir(new BigDecimal("123.45")),
        "Venda",
        Instant.parse("2026-09-12T15:00:00Z"),
        dataReferencia,
        referencia,
        tipoOriginal,
        null,
        Instant.parse("2026-09-12T15:00:01Z"));
  }

  private static LocalDate hoje() {
    return LocalDate.parse("2026-10-22");
  }
}
