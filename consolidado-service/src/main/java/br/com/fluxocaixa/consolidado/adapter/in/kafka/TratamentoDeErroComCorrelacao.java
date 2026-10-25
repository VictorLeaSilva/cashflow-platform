package br.com.fluxocaixa.consolidado.adapter.in.kafka;

import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.BackOff;

/**
 * <i>Retry</i> e DLT com correlação nos logs (T-032, <i>follow-up</i> 5 da T-022): enquanto o
 * tratamento de erro processa um registro, o MDC leva {@code requestId}, {@code eventId}, {@code
 * traceIdOrigem} e {@code traceId} (de {@link CorrelacaoDoRegistro}), e cada tentativa que falha e
 * o envio à DLT viram uma linha no logger do consumo, só com o nome da classe da exceção (nunca a
 * mensagem, que pode trazer dado do evento; Q-049).
 */
public final class TratamentoDeErroComCorrelacao extends DefaultErrorHandler {

  private static final Logger CONSUMO =
      LoggerFactory.getLogger(ConsumidorDeLancamentos.LOGGER_DE_CONSUMO);

  public TratamentoDeErroComCorrelacao(ConsumerRecordRecoverer recoverer, BackOff backoff) {
    super(recoverer, backoff);
    setRetryListeners(new LinhasDeRetryEDlt());
  }

  @Override
  public boolean handleOne(
      Exception falha,
      ConsumerRecord<?, ?> registro,
      Consumer<?, ?> consumidor,
      MessageListenerContainer container) {
    Map<String, String> postas = CorrelacaoDoRegistro.aplicar(correlacao(registro));
    try {
      return super.handleOne(falha, registro, consumidor, container);
    } finally {
      CorrelacaoDoRegistro.remover(postas);
    }
  }

  @Override
  public void handleRemaining(
      Exception falha,
      List<ConsumerRecord<?, ?>> registros,
      Consumer<?, ?> consumidor,
      MessageListenerContainer container) {
    Map<String, String> postas =
        CorrelacaoDoRegistro.aplicar(registros.isEmpty() ? Map.of() : correlacao(registros.get(0)));
    try {
      super.handleRemaining(falha, registros, consumidor, container);
    } finally {
      CorrelacaoDoRegistro.remover(postas);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> correlacao(ConsumerRecord<?, ?> registro) {
    return CorrelacaoDoRegistro.de((ConsumerRecord<String, String>) registro);
  }

  /** Uma linha por tentativa que falhou e uma quando o registro vai para a DLT. */
  private static final class LinhasDeRetryEDlt implements RetryListener {

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> registro, Exception falha, int tentativa) {
      CONSUMO.warn("tentativa {} do evento falhou: {}", tentativa, causa(falha));
    }

    @Override
    public void recovered(ConsumerRecord<?, ?> registro, Exception falha) {
      CONSUMO.warn("evento enviado para a DLT: {}", causa(falha));
    }

    private static String causa(Exception falha) {
      if (falha == null) {
        return "desconhecida";
      }
      Throwable raiz = falha.getCause() != null ? falha.getCause() : falha;
      return raiz.getClass().getName();
    }
  }
}
