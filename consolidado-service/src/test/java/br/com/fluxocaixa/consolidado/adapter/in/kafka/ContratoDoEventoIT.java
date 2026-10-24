package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.domain.ChaveDiaria;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.EventosJson.Invalido;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado.LeitorDaDlt;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado.MensagemDaDlt;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * AC-T030.3 (lado do Consolidado): cada exemplo de {@code examples/invalidos/} de {@code contracts}
 * vai para a DLT na primeira tentativa, sem alterar {@code consolidado_diario} nem {@code
 * evento_processado}, e a partição segue. AC-T030.1: o <i>golden</i> de débito é aplicado com o
 * efeito de {@code 03 §3.2}.
 */
@Tag("RF-008")
class ContratoDoEventoIT extends IntegracaoKafka {

  private static final Duration ESPERA = Duration.ofSeconds(40);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MeterRegistry metricas;

  @Test
  void invalidosVaoParaADltNaPrimeiraTentativaSemAlterarAProjecao() {
    UUID comerciante = UUID.randomUUID();
    String chave = comerciante.toString();
    List<Invalido> invalidos = EventosJson.invalidos();
    assertThat(invalidos).hasSizeGreaterThanOrEqualTo(10);
    int diasAntes = TabelasDoConsolidado.dias().size();
    int processadosAntes = TabelasDoConsolidado.eventosProcessados();
    double dltAntes = contador("consolidado.events.dlt");

    try (LeitorDaDlt dlt = KafkaConsolidado.leitorDaDlt()) {
      long inicio = System.nanoTime();
      for (int i = 0; i < invalidos.size(); i++) {
        KafkaConsolidado.publicar(chave, invalidos.get(i).json(), "req-invalido-" + i);
      }
      UUID marcador = UUID.randomUUID();
      KafkaConsolidado.publicar(
          chave,
          EventosJson.json(EventosJson.credito(marcador, comerciante, "2.00", "2026-10-22")),
          null);

      List<MensagemDaDlt> naDlt =
          await()
              .atMost(ESPERA)
              .until(
                  () -> dlt.ler(chave, Duration.ofMillis(200)), l -> l.size() == invalidos.size());
      ChaveDiaria dia = new ChaveDiaria(comerciante, LocalDate.parse("2026-10-22"));
      await().atMost(ESPERA).until(() -> TabelasDoConsolidado.dias().containsKey(dia));

      // Sem nova tentativa: uma única cadeia de retry (1 + 2 + 4 s) já passaria de 7 s.
      assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(7));
      assertThat(naDlt)
          .extracting(MensagemDaDlt::valor)
          .containsExactlyElementsOf(invalidos.stream().map(Invalido::json).toList());
      for (MensagemDaDlt mensagem : naDlt) {
        assertThat(mensagem.cabecalho("kafka_dlt-exception-cause-fqcn"))
            .isEqualTo(EventoForaDoContratoException.class.getName());
      }
      await()
          .atMost(ESPERA)
          .until(() -> contador("consolidado.events.dlt") - dltAntes == invalidos.size());
      // Só o marcador válido mudou a projeção: um dia e um evento processado.
      assertThat(TabelasDoConsolidado.dias()).hasSize(diasAntes + 1);
      assertThat(TabelasDoConsolidado.eventosProcessados()).isEqualTo(processadosAntes + 1);
      assertThat(TabelasDoConsolidado.processadoEm(marcador)).isPresent();
    }
  }

  @Test
  void goldenDeDebitoEAplicadoComOEfeitoDaRegra() {
    UUID comerciante = UUID.randomUUID();
    // O exemplo como está, só com o comerciante trocado para não dividir a projeção com outros ITs.
    ObjectNode debito =
        (ObjectNode) JSON.readTree(EventosJson.golden("lancamento-registrado.v1.debito.json"));
    debito.put("comercianteId", comerciante.toString());

    KafkaConsolidado.publicar(comerciante.toString(), JSON.writeValueAsString(debito), null);

    ChaveDiaria dia22 = new ChaveDiaria(comerciante, LocalDate.parse("2026-10-22"));
    await().atMost(ESPERA).until(() -> TabelasDoConsolidado.dias().containsKey(dia22));
    BigDecimal zero = BigDecimal.ZERO;
    assertThat(TabelasDoConsolidado.dias().get(dia22))
        .isEqualTo(new MovimentoDiario(zero, new BigDecimal("42.50"), zero, zero, 1));
  }

  private double contador(String nome) {
    return metricas.get(nome).counter().count();
  }
}
