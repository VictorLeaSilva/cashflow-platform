package br.com.fluxocaixa.lancamentos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fluxocaixa.lancamentos.application.port.out.OutboxPendente;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicadorDeEventos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** Ciclo do publicador (T-016, Q-030) e limpeza (Q-029), com Mockito só nas portas (04 §2.3). */
@Tag("RNF-007")
@Tag("RNF-004")
class PublicarOutboxTest {

  private static final Instant AGORA = Instant.parse("2026-10-23T15:00:00Z");
  private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);

  private final OutboxRepository outbox = mock(OutboxRepository.class);
  private final PublicadorDeEventos publicador = mock(PublicadorDeEventos.class);
  private final PublicarOutbox publicar =
      new PublicarOutbox(
          outbox,
          publicador,
          TransactionOperations.withoutTransaction(),
          CLOCK,
          3,
          Duration.ofDays(7));

  @Test
  void semPendentesNaoPublicaNada() {
    when(outbox.travarPendentes(3)).thenReturn(List.of());

    assertThat(publicar.publicarLote()).isEqualTo(new PublicarOutbox.ResultadoDoLote(0, 0, false));
    verify(publicador, never()).publicar(any());
  }

  @Test
  void todosConfirmadosSaoMarcadosComOInstanteDoClock() {
    List<OutboxPendente> lote = List.of(pendente(), pendente());
    when(outbox.travarPendentes(3)).thenReturn(lote);
    when(publicador.publicar(lote)).thenReturn(Map.of());

    PublicarOutbox.ResultadoDoLote resultado = publicar.publicarLote();

    assertThat(resultado).isEqualTo(new PublicarOutbox.ResultadoDoLote(2, 0, false));
    verify(outbox).marcarPublicados(List.of(lote.get(0).eventId(), lote.get(1).eventId()), AGORA);
    verify(outbox, never()).registrarFalhas(any());
  }

  /** Q-030: confirmados marcados e falhas registradas na mesma transação; lote cheio sinalizado. */
  @Test
  void falhaParcialMarcaOsConfirmadosERegistraAsFalhas() {
    List<OutboxPendente> lote = List.of(pendente(), pendente(), pendente());
    UUID falhou = lote.get(1).eventId();
    when(outbox.travarPendentes(3)).thenReturn(lote);
    when(publicador.publicar(lote)).thenReturn(Map.of(falhou, "TimeoutException: expirou"));

    PublicarOutbox.ResultadoDoLote resultado = publicar.publicarLote();

    assertThat(resultado).isEqualTo(new PublicarOutbox.ResultadoDoLote(2, 1, true));
    verify(outbox).marcarPublicados(List.of(lote.get(0).eventId(), lote.get(2).eventId()), AGORA);
    verify(outbox).registrarFalhas(Map.of(falhou, "TimeoutException: expirou"));
  }

  @Test
  void todosComFalhaNaoMarcamNada() {
    List<OutboxPendente> lote = List.of(pendente());
    when(outbox.travarPendentes(3)).thenReturn(lote);
    when(publicador.publicar(lote)).thenReturn(Map.of(lote.getFirst().eventId(), "falhou"));

    assertThat(publicar.publicarLote()).isEqualTo(new PublicarOutbox.ResultadoDoLote(0, 1, false));
    verify(outbox, never()).marcarPublicados(any(), any());
  }

  /** Q-029: blocos até esvaziar, com o corte em Clock − retenção. */
  @Test
  void limpezaRemoveEmBlocosAteEsvaziar() {
    Instant corte = AGORA.minus(Duration.ofDays(7));
    when(outbox.excluirPublicadosAntesDe(corte, PublicarOutbox.BLOCO_DE_LIMPEZA))
        .thenReturn(PublicarOutbox.BLOCO_DE_LIMPEZA, PublicarOutbox.BLOCO_DE_LIMPEZA, 7);

    assertThat(publicar.limpar()).isEqualTo(2 * PublicarOutbox.BLOCO_DE_LIMPEZA + 7);
  }

  @Test
  void limpezaSemNadaParaRemoverParaNaPrimeiraVolta() {
    when(outbox.excluirPublicadosAntesDe(any(), anyInt())).thenReturn(0);

    assertThat(publicar.limpar()).isZero();
  }

  private static OutboxPendente pendente() {
    return new OutboxPendente(
        UUID.randomUUID(), "cashflow.lancamentos.v1", "chave", "{}", null, AGORA);
  }
}
