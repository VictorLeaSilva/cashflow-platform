package br.com.fluxocaixa.consolidado.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.fluxocaixa.consolidado.support.IntegracaoWeb;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** AC-T023.5 (API9): toda rota dos controllers está no contrato, e vice-versa. */
@Tag("RNF-014")
class InventarioDeRotasTest extends IntegracaoWeb {

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mapeamentos;

  @Test
  void rotasDosControllersSaoAsDoContrato() {
    Set<String> doCodigo = new TreeSet<>();
    mapeamentos
        .getHandlerMethods()
        .forEach(
            (info, metodo) -> {
              if (metodo.getBeanType().getPackageName().startsWith("br.com.fluxocaixa")) {
                info.getPatternValues()
                    .forEach(
                        caminho ->
                            info.getMethodsCondition()
                                .getMethods()
                                .forEach(verbo -> doCodigo.add(verbo.name() + " " + caminho)));
              }
            });

    ContratoOpenApi contrato = ContratoOpenApi.carregar();
    Set<String> doContrato = new TreeSet<>();
    contrato
        .documento()
        .path("paths")
        .properties()
        .forEach(
            caminho ->
                caminho
                    .getValue()
                    .propertyNames()
                    .forEach(
                        verbo ->
                            doContrato.add(
                                verbo.toUpperCase()
                                    + " "
                                    + contrato.prefixo()
                                    + caminho.getKey())));

    assertThat(doCodigo).isNotEmpty().isEqualTo(doContrato);
  }
}
