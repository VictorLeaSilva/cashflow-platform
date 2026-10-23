package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import br.com.fluxocaixa.lancamentos.application.PublicarOutbox;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.support.IntegracaoKafka;
import br.com.fluxocaixa.lancamentos.support.KafkaLancamentos;
import br.com.fluxocaixa.lancamentos.support.KafkaLancamentos.Mensagem;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * AC-T016.3 (risco do backlog): falha depois do {@code ack} do broker e antes do {@code COMMIT} da
 * marcação. A linha continua pendente, o próximo ciclo republica o mesmo {@code eventId}, nada se
 * perde e a duplicata fica visível no tópico (at-least-once, ADR-003).
 */
@Tag("RNF-004")
@Tag("RNF-007")
class FalhaEntreEnvioEMarcacaoIT extends IntegracaoKafka {

  @MockitoSpyBean private OutboxRepository outbox;
  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private PublicarOutbox publicarOutbox;

  @AfterEach
  void restaurar() {
    reset(outbox);
  }

  @Test
  void falhaNaMarcacaoRepublicaOMesmoEventoNoProximoCiclo() {
    UUID eventId = Eventos.registrar(registrarLancamento, jdbc, UUID.randomUUID(), "rid-dup");
    doThrow(new IllegalStateException("queda simulada depois do ack"))
        .doCallRealMethod()
        .when(outbox)
        .marcarPublicados(any(), any());

    assertThatThrownBy(() -> publicarOutbox.publicarLote())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("queda simulada depois do ack");
    assertThat(Eventos.pendentes(jdbc)).as("a marcação foi desfeita").isEqualTo(1);
    List<Mensagem> depoisDaFalha = Eventos.lerAte(Set.of(eventId), Duration.ofSeconds(30));
    assertThat(depoisDaFalha).as("o broker já tinha confirmado o envio").hasSize(1);

    PublicarOutbox.ResultadoDoLote retry = publicarOutbox.publicarLote();

    assertThat(retry.publicados()).isEqualTo(1);
    assertThat(Eventos.pendentes(jdbc)).isZero();
    try (KafkaLancamentos.Leitor leitor = KafkaLancamentos.leitor()) {
      List<Mensagem> todas =
          await()
              .atMost(Duration.ofSeconds(30))
              .until(
                  () ->
                      leitor.ler(Duration.ofMillis(500)).stream()
                          .filter(m -> Eventos.eventId(m).equals(eventId))
                          .toList(),
                  lidas -> lidas.size() >= 2);
      assertThat(todas).hasSize(2).allMatch(m -> "rid-dup".equals(m.requestId()));
      assertThat(todas.get(0).valor()).isEqualTo(todas.get(1).valor());
    }
  }
}
