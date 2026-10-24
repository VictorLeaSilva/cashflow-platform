package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.consolidado.domain.LancamentoRegistrado;
import br.com.fluxocaixa.consolidado.domain.TipoLancamento;
import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.EventosJson.Invalido;
import java.math.BigDecimal;
import java.time.LocalDate;
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
        .isInstanceOf(EventoForaDoContratoException.class);
  }

  static Stream<Arguments> invalidos() {
    return EventosJson.invalidos().stream().map(i -> Arguments.of(i.nome(), i));
  }
}
