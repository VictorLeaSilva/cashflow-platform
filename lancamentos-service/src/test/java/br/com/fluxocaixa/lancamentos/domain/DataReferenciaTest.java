package br.com.fluxocaixa.lancamentos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tabela e fronteiras de `03 §4.1` (risco obrigatório "fronteiras de data", `04 §2.7`). */
@Tag("RF-009")
@Tag("RF-001")
class DataReferenciaTest {

  private static final PoliticaLancamento POLITICA = PoliticaLancamento.padrao();
  private static final Clock AGORA_22_10 = relogio("2026-10-22T12:00:00Z");

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "2026-10-21T23:30:00-03:00, 2026-10-21",
    "2026-10-22T02:30:00Z,      2026-10-21",
    "2026-10-22T02:59:59Z,      2026-10-21",
    "2026-10-22T03:00:00Z,      2026-10-22",
    "2026-10-22T01:30:00+01:00, 2026-10-21"
  })
  void usaADataNoFusoDeNegocioENuncaADataUtc(String entrada, String esperada) {
    Instant instante = OffsetDateTime.parse(entrada).toInstant();

    DataReferencia data = DataReferencia.paraRegistro(instante, POLITICA, AGORA_22_10);

    assertThat(data.data()).isEqualTo(LocalDate.parse(esperada));
  }

  @Test
  void dataOmitidaUsaOClock() {
    Clock agora = relogio("2026-10-22T01:00:00Z");

    assertThat(DataReferencia.paraRegistro(null, POLITICA, agora).data())
        .isEqualTo(LocalDate.parse("2026-10-21"));
  }

  @Test
  void diaMenos30EAceito() {
    Instant inicioDoDia = Instant.parse("2026-09-22T03:00:00Z");

    assertThat(DataReferencia.paraRegistro(inicioDoDia, POLITICA, AGORA_22_10).data())
        .isEqualTo(LocalDate.parse("2026-09-22"));
  }

  @Test
  void diaMenos31ERejeitado() {
    Instant fimDoDia = Instant.parse("2026-09-22T02:59:59Z");

    assertThatThrownBy(() -> DataReferencia.paraRegistro(fimDoDia, POLITICA, AGORA_22_10))
        .isInstanceOfSatisfying(
            RegraDeNegocioException.class,
            e ->
                assertThat(e.codigo()).isEqualTo(RegraDeNegocioException.DATA_RETROATIVA_EXCEDIDA));
  }

  @Test
  void ate60SegundosNoFuturoEAceito() {
    Instant limite = Instant.parse("2026-10-22T12:01:00Z");

    assertThat(DataReferencia.paraRegistro(limite, POLITICA, AGORA_22_10).data())
        .isEqualTo(LocalDate.parse("2026-10-22"));
  }

  @Test
  void maisDe60SegundosNoFuturoERejeitado() {
    Instant alemDoLimite = Instant.parse("2026-10-22T12:01:01Z");

    assertThatThrownBy(() -> DataReferencia.paraRegistro(alemDoLimite, POLITICA, AGORA_22_10))
        .isInstanceOfSatisfying(
            RegraDeNegocioException.class,
            e -> assertThat(e.codigo()).isEqualTo(RegraDeNegocioException.DATA_FUTURA));
  }

  @Test
  void hojeUsaODiaDeAgoraNoFusoDeNegocio() {
    Clock dezDaNoiteEmSp = relogio("2026-10-22T01:00:00Z");

    assertThat(DataReferencia.hoje(POLITICA, dezDaNoiteEmSp).data())
        .isEqualTo(LocalDate.parse("2026-10-21"));
  }

  static Clock relogio(String instante) {
    return Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
  }
}
