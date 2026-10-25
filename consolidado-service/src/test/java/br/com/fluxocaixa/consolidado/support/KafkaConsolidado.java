package br.com.fluxocaixa.consolidado.support;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka compartilhado pelos ITs do consumidor (T-022): a mesma tag do compose (T-004), sem criação
 * automática de tópicos e com o tópico de eventos e a DLT criados com 3 partições, como faz o
 * {@code create-topics.sh}. Traz um produtor de teste e um leitor da DLT.
 */
public final class KafkaConsolidado {

  /** Mesma tag de {@code deploy/docker-compose.yml} (T-004). */
  public static final String IMAGEM = "apache/kafka:3.9.1";

  public static final String TOPICO = "cashflow.lancamentos.v1";
  public static final String DLT = TOPICO + ".DLT";
  public static final int PARTICOES = 3;
  public static final String CABECALHO_REQUEST_ID = "x-request-id";

  @SuppressWarnings("resource") // encerrado pelo Ryuk ao fim da JVM (container compartilhado)
  private static final KafkaContainer CONTAINER = iniciar();

  @SuppressWarnings("resource") // idem: um produtor para todos os ITs
  private static final KafkaProducer<String, String> PRODUTOR = produtor();

  private KafkaConsolidado() {}

  private static KafkaContainer iniciar() {
    KafkaContainer container =
        new KafkaContainer(IMAGEM).withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    container.start();
    criarTopicos(container.getBootstrapServers());
    return container;
  }

  private static void criarTopicos(String bootstrap) {
    try (Admin admin = admin(bootstrap)) {
      admin
          .createTopics(
              List.of(
                  new NewTopic(TOPICO, PARTICOES, (short) 1),
                  new NewTopic(DLT, PARTICOES, (short) 1)))
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

  private static KafkaProducer<String, String> produtor() {
    Properties config = new Properties();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    return new KafkaProducer<>(config);
  }

  public static String bootstrapServers() {
    return CONTAINER.getBootstrapServers();
  }

  /** Aponta consumidor e produtor da DLT para o container. */
  public static void registrarPropriedades(DynamicPropertyRegistry registro) {
    registro.add("spring.kafka.bootstrap-servers", KafkaConsolidado::bootstrapServers);
  }

  /** Publica no tópico principal; {@code requestId} nulo publica sem o cabeçalho. */
  public static RecordMetadata publicar(String chave, String valor, String requestId) {
    return publicar(
        TOPICO,
        chave,
        valor,
        requestId == null ? null : requestId.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Publica no tópico principal com os cabeçalhos informados (por exemplo, {@code traceparent}).
   */
  public static RecordMetadata publicarComCabecalhos(
      String chave, String valor, Map<String, String> cabecalhos) {
    ProducerRecord<String, String> registro = new ProducerRecord<>(TOPICO, chave, valor);
    cabecalhos.forEach(
        (nome, conteudo) ->
            registro.headers().add(nome, conteudo.getBytes(StandardCharsets.UTF_8)));
    try {
      return PRODUTOR.send(registro).get(30, TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("falha ao publicar no tópico de teste", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** Publica com o cabeçalho {@code x-request-id} em bytes arbitrários (inclusive inválidos). */
  public static RecordMetadata publicar(
      String topico, String chave, String valor, byte[] requestId) {
    ProducerRecord<String, String> registro = new ProducerRecord<>(topico, chave, valor);
    if (requestId != null) {
      registro.headers().add(CABECALHO_REQUEST_ID, requestId);
    }
    try {
      return PRODUTOR.send(registro).get(30, TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("falha ao publicar no tópico de teste", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** Offset confirmado pelo grupo na partição, se houver. */
  public static OptionalLong offsetConfirmado(String grupo, int particao) {
    TopicPartition alvo = new TopicPartition(TOPICO, particao);
    try (Admin admin = admin(bootstrapServers())) {
      Map<TopicPartition, OffsetAndMetadata> offsets =
          admin.listConsumerGroupOffsets(grupo).partitionsToOffsetAndMetadata().get();
      OffsetAndMetadata offset = offsets.get(alvo);
      return offset == null ? OptionalLong.empty() : OptionalLong.of(offset.offset());
    } catch (ExecutionException e) {
      throw new IllegalStateException("falha ao ler os offsets do grupo", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static Admin admin(String bootstrap) {
    return Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap));
  }

  /**
   * Mensagem lida da DLT. Os cabeçalhos ficam como texto; partição e offset de origem, que o
   * spring-kafka grava em binário ({@code int} e {@code long}), têm leitura própria.
   */
  public record MensagemDaDlt(
      String chave, String valor, int particao, Map<String, byte[]> brutos) {

    public String cabecalho(String nome) {
      byte[] valor = brutos.get(nome);
      return valor == null ? null : new String(valor, StandardCharsets.UTF_8);
    }

    public Map<String, byte[]> cabecalhos() {
      return brutos;
    }

    public int particaoDeOrigem() {
      return ByteBuffer.wrap(brutos.get("kafka_dlt-original-partition")).getInt();
    }

    public long offsetDeOrigem() {
      return ByteBuffer.wrap(brutos.get("kafka_dlt-original-offset")).getLong();
    }
  }

  /** Leitor da DLT desde o início, com grupo próprio. */
  public static LeitorDaDlt leitorDaDlt() {
    return new LeitorDaDlt();
  }

  /** Consumidor de teste da DLT; cada chamada a {@link #ler} acumula o que chegou. */
  public static final class LeitorDaDlt implements AutoCloseable {

    private final KafkaConsumer<String, String> consumidor;
    private final List<MensagemDaDlt> lidas = new ArrayList<>();

    private LeitorDaDlt() {
      Properties config = new Properties();
      config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
      config.put(ConsumerConfig.GROUP_ID_CONFIG, "teste-dlt-" + UUID.randomUUID());
      config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
      config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
      config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
      consumidor = new KafkaConsumer<>(config);
      consumidor.subscribe(List.of(DLT));
    }

    /** Lê o que houver por até {@code espera} e devolve as mensagens com a {@code chave}. */
    public List<MensagemDaDlt> ler(String chave, Duration espera) {
      for (ConsumerRecord<String, String> registro : consumidor.poll(espera)) {
        Map<String, byte[]> cabecalhos = new HashMap<>();
        for (Header cabecalho : registro.headers()) {
          cabecalhos.put(cabecalho.key(), cabecalho.value());
        }
        lidas.add(
            new MensagemDaDlt(registro.key(), registro.value(), registro.partition(), cabecalhos));
      }
      return lidas.stream().filter(m -> chave.equals(m.chave())).toList();
    }

    @Override
    public void close() {
      consumidor.close();
    }
  }
}
