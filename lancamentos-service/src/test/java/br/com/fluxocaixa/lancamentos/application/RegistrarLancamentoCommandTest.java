package br.com.fluxocaixa.lancamentos.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.domain.TipoLancamento;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** AC-T012.4: o comando não expõe chave nem payload financeiro quando vai para log. */
@Tag("RF-007")
class RegistrarLancamentoCommandTest {

  @Test
  void toStringOmiteChaveEPayload() {
    var comando =
        new RegistrarLancamentoCommand(
            UUID.randomUUID(),
            "chave-secreta-123",
            TipoLancamento.CREDITO,
            new BigDecimal("4321.09"),
            "descricao privada",
            Instant.parse("2026-10-22T10:00:00Z"),
            "pedido-777",
            "req-1");

    assertThat(comando.toString())
        .contains("CREDITO")
        .doesNotContain("chave-secreta-123", "4321.09", "descricao privada", "pedido-777");
  }
}
