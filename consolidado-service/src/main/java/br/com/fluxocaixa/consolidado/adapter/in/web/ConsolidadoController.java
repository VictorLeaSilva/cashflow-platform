package br.com.fluxocaixa.consolidado.adapter.in.web;

import br.com.fluxocaixa.consolidado.application.ConsultarConsolidado;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/consolidado} e {@code GET /api/v1/consolidado/{data}} (03 §5, RF-005, RF-006):
 * só os dados do comerciante do token (RF-010); a query é validada sem nada ignorado em silêncio
 * (Q-052).
 */
@RestController
@RequestMapping("/api/v1/consolidado")
public class ConsolidadoController {

  private final ConsultarConsolidado consultarConsolidado;

  public ConsolidadoController(ConsultarConsolidado consultarConsolidado) {
    this.consultarConsolidado = consultarConsolidado;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ConsolidadoResponse> intervalo(
      @RequestParam MultiValueMap<String, String> parametros, JwtAuthenticationToken autenticacao) {
    EntradaDeConsolidado.Periodo periodo = EntradaDeConsolidado.periodo(parametros);
    return ResponseEntity.ok(
        ConsolidadoResponse.de(
            consultarConsolidado.intervalo(
                ComercianteJwtConverter.comerciante(autenticacao),
                periodo.inicio(),
                periodo.fim())));
  }

  @GetMapping(path = "/{data}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<DiaResponse> dia(
      @PathVariable("data") String data,
      @RequestParam MultiValueMap<String, String> parametros,
      JwtAuthenticationToken autenticacao) {
    return ResponseEntity.ok(
        DiaResponse.de(
            consultarConsolidado.dia(
                ComercianteJwtConverter.comerciante(autenticacao),
                EntradaDeConsolidado.data(data, parametros))));
  }
}
