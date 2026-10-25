package br.com.fluxocaixa.lancamentos.support;

import com.github.dockerjava.api.DockerClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka compartilhado pelos ITs do publicador (T-016): a mesma tag do compose (T-004), sem criação
 * automática de tópicos e com o tópico de eventos e a DLT criados com 3 partições, como faz o
 * {@code create-topics.sh}.
 */
public final class KafkaLancamentos {

  /** Mesma tag de {@code deploy/docker-compose.yml} (T-004). */
  public static final String IMAGEM = "apache/kafka:3.9.1";

  public static final String TOPICO = "cashflow.lancamentos.v1";
  public static final int PARTICOES = 3;

  @SuppressWarnings("resource") // encerrado pelo Ryuk ao fim da JVM (container compartilhado)
  private static final KafkaContainer CONTAINER = iniciar();

  private KafkaLancamentos() {}

  private static KafkaContainer iniciar() {
    KafkaContainer container =
        new KafkaContainer(IMAGEM).withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    container.start();
    criarTopicos(container.getBootstrapServers());
    return container;
  }

  private static void criarTopicos(String bootstrap) {
    try (Admin admin =
        Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
      admin
          .createTopics(
              List.of(
                  new NewTopic(TOPICO, PARTICOES, (short) 1),
                  new NewTopic(TOPICO + ".DLT", PARTICOES, (short) 1)))
          .all()
          .get();
    } catch (ExecutionException e) {
      if (!(e.getCause() instanceof TopicExistsException)) {
        throw new IllegalStateException("falha ao criar os tópicos de teste", e);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  public static String bootstrapServers() {
    return CONTAINER.getBootstrapServers();
  }

  /** Aponta o produtor para o container. */
  public static void registrarPropriedades(DynamicPropertyRegistry registro) {
    registro.add("spring.kafka.bootstrap-servers", KafkaLancamentos::bootstrapServers);
  }

  /** Congela o broker (simula Kafka fora sem perder o estado). */
  public static void pausar() {
    docker().pauseContainerCmd(CONTAINER.getContainerId()).exec();
  }

  public static void retomar() {
    docker().unpauseContainerCmd(CONTAINER.getContainerId()).exec();
  }

  private static DockerClient docker() {
    return DockerClientFactory.instance().client();
  }

  /** Mensagem lida do tópico, com o cabeçalho {@code x-request-id} já decodificado. */
  public record Mensagem(
      String chave, String valor, int particao, String requestId, String traceparent) {}

  /** Leitor do tópico desde o início, com grupo próprio (não interfere em outros testes). */
  public static Leitor leitor() {
    return new Leitor();
  }

  /** Consumidor de teste; cada chamada a {@link #ler} acumula o que chegou desde a anterior. */
  public static final class Leitor implements AutoCloseable {

    private final KafkaConsumer<String, String> consumidor;
    private final List<Mensagem> lidas = new ArrayList<>();

    private Leitor() {
      Properties config = new Properties();
      config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
      config.put(ConsumerConfig.GROUP_ID_CONFIG, "teste-" + UUID.randomUUID());
      config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
      config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
      config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
      consumidor = new KafkaConsumer<>(config);
      consumidor.subscribe(List.of(TOPICO));
    }

    /** Lê o que houver por até {@code espera} e devolve tudo o que já foi lido. */
    public List<Mensagem> ler(Duration espera) {
      for (ConsumerRecord<String, String> registro : consumidor.poll(espera)) {
        lidas.add(
            new Mensagem(
                registro.key(),
                registro.value(),
                registro.partition(),
                cabecalho(registro, "x-request-id"),
                cabecalho(registro, "traceparent")));
      }
      return List.copyOf(lidas);
    }

    private static String cabecalho(ConsumerRecord<String, String> registro, String nome) {
      Header cabecalho = registro.headers().lastHeader(nome);
      return cabecalho == null ? null : new String(cabecalho.value(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
      consumidor.close();
    }
  }
}
