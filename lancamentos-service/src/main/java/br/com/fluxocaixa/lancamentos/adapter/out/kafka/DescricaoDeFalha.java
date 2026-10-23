package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.common.errors.TimeoutException;
import org.springframework.kafka.KafkaException;

/**
 * Texto de {@code last_error} (Q-030): a classe e a mensagem da causa real, sem os embrulhos da
 * espera assíncrona, limitado a 500 caracteres. As exceções do produtor Kafka descrevem o broker e
 * a partição, nunca o conteúdo da mensagem.
 */
final class DescricaoDeFalha {

  static final int TAMANHO_MAXIMO = 500;

  private DescricaoDeFalha() {}

  static String de(Throwable erro) {
    Throwable causa = erro;
    while (embrulho(causa) && causa.getCause() != null) {
      causa = causa.getCause();
    }
    String mensagem = causa.getMessage();
    String descricao =
        mensagem == null
            ? causa.getClass().getName()
            : causa.getClass().getName() + ": " + mensagem;
    return descricao.length() <= TAMANHO_MAXIMO
        ? descricao
        : descricao.substring(0, TAMANHO_MAXIMO);
  }

  /** Espera assíncrona e o {@code KafkaException("Send failed")} do Spring só embrulham a causa. */
  private static boolean embrulho(Throwable erro) {
    return erro instanceof ExecutionException
        || erro instanceof CompletionException
        || erro instanceof KafkaException;
  }

  /** Broker indisponível: metadados ou confirmação não chegaram no prazo. */
  static boolean timeout(Throwable erro) {
    for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
      if (atual instanceof TimeoutException) {
        return true;
      }
    }
    return false;
  }
}
