package br.com.fluxocaixa.lancamentos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Tag("RF-003")
@Tag("RNF-004")
class EstornoTest {

  private static final UUID ID_ESTORNO = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  private static final PoliticaLancamento POLITICA = PoliticaLancamento.padrao();

  /** 00:10 de 22/10 em São Paulo. */
  private static final Clock MADRUGADA_22_10 = DataReferenciaTest.relogio("2026-10-22T03:10:00Z");

  @ParameterizedTest
  @EnumSource(
      value = TipoLancamento.class,
      names = {"CREDITO", "DEBITO"})
  void copiaValorETipoOriginalEReferenciaOOriginal(TipoLancamento tipo) {
    Lancamento original = original(tipo);

    Lancamento estorno = Lancamento.estornar(ID_ESTORNO, original, POLITICA, MADRUGADA_22_10);

    assertThat(estorno.id()).isEqualTo(ID_ESTORNO);
    assertThat(estorno.tipo()).isEqualTo(TipoLancamento.ESTORNO);
    assertThat(estorno.tipoOriginal()).isEqualTo(tipo);
    assertThat(estorno.referenciaLancamentoId()).isEqualTo(original.id());
    assertThat(estorno.valor()).isEqualTo(original.valor());
    assertThat(estorno.comercianteId()).isEqualTo(original.comercianteId());
    assertThat(estorno.dataOcorrencia()).isEqualTo(MADRUGADA_22_10.instant());
    assertThat(estorno.criadoEm()).isEqualTo(MADRUGADA_22_10.instant());
  }

  @Test
  void produzEfeitoNoDiaDoEstornoEmSaoPauloENaoNoDiaDoOriginal() {
    Lancamento original = original(TipoLancamento.CREDITO);

    Lancamento estorno = Lancamento.estornar(ID_ESTORNO, original, POLITICA, MADRUGADA_22_10);

    assertThat(original.dataReferencia()).isEqualTo(LocalDate.parse("2026-10-15"));
    assertThat(estorno.dataReferencia()).isEqualTo(LocalDate.parse("2026-10-22"));
  }

  @Test
  void naoAlteraOOriginal() {
    Lancamento original = original(TipoLancamento.CREDITO);
    Lancamento copia = Lancamento.reconstituir(original.dados());

    Lancamento.estornar(ID_ESTORNO, original, POLITICA, MADRUGADA_22_10);

    assertThat(original).isEqualTo(copia);
  }

  @Test
  void estornoDeEstornoERejeitado() {
    Lancamento estorno =
        Lancamento.estornar(
            ID_ESTORNO, original(TipoLancamento.CREDITO), POLITICA, MADRUGADA_22_10);

    assertThatThrownBy(
            () -> Lancamento.estornar(UUID.randomUUID(), estorno, POLITICA, MADRUGADA_22_10))
        .isInstanceOfSatisfying(
            RegraDeNegocioException.class,
            e -> assertThat(e.codigo()).isEqualTo(RegraDeNegocioException.ESTORNO_DE_ESTORNO));
  }

  private static Lancamento original(TipoLancamento tipo) {
    Clock dia15 = DataReferenciaTest.relogio("2026-10-15T15:00:00Z");
    return Lancamento.registrar(
        LancamentoTest.ID,
        LancamentoTest.novo(tipo, "250.00", "Venda", Instant.parse("2026-10-15T14:00:00Z"), null),
        POLITICA,
        dia15);
  }
}
