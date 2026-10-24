package br.com.fluxocaixa.consolidado.domain;

import static br.com.fluxocaixa.consolidado.domain.GeradorDeEventos.estorno;
import static br.com.fluxocaixa.consolidado.domain.GeradorDeEventos.evento;
import static br.com.fluxocaixa.consolidado.domain.GeradorDeEventos.uuid;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-009.1 e AC-009.2 (parte de domínio): retroativo e estorno de dia passado. */
@Tag("RF-009")
class RetroatividadeTest {

  private static final UUID COMERCIANTE = uuid(0xaa);
  private static final LocalDate HOJE = LocalDate.parse("2026-10-22");

  @Test
  void lancamentoRetroativoMudaSoODiaDele() {
    ProjecaoDeReferencia projecao =
        ProjecaoDeReferencia.aplicandoTodos(
            List.of(
                evento(uuid(1), COMERCIANTE, TipoLancamento.CREDITO, valor("500.00"), HOJE),
                evento(
                    uuid(2),
                    COMERCIANTE,
                    TipoLancamento.DEBITO,
                    valor("80.00"),
                    HOJE.minusDays(1))));
    Map<ChaveDiaria, MovimentoDiario> antes = projecao.dias();
    ChaveDiaria diaMenos3 = new ChaveDiaria(COMERCIANTE, HOJE.minusDays(3));

    projecao.aplicar(
        evento(uuid(3), COMERCIANTE, TipoLancamento.DEBITO, valor("30.00"), HOJE.minusDays(3)));

    assertThat(projecao.dias()).hasSize(3).containsAllEntriesOf(antes);
    assertThat(projecao.dia(diaMenos3).saldoDia()).isEqualTo(valor("-30.00"));
    assertThat(projecao.dia(diaMenos3).qtdLancamentos()).isEqualTo(1);
  }

  /** Exemplo de {@code architecture.md §5.1}: crédito em 15/10, estornado em 22/10. */
  @Test
  void estornoDeDiaPassadoProduzEfeitoNoDiaDoEstornoESemTocarODiaOriginal() {
    LocalDate dia15 = LocalDate.parse("2026-10-15");
    LancamentoRegistrado credito =
        evento(uuid(1), COMERCIANTE, TipoLancamento.CREDITO, valor("100.00"), dia15);
    ProjecaoDeReferencia projecao = ProjecaoDeReferencia.aplicandoTodos(List.of(credito));
    ChaveDiaria chave15 = new ChaveDiaria(COMERCIANTE, dia15);
    MovimentoDiario dia15Antes = projecao.dia(chave15);

    projecao.aplicar(estorno(uuid(2), credito, HOJE));

    assertThat(projecao.dia(chave15)).isEqualTo(dia15Antes);
    assertThat(dia15Antes.saldoDia()).isEqualTo(valor("100.00"));
    MovimentoDiario dia22 = projecao.dia(new ChaveDiaria(COMERCIANTE, HOJE));
    assertThat(dia22.totalEstornosCredito()).isEqualTo(valor("100.00"));
    assertThat(dia22.saldoDia()).isEqualTo(valor("-100.00"));
    assertThat(dia22.qtdLancamentos()).isEqualTo(1);
  }

  private static BigDecimal valor(String texto) {
    return new BigDecimal(texto);
  }
}
