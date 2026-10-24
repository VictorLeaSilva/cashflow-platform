package br.com.fluxocaixa.consolidado.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base da fatia web (T-023): contexto completo com filtros e segurança reais, o caso de uso real
 * sobre {@link LeituraEmMemoria} (sem banco), o consumidor desligado, relógio fixo e a chave JWT de
 * {@link TokensDeTeste}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({RelogioFixo.class, LeituraEmMemoria.class})
public abstract class IntegracaoWeb {

  @Autowired protected MockMvc mvc;
  @Autowired protected LeituraEmMemoria.Estado leitura;
  @Autowired private RelogioFixo.Controlado relogio;

  @DynamicPropertySource
  static void jwt(DynamicPropertyRegistry registro) {
    TokensDeTeste.registrarPropriedades(registro);
  }

  @BeforeEach
  void estadoLimpo() {
    leitura.limpar();
    relogio.restaurar();
  }
}
