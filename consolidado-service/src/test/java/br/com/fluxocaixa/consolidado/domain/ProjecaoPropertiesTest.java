package br.com.fluxocaixa.consolidado.domain;

import static br.com.fluxocaixa.consolidado.domain.GeradorDeEventos.DIA_BASE;
import static br.com.fluxocaixa.consolidado.domain.GeradorDeEventos.VALOR_MINIMO;
import static br.com.fluxocaixa.consolidado.domain.GeradorDeEventos.estorno;
import static br.com.fluxocaixa.consolidado.domain.GeradorDeEventos.evento;
import static br.com.fluxocaixa.consolidado.domain.GeradorDeEventos.uuid;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.consolidado.domain.GeradorDeEventos.Cenario;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-008.3 e AC-008.2 (parte de domínio, Q-033) e AC-009.1 por propriedade: ordem e duplicatas não
 * mudam a projeção; a deduplicação é só por {@code eventId}; cada evento afeta uma única chave.
 */
@Tag("RF-008")
@Tag("RNF-004")
class ProjecaoPropertiesTest {

  private static final UUID COMERCIANTE = uuid(0xaa);

  /** Um cenário e um evento a acrescentar, possivelmente retroativo. */
  record ComExtra(Cenario cenario, LancamentoRegistrado extra) {

    @Override
    public String toString() {
      return "ComExtra[cenario=%s, extra=%s %s %s em %s com %s]"
          .formatted(
              cenario,
              extra.eventId(),
              extra.tipo(),
              extra.valor().toPlainString(),
              extra.dataReferencia(),
              extra.comercianteId());
    }
  }

  @Test
  void ordemEDuplicatasNaoMudamAProjecao() {
    Propriedades.paraTodo(
        "qualquer ordem e quaisquer duplicatas dão a projeção dos distintos",
        GeradorDeEventos.fronteiras(),
        GeradorDeEventos::cenario,
        caso -> {
          Map<ChaveDiaria, MovimentoDiario> distintos =
              ProjecaoDeReferencia.aplicandoTodos(caso.distintos()).dias();

          assertThat(ProjecaoDeReferencia.aplicandoTodos(caso.entrega1()).dias())
              .isEqualTo(distintos);
          assertThat(ProjecaoDeReferencia.aplicandoTodos(caso.entrega2()).dias())
              .isEqualTo(distintos);
        });
  }

  @Test
  void reentregaComMesmoEventIdEIgnoradaMesmoComConteudoDiferente() {
    Propriedades.paraTodo(
        "evento com eventId já aplicado não muda a projeção (Q-036)",
        GeradorDeEventos.fronteiras(),
        GeradorDeEventos::cenario,
        caso -> {
          ProjecaoDeReferencia projecao = ProjecaoDeReferencia.aplicandoTodos(caso.entrega1());
          Map<ChaveDiaria, MovimentoDiario> antes = projecao.dias();

          for (LancamentoRegistrado evento : caso.distintos()) {
            assertThat(projecao.aplicar(evento)).isFalse();
            assertThat(projecao.aplicar(alterado(evento))).isFalse();
          }

          assertThat(projecao.dias()).isEqualTo(antes);
        });
  }

  @Test
  void estornoDeLancamentoJaAplicadoEAplicado() {
    Propriedades.paraTodo(
        "estorno com eventId novo é aplicado mesmo com o original já aplicado (ADR-005)",
        GeradorDeEventos.fronteiras(),
        GeradorDeEventos::cenario,
        caso -> {
          ProjecaoDeReferencia projecao = ProjecaoDeReferencia.aplicandoTodos(caso.entrega1());

          for (LancamentoRegistrado original : caso.distintos()) {
            if (original.tipo() == TipoLancamento.ESTORNO) {
              continue;
            }
            LancamentoRegistrado estorno =
                estorno(eventIdNovo(original), original, original.dataReferencia().plusDays(1));
            Efeito efeito = Efeito.de(estorno);
            MovimentoDiario antes = projecao.dia(efeito.chave());

            assertThat(projecao.aplicar(estorno)).isTrue();
            assertThat(projecao.dia(efeito.chave())).isEqualTo(antes.somar(efeito.delta()));
          }
        });
  }

