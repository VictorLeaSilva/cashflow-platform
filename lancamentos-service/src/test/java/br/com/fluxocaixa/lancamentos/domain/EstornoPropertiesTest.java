package br.com.fluxocaixa.lancamentos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-T010.1 (estorno) e RF-009: para qualquer lançamento válido, o estorno preserva valor e tipo
 * original, referencia o original, produz efeito hoje no fuso de negócio e não altera o original.
 */
@Tag("RF-003")
@Tag("RF-009")
@Tag("RNF-004")
class EstornoPropertiesTest {

  private static final PoliticaLancamento POLITICA = PoliticaLancamento.padrao();
  private static final Instant AGORA = Instant.parse("2026-10-22T12:00:00Z");
  private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

  /**
   * Entradas de um caso, todas derivadas da semente (inclusive os IDs), para que a falha seja
   * reproduzível e legível. O {@code toString} mostra o valor de propósito: é dado de teste.
   */
  record Caso(
      TipoLancamento tipo,
      BigDecimal valor,
      Instant registro,
      Duration ateOEstorno,
      UUID idOriginal,
      UUID comercianteId,
      UUID idEstorno) {

    Lancamento original() {
      return Lancamento.registrar(
          idOriginal,
          new Lancamento.Novo(comercianteId, tipo, valor, null, registro, null),
          POLITICA,
          Clock.fixed(registro, ZoneOffset.UTC));
    }

    Clock momentoDoEstorno() {
      return Clock.fixed(registro.plus(ateOEstorno), ZoneOffset.UTC);
    }

    @Override
    public String toString() {
      return "Caso[tipo=%s, valor=%s, registro=%s, ateOEstorno=%s, idOriginal=%s, comercianteId=%s, idEstorno=%s]"
          .formatted(
              tipo,
              valor.toPlainString(),
              registro,
              ateOEstorno,
              idOriginal,
              comercianteId,
              idEstorno);
    }
  }

  @Test
  void estornoPreservaValorETipoOriginalEProduzEfeitoNoDiaDoEstorno() {
    Propriedades.paraTodo(
        "estornar(original) é compensatório e não altera o original",
        List.of(
            fronteira(TipoLancamento.CREDITO, new BigDecimal("0.01"), Duration.ZERO),
            fronteira(TipoLancamento.DEBITO, POLITICA.valorMaximo(), Duration.ofDays(400))),
        EstornoPropertiesTest::casoQualquer,
        caso -> {
          Lancamento original = caso.original();
          Lancamento antes = Lancamento.reconstituir(original.dados());

          Lancamento estorno =
              Lancamento.estornar(caso.idEstorno(), original, POLITICA, caso.momentoDoEstorno());

          assertThat(estorno.id()).isEqualTo(caso.idEstorno());
          assertThat(estorno.valor()).isEqualTo(original.valor());
          assertThat(estorno.tipoOriginal()).isEqualTo(original.tipo());
          assertThat(estorno.referenciaLancamentoId()).isEqualTo(original.id());
          assertThat(estorno.comercianteId()).isEqualTo(original.comercianteId());
          assertThat(estorno.dataReferencia())
              .isEqualTo(
                  caso.momentoDoEstorno().instant().atZone(POLITICA.zonaNegocio()).toLocalDate());
          assertThat(original).isEqualTo(antes);
        });
  }

  @Test
  void dataDeReferenciaEADataNoFusoDeNegocioDentroDaJanela() {
    Propriedades.paraTodo(
        "dataReferencia = instante em America/Sao_Paulo",
        List.of(Instant.parse("2026-09-22T03:00:00Z"), AGORA.plusSeconds(60)),
        aleatorio -> AGORA.minusSeconds(aleatorio.nextLong(0, Duration.ofDays(29).toSeconds())),
        instante ->
            assertThat(DataReferencia.paraRegistro(instante, POLITICA, RELOGIO).data())
                .isEqualTo(LocalDate.ofInstant(instante, POLITICA.zonaNegocio())));
  }

  private static Caso casoQualquer(SplittableRandom aleatorio) {
    TipoLancamento tipo = aleatorio.nextBoolean() ? TipoLancamento.CREDITO : TipoLancamento.DEBITO;
    BigDecimal valor = new BigDecimal(BigInteger.valueOf(aleatorio.nextLong(1, 1_000_000_001L)), 2);
    Instant registro = AGORA.minusSeconds(aleatorio.nextLong(0, Duration.ofDays(29).toSeconds()));
    Duration ateOEstorno =
        Duration.ofSeconds(aleatorio.nextLong(0, Duration.ofDays(400).toSeconds()));
    return new Caso(
        tipo, valor, registro, ateOEstorno, uuid(aleatorio), uuid(aleatorio), uuid(aleatorio));
  }

  private static Caso fronteira(TipoLancamento tipo, BigDecimal valor, Duration ateOEstorno) {
    return new Caso(
        tipo,
        valor,
        AGORA,
        ateOEstorno,
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
        UUID.fromString("00000000-0000-0000-0000-0000000000e1"));
  }

  private static UUID uuid(SplittableRandom aleatorio) {
    return new UUID(aleatorio.nextLong(), aleatorio.nextLong());
  }
}
