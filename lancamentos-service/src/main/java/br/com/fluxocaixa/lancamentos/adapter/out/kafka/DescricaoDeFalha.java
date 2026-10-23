package br.com.fluxocaixa.lancamentos.adapter.out.kafka;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

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
    while ((causa instanceof ExecutionException || causa instanceof CompletionException)
        && causa.getCause() != null) {
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
}
