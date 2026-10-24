package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.fluxocaixa.consolidado.domain.ChaveDiaria;
import br.com.fluxocaixa.consolidado.support.EventosJson;
import br.com.fluxocaixa.consolidado.support.IntegracaoKafka;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado.LeitorDaDlt;
import br.com.fluxocaixa.consolidado.support.KafkaConsolidado.MensagemDaDlt;
import br.com.fluxocaixa.consolidado.support.ProjecaoComFalhas;
import br.com.fluxocaixa.consolidado.support.TabelasDoConsolidado;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AC-008.4 (fora do contrato ou do domínio direto para a DLT; falha persistente depois de 1 + 3
 * tentativas; a partição não trava) e AC-008.5 (reinjeção aplica uma vez). Risco "DLT e
 * reprocessamento" de {@code 04 §2.7}.
 */
@Tag("RF-008")
@Tag("RNF-004")
class DltIT extends IntegracaoKafka {

  private static final Duration ESPERA = Duration.ofSeconds(40);
  private static final String DIA = "2026-10-22";

  @Autowired private MeterRegistry metricas;

  @Test
  void foraDoContratoOuDoDominioVaiDiretoParaADltSemTravarAParticao() {
    UUID comerciante = UUID.randomUUID();
    String chave = comerciante.toString();
    List<String> valores = valoresForaDoContrato(comerciante);
    double dltAntes = contador("consolidado.events.dlt");

    try (LeitorDaDlt dlt = KafkaConsolidado.leitorDaDlt()) {
      long inicio = System.nanoTime();
      List<RecordMetadata> publicados = new ArrayList<>();
      for (int i = 0; i < valores.size(); i++) {
        publicados.add(KafkaConsolidado.publicar(chave, valores.get(i), "req-invalido-" + i));
      }
      KafkaConsolidado.publicar(
          chave,
          EventosJson.json(EventosJson.credito(UUID.randomUUID(), comerciante, "2.00", DIA)),
          null);

      List<MensagemDaDlt> naDlt =
          await()
              .atMost(ESPERA)
              .until(() -> dlt.ler(chave, Duration.ofMillis(200)), l -> l.size() == valores.size());

      // Sem nova tentativa: uma única cadeia de retry (1 + 2 + 4 s) já passaria de 7 s.
      assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(7));
      assertThat(naDlt).extracting(MensagemDaDlt::valor).containsExactlyElementsOf(valores);
      assertCabecalhosDeOrigem(naDlt, publicados);
      assertThat(naDlt.get(0).cabecalho("kafka_dlt-exception-cause-fqcn"))
          .isEqualTo(EventoForaDoContratoException.class.getName());
      assertThat(naDlt.get(4).cabecalho("kafka_dlt-exception-cause-fqcn"))
          .isEqualTo("br.com.fluxocaixa.consolidado.domain.EventoInvalidoException");
      ChaveDiaria dia = new ChaveDiaria(comerciante, LocalDate.parse(DIA));
      await().atMost(ESPERA).until(() -> TabelasDoConsolidado.dias().containsKey(dia));
      assertThat(TabelasDoConsolidado.dias().get(dia).qtdLancamentos()).isEqualTo(1);
      assertThat(contador("consolidado.events.dlt") - dltAntes).isEqualTo(valores.size());
    }
  }

  /** JSON malformado, três violações de schema e uma de domínio ({@code valor} zero). */
  private static List<String> valoresForaDoContrato(UUID comerciante) {
    Map<String, Object> extra = campos(comerciante, "1.00");
    extra.put("descricao", "campo fora do contrato");
    Map<String, Object> estornoSemTipo = campos(comerciante, "1.00");
    estornoSemTipo.put("tipo", "ESTORNO");
    estornoSemTipo.put("referenciaLancamentoId", UUID.randomUUID().toString());
    List<String> valores = new ArrayList<>();
    valores.add("{ isto não é json");
    for (Map<String, Object> invalido :
        List.of(
            campos(comerciante, "10.123"), extra, estornoSemTipo, campos(comerciante, "0.00"))) {
      valores.add(EventosJson.json(invalido));
    }
    return valores;
  }

  private static void assertCabecalhosDeOrigem(
      List<MensagemDaDlt> naDlt, List<RecordMetadata> publicados) {
    for (int i = 0; i < naDlt.size(); i++) {
      MensagemDaDlt mensagem = naDlt.get(i);
      assertThat(mensagem.particao()).isEqualTo(publicados.get(i).partition());
      assertThat(mensagem.cabecalho("x-request-id")).isEqualTo("req-invalido-" + i);
      assertThat(mensagem.cabecalho("kafka_dlt-original-topic")).isEqualTo(KafkaConsolidado.TOPICO);
      assertThat(mensagem.particaoDeOrigem()).isEqualTo(publicados.get(i).partition());
      assertThat(mensagem.offsetDeOrigem()).isEqualTo(publicados.get(i).offset());
      assertThat(mensagem.cabecalhos()).containsKey("kafka_dlt-exception-fqcn");
    }
  }

  @Test
  void falhaPersistenteVaiParaADltDepoisDeQuatroTentativasComBackoff() {
    UUID comerciante = UUID.randomUUID();
    String chave = comerciante.toString();
    UUID evento = UUID.randomUUID();
    falhas.falharAntes(evento, ProjecaoComFalhas.SEMPRE);
    String valor = EventosJson.json(EventosJson.credito(evento, comerciante, "5.00", DIA));

    try (LeitorDaDlt dlt = KafkaConsolidado.leitorDaDlt()) {
      KafkaConsolidado.publicar(chave, valor, "req-persistente");
      UUID depois = UUID.randomUUID();
      KafkaConsolidado.publicar(
          chave, EventosJson.json(EventosJson.credito(depois, comerciante, "1.00", DIA)), null);

      List<MensagemDaDlt> naDlt =
          await()
              .atMost(ESPERA)
              .until(() -> dlt.ler(chave, Duration.ofMillis(200)), l -> !l.isEmpty());

      assertThat(naDlt).singleElement().extracting(MensagemDaDlt::valor).isEqualTo(valor);
      assertThat(falhas.chamadas(evento)).isEqualTo(4);
      List<Long> instantes = falhas.instantesDasChamadas(evento);
      assertThat(instantes.get(1) - instantes.get(0)).isGreaterThanOrEqualTo(1_000);
      assertThat(instantes.get(2) - instantes.get(1)).isGreaterThanOrEqualTo(2_000);
      assertThat(instantes.get(3) - instantes.get(2)).isGreaterThanOrEqualTo(4_000);
      ChaveDiaria dia = new ChaveDiaria(comerciante, LocalDate.parse(DIA));
      await().atMost(ESPERA).until(() -> TabelasDoConsolidado.dias().containsKey(dia));
      assertThat(TabelasDoConsolidado.processadoEm(evento)).isEmpty();
    }
  }

  @Test
  void falhaQueSomeNaSegundaTentativaNaoVaiParaADlt() {
    UUID comerciante = UUID.randomUUID();
    String chave = comerciante.toString();
    UUID evento = UUID.randomUUID();
    UUID marcador = UUID.randomUUID();
    falhas.falharAntes(evento, 1);

    try (LeitorDaDlt dlt = KafkaConsolidado.leitorDaDlt()) {
      KafkaConsolidado.publicar(
          chave, EventosJson.json(EventosJson.credito(evento, comerciante, "5.00", DIA)), null);
      // Marcador fora do contrato, publicado depois na mesma partição: quando ele chega à DLT, o
      // evento anterior já teve a chance de ir para lá.
      KafkaConsolidado.publicar(chave, "{ marcador " + marcador, null);

      List<MensagemDaDlt> naDlt =
          await()
              .atMost(ESPERA)
              .until(() -> dlt.ler(chave, Duration.ofMillis(200)), l -> !l.isEmpty());

      assertThat(naDlt)
          .singleElement()
          .extracting(MensagemDaDlt::valor)
          .asString()
          .contains(marcador.toString());
      assertThat(TabelasDoConsolidado.processadoEm(evento)).isPresent();
      assertThat(falhas.chamadas(evento)).isEqualTo(2);
    }
  }

  @Test
  void reinjecaoDaDltAplicaUmaVezEReinjetarDeNovoNaoMuda() {
    UUID comerciante = UUID.randomUUID();
    String chave = comerciante.toString();
    UUID evento = UUID.randomUUID();
    falhas.falharAntes(evento, ProjecaoComFalhas.SEMPRE);
    ChaveDiaria dia = new ChaveDiaria(comerciante, LocalDate.parse(DIA));

    try (LeitorDaDlt dlt = KafkaConsolidado.leitorDaDlt()) {
      KafkaConsolidado.publicar(
          chave,
          EventosJson.json(EventosJson.credito(evento, comerciante, "9.99", DIA)),
          "req-reinjecao");
      MensagemDaDlt naDlt =
          await()
              .atMost(ESPERA)
              .until(() -> dlt.ler(chave, Duration.ofMillis(200)), l -> !l.isEmpty())
              .get(0);
      assertThat(TabelasDoConsolidado.dias()).doesNotContainKey(dia);

      falhas.limpar();
      double duplicadosAntes = contador("consolidado.events.duplicates");
      reinjetar(naDlt);
      await().atMost(ESPERA).until(() -> TabelasDoConsolidado.dias().containsKey(dia));
      reinjetar(naDlt);
      await()
          .atMost(ESPERA)
          .until(() -> contador("consolidado.events.duplicates") - duplicadosAntes >= 1.0);

      assertThat(TabelasDoConsolidado.dias().get(dia).qtdLancamentos()).isEqualTo(1);
      assertThat(TabelasDoConsolidado.dias().get(dia).totalCreditos()).isEqualByComparingTo("9.99");
    }
  }

  /**
   * Como o runbook: mesma chave, mesmo valor e o mesmo {@code x-request-id}, no tópico principal.
   */
  private static void reinjetar(MensagemDaDlt mensagem) {
    KafkaConsolidado.publicar(
        KafkaConsolidado.TOPICO,
        mensagem.chave(),
        mensagem.valor(),
        mensagem.cabecalho("x-request-id").getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, Object> campos(UUID comerciante, String valor) {
    return EventosJson.credito(UUID.randomUUID(), comerciante, valor, DIA);
  }

  private double contador(String nome) {
    return metricas.get(nome).counter().count();
  }
}
