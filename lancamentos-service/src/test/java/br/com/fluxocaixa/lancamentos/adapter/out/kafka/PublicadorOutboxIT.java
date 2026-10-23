package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.application.PublicarOutbox;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.support.IntegracaoKafka;
import br.com.fluxocaixa.lancamentos.support.KafkaLancamentos.Mensagem;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-T016.1 e AC-T016.2 com PostgreSQL e Kafka reais: chave = comerciante, valor = payload da
 * outbox, cabeçalho {@code x-request-id} (RNF-012), {@code published_at} marcado e partição estável
 * por comerciante.
 */
@Tag("RNF-001")
@Tag("RNF-012")
class PublicadorOutboxIT extends IntegracaoKafka {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RegistrarLancamento registrarLancamento;
  @Autowired private PublicarOutbox publicarOutbox;

  @Test
  void publicaComChaveValorECabecalhoEMarcaAPublicacao() {
    UUID comerciante = UUID.randomUUID();
    UUID comRequestId = Eventos.registrar(registrarLancamento, jdbc, comerciante, "rid-pub-1");
    UUID semRequestId = Eventos.registrar(registrarLancamento, jdbc, comerciante, null);

    PublicarOutbox.ResultadoDoLote resultado = publicarOutbox.publicarLote();

    assertThat(resultado).isEqualTo(new PublicarOutbox.ResultadoDoLote(2, 0, false));
    Map<UUID, Mensagem> mensagens = porEvento(Set.of(comRequestId, semRequestId));
    Mensagem com = mensagens.get(comRequestId);
    assertThat(com.chave()).isEqualTo(comerciante.toString());
    assertThat(com.requestId()).isEqualTo("rid-pub-1");
    assertThat(JSON.readTree(com.valor())).isEqualTo(JSON.readTree(payload(comRequestId)));
    assertThat(JSON.readTree(com.valor()).path("valor").asString()).isEqualTo("12.30");
    assertThat(mensagens.get(semRequestId).requestId()).isNull();
    assertThat(Eventos.pendentes(jdbc)).isZero();
    assertThat(
            jdbc.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NOT NULL")
                .query(Long.class)
                .single())
        .isEqualTo(2);
  }

  @Test
  void mesmoComercianteVaiSempreParaAMesmaParticao() {
    // 12 comerciantes: a chance de todos caírem na mesma das 3 partições é 3 × (1/3)^12.
    List<UUID> comerciantes = Stream.generate(UUID::randomUUID).limit(12).toList();
    Map<UUID, UUID> comerciantePorEvento = new HashMap<>();
    for (UUID comerciante : comerciantes) {
      for (int i = 0; i < 3; i++) {
        comerciantePorEvento.put(
            Eventos.registrar(registrarLancamento, jdbc, comerciante, null), comerciante);
      }
    }

    publicarOutbox.publicarLote();

    Map<String, Set<Integer>> particoesPorChave =
        porEvento(comerciantePorEvento.keySet()).values().stream()
            .collect(
                Collectors.groupingBy(
                    Mensagem::chave, Collectors.mapping(Mensagem::particao, Collectors.toSet())));
    assertThat(particoesPorChave).hasSize(comerciantes.size());
    assertThat(particoesPorChave.values()).allMatch(particoes -> particoes.size() == 1);
    assertThat(particoesPorChave.values().stream().flatMap(Set::stream).distinct().count())
        .isGreaterThan(1);
  }

  private static Map<UUID, Mensagem> porEvento(Set<UUID> eventIds) {
    return Eventos.lerAte(eventIds, Duration.ofSeconds(30)).stream()
        .collect(Collectors.toMap(Eventos::eventId, m -> m, (a, b) -> a));
  }

  private String payload(UUID eventId) {
    return jdbc.sql("SELECT payload::text FROM outbox_event WHERE event_id = ?")
        .param(eventId)
        .query(String.class)
        .single();
  }
}
