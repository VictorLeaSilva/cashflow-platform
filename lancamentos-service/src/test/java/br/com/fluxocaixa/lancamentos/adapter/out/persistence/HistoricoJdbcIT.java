package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.application.port.out.FiltroDeHistorico;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AC-004.2 no SQL real (T-015): ordem {@code dataReferencia} decrescente e {@code id} crescente
 * (Q-024), filtros inclusivos (Q-023), paginação sem repetir nem pular, total e filtro por
 * comerciante.
 */
@Tag("RF-004")
@Tag("RF-010")
class HistoricoJdbcIT extends IntegracaoPostgres {

  private static final PoliticaLancamento POLITICA = PoliticaLancamento.padrao();
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneOffset.UTC);

  @Autowired private LancamentoRepository repositorio;

  private UUID comerciante;
  private List<Lancamento> meus;

  /**
   * Três dias, vários lançamentos por dia (para exercitar o desempate), um estorno e outro dono.
   */
  @BeforeEach
  void dados() {
    comerciante = UUID.randomUUID();
    meus = new ArrayList<>();
    String[] dias = {
      "2026-10-20", "2026-10-21", "2026-10-21", "2026-10-22", "2026-10-22", "2026-10-22"
    };
    for (int i = 0; i < dias.length; i++) {
      TipoLancamento tipo = i % 2 == 0 ? TipoLancamento.CREDITO : TipoLancamento.DEBITO;
      meus.add(inserir(comerciante, tipo, dias[i]));
    }
    Lancamento estorno = Lancamento.estornar(UUID.randomUUID(), meus.getFirst(), POLITICA, CLOCK);
    repositorio.inserir(estorno);
    meus.add(estorno);
    inserir(UUID.randomUUID(), TipoLancamento.CREDITO, "2026-10-22");
  }

  @Test
  void ordemEhDataDecrescenteEIdCrescente() {
    List<Lancamento> lidos = repositorio.listar(filtro(null, null, null, 0, 100));

    assertThat(lidos).containsExactlyElementsOf(esperados(meus));
    assertThat(repositorio.contar(filtro(null, null, null, 0, 100))).isEqualTo(meus.size());
  }

  @Test
  void paginasCobremTudoSemRepetirNemPular() {
    List<Lancamento> paginados = new ArrayList<>();
    for (int pagina = 0; pagina < 4; pagina++) {
      paginados.addAll(repositorio.listar(filtro(null, null, null, pagina, 2)));
    }

    assertThat(paginados).containsExactlyElementsOf(esperados(meus));
    assertThat(repositorio.listar(filtro(null, null, null, 4, 2))).isEmpty();
  }

  @Test
  void filtrosSaoInclusivosECombinam() {
    LocalDate dia21 = LocalDate.of(2026, 10, 21);
    LocalDate dia22 = LocalDate.of(2026, 10, 22);

    assertThat(repositorio.listar(filtro(dia21, dia22, null, 0, 100)))
        .containsExactlyElementsOf(
            esperados(
                meus.stream()
                    .filter(
                        l ->
                            !l.dataReferencia().isBefore(dia21)
                                && !l.dataReferencia().isAfter(dia22))
                    .toList()));
    assertThat(repositorio.contar(filtro(dia21, dia21, null, 0, 100))).isEqualTo(2);
    assertThat(repositorio.listar(filtro(null, null, TipoLancamento.ESTORNO, 0, 100)))
        .containsExactly(meus.getLast());
    assertThat(repositorio.contar(filtro(dia22, null, TipoLancamento.CREDITO, 0, 100)))
        .isEqualTo(
            meus.stream()
                .filter(
                    l -> l.tipo() == TipoLancamento.CREDITO && !l.dataReferencia().isBefore(dia22))
                .count());
  }

  @Test
  void outroComercianteNaoAparece() {
    assertThat(repositorio.listar(filtro(null, null, null, 0, 100)))
        .allMatch(l -> l.comercianteId().equals(comerciante));
    assertThat(repositorio.contar(filtro(null, null, null, 0, 100))).isEqualTo(meus.size());
  }

  private Lancamento inserir(UUID dono, TipoLancamento tipo, String dia) {
    Lancamento lancamento =
        Lancamento.registrar(
            UUID.randomUUID(),
            new Lancamento.Novo(
                dono, tipo, new BigDecimal("10.00"), null, Instant.parse(dia + "T15:00:00Z"), null),
            POLITICA,
            CLOCK);
    repositorio.inserir(lancamento);
    return lancamento;
  }

  private FiltroDeHistorico filtro(
      LocalDate inicio, LocalDate fim, TipoLancamento tipo, int page, int size) {
    return new FiltroDeHistorico(comerciante, inicio, fim, tipo, page, size);
  }

  /**
   * Ordem do PostgreSQL: data decrescente; UUID crescente pelos bytes sem sinal (texto canônico).
   */
  private static List<Lancamento> esperados(List<Lancamento> lancamentos) {
    return lancamentos.stream()
        .sorted(
            Comparator.comparing(Lancamento::dataReferencia)
                .reversed()
                .thenComparing(l -> l.id().toString()))
        .toList();
  }
}
