package br.com.fluxocaixa.lancamentos.config;

import br.com.fluxocaixa.lancamentos.application.ConsultarLancamentos;
import br.com.fluxocaixa.lancamentos.application.EstornarLancamento;
import br.com.fluxocaixa.lancamentos.application.ExecucaoIdempotente;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.port.out.ContextoDeTrace;
import br.com.fluxocaixa.lancamentos.application.port.out.IdGenerator;
import br.com.fluxocaixa.lancamentos.application.port.out.IdempotencyKeyRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.MetricasDeLancamentos;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

/** Montagem dos casos de uso; a camada {@code application} não conhece o contêiner Spring. */
@Configuration(proxyBeanMethods = false)
public class CasosDeUsoConfig {

  @Bean
  ExecucaoIdempotente execucaoIdempotente(
      IdempotencyKeyRepository chaves, TransactionTemplate transactionTemplate) {
    return new ExecucaoIdempotente(chaves, transactionTemplate);
  }

  @Bean
  RegistrarLancamento registrarLancamento(
      ExecucaoIdempotente execucaoIdempotente,
      LancamentoRepository lancamentos,
      OutboxRepository outbox,
      IdGenerator ids,
      PoliticaLancamento politica,
      Clock clock,
      MetricasDeLancamentos metricas,
      ContextoDeTrace trace) {
    return new RegistrarLancamento(
        execucaoIdempotente, lancamentos, outbox, ids, politica, clock, metricas, trace);
  }

  @Bean
  ConsultarLancamentos consultarLancamentos(LancamentoRepository lancamentos) {
    return new ConsultarLancamentos(lancamentos);
  }

  @Bean
  EstornarLancamento estornarLancamento(
      ExecucaoIdempotente execucaoIdempotente,
      LancamentoRepository lancamentos,
      OutboxRepository outbox,
      IdGenerator ids,
      PoliticaLancamento politica,
      Clock clock,
      MetricasDeLancamentos metricas,
      ContextoDeTrace trace) {
    return new EstornarLancamento(
        execucaoIdempotente, lancamentos, outbox, ids, politica, clock, metricas, trace);
  }
}
