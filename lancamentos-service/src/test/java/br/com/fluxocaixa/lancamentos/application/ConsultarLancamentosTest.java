package br.com.fluxocaixa.lancamentos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.fluxocaixa.lancamentos.application.port.out.FiltroDeHistorico;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Caso de uso de consulta (T-015), com Mockito só na porta de saída (04 §2.3). */
@Tag("RF-004")
@Tag("RF-010")
class ConsultarLancamentosTest {

  private static final UUID COMERCIANTE = UUID.randomUUID();
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneOffset.UTC);

  private final LancamentoRepository repositorio = mock(LancamentoRepository.class);
  private final ConsultarLancamentos consultar = new ConsultarLancamentos(repositorio);

  @Test
  void detalharDevolveOLancamentoDoComerciante() {
    Lancamento lancamento = lancamento();
    when(repositorio.buscarPorId(COMERCIANTE, lancamento.id())).thenReturn(Optional.of(lancamento));

    assertThat(consultar.detalhar(COMERCIANTE, lancamento.id())).isSameAs(lancamento);
  }

  @Test
  void detalharSemResultadoLancaNaoEncontrado() {
    UUID id = UUID.randomUUID();
    when(repositorio.buscarPorId(COMERCIANTE, id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> consultar.detalhar(COMERCIANTE, id))
        .isInstanceOf(LancamentoNaoEncontradoException.class)
        .hasMessageNotContaining(id.toString());
  }

  @Test
  void listarDevolveAPaginaComOTotalEOsParametrosAplicados() {
    FiltroDeHistorico filtro =
        new FiltroDeHistorico(COMERCIANTE, null, null, TipoLancamento.CREDITO, 2, 10);
    Lancamento lancamento = lancamento();
    when(repositorio.listar(filtro)).thenReturn(List.of(lancamento));
    when(repositorio.contar(filtro)).thenReturn(21L);

    PaginaDeLancamentos pagina = consultar.listar(filtro);

    assertThat(pagina.itens()).containsExactly(lancamento);
    assertThat(pagina.page()).isEqualTo(2);
    assertThat(pagina.size()).isEqualTo(10);
    assertThat(pagina.total()).isEqualTo(21L);
  }

  @Test
  void offsetDoFiltroNaoTransbordaInteiro() {
    FiltroDeHistorico filtro =
        new FiltroDeHistorico(COMERCIANTE, null, null, null, Integer.MAX_VALUE, 100);

    assertThat(filtro.offset()).isEqualTo((long) Integer.MAX_VALUE * 100);
  }

  private static Lancamento lancamento() {
    return Lancamento.registrar(
        UUID.randomUUID(),
        new Lancamento.Novo(
            COMERCIANTE, TipoLancamento.CREDITO, new BigDecimal("1.00"), null, null, null),
        PoliticaLancamento.padrao(),
        CLOCK);
  }
}
