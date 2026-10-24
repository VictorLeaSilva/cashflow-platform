package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import br.com.fluxocaixa.consolidado.domain.TipoLancamento;
import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.EventosJson.Invalido;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-T030.1 e AC-T030.3 (lado do Consolidado, sem Kafka): o leitor aceita os três exemplos
 * <i>golden</i> de {@code contracts} e recusa cada exemplo de {@code examples/invalidos/} como fora
 * do contrato. É o mesmo schema e a mesma configuração de {@code format} que o produtor usa nos
 * testes do Lançamentos.
 */
@Tag("RF-008")
class ContratoDosExemplosTest {
  /**
   * Violação exata de cada inválido (ponteiro e palavra-chave); cada arquivo viola uma só regra.
   */
  private static final Map<String, String> VIOLACAO_ESPERADA =
      Map.ofEntries(
          Map.entry("comerciante-id-ausente.json", ":required"),
          Map.entry("credito-com-tipo-original.json", ":not"),
          Map.entry("data-referencia-invalida.json", "/dataReferencia:format"),
          Map.entry("estorno-sem-referencia.json", ":required"),
          Map.entry("estorno-sem-tipo-original.json", ":required"),
          Map.entry("event-id-nao-uuid.json", "/eventId:format"),
          Map.entry("event-type-diferente.json", "/eventType:const"),
          Map.entry("occurred-at-sem-offset.json", "/occurredAt:format"),
          Map.entry("propriedade-extra.json", ":additionalProperties"),
          Map.entry("schema-version-2.json", "/schemaVersion:const"),
          Map.entry("tipo-invalido.json", "/tipo:enum"),
          Map.entry("trace-id-vazio.json", "/traceId:minLength"),
          Map.entry("valor-com-tres-casas.json", "/valor:pattern"),
          Map.entry("valor-numerico.json", "/valor:type"));

  private final LeitorDeEvento leitor = new LeitorDeEvento(JsonMapper.builder().build());

  @ParameterizedTest
  @ValueSource(
      strings = {
        "lancamento-registrado.v1.json",
        "lancamento-registrado.v1.debito.json",
        "lancamento-registrado.v1.estorno.json"
      })
  void leOsExemplosGolden(String arquivo) {
    assertThat(leitor.ler(EventosJson.golden(arquivo)).evento()).isNotNull();
  }

  @Test
  void leOExemploGoldenDeDebito() {
    LancamentoRegistrado debito =
        leitor.ler(EventosJson.golden("lancamento-registrado.v1.debito.json")).evento();

    assertThat(debito.tipo()).isEqualTo(TipoLancamento.DEBITO);
    assertThat(debito.valor()).isEqualTo(new BigDecimal("42.50"));
    assertThat(debito.dataReferencia()).isEqualTo(LocalDate.parse("2026-10-22"));
    assertThat(debito.eventId()).isEqualTo(UUID.fromString("e5f6a7b8-c9d0-4123-8def-5678901234ef"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidos")
  void recusaOsExemplosInvalidos(String nome, Invalido invalido) {
    assertThatThrownBy(() -> leitor.ler(invalido.json()))
        .as("o leitor deve recusar %s", nome)
        .isInstanceOfSatisfying(
            EventoForaDoContratoException.class,
            e -> assertThat(e.violacoes()).containsExactly(VIOLACAO_ESPERADA.get(nome)));
  }

  static Stream<Arguments> invalidos() {
    return EventosJson.invalidos().stream().map(i -> Arguments.of(i.nome(), i));
  }
}
