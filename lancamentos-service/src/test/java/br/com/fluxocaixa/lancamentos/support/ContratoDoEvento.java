package br.com.fluxocaixa.lancamentos.support;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O contrato do evento {@code LancamentoRegistrado.v1} como o Lançamentos o enxerga nos testes
 * (T-030, Q-059): schema e exemplos do artefato {@code contracts} no <i>classpath</i>, sem cópia no
 * serviço, e a mesma configuração de validação do consumidor ({@code format} conferido, Q-043).
 */
public final class ContratoDoEvento {

  public static final String SCHEMA = "events/lancamento-registrado.v1.schema.json";

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Schema VALIDADOR = carregarSchema();

  private ContratoDoEvento() {}

  /** Exemplo inválido: o nome do arquivo e o conteúdo, como está. */
  public record Invalido(String nome, String json) {}

  /** Exemplo <i>golden</i> de {@code examples/}, como está. */
  public static String golden(String arquivo) {
    return ler("examples/" + arquivo);
  }

  public static JsonNode goldenComoArvore(String arquivo) {
    return JSON.readTree(golden(arquivo));
  }

  public static JsonNode comoArvore(String json) {
    return JSON.readTree(json);
  }

  /** Violações como {@code ponteiro:palavra-chave}; vazio se o evento é válido. */
  public static List<String> violacoes(JsonNode evento) {
    List<Error> erros =
        VALIDADOR.validate(
            evento,
            contexto -> contexto.executionConfig(config -> config.formatAssertionsEnabled(true)));
    return erros.stream()
        .map(erro -> erro.getInstanceLocation() + ":" + erro.getKeyword())
        .distinct()
        .toList();
  }

  /** Todos os arquivos de {@code examples/invalidos/}, em ordem de nome. */
  public static List<Invalido> invalidos() {
    try {
      Resource[] recursos =
          new PathMatchingResourcePatternResolver()
              .getResources("classpath*:examples/invalidos/*.json");
      return Arrays.stream(recursos)
          .map(r -> r.getFilename())
          .sorted(Comparator.naturalOrder())
          .map(nome -> new Invalido(nome, ler("examples/invalidos/" + nome)))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** O {@code README.md} que descreve a violação de cada inválido. */
  public static String readmeDosInvalidos() {
    return ler("examples/invalidos/README.md");
  }

  private static String ler(String recurso) {
    try (InputStream entrada =
        ContratoDoEvento.class.getClassLoader().getResourceAsStream(recurso)) {
      if (entrada == null) {
        throw new IllegalStateException("recurso de contracts ausente no classpath: " + recurso);
      }
      return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Schema carregarSchema() {
    try (InputStream recurso =
        ContratoDoEvento.class.getClassLoader().getResourceAsStream(SCHEMA)) {
      if (recurso == null) {
        throw new IllegalStateException("schema do evento ausente no classpath: " + SCHEMA);
      }
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(recurso, InputFormat.JSON);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
