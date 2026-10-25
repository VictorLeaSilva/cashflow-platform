package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.support.BacklogDeEventos;
import br.com.fluxocaixa.consolidado.support.BacklogDeEventos.Item;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AC-T031.2 (RNF-001, risco "Consolidado parado" de 04 §2.7, lado do Consolidado): o tópico recebe
 * N eventos <b>antes</b> de o contexto subir. Com o consumidor ligado e sem nenhuma entrega prévia
 * desses eventos, a projeção converge para a do modelo de referência ({@code ProjecaoDeReferencia})
 * e todos os N entram em {@code evento_processado}. O grupo é o do {@code application.yml}: os
 * eventos são posteriores aos <i>offsets</i> que os outros ITs confirmaram.
 */
@Tag("RNF-001")
class BacklogDoConsumidorIT extends IntegracaoKafka {

  private static final int EVENTOS = 30;
  private static final Duration LIMITE = Duration.ofSeconds(90);
  private static final UUID COMERCIANTE = UUID.randomUUID();
  private static final List<Item> BACKLOG = BacklogDeEventos.gerar(COMERCIANTE, EVENTOS);

  @Autowired private MeterRegistry metricas;

  /** Antes do contexto: o consumidor ainda não existe quando os eventos chegam ao tópico. */
  @BeforeAll
  static void publicarBacklogAntesDeSubirOConsumidor() {
    BacklogDeEventos.publicar(COMERCIANTE, BACKLOG);
  }

  /**
   * Sem esvaziar as tabelas: o consumidor aplica o <i>backlog</i> assim que o contexto sobe, antes
   * do primeiro teste, e o esvaziamento da base apagaria o que ele fez. Os outros ITs usam
   * comerciantes próprios, então o que sobrou deles não interfere na comparação.
   */
  @Override
  @BeforeEach
  protected void tabelasVazias() {
    // nada
  }

  @Test
  void projecaoConvergeParaAReferenciaEContaTodosOsEventos() {
    await()
        .atMost(LIMITE)
        .untilAsserted(
            () ->
                assertThat(TabelasDoConsolidado.diasDe(COMERCIANTE))
                    .isEqualTo(BacklogDeEventos.esperado(BACKLOG)));

    assertThat(TabelasDoConsolidado.processadosEntre(BacklogDeEventos.eventIds(BACKLOG)))
        .isEqualTo(EVENTOS);
    // Pelo menos N: eventos pendentes de outros ITs no mesmo tópico também podem ser aplicados.
    assertThat(metricas.get("consolidado.events.processed").counter().count())
        .isGreaterThanOrEqualTo(EVENTOS);
  }
}