  @Tag("RF-009")
  @Test
  void efeitoAfetaUmaUnicaChave() {
    Propriedades.paraTodo(
        "acrescentar um evento muda só a chave dele, e o saldo dela pelo efeito do evento",
        GeradorDeEventos.fronteiras().stream()
            .map(
                c ->
                    new ComExtra(
                        c,
                        evento(
                            uuid(77),
                            COMERCIANTE,
                            TipoLancamento.DEBITO,
                            VALOR_MINIMO,
                            DIA_BASE.minusDays(3))))
            .toList(),
        ProjecaoPropertiesTest::comExtra,
        caso -> {
          Map<ChaveDiaria, MovimentoDiario> antes =
              ProjecaoDeReferencia.aplicandoTodos(caso.cenario().entrega1()).dias();
          ProjecaoDeReferencia projecao =
              ProjecaoDeReferencia.aplicandoTodos(caso.cenario().entrega1());
          LancamentoRegistrado extra = caso.extra();
          ChaveDiaria chave = new ChaveDiaria(extra.comercianteId(), extra.dataReferencia());
          assertThat(Efeito.de(extra).chave()).isEqualTo(chave);
          MovimentoDiario anterior = antes.getOrDefault(chave, MovimentoDiario.ZERO);

          projecao.aplicar(extra);

          Map<ChaveDiaria, MovimentoDiario> outrasDepois = new HashMap<>(projecao.dias());
          outrasDepois.remove(chave);
          Map<ChaveDiaria, MovimentoDiario> outrasAntes = new HashMap<>(antes);
          outrasAntes.remove(chave);
          assertThat(outrasDepois).isEqualTo(outrasAntes);
          assertThat(projecao.dia(chave)).isEqualTo(anterior.somar(Efeito.de(extra).delta()));
          assertThat(projecao.dia(chave).saldoDia().subtract(anterior.saldoDia()))
              .isEqualTo(GeradorDeEventos.efeitoNoSaldo(extra));
        });
  }

  private static ComExtra comExtra(SplittableRandom aleatorio) {
    Cenario cenario = GeradorDeEventos.cenario(aleatorio);
    UUID comerciante =
        cenario.distintos().isEmpty() || aleatorio.nextBoolean()
            ? uuid(aleatorio)
            : cenario
                .distintos()
                .get(aleatorio.nextInt(cenario.distintos().size()))
                .comercianteId();
    LancamentoRegistrado base =
        evento(
            uuid(aleatorio),
            comerciante,
            aleatorio.nextBoolean() ? TipoLancamento.CREDITO : TipoLancamento.DEBITO,
            GeradorDeEventos.valor(aleatorio),
            DIA_BASE.plusDays(aleatorio.nextInt(-30, 6)));
    LancamentoRegistrado extra =
        aleatorio.nextBoolean()
            ? base
            : estorno(uuid(aleatorio), base, base.dataReferencia().plusDays(aleatorio.nextInt(8)));
    return new ComExtra(cenario, extra);
  }

  /** Mesmo {@code eventId}, todo o resto diferente. */
  private static LancamentoRegistrado alterado(LancamentoRegistrado evento) {
    return evento(
        evento.eventId(),
        uuid(0xbb),
        evento.tipo() == TipoLancamento.CREDITO ? TipoLancamento.DEBITO : TipoLancamento.CREDITO,
        evento.valor().add(BigDecimal.ONE),
        evento.dataReferencia().plusDays(1));
  }

  private static UUID eventIdNovo(LancamentoRegistrado original) {
    UUID id = original.eventId();
    return new UUID(id.getLeastSignificantBits() ^ 0x5a5a, id.getMostSignificantBits() ^ 0xa5a5);
  }
}
