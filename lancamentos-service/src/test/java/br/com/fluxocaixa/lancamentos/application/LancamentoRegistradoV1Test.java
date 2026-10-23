package br.com.fluxocaixa.lancamentos.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Montagem do evento a partir do lançamento (03 §4). */
@Tag("RF-001")
@Tag("RF-002")
class LancamentoRegistradoV1Test {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneOffset.UTC);
  private static final UUID EVENTO = UUID.fromString("a1b2c3d4-e5f6-4789-8abc-1234567890ab");

  @Test
  void creditoSemCamposDeEstorno() {
    Lancamento credito = registrar(TipoLancamento.CREDITO, "150.75");

    var evento = LancamentoRegistradoV1.de(credito, EVENTO, CLOCK.instant());

    assertThat(evento.eventId()).isEqualTo(EVENTO);
    assertThat(evento.eventType()).isEqualTo("LancamentoRegistrado");
    assertThat(evento.schemaVersion()).isEqualTo(1);
    assertThat(evento.occurredAt()).isEqualTo(CLOCK.instant());
    assertThat(evento.comercianteId()).isEqualTo(credito.comercianteId());
    assertThat(evento.lancamentoId()).isEqualTo(credito.id());
    assertThat(evento.tipo()).isEqualTo(TipoLancamento.CREDITO);
    assertThat(evento.valor()).isEqualTo(new BigDecimal("150.75"));
    assertThat(evento.dataReferencia()).isEqualTo(LocalDate.of(2026, 10, 23));
    assertThat(evento.tipoOriginal()).isNull();
    assertThat(evento.referenciaLancamentoId()).isNull();
  }

  @Test
  void debitoMantemOTipo() {
    Lancamento debito = registrar(TipoLancamento.DEBITO, "10.00");

    assertThat(LancamentoRegistradoV1.de(debito, EVENTO, CLOCK.instant()).tipo())
        .isEqualTo(TipoLancamento.DEBITO);
  }

  @Test
  void estornoLevaTipoOriginalEReferencia() {
    Lancamento debito = registrar(TipoLancamento.DEBITO, "10.00");
    Lancamento estorno =
        Lancamento.estornar(UUID.randomUUID(), debito, PoliticaLancamento.padrao(), CLOCK);

    var evento = LancamentoRegistradoV1.de(estorno, EVENTO, CLOCK.instant());

    assertThat(evento.tipo()).isEqualTo(TipoLancamento.ESTORNO);
    assertThat(evento.tipoOriginal()).isEqualTo(TipoLancamento.DEBITO);
    assertThat(evento.referenciaLancamentoId()).isEqualTo(debito.id());
  }

  @Test
  void toStringNaoExpoeValor() {
    Lancamento credito = registrar(TipoLancamento.CREDITO, "987.65");

    assertThat(LancamentoRegistradoV1.de(credito, EVENTO, CLOCK.instant()).toString())
        .doesNotContain("987.65");
  }

  private static Lancamento registrar(TipoLancamento tipo, String valor) {
    return Lancamento.registrar(
        UUID.randomUUID(),
        new Lancamento.Novo(
            UUID.randomUUID(), tipo, new BigDecimal(valor), "descricao livre", null, null),
        PoliticaLancamento.padrao(),
        CLOCK);
  }
}
