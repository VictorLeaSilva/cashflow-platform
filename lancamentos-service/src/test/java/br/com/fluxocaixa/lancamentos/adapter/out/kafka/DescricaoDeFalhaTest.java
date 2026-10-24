package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutionException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Q-030 e AC-T016.10: {@code last_error} com classe e mensagem, limitado, sem embrulhos. */
@Tag("RNF-007")
class DescricaoDeFalhaTest {

  @Test
  void usaAClasseEAMensagemDaCausaReal() {
    Throwable embrulhada =
        new ExecutionException(new TimeoutException("Expiring 1 record(s) for topic-0"));

    assertThat(DescricaoDeFalha.de(embrulhada))
        .isEqualTo(
            "org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for topic-0");
  }

  /** O {@code KafkaException("Send failed")} do Spring só embrulha a causa real do produtor. */
  @Test
  void desembrulhaOSendFailedDoSpring() {
    Throwable embrulhada =
        new org.springframework.kafka.KafkaException(
            "Send failed", new TimeoutException("Topic not present in metadata after 3000 ms."));

    assertThat(DescricaoDeFalha.de(embrulhada))
        .isEqualTo(
            "org.apache.kafka.common.errors.TimeoutException: Topic not present in metadata after 3000 ms.");
  }

  @Test
  void semMensagemFicaSoAClasse() {
    assertThat(DescricaoDeFalha.de(new IllegalStateException()))
        .isEqualTo("java.lang.IllegalStateException");
  }

  @Test
  void limitaA500Caracteres() {
    String descricao = DescricaoDeFalha.de(new IllegalStateException("x".repeat(2_000)));

    assertThat(descricao).hasSize(DescricaoDeFalha.TAMANHO_MAXIMO).startsWith("java.lang.");
  }
}
