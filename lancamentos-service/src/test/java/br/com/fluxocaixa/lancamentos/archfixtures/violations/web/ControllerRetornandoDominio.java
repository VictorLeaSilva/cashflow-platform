package br.com.fluxocaixa.lancamentos.archfixtures.violations.web;

import br.com.fluxocaixa.lancamentos.domain.Lancamento;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fixture: viola "controllers retornam DTOs". Existe só para provar que a regra falha; é abstrata
 * para que a varredura de componentes do Spring a ignore nos testes com contexto.
 */
@RestController
public abstract class ControllerRetornandoDominio {

  public abstract Lancamento obter();
}
