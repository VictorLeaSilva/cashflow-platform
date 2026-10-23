package br.com.fluxocaixa.lancamentos.support;

import br.com.fluxocaixa.lancamentos.application.IdentificadorExternoDuplicadoException;
import br.com.fluxocaixa.lancamentos.application.LancamentoJaEstornadoException;
import br.com.fluxocaixa.lancamentos.application.port.out.EventoOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.FiltroDeHistorico;
import br.com.fluxocaixa.lancamentos.application.port.out.IdGenerator;
import br.com.fluxocaixa.lancamentos.application.port.out.IdempotencyKeyRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxPendente;
import br.com.fluxocaixa.lancamentos.application.port.out.OutboxRepository;
import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Portas de saída em memória para a fatia web (T-013): o caso de uso real roda sobre elas, sem
 * banco e sem Mockito (04 §2.3). A transação em memória desfaz as escritas em rollback, como o
 * banco faria, para que uma rejeição dentro da execução idempotente não deixe chave órfã.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PortasEmMemoria {

  @Bean
  Estado estadoEmMemoria() {
    return new Estado();
  }

  @Bean
  @Primary
  LancamentoRepository lancamentosEmMemoria(Estado estado) {
    return estado.lancamentos;
  }

  @Bean
  @Primary
  IdempotencyKeyRepository chavesEmMemoria(Estado estado) {
    return estado.chaves;
  }

  @Bean
  @Primary
  OutboxRepository outboxEmMemoria(Estado estado) {
    return estado.outbox;
  }

  @Bean
  @Primary
  IdGenerator idsEmSequencia(Estado estado) {
    return estado.ids;
  }

  @Bean
  @Primary
  TransactionTemplate transacaoEmMemoria(Estado estado) {
    return new TransacaoEmMemoria(estado);
  }

  /** Estado compartilhado pelas portas; os testes leem e programam falhas por aqui. */
  public static final class Estado {

    private final Lancamentos lancamentos = new Lancamentos();
    private final Chaves chaves = new Chaves();
    private final Outbox outbox = new Outbox();
    private final Ids ids = new Ids();

    public synchronized void limpar() {
      lancamentos.porId.clear();
      chaves.porChave.clear();
      outbox.eventos.clear();
      ids.gerados.clear();
      chaves.disputaPermanente = false;
      lancamentos.falhaInesperada = false;
    }

    public synchronized int quantidadeDeLancamentos() {
      return lancamentos.porId.size();
    }

    public synchronized int quantidadeDeEventos() {
      return outbox.eventos.size();
    }

    public synchronized int quantidadeDeChaves() {
      return chaves.porChave.size();
    }

    public synchronized List<EventoOutbox> eventos() {
      return List.copyOf(outbox.eventos);
    }

    public synchronized List<Lancamento> lancamentos() {
      return List.copyOf(lancamentos.porId.values());
    }

    /** Ids gerados até agora, em ordem. */
    public synchronized List<UUID> idsGerados() {
      return List.copyOf(ids.gerados);
    }

    /** Toda chave parece em disputa: força a {@code IdempotencyConflictException}. */
    public synchronized void simularDisputaPermanente() {
      chaves.disputaPermanente = true;
    }

    /** O repositório de lançamentos lança uma exceção inesperada (500). */
    public synchronized void simularFalhaInesperada() {
      lancamentos.falhaInesperada = true;
    }

    private synchronized Retrato retrato() {
      return new Retrato(
          new LinkedHashMap<>(lancamentos.porId),
          new LinkedHashMap<>(chaves.porChave),
          new ArrayList<>(outbox.eventos));
    }

    private synchronized void restaurar(Retrato retrato) {
      lancamentos.porId.clear();
      lancamentos.porId.putAll(retrato.lancamentos());
      chaves.porChave.clear();
      chaves.porChave.putAll(retrato.chaves());
      outbox.eventos.clear();
      outbox.eventos.addAll(retrato.eventos());
    }
  }

  private record Retrato(
      Map<UUID, Lancamento> lancamentos,
      Map<String, ChaveGravada> chaves,
      List<EventoOutbox> eventos) {}

  private record ChaveGravada(String requestHash, UUID lancamentoId) {}

  private static final class Lancamentos implements LancamentoRepository {

    private final Map<UUID, Lancamento> porId = new LinkedHashMap<>();
    private boolean falhaInesperada;

    @Override
    public synchronized void inserir(Lancamento lancamento) {
      if (falhaInesperada) {
        throw new IllegalStateException("falha inesperada simulada");
      }
      boolean duplicado =
          lancamento.identificadorExterno() != null
              && porId.values().stream()
                  .anyMatch(
                      outro ->
                          outro.comercianteId().equals(lancamento.comercianteId())
                              && lancamento
                                  .identificadorExterno()
                                  .equals(outro.identificadorExterno()));
      if (duplicado) {
        throw new IdentificadorExternoDuplicadoException();
      }
      // ux_estorno_unico (T-014): um único estorno por lançamento original.
      boolean jaEstornado =
          lancamento.tipo() == TipoLancamento.ESTORNO
              && porId.values().stream()
                  .anyMatch(
                      outro ->
                          outro.tipo() == TipoLancamento.ESTORNO
                              && outro
                                  .referenciaLancamentoId()
                                  .equals(lancamento.referenciaLancamentoId()));
      if (jaEstornado) {
        throw new LancamentoJaEstornadoException();
      }
      porId.put(lancamento.id(), lancamento);
    }

    @Override
    public synchronized Optional<Lancamento> buscarPorId(UUID comercianteId, UUID id) {
      return Optional.ofNullable(porId.get(id))
          .filter(lancamento -> lancamento.comercianteId().equals(comercianteId));
    }

    /**
     * Mesma ordem do SQL (T-015): {@code dataReferencia} decrescente e {@code id} crescente; o
     * texto canônico do UUID ordena como o PostgreSQL (bytes sem sinal), ao contrário de {@link
     * UUID#compareTo}.
     */
    @Override
    public synchronized List<Lancamento> listar(FiltroDeHistorico filtro) {
      return filtrados(filtro).stream()
          .sorted(
              Comparator.comparing(Lancamento::dataReferencia)
                  .reversed()
                  .thenComparing(lancamento -> lancamento.id().toString()))
          .skip(filtro.offset())
          .limit(filtro.size())
          .toList();
    }

    @Override
    public synchronized long contar(FiltroDeHistorico filtro) {
      return filtrados(filtro).size();
    }

    private List<Lancamento> filtrados(FiltroDeHistorico filtro) {
      return porId.values().stream()
          .filter(l -> l.comercianteId().equals(filtro.comercianteId()))
          .filter(
              l -> filtro.dataInicio() == null || !l.dataReferencia().isBefore(filtro.dataInicio()))
          .filter(l -> filtro.dataFim() == null || !l.dataReferencia().isAfter(filtro.dataFim()))
          .filter(l -> filtro.tipo() == null || l.tipo() == filtro.tipo())
          .toList();
    }
  }

  private static final class Chaves implements IdempotencyKeyRepository {

    private final Map<String, ChaveGravada> porChave = new LinkedHashMap<>();
    private boolean disputaPermanente;

    @Override
    public synchronized boolean tentarRegistrar(
        UUID comercianteId, String chave, String requestHash, UUID lancamentoId) {
      if (disputaPermanente) {
        return false;
      }
      return porChave.putIfAbsent(
              comercianteId + "|" + chave, new ChaveGravada(requestHash, lancamentoId))
          == null;
    }

    @Override
    public synchronized Optional<ChaveRegistrada> buscar(UUID comercianteId, String chave) {
      if (disputaPermanente) {
        return Optional.empty();
      }
      return Optional.ofNullable(porChave.get(comercianteId + "|" + chave))
          .map(gravada -> new ChaveRegistrada(gravada.requestHash(), gravada.lancamentoId()));
    }
  }

  private static final class Outbox implements OutboxRepository {

    private final List<EventoOutbox> eventos = new ArrayList<>();

    @Override
    public synchronized void inserir(EventoOutbox evento) {
      eventos.add(Objects.requireNonNull(evento));
    }

    /** O publicador (T-016) fica desligado na fatia web: só leitura, para as métricas. */
    @Override
    public synchronized long contarPendentes() {
      return eventos.size();
    }

    @Override
    public Optional<Instant> maisAntigoPendente() {
      return Optional.empty();
    }

    @Override
    public List<OutboxPendente> travarPendentes(int limite) {
      throw new UnsupportedOperationException("publicador desligado na fatia web");
    }

    @Override
    public void marcarPublicados(Collection<UUID> eventIds, Instant publicadoEm) {
      throw new UnsupportedOperationException("publicador desligado na fatia web");
    }

    @Override
    public void registrarFalhas(Map<UUID, String> falhasPorEvento) {
      throw new UnsupportedOperationException("publicador desligado na fatia web");
    }

    @Override
    public int excluirPublicadosAntesDe(Instant instante, int limite) {
      throw new UnsupportedOperationException("publicador desligado na fatia web");
    }
  }

  /** UUIDs previsíveis: {@code 00000000-0000-4000-8000-<sequência>}. */
  private static final class Ids implements IdGenerator {

    private final AtomicLong sequencia = new AtomicLong();
    private final List<UUID> gerados = new ArrayList<>();

    @Override
    public synchronized UUID gerar() {
      UUID id =
          new UUID(0x0000_0000_0000_4000L, 0x8000_0000_0000_0000L | sequencia.incrementAndGet());
      gerados.add(id);
      return id;
    }
  }

  /** Executa o callback e desfaz as escritas em memória em rollback ou exceção. */
  private static final class TransacaoEmMemoria extends TransactionTemplate {

    private static final long serialVersionUID = 1L;

    private final transient Estado estado;

    private TransacaoEmMemoria(Estado estado) {
      this.estado = estado;
    }

    /** Não há gerenciador de transação: a "transação" é o retrato do estado em memória. */
    @Override
    public void afterPropertiesSet() {
      // nada a validar
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback) throws TransactionException {
      synchronized (estado) {
        Retrato antes = estado.retrato();
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        try {
          T resultado = callback.doInTransaction(status);
          if (status.isRollbackOnly()) {
            estado.restaurar(antes);
          }
          return resultado;
        } catch (RuntimeException | Error falha) {
          estado.restaurar(antes);
          throw falha;
        }
      }
    }
  }
}
