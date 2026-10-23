package br.com.fluxocaixa.lancamentos.adapter.in.web;

import br.com.fluxocaixa.lancamentos.application.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.RegistrarLancamentoCommand;
import br.com.fluxocaixa.lancamentos.domain.PoliticaLancamento;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/lancamentos} (03 §5): o comerciante vem do token (RF-010), a entrada é
 * validada sem correções silenciosas e o caso de uso da T-012 garante a idempotência. Em replay, o
 * mesmo corpo volta com {@code Idempotent-Replayed: true}.
 */
@RestController
@RequestMapping("/api/v1/lancamentos")
public class LancamentoController {

  static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

  private final RegistrarLancamento registrarLancamento;
  private final PoliticaLancamento politica;

  public LancamentoController(
      RegistrarLancamento registrarLancamento, PoliticaLancamento politica) {
    this.registrarLancamento = registrarLancamento;
    this.politica = politica;
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<LancamentoResponse> registrar(
      @RequestHeader(name = EntradaDeLancamento.IDEMPOTENCY_KEY, required = false) String chave,
      @RequestBody(required = false) RegistrarLancamentoRequest corpo,
      JwtAuthenticationToken autenticacao,
      HttpServletRequest requisicao) {
    RegistrarLancamentoCommand comando =
        EntradaDeLancamento.comando(
            ComercianteJwtConverter.comerciante(autenticacao),
            chave,
            corpo,
            RequestIdFilter.requestIdDe(requisicao),
            politica);
    RegistrarLancamento.Resultado resultado = registrarLancamento.registrar(comando);
    LancamentoResponse resposta = LancamentoResponse.de(resultado.lancamento());
    ResponseEntity.BodyBuilder criado =
        ResponseEntity.created(URI.create("/api/v1/lancamentos/" + resposta.id()));
    if (resultado.replay()) {
      criado.header(IDEMPOTENT_REPLAYED, "true");
    }
    return criado.body(resposta);
  }
}
