package br.com.fluxocaixa.consolidado.support;

import br.com.fluxocaixa.consolidado.application.port.out.ConsultaConsolidadoRepository;
import br.com.fluxocaixa.consolidado.application.port.out.IdGenerator;
import br.com.fluxocaixa.consolidado.application.port.out.LeituraDoPeriodo;
import br.com.fluxocaixa.consolidado.application.port.out.MovimentoGravado;
import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Portas de saída em memória para a fatia web (T-023): o caso de uso real roda sobre elas, sem
 * banco e sem Mockito (04 §2.3). Os IDs gerados seguem uma sequência previsível.
 */
@TestConfiguration(proxyBeanMethods = false)
public class LeituraEmMemoria {

  @Bean
  Estado estadoDaLeitura() {
    return new Estado();
  }

  @Bean
  @Primary
  ConsultaConsolidadoRepository leituraEmMemoria(Estado estado) {
    return estado;
  }

  @Bean
  @Primary
  IdGenerator idsEmSequencia(Estado estado) {
    return estado::proximoId;
  }

  /** Dias gravados por comerciante; o teste grava, a porta lê. */
  public static final class Estado implements ConsultaConsolidadoRepository {

    private final Map<UUID, Map<LocalDate, MovimentoGravado>> dias = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private volatile boolean falhar;

    public void gravar(UUID comerciante, LocalDate data, MovimentoDiario movimento, Instant em) {
      dias.computeIfAbsent(comerciante, c -> new ConcurrentHashMap<>())
          .put(data, new MovimentoGravado(movimento, em));
    }

    public void limpar() {
      dias.clear();
      ids.set(0);
      falhar = false;
    }

    /** A próxima leitura lança uma exceção inesperada (500). */
    public void simularFalhaInesperada() {
      falhar = true;
    }

    UUID proximoId() {
      return new UUID(0L, ids.incrementAndGet());
    }

    @Override
    public LeituraDoPeriodo ler(UUID comercianteId, LocalDate inicio, LocalDate fim) {
      if (falhar) {
        throw new IllegalStateException("falha simulada na leitura");
      }
      MovimentoDiario anterior = MovimentoDiario.ZERO;
      Map<LocalDate, MovimentoGravado> noIntervalo = new HashMap<>();
      for (Map.Entry<LocalDate, MovimentoGravado> dia :
          dias.getOrDefault(comercianteId, Map.of()).entrySet()) {
        if (dia.getKey().isBefore(inicio)) {
          anterior = anterior.somar(dia.getValue().movimento());
        } else if (!dia.getKey().isAfter(fim)) {
          noIntervalo.put(dia.getKey(), dia.getValue());
        }
      }
      return new LeituraDoPeriodo(anterior, noIntervalo);
    }
  }
}
