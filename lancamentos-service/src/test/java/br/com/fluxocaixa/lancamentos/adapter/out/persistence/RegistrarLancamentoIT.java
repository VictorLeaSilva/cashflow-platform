package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-001.1 e AC-002.1 (parte de aplicação): um registro grava 1 lançamento e 1 evento na outbox com
 * o payload de 03 §4. AC-T012.4: nada da chave nem do payload financeiro vai para o log.
 */
@Tag("RF-001")
@Tag("RF-002")
@ExtendWith(OutputCaptureExtension.class)
class RegistrarLancamentoIT extends IntegracaoPostgres {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private JdbcClient jdbc;

  @ParameterizedTest
  @EnumSource(
      value = TipoLancamento.class,
      names = {"CREDITO", "DEBITO"})
  void registroGravaLancamentoEEventoComOMesmoId(TipoLancamento tipo) {
    UUID comerciante = UUID.randomUUID();

    var resultado = registrarLancamento.registrar(comando(comerciante, tipo, "100", "req-" + tipo));

    assertThat(resultado.replay()).isFalse();
    assertThat(Contagens.de(jdbc, comerciante)).isEqualTo(new Contagens(1, 1, 1));

    UUID lancamentoId = resultado.lancamento().id();
    Map<String, Object> outbox =
        jdbc.sql(
                "SELECT event_id, aggregate_id, topic, message_key, event_type, schema_version,"
                    + " payload::text AS payload, published_at, attempt_count, request_id"
                    + " FROM outbox_event WHERE aggregate_id = ?")
            .param(lancamentoId)
            .query()
            .singleRow();
    assertThat(outbox)
        .containsEntry("aggregate_id", lancamentoId)
        .containsEntry("topic", "cashflow.lancamentos.v1")
        .containsEntry("message_key", comerciante.toString())
        .containsEntry("event_type", "LancamentoRegistrado")
        .containsEntry("schema_version", 1)
        .containsEntry("published_at", null)
        .containsEntry("attempt_count", 0)
        .containsEntry("request_id", "req-" + tipo);

    Map<String, Object> payload = lerPayload((String) outbox.get("payload"));
    assertThat(payload)
        .containsOnlyKeys(
            "eventId",
            "eventType",
            "schemaVersion",
            "occurredAt",
            "comercianteId",
            "lancamentoId",
            "tipo",
            "valor",
            "dataReferencia")
        .containsEntry("eventId", outbox.get("event_id").toString())
        .containsEntry("eventType", "LancamentoRegistrado")
        .containsEntry("schemaVersion", 1)
        .containsEntry("occurredAt", RelogioFixo.AGORA.toString())
        .containsEntry("comercianteId", comerciante.toString())
        .containsEntry("lancamentoId", lancamentoId.toString())
        .containsEntry("tipo", tipo.name())
        .containsEntry("valor", "100.00")
        .containsEntry("dataReferencia", "2026-10-23");
    assertThat(outbox.get("event_id")).isNotEqualTo(lancamentoId);
  }

  @Test
  void requestIdAusenteFicaNulo() {
    UUID comerciante = UUID.randomUUID();

    var resultado =
        registrarLancamento.registrar(comando(comerciante, TipoLancamento.CREDITO, "1.50", null));

    assertThat(
            jdbc.sql("SELECT request_id FROM outbox_event WHERE aggregate_id = ?")
                .param(resultado.lancamento().id())
                .query(String.class)
                .optional())
        .isEmpty();
  }

  @Test
  void logNaoContemChaveNemPayloadFinanceiro(CapturedOutput saida) {
    UUID comerciante = UUID.randomUUID();
    var comando =
        new RegistrarLancamentoCommand(
            comerciante,
            "chave-que-nao-pode-vazar",
            TipoLancamento.CREDITO,
            new BigDecimal("8765.43"),
            "descricao que nao pode vazar",
            null,
            "externo-que-nao-pode-vazar",
            null);

    registrarLancamento.registrar(comando);
    registrarLancamento.registrar(comando);

    assertThat(saida.getAll())
        .doesNotContain(
            "chave-que-nao-pode-vazar",
            "8765.43",
            "descricao que nao pode vazar",
            "externo-que-nao-pode-vazar");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> lerPayload(String json) {
    return JSON.readValue(json, Map.class);
  }

  private static RegistrarLancamentoCommand comando(
      UUID comerciante, TipoLancamento tipo, String valor, String requestId) {
    return new RegistrarLancamentoCommand(
        comerciante,
        UUID.randomUUID().toString(),
        tipo,
        new BigDecimal(valor),
        "Venda balcão",
        null,
        null,
        requestId);
  }
}
