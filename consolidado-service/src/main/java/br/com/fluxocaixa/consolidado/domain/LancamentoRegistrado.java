package br.com.fluxocaixa.consolidado.domain;

import br.com.fluxocaixa.consolidado.domain.EventoInvalidoException.ErroCampo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Evento {@code LancamentoRegistrado.v1} como o domínio do Consolidado o enxerga (Q-034): só os
 * campos do efeito e da deduplicação. {@code eventType} e {@code schemaVersion} são conferidos pelo
 * adaptador; {@code occurredAt} e {@code traceId} ficam com a T-022. Sem {@code occurredAt}, a data
 * de referência não tem como ser recalculada ({@code 03 §4.1}, item 3).
 *
 * <p>Não há teto de valor: A7 é política do Lançamentos, e o contrato não fixa máximo.
 *
 * @param tipoOriginal presente se e somente se {@code tipo = ESTORNO}
 * @param referenciaLancamentoId presente se e somente se {@code tipo = ESTORNO}
 */
public record LancamentoRegistrado(
    UUID eventId,
    UUID comercianteId,
    UUID lancamentoId,
    TipoLancamento tipo,
    BigDecimal valor,
    LocalDate dataReferencia,
    TipoLancamento tipoOriginal,
    UUID referenciaLancamentoId) {

  static final int ESCALA = 2;

  /**
   * Valida as invariantes e normaliza {@code valor} para escala 2, sem arredondar.
   *
   * @throws EventoInvalidoException com todas as violações encontradas
   */
  public LancamentoRegistrado {
    List<ErroCampo> erros = new ArrayList<>();
    obrigatorio(erros, "eventId", eventId);
    obrigatorio(erros, "comercianteId", comercianteId);
    obrigatorio(erros, "lancamentoId", lancamentoId);
    obrigatorio(erros, "tipo", tipo);
    obrigatorio(erros, "dataReferencia", dataReferencia);
    validarValor(erros, valor);
    validarEstorno(erros, tipo, tipoOriginal, referenciaLancamentoId);
    if (!erros.isEmpty()) {
      throw new EventoInvalidoException(erros);
    }
    valor = valor.setScale(ESCALA);
  }

  /** Sem {@code valor} nem {@code comercianteId}: payload financeiro não vai para log. */
  @Override
  public String toString() {
    return "LancamentoRegistrado[eventId=%s, lancamentoId=%s, tipo=%s, dataReferencia=%s]"
        .formatted(eventId, lancamentoId, tipo, dataReferencia);
  }

  private static void obrigatorio(List<ErroCampo> erros, String campo, Object valor) {
    if (valor == null) {
      erros.add(new ErroCampo(campo, "obrigatorio"));
    }
  }

  private static void validarValor(List<ErroCampo> erros, BigDecimal valor) {
    if (valor == null) {
      erros.add(new ErroCampo("valor", "obrigatorio"));
    } else if (valor.signum() <= 0) {
      erros.add(new ErroCampo("valor", "nao-positivo"));
    } else if (valor.scale() > ESCALA) {
      erros.add(new ErroCampo("valor", "casas-decimais"));
    }
  }

  private static void validarEstorno(
      List<ErroCampo> erros,
      TipoLancamento tipo,
      TipoLancamento tipoOriginal,
      UUID referenciaLancamentoId) {
    if (tipo == null) {
      return;
    }
    if (tipo == TipoLancamento.ESTORNO) {
      obrigatorioNoEstorno(erros, "tipoOriginal", tipoOriginal);
      obrigatorioNoEstorno(erros, "referenciaLancamentoId", referenciaLancamentoId);
      if (tipoOriginal == TipoLancamento.ESTORNO) {
        erros.add(new ErroCampo("tipoOriginal", "estorno-de-estorno"));
      }
    } else {
      proibidoForaDoEstorno(erros, "tipoOriginal", tipoOriginal);
      proibidoForaDoEstorno(erros, "referenciaLancamentoId", referenciaLancamentoId);
    }
  }

  private static void obrigatorioNoEstorno(List<ErroCampo> erros, String campo, Object valor) {
    if (valor == null) {
      erros.add(new ErroCampo(campo, "obrigatorio-no-estorno"));
    }
  }

  private static void proibidoForaDoEstorno(List<ErroCampo> erros, String campo, Object valor) {
    if (valor != null) {
      erros.add(new ErroCampo(campo, "proibido-fora-do-estorno"));
    }
  }
}
