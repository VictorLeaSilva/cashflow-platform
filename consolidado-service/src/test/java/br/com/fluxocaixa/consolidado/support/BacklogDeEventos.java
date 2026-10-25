package br.com.fluxocaixa.consolidado.support;

import br.com.fluxocaixa.consolidado.domain.ChaveDiaria;
import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import br.com.fluxocaixa.consolidado.domain.ProjecaoDeReferencia;
import br.com.fluxocaixa.consolidado.domain.TipoLancamento;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Um lote determinístico de eventos de um comerciante (T-031): o evento de domínio, o JSON que o
 * produtor publicaria e a projeção esperada, calculada pelo modelo de referência ({@link
 * ProjecaoDeReferencia}). Mistura créditos e débitos em três dias; os {@code eventId}s são
 * derivados do comerciante e da posição, então o mesmo lote publicado duas vezes é duplicata.
 */
public final class BacklogDeEventos {

  private static final LocalDate PRIMEIRO_DIA = LocalDate.parse("2026-10-20");

  private BacklogDeEventos() {}

  /** Evento de domínio e a mensagem correspondente. */
  public record Item(LancamentoRegistrado evento, String json) {}

  public static List<Item> gerar(UUID comerciante, int quantidade) {
    List<Item> itens = new ArrayList<>();
    for (int i = 0; i < quantidade; i++) {
      UUID eventId =
          UUID.nameUUIDFromBytes(
              ("backlog-" + comerciante + "-" + i).getBytes(StandardCharsets.UTF_8));
      TipoLancamento tipo = i % 3 == 2 ? TipoLancamento.DEBITO : TipoLancamento.CREDITO;
      BigDecimal valor = BigDecimal.valueOf(1000L + 37L * i, 2);
      LocalDate dia = PRIMEIRO_DIA.plusDays(i % 3);
      Map<String, Object> campos =
          EventosJson.credito(eventId, comerciante, valor.toPlainString(), dia.toString());
      campos.put("tipo", tipo.name());
      UUID lancamentoId = UUID.fromString((String) campos.get("lancamentoId"));
      itens.add(
          new Item(
              new LancamentoRegistrado(
                  eventId, comerciante, lancamentoId, tipo, valor, dia, null, null),
              EventosJson.json(campos)));
    }
    return itens;
  }

  /** Publica o lote na ordem, com a chave do comerciante. */
  public static void publicar(UUID comerciante, List<Item> itens) {
    for (Item item : itens) {
      KafkaConsolidado.publicar(comerciante.toString(), item.json(), null);
    }
  }

  /** Projeção do modelo de referência para o lote aplicado inteiro. */
  public static Map<ChaveDiaria, MovimentoDiario> esperado(List<Item> itens) {
    return ProjecaoDeReferencia.aplicandoTodos(itens.stream().map(Item::evento).toList()).dias();
  }

  public static List<UUID> eventIds(List<Item> itens) {
    return itens.stream().map(item -> item.evento().eventId()).toList();
  }
}
