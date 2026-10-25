package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.support.BacklogDeEventos;
import br.com.fluxocaixa.consolidado.support.BacklogDeEventos.Item;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.TestPropertySource;

/**
 * AC-T031.3 (RNF-001, RNF-004): com 2N eventos no tópico e o consumidor numa cadência controlada
 * (um atraso por evento, no decorador da porta de projeção), o <i>listener container</i> é parado
 * no meio do <i>backlog</i> e religado. A projeção fica igual à da aplicação única do modelo de
 * referência, e nenhuma linha a mais entra em {@code evento_processado}: o que já tinha sido
 * aplicado volta como duplicata e é absorvido. A parada é imediata ({@code
 * spring.kafka.listener.immediate-stop}), para o que foi lido e não aplicado não terminar no mesmo
 * <i>poll</i>.
 */
@Tag("RNF-001")
@Tag("RNF-004")
@TestPropertySource(properties = "spring.kafka.listener.immediate-stop=true")
class ConsumidorReiniciadoIT extends IntegracaoKafka {

  private static final int EVENTOS = 60;
  private static final Duration LIMITE = Duration.ofSeconds(120);
  private static final Duration CADENCIA = Duration.ofMillis(40);

  @Autowired private KafkaListenerEndpointRegistry registro;

  @Test
  void reinicioNoMeioDoBacklogNaoPerdeNemDuplica() {
    UUID comerciante = UUID.randomUUID();
    List<Item> backlog = BacklogDeEventos.gerar(comerciante, EVENTOS);
    List<UUID> eventIds = BacklogDeEventos.eventIds(backlog);
    MessageListenerContainer container =
        registro.getListenerContainer(ConsumidorDeLancamentos.ID_DO_LISTENER);
    assertThat(container).isNotNull();

    falhas.atrasar(CADENCIA);
    BacklogDeEventos.publicar(comerciante, backlog);
    await().atMost(LIMITE).until(() -> TabelasDoConsolidado.processadosEntre(eventIds) >= 1);

    container.stop();
    long antesDoReinicio = TabelasDoConsolidado.processadosEntre(eventIds);
    assertThat(antesDoReinicio).as("parou no meio do backlog").isBetween(1L, (long) EVENTOS - 1);

    falhas.atrasar(Duration.ZERO);
    container.start();

    await()
        .atMost(LIMITE)
        .untilAsserted(
            () ->
                assertThat(TabelasDoConsolidado.diasDe(comerciante))
                    .isEqualTo(BacklogDeEventos.esperado(backlog)));
    assertThat(TabelasDoConsolidado.processadosEntre(eventIds)).isEqualTo(EVENTOS);
  }
}
