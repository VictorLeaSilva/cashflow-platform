package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.application.EstornarLancamento;
import br.com.fluxocaixa.lancamentos.application.EstornarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.application.ExecucaoIdempotente;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.application.port.out.IdGenerator;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.ContratoDoEvento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * AC-T030.2: com {@code Clock} e {@code IdGenerator} fixos e a entrada do exemplo, o JSON gravado
 * em {@code outbox_event.payload} é igual (árvore JSON) ao <i>golden</i> de {@code contracts}, e
 * também é válido no schema. Os casos de uso são montados aqui com os mesmos componentes do
 * contexto, trocando só a sequência de ids e o relógio de cada exemplo.
 *
 * <p>Exceção da Q-069: o <i>golden</i> de estorno traz {@code traceId}, que o produtor só emite a
 * partir da T-032. O teste retira esse campo do esperado e uma sentinela falha quando o produtor
 * passar a emiti-lo, obrigando a remover a exceção.
 */
@Tag("RF-008")
class ContratoDoEventoIT extends IntegracaoPostgres {

  private static final String CREDITO = "lancamento-registrado.v1.json";
  private static final String DEBITO = "lancamento-registrado.v1.debito.json";
  private static final String ESTORNO = "lancamento-registrado.v1.estorno.json";

  @Autowired private ExecucaoIdempotente execucaoIdempotente;
  @Autowired private LancamentoRepository lancamentos;
  @Autowired private OutboxRepository outbox;
  @Autowired private PoliticaLancamento politica;
  @Autowired private RelogioFixo.Controlado relogio;
  @Autowired private JdbcClient jdbc;

  @Test
  void eventoProduzidoEIgualAoGoldenDeCreditoDebitoEEstorno() {
    JsonNode credito = ContratoDoEvento.goldenComoArvore(CREDITO);
    JsonNode debito = ContratoDoEvento.goldenComoArvore(DEBITO);
    JsonNode estorno = ContratoDoEvento.goldenComoArvore(ESTORNO);

    assertIgual(CREDITO, credito, registrar(credito, TipoLancamento.CREDITO));
    assertIgual(DEBITO, debito, registrar(debito, TipoLancamento.DEBITO));
    assertEstornoIgual(estorno, estornar(estorno));
  }

  private void assertEstornoIgual(JsonNode golden, JsonNode produzido) {
    assertThat(produzido.has("traceId"))
        .as(
            "o produtor passou a emitir traceId (T-032): remova a exceção da Q-069 deste teste e"
                + " compare o golden inteiro")
        .isFalse();
    ObjectNode esperado = (ObjectNode) golden.deepCopy();
    esperado.remove("traceId");
    assertIgual(ESTORNO + " (sem traceId, Q-069)", esperado, produzido);
  }

  private static void assertIgual(String golden, JsonNode esperado, JsonNode produzido) {
    assertThat(produzido).as("payload produzido para %s", golden).isEqualTo(esperado);
    assertThat(ContratoDoEvento.violacoes(produzido))
        .as("o evento produzido para %s valida no schema", golden)
        .isEmpty();
  }

  private JsonNode registrar(JsonNode golden, TipoLancamento tipo) {
    relogio.ajustar(Instant.parse(golden.get("occurredAt").asString()));
    var casoDeUso =
        new RegistrarLancamento(
            execucaoIdempotente,
            lancamentos,
            outbox,
            ids(golden.get("lancamentoId").asString(), golden.get("eventId").asString()),
            politica,
            relogio);
    casoDeUso.registrar(
        new RegistrarLancamentoCommand(
            UUID.fromString(golden.get("comercianteId").asString()),
            UUID.randomUUID().toString(),
            tipo,
            new BigDecimal(golden.get("valor").asString()),
            "Venda balcão",
            null,
            null,
            null));
    return payload(golden.get("eventId").asString());
  }

  private JsonNode estornar(JsonNode golden) {
    relogio.ajustar(Instant.parse(golden.get("occurredAt").asString()));
    var casoDeUso =
        new EstornarLancamento(
            execucaoIdempotente,
            lancamentos,
            outbox,
            ids(golden.get("lancamentoId").asString(), golden.get("eventId").asString()),
            politica,
            relogio);
    casoDeUso.estornar(
        new EstornarLancamentoCommand(
            UUID.fromString(golden.get("comercianteId").asString()),
            UUID.randomUUID().toString(),
            UUID.fromString(golden.get("referenciaLancamentoId").asString()),
            null));
    return payload(golden.get("eventId").asString());
  }

  private JsonNode payload(String eventId) {
    String json =
        jdbc.sql("SELECT payload::text FROM outbox_event WHERE event_id = ?")
            .param(UUID.fromString(eventId))
            .query(String.class)
            .single();
    return ContratoDoEvento.comoArvore(json);
  }

  /** Primeiro id: o do lançamento; segundo: o do evento (a ordem em que os casos de uso pedem). */
  private static IdGenerator ids(String lancamentoId, String eventId) {
    Deque<UUID> fila =
        new ArrayDeque<>(List.of(UUID.fromString(lancamentoId), UUID.fromString(eventId)));
    return fila::removeFirst;
  }
}
