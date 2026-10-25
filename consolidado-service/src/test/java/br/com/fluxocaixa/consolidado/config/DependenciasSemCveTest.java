package br.com.fluxocaixa.consolidado.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * AC-T037.1 (RNF-005, Q-081): o <i>classpath</i> traz as versões que corrigem os achados HIGH e
 * CRITICAL do Trivy no primeiro run do {@code security.yml}. A versão vem do nome do JAR que
 * carrega a classe, porque é exatamente o que vai para a imagem.
 */
@Tag("RNF-005")
class DependenciasSemCveTest {

  private static final Pattern VERSAO = Pattern.compile("-(\\d+)\\.(\\d+)\\.(\\d+)\\.jar$");

  static Stream<Arguments> minimas() {
    return Stream.of(
        Arguments.of("tomcat-embed-core", "org.apache.catalina.util.ServerInfo", "11.0.25"),
        Arguments.of("jackson-core", "tools.jackson.core.JsonParser", "3.1.7"),
        Arguments.of("jackson-databind", "tools.jackson.databind.ObjectMapper", "3.1.7"),
        Arguments.of("lz4-java", "net.jpountz.lz4.LZ4Factory", "1.11.4"));
  }

  @ParameterizedTest(name = "{0} >= {2}")
  @MethodSource("minimas")
  void versaoNoClasspathCorrigeOsCves(String nome, String classe, String minima) throws Exception {
    int[] encontrada = versaoDoJar(classe);

    assertThat(comparar(encontrada, partes(minima)))
        .as(
            "%s no classpath é %d.%d.%d; o mínimo corrigido é %s",
            nome, encontrada[0], encontrada[1], encontrada[2], minima)
        .isGreaterThanOrEqualTo(0);
  }

  private static int[] versaoDoJar(String classe)
      throws ClassNotFoundException, URISyntaxException {
    Class<?> tipo = Class.forName(classe);
    Path jar = Path.of(tipo.getProtectionDomain().getCodeSource().getLocation().toURI());
    Matcher m = VERSAO.matcher(jar.getFileName().toString());
    assertThat(m.find()).as("nome do JAR de %s: %s", classe, jar.getFileName()).isTrue();
    return new int[] {
      Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))
    };
  }

  private static int[] partes(String versao) {
    String[] p = versao.split("\\.");
    return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
  }

  private static int comparar(int[] a, int[] b) {
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return Integer.compare(a[i], b[i]);
      }
    }
    return 0;
  }
}
