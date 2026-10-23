package br.com.fluxocaixa.lancamentos.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Data em que o lançamento produz efeito no consolidado: o instante convertido para o fuso de
 * negócio, nunca a data UTC (`03 §4.1`). Calculada só em Lançamentos.
 */
public record DataReferencia(LocalDate data) {

  public DataReferencia {
    Objects.requireNonNull(data, "data");
  }

  /** Data do instante no fuso informado. */
  public static DataReferencia de(Instant instante, ZoneId zona) {
    return new DataReferencia(instante.atZone(zona).toLocalDate());
  }

  /** Data de hoje no fuso de negócio (estornos produzem efeito no dia do estorno, D-07). */
  public static DataReferencia hoje(PoliticaLancamento politica, Clock clock) {
    return de(clock.instant(), politica.zonaNegocio());
  }

  /**
   * Data de referência de um novo crédito ou débito, aplicando as regras de futuro (A11) e
   * retroatividade (A4).
   *
   * @param ocorrencia instante informado; {@code null} usa o instante do {@code clock}
   * @throws RegraDeNegocioException {@code data-futura} ou {@code data-retroativa-excedida}
   */
  public static DataReferencia paraRegistro(
      Instant ocorrencia, PoliticaLancamento politica, Clock clock) {
    Instant agora = clock.instant();
    Instant instante = ocorrencia != null ? ocorrencia : agora;
    if (instante.isAfter(agora.plus(politica.maxFutureSkew()))) {
      throw new RegraDeNegocioException(
          RegraDeNegocioException.DATA_FUTURA,
          "dataOcorrencia no futuro além da tolerância de " + politica.maxFutureSkew());
    }
    DataReferencia data = de(instante, politica.zonaNegocio());
    LocalDate limite = hoje(politica, clock).data().minusDays(politica.maxBackdateDays());
    if (data.data().isBefore(limite)) {
      throw new RegraDeNegocioException(
          RegraDeNegocioException.DATA_RETROATIVA_EXCEDIDA,
          "dataReferencia anterior ao limite de " + politica.maxBackdateDays() + " dias");
    }
    return data;
  }
}
