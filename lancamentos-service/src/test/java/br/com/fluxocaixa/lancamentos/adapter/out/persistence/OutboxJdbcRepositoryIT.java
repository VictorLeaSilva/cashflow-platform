package br.com.fluxocaixa.lancamentos.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxPendente;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoPostgres;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Operações do publicador na outbox (T-016), no PostgreSQL real e pelo pool dedicado: AC-T016.6
 * (lote, ordem, SKIP LOCKED), marcação, falhas (Q-030) e limpeza (AC-T016.7, Q-029).
 */
@Tag("RNF-004")
@Tag("RNF-007")
class OutboxJdbcRepositoryIT extends IntegracaoPostgres {

  @Autowired private OutboxRepository outbox;
  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private JdbcClient jdbc;
  @Autowired private RelogioFixo.Controlado relogio;

  @Autowired
  @Qualifier("publicador")
  private TransactionTemplate transacaoDoPublicador;

  @BeforeEach
  void outboxVazia() {
    jdbc.sql("DELETE FROM outbox_event").update();
  }

  @Test
  void travaNoMaximoOLimiteAsMaisAntigasPrimeiro() {
    List<UUID> criados = registrar(5);

    List<OutboxPendente> lote = transacaoDoPublicador.execute(s -> outbox.travarPendentes(3));

    assertThat(lote)
        .extracting(OutboxPendente::eventId)
        .containsExactlyElementsOf(criados.subList(0, 3));
    OutboxPendente primeiro = lote.getFirst();
    assertThat(primeiro.topic()).isEqualTo("cashflow.lancamentos.v1");
    assertThat(primeiro.payload()).contains("\"eventId\"", "\"valor\"");
    assertThat(primeiro.requestId()).isEqualTo("rid-0");
    assertThat(primeiro.createdAt()).isNotNull();
  }

  /** SKIP LOCKED: uma segunda transação não espera nem repete as linhas travadas pela primeira. */
  @Test
  void segundaTransacaoPulaAsLinhasTravadas() {
    List<UUID> criados = registrar(4);

    List<UUID> segunda =
        transacaoDoPublicador.execute(
            externa -> {
              List<UUID> primeira = ids(outbox.travarPendentes(2));
              assertThat(primeira).containsExactlyElementsOf(criados.subList(0, 2));
              return transacaoDeOutraConexao(() -> ids(outbox.travarPendentes(10)));
            });

    assertThat(segunda).containsExactlyElementsOf(criados.subList(2, 4));
  }

  @Test
  void marcaPublicadosERegistraFalhasSemPayload() {
    List<UUID> criados = registrar(3);
    Instant publicadoEm = RelogioFixo.AGORA.plusSeconds(1);

    transacaoDoPublicador.executeWithoutResult(
        s -> {
          outbox.marcarPublicados(criados.subList(0, 2), publicadoEm);
          outbox.registrarFalhas(Map.of(criados.get(2), "TimeoutException: expirou"));
        });
    transacaoDoPublicador.executeWithoutResult(
        s -> outbox.registrarFalhas(Map.of(criados.get(2), "TimeoutException: de novo")));

    assertThat(outbox.contarPendentes()).isEqualTo(1);
    Map<String, Object> falha = linha(criados.get(2));
    assertThat(falha)
        .containsEntry("attempt_count", 2)
        .containsEntry("last_error", "TimeoutException: de novo");
    assertThat(falha.get("published_at")).isNull();
    assertThat(linha(criados.getFirst()).get("published_at")).isNotNull();
  }

  @Test
  void contaPendentesEInformaOMaisAntigo() {
    assertThat(outbox.contarPendentes()).isZero();
    assertThat(outbox.maisAntigoPendente()).isEmpty();

    List<UUID> criados = registrar(2);

    assertThat(outbox.contarPendentes()).isEqualTo(2);
    assertThat(outbox.maisAntigoPendente()).contains(createdAt(criados.getFirst()));
  }

  /** AC-T016.7: só as publicadas antes do corte saem; pendentes nunca. */
  @Test
  void limpezaRemoveSoPublicadasAntigasEmBlocos() {
    List<UUID> criados = registrar(5);
    Instant antigo = RelogioFixo.AGORA.minus(Duration.ofDays(8));
    Instant recente = RelogioFixo.AGORA.minus(Duration.ofDays(1));
    transacaoDoPublicador.executeWithoutResult(
        s -> {
          outbox.marcarPublicados(criados.subList(0, 3), antigo);
          outbox.marcarPublicados(List.of(criados.get(3)), recente);
        });
    Instant corte = RelogioFixo.AGORA.minus(Duration.ofDays(7));

    int primeiroBloco =
        transacaoDoPublicador.execute(s -> outbox.excluirPublicadosAntesDe(corte, 2));
    int segundoBloco =
        transacaoDoPublicador.execute(s -> outbox.excluirPublicadosAntesDe(corte, 2));

    assertThat(primeiroBloco).isEqualTo(2);
    assertThat(segundoBloco).isEqualTo(1);
    assertThat(restantes()).containsExactlyInAnyOrder(criados.get(3), criados.get(4));
  }

  private List<UUID> registrar(int quantidade) {
    UUID comerciante = UUID.randomUUID();
    for (int i = 0; i < quantidade; i++) {
      relogio.ajustar(RelogioFixo.AGORA.plusMillis(i));
      registrarLancamento.registrar(
          new RegistrarLancamentoCommand(
              comerciante,
              "outbox-" + UUID.randomUUID(),
              TipoLancamento.CREDITO,
              new BigDecimal("1.00"),
              null,
              null,
              null,
              "rid-" + i));
    }
    relogio.restaurar();
    return jdbc.sql("SELECT event_id FROM outbox_event WHERE message_key = ? ORDER BY created_at")
        .param(comerciante.toString())
        .query(UUID.class)
        .list();
  }

  private List<UUID> transacaoDeOutraConexao(Supplier<List<UUID>> trabalho) {
    TransactionTemplate nova =
        new TransactionTemplate(transacaoDoPublicador.getTransactionManager());
    nova.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    nova.setTimeout(5);
    return nova.execute(s -> trabalho.get());
  }

  private static List<UUID> ids(List<OutboxPendente> pendentes) {
    return pendentes.stream().map(OutboxPendente::eventId).toList();
  }

  private Map<String, Object> linha(UUID eventId) {
    return jdbc.sql("SELECT * FROM outbox_event WHERE event_id = ?")
        .param(eventId)
        .query()
        .singleRow();
  }

  private Instant createdAt(UUID eventId) {
    return jdbc.sql("SELECT created_at FROM outbox_event WHERE event_id = ?")
        .param(eventId)
        .query(OffsetDateTime.class)
        .single()
        .toInstant();
  }

  /** {@code created_at} vem do Clock (instante do evento), não do {@code now()} do banco. */
  @Test
  void createdAtEhOInstanteDoEventoPeloClock() {
    List<UUID> criados = registrar(1);

    assertThat(createdAt(criados.getFirst())).isEqualTo(RelogioFixo.AGORA);
  }

  private List<UUID> restantes() {
    return jdbc.sql("SELECT event_id FROM outbox_event").query(UUID.class).list();
  }
}
