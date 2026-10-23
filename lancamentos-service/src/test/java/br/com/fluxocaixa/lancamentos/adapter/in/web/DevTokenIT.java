package br.com.fluxocaixa.lancamentos.adapter.in.web;

import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.credito;
import static br.com.fluxocaixa.lancamentos.adapter.in.web.Chamadas.postarComo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.support.PortasEmMemoria;
import br.com.fluxocaixa.lancamentos.support.RelogioFixo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * AC-T013.8: o token do {@code scripts/DevToken.java}, executado pelo JDK do build num diretório
 * temporário, é aceito pela configuração do profile {@code local} (emissor e audiência de lá); só a
 * localização da chave pública aponta para o par que o script gerou.
 */
@Tag("RNF-005")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import({RelogioFixo.class, PortasEmMemoria.class})
class DevTokenIT {

  private static final Path SCRIPT = Path.of("..", "scripts", "DevToken.java").toAbsolutePath();
  private static final Path DIRETORIO = criarDiretorio();
  private static final UUID COMERCIANTE = UUID.fromString("6f1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
  private static final String TOKEN =
      executar(
              "--merchant", COMERCIANTE.toString(), "--scopes", "lancamentos:write", "--ttl", "600")
          .saida()
          .strip();

  @Autowired private MockMvc mvc;
  @Autowired private RelogioFixo.Controlado relogio;

  @DynamicPropertySource
  static void chaveDoScript(DynamicPropertyRegistry registro) {
    registro.add(
        "spring.security.oauth2.resourceserver.jwt.public-key-location",
        () -> DIRETORIO.resolve("deploy/dev-keys/jwt-public.pem").toUri().toString());
  }

  @AfterEach
  void restaurarRelogio() {
    relogio.restaurar();
  }

  @Test
  void tokenDoScriptEhAceitoPeloProfileLocal() throws Exception {
    relogio.ajustar(emitidoEm(TOKEN));

    mvc.perform(postarComo(TOKEN, credito("10.00"), "devtoken-1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.comercianteId").value(COMERCIANTE.toString()));
  }

  @Test
  void scriptGeraOParDeChavesForaDoRepositorio() {
    assertThat(DIRETORIO.resolve("deploy/dev-keys/jwt-private.pem")).isRegularFile();
    assertThat(DIRETORIO.resolve("deploy/dev-keys/jwt-public.pem")).isRegularFile();
  }

  @Test
  void argumentosInvalidosTerminamComErro() {
    assertThat(executar().codigo()).isNotZero();
    assertThat(executar("--merchant", "nao-uuid", "--scopes", "a", "--ttl", "60").codigo())
        .isNotZero();
    assertThat(
            executar("--merchant", COMERCIANTE.toString(), "--scopes", "a", "--ttl", "-1").codigo())
        .isNotZero();
  }

  private record Execucao(int codigo, String saida) {}

  private static Execucao executar(String... argumentos) {
    List<String> comando = new ArrayList<>();
    comando.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    comando.add(SCRIPT.toString());
    comando.addAll(List.of(argumentos));
    try {
      Process processo =
          new ProcessBuilder(comando)
              .directory(DIRETORIO.toFile())
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!processo.waitFor(60, TimeUnit.SECONDS)) {
        processo.destroyForcibly();
        throw new IllegalStateException("DevToken.java não terminou em 60 s");
      }
      return new Execucao(processo.exitValue(), saida);
    } catch (IOException e) {
      throw new IllegalStateException("não foi possível executar o DevToken.java", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Instant emitidoEm(String token) {
    String carga =
        new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    Map<String, Object> claims = JsonMapper.builder().build().readValue(carga, Map.class);
    return Instant.ofEpochSecond(((Number) claims.get("iat")).longValue());
  }

  private static Path criarDiretorio() {
    try {
      Path diretorio = Files.createTempDirectory("devtoken-");
      diretorio.toFile().deleteOnExit();
      return diretorio;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
