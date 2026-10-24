package br.com.fluxocaixa.consolidado.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.consolidado.domain.GeradorDeEventos.Cenario;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-T020.3 ({@code somar} é comutativa e associativa, com {@code ZERO} neutro) e AC-005.2 por
 * propriedade (o saldo do dia é a soma com sinal dos eventos distintos daquele dia).
 */
@Tag("RF-008")
class MovimentoDiarioPropertiesTest {

  /** Três movimentos de um caso. O {@code toString} padrão mostra os valores: é dado de teste. */
  record Tres(MovimentoDiario a, MovimentoDiario b, MovimentoDiario c) {}

  @Test
  void somarEComutativaAssociativaETemZeroComoNeutro() {
    MovimentoDiario grande =
        new MovimentoDiario(
            new BigDecimal("92233720368547758.07"),
            new BigDecimal("0.01"),
            new BigDecimal("0.00"),
            new BigDecimal("92233720368547758.07"),
            0);
    Propriedades.paraTodo(
        "somar forma um monoide comutativo",
        List.of(
            new Tres(MovimentoDiario.ZERO, MovimentoDiario.ZERO, MovimentoDiario.ZERO),
            new Tres(grande, grande, MovimentoDiario.ZERO)),
        aleatorio ->
            new Tres(
                GeradorDeEventos.movimento(aleatorio),
                GeradorDeEventos.movimento(aleatorio),
                GeradorDeEventos.movimento(aleatorio)),
        caso -> {
          MovimentoDiario a = caso.a();
          MovimentoDiario b = caso.b();
          MovimentoDiario c = caso.c();

          assertThat(a.somar(b)).isEqualTo(b.somar(a));
          assertThat(a.somar(b).somar(c)).isEqualTo(a.somar(b.somar(c)));
          assertThat(a.somar(MovimentoDiario.ZERO)).isEqualTo(a);
          assertThat(MovimentoDiario.ZERO.somar(a)).isEqualTo(a);
          assertTotaisValidos(a.somar(b).somar(c));
        });
  }

  @Tag("RF-005")
  @Test
  void saldoDiaEASomaComSinalDosEventosDistintosDoDia() {
    Propriedades.paraTodo(
        "saldoDia e qtdLancamentos de cada dia batem com os eventos distintos daquele dia",
        GeradorDeEventos.fronteiras(),
        GeradorDeEventos::cenario,
        caso -> {
          ProjecaoDeReferencia projecao = ProjecaoDeReferencia.aplicandoTodos(caso.entrega1());

          projecao
              .dias()
              .forEach(
                  (chave, movimento) -> {
                    List<LancamentoRegistrado> doDia = doDia(caso, chave);
                    BigDecimal esperado =
                        doDia.stream()
                            .map(GeradorDeEventos::efeitoNoSaldo)
                            .reduce(new BigDecimal("0.00"), BigDecimal::add);
                    assertThat(movimento.saldoDia()).isEqualTo(esperado);
                    assertThat(movimento.qtdLancamentos()).isEqualTo(doDia.size());
                  });
          assertThat(
                  projecao.dias().values().stream().mapToInt(MovimentoDiario::qtdLancamentos).sum())
              .isEqualTo(caso.distintos().size());
        });
  }

  private static List<LancamentoRegistrado> doDia(Cenario caso, ChaveDiaria chave) {
    UUID comerciante = chave.comercianteId();
    return caso.distintos().stream()
        .filter(e -> e.comercianteId().equals(comerciante))
        .filter(e -> e.dataReferencia().equals(chave.dataReferencia()))
        .toList();
  }

  private static void assertTotaisValidos(MovimentoDiario movimento) {
    for (BigDecimal total :
        List.of(
            movimento.totalCreditos(),
            movimento.totalDebitos(),
            movimento.totalEstornosCredito(),
            movimento.totalEstornosDebito())) {
      assertThat(total.signum()).isNotNegative();
      assertThat(total.scale()).isEqualTo(2);
    }
    assertThat(movimento.qtdLancamentos()).isNotNegative();
  }
}
