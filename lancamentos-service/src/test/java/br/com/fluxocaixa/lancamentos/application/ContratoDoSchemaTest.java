package br.com.fluxocaixa.lancamentos.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.support.ContratoDoEvento;
import br.com.fluxocaixa.lancamentos.support.ContratoDoEvento.Invalido;
import java.util.List;
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
        .isNotEmpty();
  }

  @Test
  void existemInvalidosEOReadmeDescreveCadaUm() {
    List<Invalido> invalidos = ContratoDoEvento.invalidos();

    assertThat(invalidos).hasSizeGreaterThanOrEqualTo(10);
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
