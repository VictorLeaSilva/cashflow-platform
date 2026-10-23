package br.com.fluxocaixa.lancamentos.adapter.out.id;

import br.com.fluxocaixa.lancamentos.application.port.out.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** UUID aleatório (versão 4). */
@Component
public class UuidIdGenerator implements IdGenerator {

  @Override
  public UUID gerar() {
    return UUID.randomUUID();
  }
}
