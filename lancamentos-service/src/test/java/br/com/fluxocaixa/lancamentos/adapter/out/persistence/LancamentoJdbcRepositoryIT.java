package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.lancamentos.application.IdentificadorExternoDuplicadoException;
import br.com.fluxocaixa.lancamentos.application.LancamentoJaEstornadoException;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

/** AC-T011.6 e AC-T011.7: round-trip exato e leitura sempre filtrada por comerciante. */
@Tag("RNF-004")
@Tag("RF-010")
@Tag("RF-003")
class LancamentoJdbcRepositoryIT extends IntegracaoPostgres {

  private static final PoliticaLancamento POLITICA = PoliticaLancamento.padrao();

  /** Relógio fixo com microssegundos (a precisão do TIMESTAMPTZ). */
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T03:00:00.123456Z"), ZoneOffset.UTC);

  /**
   * 21:30 em São Paulo (-03:00) é 00:30 do dia seguinte em UTC: a data de referência (22/10) difere
   * da data UTC do instante (23/10), então o banco não pode recalculá-la.
   */
  private static final OffsetDateTime OCORRENCIA_SP =
      OffsetDateTime.parse("2026-10-22T21:30:15.654321-03:00");

  @Autowired private LancamentoRepository repositorio;

  @ParameterizedTest
  @ValueSource(strings = {"0.10", "9999999.99", "10000000.00"})
  void roundTripPreservaValorInstanteEDataDeReferencia(String valor) {
    Lancamento original =
        registrar(UUID.randomUUID(), TipoLancamento.CREDITO, valor, "Venda à vista", null);

    repositorio.inserir(original);
    Lancamento lido =
        repositorio.buscarPorId(original.comercianteId(), original.id()).orElseThrow();

    assertThat(lido).isEqualTo(original);
    assertThat(lido.valor().valor()).isEqualByComparingTo(valor);
    assertThat(lido.valor().valor().toPlainString()).isEqualTo(valor);
    assertThat(lido.dataOcorrencia()).isEqualTo(OCORRENCIA_SP.toInstant());
    assertThat(lido.dataReferencia()).isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(lido.criadoEm()).isEqualTo(CLOCK.instant());
  }

  @Test
  void roundTripDeDebitoComTextosOpcionais() {
    Lancamento original =
        registrar(
            UUID.randomUUID(),
            TipoLancamento.DEBITO,
            "123.45",
            "Pagamento de fornecedor — São João",
            "nf-2026/000123");

    repositorio.inserir(original);

    assertThat(repositorio.buscarPorId(original.comercianteId(), original.id())).contains(original);
  }

  @Test
  void roundTripDeEstorno() {
    Lancamento credito = registrar(UUID.randomUUID(), TipoLancamento.CREDITO, "50.00", null, null);
    Lancamento estorno = Lancamento.estornar(UUID.randomUUID(), credito, POLITICA, CLOCK);

    repositorio.inserir(credito);
    repositorio.inserir(estorno);
    Lancamento lido = repositorio.buscarPorId(estorno.comercianteId(), estorno.id()).orElseThrow();

    assertThat(lido).isEqualTo(estorno);
    assertThat(lido.referenciaLancamentoId()).isEqualTo(credito.id());
    assertThat(lido.tipoOriginal()).isEqualTo(TipoLancamento.CREDITO);
  }

  @Test
  void buscarPorIdDeOutroComercianteRetornaVazio() {
    Lancamento doDono = registrar(UUID.randomUUID(), TipoLancamento.CREDITO, "10.00", null, null);
    repositorio.inserir(doDono);

    assertThat(repositorio.buscarPorId(UUID.randomUUID(), doDono.id())).isEmpty();
    assertThat(repositorio.buscarPorId(doDono.comercianteId(), doDono.id())).isPresent();
  }

  @Test
  void buscarPorIdInexistenteRetornaVazio() {
    assertThat(repositorio.buscarPorId(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
  }

  /** AC-T013.11 (Q-015): a violação de ux_identificador_externo vira exceção de aplicação. */
  @Test
  void identificadorExternoRepetidoViraExcecaoDeAplicacaoSemExporDados() {
    UUID comerciante = UUID.randomUUID();
    repositorio.inserir(registrar(comerciante, TipoLancamento.CREDITO, "1.00", null, "nf-dup-1"));

    assertThatThrownBy(
            () ->
                repositorio.inserir(
                    registrar(comerciante, TipoLancamento.DEBITO, "2.00", null, "nf-dup-1")))
        .isInstanceOf(IdentificadorExternoDuplicadoException.class)
        .hasMessageNotContaining("nf-dup-1")
        .hasMessageNotContaining(comerciante.toString());
  }

  /** AC-T014.7: a violação de ux_estorno_unico vira exceção de aplicação, sem expor dados. */
  @Test
  void estornoDuplicadoViraExcecaoDeAplicacaoSemExporDados() {
    Lancamento credito = registrar(UUID.randomUUID(), TipoLancamento.CREDITO, "5.00", null, null);
    repositorio.inserir(credito);
    repositorio.inserir(Lancamento.estornar(UUID.randomUUID(), credito, POLITICA, CLOCK));

    assertThatThrownBy(
            () ->
                repositorio.inserir(
                    Lancamento.estornar(UUID.randomUUID(), credito, POLITICA, CLOCK)))
        .isInstanceOf(LancamentoJaEstornadoException.class)
        .hasMessageNotContaining(credito.id().toString())
        .hasMessageNotContaining(credito.comercianteId().toString());
  }

  /** Outras violações de unicidade continuam sem tradução (ex.: id repetido). */
  @Test
  void idRepetidoContinuaComoChaveDuplicada() {
    Lancamento credito = registrar(UUID.randomUUID(), TipoLancamento.CREDITO, "5.00", null, null);
    repositorio.inserir(credito);

    assertThatThrownBy(() -> repositorio.inserir(credito))
        .isInstanceOf(DuplicateKeyException.class)
        .isNotInstanceOf(IdentificadorExternoDuplicadoException.class)
        .isNotInstanceOf(LancamentoJaEstornadoException.class);
  }

  private static Lancamento registrar(
      UUID comercianteId,
      TipoLancamento tipo,
      String valor,
      String descricao,
      String identificadorExterno) {
    return Lancamento.registrar(
        UUID.randomUUID(),
        new Lancamento.Novo(
            comercianteId,
            tipo,
            new BigDecimal(valor),
            descricao,
            OCORRENCIA_SP.toInstant(),
            identificadorExterno),
        POLITICA,
        CLOCK);
  }
}
