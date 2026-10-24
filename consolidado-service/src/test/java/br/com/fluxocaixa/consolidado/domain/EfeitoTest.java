package br.com.fluxocaixa.consolidado.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** AC-T020.1: regra de efeito de {@code 03 §3.2}, um delta por evento numa única chave. */
@Tag("RF-008")
class EfeitoTest {

  private static final UUID COMERCIANTE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
  private static final LocalDate DIA = LocalDate.parse("2026-10-22");
  private static final BigDecimal VALOR = new BigDecimal("150.75");
  private static final BigDecimal ZERO = new BigDecimal("0.00");

  static Stream<Arguments> tabelaDeEfeito() {
    return Stream.of(
        Arguments.of(TipoLancamento.CREDITO, null, new MovimentoDiario(VALOR, ZERO, ZERO, ZERO, 1)),
        Arguments.of(TipoLancamento.DEBITO, null, new MovimentoDiario(ZERO, VALOR, ZERO, ZERO, 1)),
        Arguments.of(
            TipoLancamento.ESTORNO,
            TipoLancamento.CREDITO,
            new MovimentoDiario(ZERO, ZERO, VALOR, ZERO, 1)),
        Arguments.of(
            TipoLancamento.ESTORNO,
            TipoLancamento.DEBITO,
            new MovimentoDiario(ZERO, ZERO, ZERO, VALOR, 1)));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("tabelaDeEfeito")
  void cadaLinhaDaTabelaDaOExatamenteODeltaListado(
      TipoLancamento tipo, TipoLancamento tipoOriginal, MovimentoDiario esperado) {
    UUID referencia = tipoOriginal == null ? null : GeradorDeEventos.uuid(9);
    LancamentoRegistrado evento =
        new LancamentoRegistrado(
            GeradorDeEventos.uuid(1),
            COMERCIANTE,
            GeradorDeEventos.uuid(2),
            tipo,
            VALOR,
            DIA,
            tipoOriginal,
            referencia);

    Efeito efeito = Efeito.de(evento);

    assertThat(efeito.chave()).isEqualTo(new ChaveDiaria(COMERCIANTE, DIA));
    assertThat(efeito.delta()).isEqualTo(esperado);
  }

  /** Exemplo golden {@code contracts/.../lancamento-registrado.v1.json}, transcrito. */
  @Test
  void exemploGoldenDeCreditoSomaEmTotalCreditosNoDiaDoEvento() {
    LancamentoRegistrado credito =
        new LancamentoRegistrado(
            UUID.fromString("a1b2c3d4-e5f6-4789-8abc-1234567890ab"),
            COMERCIANTE,
            UUID.fromString("b2c3d4e5-f6a7-4890-9bcd-2345678901bc"),
            TipoLancamento.CREDITO,
            new BigDecimal("150.75"),
            LocalDate.parse("2026-10-22"),
            null,
            null);

    Efeito efeito = Efeito.de(credito);

    assertThat(efeito.chave())
        .isEqualTo(new ChaveDiaria(COMERCIANTE, LocalDate.parse("2026-10-22")));
    assertThat(efeito.delta()).isEqualTo(new MovimentoDiario(VALOR, ZERO, ZERO, ZERO, 1));
  }

  /**
   * Exemplo golden {@code contracts/.../lancamento-registrado.v1.estorno.json}, transcrito: o
   * estorno soma no dia dele (23/10), não no do original (22/10), D-07.
   */
  @Test
  void exemploGoldenDeEstornoSomaEmEstornosDeCreditoNoDiaDoEstorno() {
    LancamentoRegistrado estorno =
        new LancamentoRegistrado(
            UUID.fromString("c3d4e5f6-a7b8-4901-8cde-3456789012cd"),
            COMERCIANTE,
            UUID.fromString("d4e5f6a7-b8c9-4012-9def-4567890123de"),
            TipoLancamento.ESTORNO,
            new BigDecimal("150.75"),
            LocalDate.parse("2026-10-23"),
            TipoLancamento.CREDITO,
            UUID.fromString("b2c3d4e5-f6a7-4890-9bcd-2345678901bc"));

    Efeito efeito = Efeito.de(estorno);

    assertThat(efeito.chave())
        .isEqualTo(new ChaveDiaria(COMERCIANTE, LocalDate.parse("2026-10-23")));
    assertThat(efeito.delta()).isEqualTo(new MovimentoDiario(ZERO, ZERO, VALOR, ZERO, 1));
  }
}
