package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.consolidado.domain.EventoInvalidoException;
import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import br.com.fluxocaixa.consolidado.domain.TipoLancamento;
import br.com.fluxocaixa.consolidado.support.EventosJson;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** Leitura do evento: JSON, schema de {@code contracts/} (Q-043) e domínio (Q-034). */
@Tag("RF-008")
class LeitorDeEventoTest {

  private static final UUID EVENTO = UUID.fromString("a1b2c3d4-e5f6-4789-8abc-1234567890ab");
  private static final UUID COMERCIANTE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

  private final LeitorDeEvento leitor = new LeitorDeEvento(JsonMapper.builder().build());

  @Test
  void leOExemploGoldenDeCredito() {
    EventoLido lido = leitor.ler(EventosJson.golden("lancamento-registrado.v1.json"));

    assertThat(lido.occurredAt()).isEqualTo(Instant.parse("2026-10-22T13:30:00Z"));
    assertThat(lido.evento())
        .isEqualTo(
            new LancamentoRegistrado(
                EVENTO,
                COMERCIANTE,
                UUID.fromString("b2c3d4e5-f6a7-4890-9bcd-2345678901bc"),
                TipoLancamento.CREDITO,
                new BigDecimal("150.75"),
                LocalDate.parse("2026-10-22"),
                null,
                null));
  }

  @Test
  void leOExemploGoldenDeEstorno() {
    LancamentoRegistrado estorno =
        leitor.ler(EventosJson.golden("lancamento-registrado.v1.estorno.json")).evento();

    assertThat(estorno.tipo()).isEqualTo(TipoLancamento.ESTORNO);
    assertThat(estorno.tipoOriginal()).isEqualTo(TipoLancamento.CREDITO);
    assertThat(estorno.referenciaLancamentoId())
        .isEqualTo(UUID.fromString("b2c3d4e5-f6a7-4890-9bcd-2345678901bc"));
    assertThat(estorno.dataReferencia()).isEqualTo(LocalDate.parse("2026-10-23"));
  }

  @Test
  void occurredAtComOffsetViraOInstante() {
    Map<String, Object> campos = credito();
    campos.put("occurredAt", "2026-10-21T23:30:00-03:00");

    assertThat(leitor.ler(EventosJson.json(campos)).occurredAt())
        .isEqualTo(Instant.parse("2026-10-22T02:30:00Z"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"{", "não é json", "[]", "null", "\"texto\""})
  void malformadoOuNaoObjetoEstaForaDoContrato(String texto) {
    assertThatThrownBy(() -> leitor.ler(texto)).isInstanceOf(EventoForaDoContratoException.class);
  }

  static Stream<Arguments> violacoesDeSchema() {
    return Stream.of(
        violacao("valor com 3 casas", c -> c.put("valor", "10.123"), "valor", "pattern"),
        violacao("valor como número", c -> c.put("valor", 10), "valor", "type"),
        violacao("campo extra", c -> c.put("descricao", "x"), "", "additionalProperties"),
        violacao("sem comercianteId", c -> c.remove("comercianteId"), "", "required"),
        violacao(
            "eventId fora do formato", c -> c.put("eventId", "1-1-1-1-1"), "eventId", "format"),
        violacao(
            "dataReferencia inexistente",
            c -> c.put("dataReferencia", "2026-02-30"),
            "dataReferencia",
            "format"),
        violacao(
            "occurredAt sem offset",
            c -> c.put("occurredAt", "2026-10-22T13:30:00"),
            "occurredAt",
            "format"),
        violacao("tipo desconhecido", c -> c.put("tipo", "PIX"), "tipo", "enum"),
        violacao("schemaVersion 2", c -> c.put("schemaVersion", 2), "schemaVersion", "const"),
        violacao("crédito com tipoOriginal", c -> c.put("tipoOriginal", "DEBITO"), "", "not"),
        violacao(
            "estorno sem tipoOriginal",
            c -> {
              c.put("tipo", "ESTORNO");
              c.put("referenciaLancamentoId", UUID.randomUUID().toString());
            },
            "",
            "required"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("violacoesDeSchema")
  void violacaoDeSchemaListaCampoEPalavraChave(
      String caso, Consumer<Map<String, Object>> mudanca, String campo, String palavraChave) {
    Map<String, Object> campos = credito();
    mudanca.accept(campos);

    assertThatThrownBy(() -> leitor.ler(EventosJson.json(campos)))
        .isInstanceOf(EventoForaDoContratoException.class)
        .extracting(e -> ((EventoForaDoContratoException) e).violacoes())
        .satisfies(
            violacoes ->
                assertThat(violacoes)
                    .as(caso)
                    .anyMatch(v -> v.contains(campo) && v.endsWith(":" + palavraChave)));
  }

  @Test
  void mensagemDaViolacaoNaoTrazValoresRecebidos() {
    Map<String, Object> campos = credito();
    campos.put("valor", "123.456");
    campos.put("eventId", "valor-secreto-no-id");

    assertThatThrownBy(() -> leitor.ler(EventosJson.json(campos)))
        .isInstanceOf(EventoForaDoContratoException.class)
        .message()
        .doesNotContain("123.456", "valor-secreto-no-id", COMERCIANTE.toString());
  }

  @Test
  void valorZeroPassaNoSchemaMasViolaODominio() {
    Map<String, Object> campos = credito();
    campos.put("valor", "0.00");

    assertThatThrownBy(() -> leitor.ler(EventosJson.json(campos)))
        .isInstanceOf(EventoInvalidoException.class);
  }

  @Test
  void schemaVemDoArtefatoContractsSemCopiaNoServico() {
    URL schema =
        getClass().getClassLoader().getResource("events/lancamento-registrado.v1.schema.json");

    assertThat(schema).isNotNull();
    assertThat(schema.toString()).contains("contracts");
    assertThat(Path.of("src", "main", "resources", "events")).doesNotExist();
    assertThat(Files.exists(Path.of("src", "main", "resources", "examples"))).isFalse();
  }

  private static Map<String, Object> credito() {
    return EventosJson.credito(EVENTO, COMERCIANTE, "150.75", "2026-10-22");
  }

  private static Arguments violacao(
      String caso, Consumer<Map<String, Object>> mudanca, String campo, String palavraChave) {
    return Arguments.of(caso, mudanca, campo, palavraChave);
  }
}
