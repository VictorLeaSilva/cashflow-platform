package br.com.fluxocaixa.lancamentos.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * AC-T013.9: {@code lancamentos.*} chega à {@code PoliticaLancamento}, com os padrões de A4/A7/A11.
 */
@Tag("RF-001")
class PoliticaLancamentoPropertiesTest {

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner().withUserConfiguration(PoliticaLancamentoConfig.class);

  @Test
  void semPropriedadesValemOsPadroesDaConstituicao() {
    contexto.run(
        aplicacao ->
            assertThat(aplicacao.getBean(PoliticaLancamento.class))
                .isEqualTo(PoliticaLancamento.padrao()));
  }

  @Test
  void propriedadesAlteramAPolitica() {
    contexto
        .withPropertyValues(
            "lancamentos.max-valor=500.00",
            "lancamentos.max-backdate-days=7",
            "lancamentos.max-future-skew=5s")
        .run(
            aplicacao -> {
              PoliticaLancamento politica = aplicacao.getBean(PoliticaLancamento.class);
              assertThat(politica.valorMaximo()).isEqualByComparingTo(new BigDecimal("500.00"));
              assertThat(politica.maxBackdateDays()).isEqualTo(7);
              assertThat(politica.maxFutureSkew()).isEqualTo(Duration.ofSeconds(5));
              assertThat(politica.zonaNegocio()).isEqualTo(PoliticaLancamento.ZONA_NEGOCIO);
            });
  }

  @Test
  void valorMaximoInvalidoImpedeASubida() {
    contexto
        .withPropertyValues("lancamentos.max-valor=0")
        .run(aplicacao -> assertThat(aplicacao).hasFailed());
  }
}
