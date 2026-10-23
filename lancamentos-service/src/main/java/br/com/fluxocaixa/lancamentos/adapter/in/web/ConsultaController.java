package br.com.fluxocaixa.lancamentos.adapter.in.web;

import br.com.fluxocaixa.lancamentos.application.ConsultarLancamentos;
import java.util.UUID;
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
 * {@code GET /api/v1/lancamentos/{id}} e {@code GET /api/v1/lancamentos} (03 §5, RF-004): só os
 * lançamentos do comerciante do token (RF-010); a query é validada sem nada ignorado em silêncio
 * (Q-025).
 */
@RestController
@RequestMapping("/api/v1/lancamentos")
public class ConsultaController {

  private final ConsultarLancamentos consultarLancamentos;

  public ConsultaController(ConsultarLancamentos consultarLancamentos) {
    this.consultarLancamentos = consultarLancamentos;
  }

  @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<LancamentoResponse> detalhar(
      @PathVariable("id") String id,
      @RequestParam MultiValueMap<String, String> parametros,
      JwtAuthenticationToken autenticacao) {
    UUID valido = EntradaDeConsulta.id(id, parametros);
    return ResponseEntity.ok(
        LancamentoResponse.de(
            consultarLancamentos.detalhar(
                ComercianteJwtConverter.comerciante(autenticacao), valido)));
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PaginaDeLancamentosResponse> listar(
      @RequestParam MultiValueMap<String, String> parametros, JwtAuthenticationToken autenticacao) {
    return ResponseEntity.ok(
        PaginaDeLancamentosResponse.de(
            consultarLancamentos.listar(
                EntradaDeConsulta.filtro(
                    ComercianteJwtConverter.comerciante(autenticacao), parametros))));
  }
}
