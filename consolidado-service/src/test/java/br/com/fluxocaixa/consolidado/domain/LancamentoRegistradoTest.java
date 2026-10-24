package br.com.fluxocaixa.consolidado.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-T020.2: invariantes do evento no domínio (Q-034). */
@Tag("RF-008")
@Tag("RNF-004")
class LancamentoRegistradoTest {

  private static final UUID EVENTO = GeradorDeEventos.uuid(1);
  private static final UUID COMERCIANTE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
  private static final UUID LANCAMENTO = GeradorDeEventos.uuid(2);
  private static final UUID ORIGINAL = GeradorDeEventos.uuid(3);
  private static final LocalDate DIA = LocalDate.parse("2026-10-22");

  @ParameterizedTest
  @ValueSource(strings = {"100", "100.0", "100.00"})
  void valorComAteDuasCasasENormalizadoParaEscala2(String valor) {
    LancamentoRegistrado evento = credito(new BigDecimal(valor));

    assertThat(evento.valor()).isEqualTo(new BigDecimal("100.00"));
    assertThat(evento.valor().scale()).isEqualTo(2);
  }

  @Test
  void aceitaEstornoComTipoOriginalEReferencia() {
    LancamentoRegistrado estorno =
        new LancamentoRegistrado(
            EVENTO,
            COMERCIANTE,
            LANCAMENTO,
            TipoLancamento.ESTORNO,
            new BigDecimal("1.00"),
            DIA,
            TipoLancamento.DEBITO,
            ORIGINAL);

    assertThat(estorno.tipoOriginal()).isEqualTo(TipoLancamento.DEBITO);
    assertThat(estorno.referenciaLancamentoId()).isEqualTo(ORIGINAL);
  }

  @Test
  void naoHaTetoDeValorNoConsolidado() {
    assertThat(credito(new BigDecimal("9999999999999999.99")).valor())
        .isEqualTo(new BigDecimal("9999999999999999.99"));
  }

  @Test
  void rejeitaValorComMaisDeDuasCasasSemArredondar() {
    assertErros(() -> credito(new BigDecimal("0.001")), "valor", "casas-decimais");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.00", "-0.01"})
  void rejeitaValorNaoPositivo(String valor) {
    assertErros(() -> credito(new BigDecimal(valor)), "valor", "nao-positivo");
  }

  @Test
  void rejeitaCadaObrigatorioNuloETodosAparecemJuntos() {
    assertThatThrownBy(
            () -> new LancamentoRegistrado(null, null, null, null, null, null, null, null))
        .isInstanceOf(EventoInvalidoException.class)
        .extracting(e -> ((EventoInvalidoException) e).erros())
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .extracting("campo", "codigo")
        .containsExactlyInAnyOrder(
            tuple("eventId", "obrigatorio"),
            tuple("comercianteId", "obrigatorio"),
            tuple("lancamentoId", "obrigatorio"),
            tuple("tipo", "obrigatorio"),
            tuple("valor", "obrigatorio"),
            tuple("dataReferencia", "obrigatorio"));
  }

  @Test
  void estornoSemTipoOriginalNemReferenciaListaOsDois() {
    assertThatThrownBy(() -> evento(TipoLancamento.ESTORNO, null, null))
        .isInstanceOf(EventoInvalidoException.class)
        .extracting(e -> ((EventoInvalidoException) e).erros())
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .extracting("campo", "codigo")
        .containsExactlyInAnyOrder(
            tuple("tipoOriginal", "obrigatorio-no-estorno"),
            tuple("referenciaLancamentoId", "obrigatorio-no-estorno"));
  }

  @Test
  void creditoOuDebitoComTipoOriginalOuReferenciaERejeitado() {
    assertErros(
        () -> evento(TipoLancamento.CREDITO, TipoLancamento.DEBITO, null),
        "tipoOriginal",
        "proibido-fora-do-estorno");
    assertErros(
        () -> evento(TipoLancamento.DEBITO, null, ORIGINAL),
        "referenciaLancamentoId",
        "proibido-fora-do-estorno");
  }

  @Test
  void tipoOriginalEstornoERejeitado() {
    assertErros(
        () -> evento(TipoLancamento.ESTORNO, TipoLancamento.ESTORNO, ORIGINAL),
        "tipoOriginal",
        "estorno-de-estorno");
  }

  @Test
  void violacoesDeGruposDiferentesAparecemTodas() {
    assertThatThrownBy(
            () ->
                new LancamentoRegistrado(
                    null,
                    COMERCIANTE,
                    LANCAMENTO,
                    TipoLancamento.CREDITO,
                    new BigDecimal("1.001"),
                    DIA,
                    TipoLancamento.DEBITO,
                    ORIGINAL))
        .isInstanceOf(EventoInvalidoException.class)
        .extracting(e -> ((EventoInvalidoException) e).erros())
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .extracting("campo", "codigo")
        .containsExactlyInAnyOrder(
            tuple("eventId", "obrigatorio"),
            tuple("valor", "casas-decimais"),
            tuple("tipoOriginal", "proibido-fora-do-estorno"),
            tuple("referenciaLancamentoId", "proibido-fora-do-estorno"));
  }

  @Test
  void mensagemDoErroNaoTrazValorNemComerciante() {
    assertThatThrownBy(
            () ->
                new LancamentoRegistrado(
                    EVENTO,
                    COMERCIANTE,
                    LANCAMENTO,
                    TipoLancamento.CREDITO,
                    new BigDecimal("123.456"),
                    DIA,
                    TipoLancamento.CREDITO,
                    null))
        .isInstanceOf(EventoInvalidoException.class)
        .message()
        .doesNotContain("123.456", "123", COMERCIANTE.toString());
  }

  @Test
  void toStringNaoTrazValorNemComerciante() {
    String texto = credito(new BigDecimal("4321.98")).toString();

    assertThat(texto).doesNotContain("4321", COMERCIANTE.toString()).contains(EVENTO.toString());
  }

  private static LancamentoRegistrado credito(BigDecimal valor) {
    return new LancamentoRegistrado(
        EVENTO, COMERCIANTE, LANCAMENTO, TipoLancamento.CREDITO, valor, DIA, null, null);
  }

  private static LancamentoRegistrado evento(
      TipoLancamento tipo, TipoLancamento tipoOriginal, UUID referencia) {
    return new LancamentoRegistrado(
        EVENTO,
        COMERCIANTE,
        LANCAMENTO,
        tipo,
        new BigDecimal("10.00"),
        DIA,
        tipoOriginal,
        referencia);
  }

  private static void assertErros(ThrowingCallable acao, String campo, String codigo) {
    assertThatThrownBy(acao)
        .isInstanceOf(EventoInvalidoException.class)
        .extracting(e -> ((EventoInvalidoException) e).erros())
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .containsExactly(new EventoInvalidoException.ErroCampo(campo, codigo));
  }
}
