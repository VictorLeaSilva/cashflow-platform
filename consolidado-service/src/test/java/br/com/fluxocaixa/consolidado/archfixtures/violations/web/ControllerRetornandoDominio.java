package br.com.fluxocaixa.consolidado.archfixtures.violations.web;

import br.com.fluxocaixa.consolidado.domain.MovimentoDiario;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fixture: viola "controllers retornam DTOs". Existe só para provar que a regra falha; é abstrata
 * para que a varredura de componentes do Spring a ignore nos testes com contexto.
 */
@RestController
public abstract class ControllerRetornandoDominio {

  public abstract MovimentoDiario obter();
}
