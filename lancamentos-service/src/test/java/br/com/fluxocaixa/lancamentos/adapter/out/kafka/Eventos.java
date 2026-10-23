package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.KafkaLancamentos;
import br.com.fluxocaixa.lancamentos.support.KafkaLancamentos.Mensagem;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/** Criação de eventos na outbox e leitura do tópico, comuns aos ITs do publicador (T-016). */
final class Eventos {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private Eventos() {}

  /** Registra um crédito pelo caso de uso real e devolve o {@code eventId} gravado na outbox. */
  static UUID registrar(
      RegistrarLancamento registrar, JdbcClient jdbc, UUID comerciante, String requestId) {
    UUID lancamento =
        registrar
            .registrar(
                new RegistrarLancamentoCommand(
                    comerciante,
                    "pub-" + UUID.randomUUID(),
                    TipoLancamento.CREDITO,
                    new BigDecimal("12.3"),
                    null,
                    null,
                    null,
                    requestId))
            .lancamento()
            .id();
    return jdbc.sql("SELECT event_id FROM outbox_event WHERE aggregate_id = ?")
        .param(lancamento)
        .query(UUID.class)
        .single();
  }

  /** Mensagens do tópico desses eventos (com duplicatas), esperando até todos aparecerem. */
  static List<Mensagem> lerAte(Set<UUID> eventIds, Duration limite) {
    try (KafkaLancamentos.Leitor leitor = KafkaLancamentos.leitor()) {
      return await()
          .atMost(limite)
          .until(
              () ->
                  leitor.ler(Duration.ofMillis(500)).stream()
                      .filter(m -> eventIds.contains(eventId(m)))
                      .toList(),
              lidas -> lidas.stream().map(Eventos::eventId).toList().containsAll(eventIds));
    }
  }

  static UUID eventId(Mensagem mensagem) {
    return UUID.fromString(JSON.readTree(mensagem.valor()).path("eventId").asString());
  }

  static long pendentes(JdbcClient jdbc) {
    return jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NULL")
        .query(Long.class)
        .single();
  }
}
