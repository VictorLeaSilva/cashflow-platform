package br.com.fluxocaixa.consolidado.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Geradores de casos para as propriedades da projeção (T-020). Tudo deriva do {@link
 * SplittableRandom} da semente, inclusive os IDs, para que a falha seja reproduzível.
 */
final class GeradorDeEventos {

  static final BigDecimal VALOR_MINIMO = new BigDecimal("0.01");
  static final BigDecimal VALOR_MAXIMO = new BigDecimal("10000000.00");
  static final LocalDate DIA_BASE = LocalDate.parse("2026-10-15");

  private static final long CENTAVOS_MAXIMOS = 1_000_000_000L;
  private static final int MAX_EVENTOS = 60;

  private GeradorDeEventos() {}

  /**
   * Eventos distintos e duas sequências de entrega independentes dos mesmos eventos. O {@code
   * toString} mostra os valores de propósito: é dado de teste.
   */
  record Cenario(
      List<LancamentoRegistrado> distintos,
      List<LancamentoRegistrado> entrega1,
      List<LancamentoRegistrado> entrega2) {

    static Cenario de(List<LancamentoRegistrado> distintos, SplittableRandom aleatorio) {
      return new Cenario(distintos, entrega(distintos, aleatorio), entrega(distintos, aleatorio));
    }

    /** Fronteira: as entregas são os distintos repetidos e invertidos, sem sorteio. */
    static Cenario fixo(List<LancamentoRegistrado> distintos) {
      List<LancamentoRegistrado> invertidos = new ArrayList<>(distintos);
      Collections.reverse(invertidos);
      List<LancamentoRegistrado> repetidos = new ArrayList<>(distintos);
      repetidos.addAll(invertidos);
      repetidos.addAll(distintos);
      return new Cenario(distintos, repetidos, invertidos);
    }

    @Override
    public String toString() {
      return "Cenario[distintos="
          + descrever(distintos)
          + ", entrega1="
          + ids(entrega1)
          + ", entrega2="
          + ids(entrega2)
          + "]";
    }
  }

  static Cenario cenario(SplittableRandom aleatorio) {
    return Cenario.de(distintos(aleatorio), aleatorio);
  }

  /**
   * De 0 a 60 eventos distintos, de 1 a 3 comerciantes e de 1 a 5 datas. Um estorno referencia um
   * lançamento gerado antes, ainda não estornado, do mesmo comerciante, com data igual ou posterior
   * (D-07).
   */
  static List<LancamentoRegistrado> distintos(SplittableRandom aleatorio) {
    List<UUID> comerciantes = uuids(aleatorio, aleatorio.nextInt(1, 4));
    int quantidadeDeDatas = aleatorio.nextInt(1, 6);
    int quantidade = aleatorio.nextInt(0, MAX_EVENTOS + 1);
    List<LancamentoRegistrado> eventos = new ArrayList<>();
    List<LancamentoRegistrado> estornaveis = new ArrayList<>();
    for (int i = 0; i < quantidade; i++) {
      if (!estornaveis.isEmpty() && aleatorio.nextInt(4) == 0) {
        LancamentoRegistrado original = estornaveis.remove(aleatorio.nextInt(estornaveis.size()));
        int atraso = aleatorio.nextInt(0, quantidadeDeDatas);
        eventos.add(estorno(uuid(aleatorio), original, original.dataReferencia().plusDays(atraso)));
      } else {
        LancamentoRegistrado novo =
            evento(
                uuid(aleatorio),
                comerciantes.get(aleatorio.nextInt(comerciantes.size())),
                aleatorio.nextBoolean() ? TipoLancamento.CREDITO : TipoLancamento.DEBITO,
                valor(aleatorio),
                DIA_BASE.plusDays(aleatorio.nextInt(quantidadeDeDatas)));
        eventos.add(novo);
        estornaveis.add(novo);
      }
    }
    return List.copyOf(eventos);
  }

