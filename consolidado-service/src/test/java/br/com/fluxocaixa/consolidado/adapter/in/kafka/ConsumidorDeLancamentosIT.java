package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.domain.ChaveDiaria;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AC-008.1 (projeção, {@code evento_processado} e offset só depois do commit), AC-T022.1 (falha
 * depois do commit vira duplicata na reentrega) e AC-008.2 (mesmo {@code eventId} duas vezes).
 */
@Tag("RF-008")
@Tag("RNF-004")
class ConsumidorDeLancamentosIT extends IntegracaoKafka {

  private static final Duration ESPERA = Duration.ofSeconds(30);
  private static final String DIA = "2026-10-22";

  @Autowired private MeterRegistry metricas;

  @Test
  void eventoValidoAtualizaAProjecaoEConfirmaOOffsetDepois() {
    UUID evento = UUID.randomUUID();
    UUID comerciante = UUID.randomUUID();

    RecordMetadata publicado = publicarCredito(evento, comerciante, "150.75");

    ChaveDiaria chave = new ChaveDiaria(comerciante, LocalDate.parse(DIA));
    await().atMost(ESPERA).until(() -> TabelasDoConsolidado.dias().containsKey(chave));
    assertThat(TabelasDoConsolidado.dias().get(chave)).isEqualTo(credito("150.75", 1));
    assertThat(TabelasDoConsolidado.processadoEm(evento)).isPresent();
    await()
        .atMost(ESPERA)
        .until(() -> offsetConfirmado(publicado).orElse(-1) >= publicado.offset() + 1);
  }

  @Test
  void falhaAntesDoCommitNaoGravaNemConfirmaETentaDeNovo() throws InterruptedException {
    UUID evento = UUID.randomUUID();
    UUID comerciante = UUID.randomUUID();
    CountDownLatch primeiraFalha = falhas.falharAntes(evento, 1);

    RecordMetadata publicado = publicarCredito(evento, comerciante, "10.00");

    assertThat(primeiraFalha.await(30, TimeUnit.SECONDS)).isTrue();
    ChaveDiaria chave = new ChaveDiaria(comerciante, LocalDate.parse(DIA));
    assertThat(TabelasDoConsolidado.dias()).doesNotContainKey(chave);
    assertThat(TabelasDoConsolidado.processadoEm(evento)).isEmpty();
    assertThat(offsetConfirmado(publicado).orElse(-1)).isLessThanOrEqualTo(publicado.offset());

    await().atMost(ESPERA).until(() -> TabelasDoConsolidado.dias().containsKey(chave));
    assertThat(TabelasDoConsolidado.dias().get(chave)).isEqualTo(credito("10.00", 1));
    assertThat(falhas.chamadas(evento)).isEqualTo(2);
    await()
        .atMost(ESPERA)
        .until(() -> offsetConfirmado(publicado).orElse(-1) >= publicado.offset() + 1);
  }

  @Test
  void falhaDepoisDoCommitReentregaComoDuplicadoSemSomarDeNovo() {
    UUID evento = UUID.randomUUID();
    UUID comerciante = UUID.randomUUID();
    falhas.falharDepois(evento, 1);
    double duplicadosAntes = contador("consolidado.events.duplicates");
    double aplicadosAntes = contador("consolidado.events.processed");

    RecordMetadata publicado = publicarCredito(evento, comerciante, "7.50");

    await().atMost(ESPERA).until(() -> falhas.chamadas(evento) == 2);
    await()
        .atMost(ESPERA)
        .until(() -> offsetConfirmado(publicado).orElse(-1) >= publicado.offset() + 1);
    ChaveDiaria chave = new ChaveDiaria(comerciante, LocalDate.parse(DIA));
    assertThat(TabelasDoConsolidado.dias().get(chave)).isEqualTo(credito("7.50", 1));
    assertThat(contador("consolidado.events.duplicates") - duplicadosAntes).isEqualTo(1.0);
    assertThat(contador("consolidado.events.processed") - aplicadosAntes).isZero();
  }

  @Test
  void mesmoEventoDuasVezesAlteraAProjecaoUmaVez() {
    UUID evento = UUID.randomUUID();
    UUID comerciante = UUID.randomUUID();
    double duplicadosAntes = contador("consolidado.events.duplicates");
    double aplicadosAntes = contador("consolidado.events.processed");

    publicarCredito(evento, comerciante, "3.00");
    RecordMetadata segundo = publicarCredito(evento, comerciante, "3.00");

    await()
        .atMost(ESPERA)
        .until(() -> offsetConfirmado(segundo).orElse(-1) >= segundo.offset() + 1);
    ChaveDiaria chave = new ChaveDiaria(comerciante, LocalDate.parse(DIA));
    assertThat(TabelasDoConsolidado.dias().get(chave)).isEqualTo(credito("3.00", 1));
    assertThat(contador("consolidado.events.processed") - aplicadosAntes).isEqualTo(1.0);
    assertThat(contador("consolidado.events.duplicates") - duplicadosAntes).isEqualTo(1.0);
  }

  private static RecordMetadata publicarCredito(UUID evento, UUID comerciante, String valor) {
    return KafkaConsolidado.publicar(
        comerciante.toString(),
        EventosJson.json(EventosJson.credito(evento, comerciante, valor, DIA)),
        "req-" + evento);
  }

  private static OptionalLong offsetConfirmado(RecordMetadata publicado) {
    return KafkaConsolidado.offsetConfirmado(GRUPO, publicado.partition());
  }

  private double contador(String nome) {
    return metricas.get(nome).counter().count();
  }

  private static MovimentoDiario credito(String valor, int qtd) {
    return new MovimentoDiario(
        new BigDecimal(valor), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, qtd);
  }
}
