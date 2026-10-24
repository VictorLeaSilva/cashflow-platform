package br.com.fluxocaixa.lancamentos.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.support.ContratoDoEvento;
import br.com.fluxocaixa.lancamentos.support.ContratoDoEvento.Invalido;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * AC-T030.1 e AC-T030.3 (lado do Lançamentos): os exemplos <i>golden</i> de {@code contracts} são
 * válidos no schema com {@code format} conferido, e cada exemplo de {@code examples/invalidos/} é
 * rejeitado. O schema e os exemplos vêm do artefato {@code contracts}, sem cópia (AC-T030.5).
 */
@Tag("RF-008")
class ContratoDoSchemaTest {

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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "lancamento-registrado.v1.json",
        "lancamento-registrado.v1.debito.json",
        "lancamento-registrado.v1.estorno.json"
      })
  void exemploGoldenEhValidoNoSchema(String arquivo) {
    assertThat(ContratoDoEvento.violacoes(ContratoDoEvento.goldenComoArvore(arquivo))).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidos")
  void exemploInvalidoEhRejeitadoPeloSchema(String nome, Invalido invalido) {
    assertThat(ContratoDoEvento.violacoes(ContratoDoEvento.comoArvore(invalido.json())))
        .as("violações de %s", nome)
        .containsExactly(VIOLACAO_ESPERADA.get(nome));
  }

  @Test
  void existemInvalidosEOReadmeDescreveCadaUm() {
    List<Invalido> invalidos = ContratoDoEvento.invalidos();

    assertThat(invalidos)
        .extracting(Invalido::nome)
        .containsExactlyInAnyOrderElementsOf(VIOLACAO_ESPERADA.keySet());
    String readme = ContratoDoEvento.readmeDosInvalidos();
    for (Invalido invalido : invalidos) {
      assertThat(readme)
          .as("README cita %s", invalido.nome())
          .contains("`" + invalido.nome() + "`");
    }
  }

  static Stream<Arguments> invalidos() {
    return ContratoDoEvento.invalidos().stream().map(i -> Arguments.of(i.nome(), i));
  }
}