  /** Cada evento repetido de 1 a 3 vezes, em ordem embaralhada (Fisher–Yates). */
  static List<LancamentoRegistrado> entrega(
      List<LancamentoRegistrado> distintos, SplittableRandom aleatorio) {
    List<LancamentoRegistrado> entrega = new ArrayList<>();
    for (LancamentoRegistrado evento : distintos) {
      int vezes = aleatorio.nextInt(1, 4);
      for (int i = 0; i < vezes; i++) {
        entrega.add(evento);
      }
    }
    for (int i = entrega.size() - 1; i > 0; i--) {
      Collections.swap(entrega, i, aleatorio.nextInt(i + 1));
    }
    return List.copyOf(entrega);
  }

  /** Valor em [0,01; 10.000.000,00] com escala 2; as fronteiras aparecem com mais frequência. */
  static BigDecimal valor(SplittableRandom aleatorio) {
    return switch (aleatorio.nextInt(20)) {
      case 0 -> VALOR_MINIMO;
      case 1 -> VALOR_MAXIMO;
      default -> new BigDecimal(BigInteger.valueOf(aleatorio.nextLong(1, CENTAVOS_MAXIMOS + 1)), 2);
    };
  }

  static MovimentoDiario movimento(SplittableRandom aleatorio) {
    return new MovimentoDiario(
        total(aleatorio),
        total(aleatorio),
        total(aleatorio),
        total(aleatorio),
        aleatorio.nextInt(0, 100_000));
  }

  static LancamentoRegistrado evento(
      UUID eventId, UUID comerciante, TipoLancamento tipo, BigDecimal valor, LocalDate data) {
    return new LancamentoRegistrado(
        eventId, comerciante, uuidDerivado(eventId), tipo, valor, data, null, null);
  }

  static LancamentoRegistrado estorno(UUID eventId, LancamentoRegistrado original, LocalDate data) {
    return new LancamentoRegistrado(
        eventId,
        original.comercianteId(),
        uuidDerivado(eventId),
        TipoLancamento.ESTORNO,
        original.valor(),
        data,
        original.tipo(),
        original.lancamentoId());
  }

  /**
   * Efeito com sinal no saldo do dia: + crédito e estorno de débito; − débito e estorno de crédito.
   */
  static BigDecimal efeitoNoSaldo(LancamentoRegistrado evento) {
    TipoLancamento natureza =
        evento.tipo() == TipoLancamento.ESTORNO ? evento.tipoOriginal() : evento.tipo();
    boolean somaNoSaldo =
        (natureza == TipoLancamento.CREDITO) != (evento.tipo() == TipoLancamento.ESTORNO);
    return somaNoSaldo ? evento.valor() : evento.valor().negate();
  }

  static UUID uuid(SplittableRandom aleatorio) {
    return new UUID(aleatorio.nextLong(), aleatorio.nextLong());
  }

  static UUID uuid(int n) {
    return new UUID(0L, n);
  }

  private static BigDecimal total(SplittableRandom aleatorio) {
    return aleatorio.nextInt(10) == 0
        ? BigDecimal.ZERO.setScale(2)
        : new BigDecimal(BigInteger.valueOf(aleatorio.nextLong(0, Long.MAX_VALUE)), 2);
  }

  private static List<UUID> uuids(SplittableRandom aleatorio, int quantidade) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < quantidade; i++) {
      ids.add(uuid(aleatorio));
    }
    return ids;
  }

  /** O {@code lancamentoId} não importa para o efeito; derivá-lo evita consumir o sorteio. */
  private static UUID uuidDerivado(UUID eventId) {
    return new UUID(~eventId.getMostSignificantBits(), ~eventId.getLeastSignificantBits());
  }

  private static String descrever(List<LancamentoRegistrado> eventos) {
    return eventos.stream()
        .map(
            e ->
                "%s %s %s %s em %s com %s%s"
                    .formatted(
                        e.eventId(),
                        e.tipo(),
                        e.tipoOriginal() == null ? "" : "de " + e.tipoOriginal(),
                        e.valor().toPlainString(),
                        e.dataReferencia(),
                        e.comercianteId(),
                        e.referenciaLancamentoId() == null
                            ? ""
                            : " ref " + e.referenciaLancamentoId()))
        .collect(Collectors.joining("; ", "[", "]"));
  }

  private static String ids(List<LancamentoRegistrado> eventos) {
    return eventos.stream()
        .map(e -> e.eventId().toString())
        .collect(Collectors.joining(", ", "[", "]"));
  }
}
