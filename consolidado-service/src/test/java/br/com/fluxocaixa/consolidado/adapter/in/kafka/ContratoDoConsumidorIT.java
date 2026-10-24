package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.domain.ChaveDiaria;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-009.4 (o consumidor não recalcula a data) e AC-T022.2 (os exemplos <i>golden</i> de {@code
 * contracts/}, como estão, são aplicados com os efeitos de {@code 03 §3.2}).
 */
@Tag("RF-008")
@Tag("RF-009")
class ContratoDoConsumidorIT extends IntegracaoKafka {

  private static final Duration ESPERA = Duration.ofSeconds(30);
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  @Test
  void dataDeReferenciaDoEventoNaoERecalculada() {
    UUID comerciante = UUID.randomUUID();
    Map<String, Object> campos =
        EventosJson.credito(UUID.randomUUID(), comerciante, "11.00", "2026-10-21");
    // 23:30 de 21/10 em São Paulo; a data UTC (22) estaria errada (03 §4.1).
    campos.put("occurredAt", "2026-10-22T02:30:00Z");

    KafkaConsolidado.publicar(comerciante.toString(), EventosJson.json(campos), null);

    ChaveDiaria dia21 = new ChaveDiaria(comerciante, LocalDate.parse("2026-10-21"));
    await().atMost(ESPERA).until(() -> TabelasDoConsolidado.dias().containsKey(dia21));
    assertThat(TabelasDoConsolidado.dias())
        .doesNotContainKey(new ChaveDiaria(comerciante, LocalDate.parse("2026-10-22")));
  }

  @Test
  void exemplosGoldenSaoAplicadosComOsEfeitosDaRegra() {
    UUID comerciante = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    KafkaConsolidado.publicar(
        comerciante.toString(), EventosJson.golden("lancamento-registrado.v1.json"), null);
    KafkaConsolidado.publicar(
        comerciante.toString(), EventosJson.golden("lancamento-registrado.v1.estorno.json"), null);

    ChaveDiaria dia22 = new ChaveDiaria(comerciante, LocalDate.parse("2026-10-22"));
    ChaveDiaria dia23 = new ChaveDiaria(comerciante, LocalDate.parse("2026-10-23"));
    await()
        .atMost(ESPERA)
        .until(
            () ->
                TabelasDoConsolidado.dias().containsKey(dia22)
                    && TabelasDoConsolidado.dias().containsKey(dia23));
    BigDecimal valor = new BigDecimal("150.75");
    assertThat(TabelasDoConsolidado.dias().get(dia22))
        .isEqualTo(new MovimentoDiario(valor, ZERO, ZERO, ZERO, 1));
    assertThat(TabelasDoConsolidado.dias().get(dia23))
        .isEqualTo(new MovimentoDiario(ZERO, ZERO, valor, ZERO, 1));
  }
}
