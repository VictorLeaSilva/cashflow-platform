package br.com.fluxocaixa.lancamentos.adapter.in.web;

import br.com.fluxocaixa.lancamentos.application.EstornarLancamento;
import br.com.fluxocaixa.lancamentos.application.EstornarLancamentoCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /api/v1/lancamentos/{id}/estornos} (03 §5, RF-003): o comerciante vem do token
 * (RF-010), o corpo é opcional e sem campos (Q-017), e o caso de uso garante a idempotência e o
 * estorno único. O recurso criado é o estorno; em replay, o mesmo corpo volta com {@code
 * Idempotent-Replayed: true}.
 */
@RestController
@RequestMapping("/api/v1/lancamentos")
public class EstornoController {

  private final EstornarLancamento estornarLancamento;

  /** Leitor imutável do corpo, com a configuração estrita do mapper da API. */
  private final ObjectReader leitorDoCorpo;

  public EstornoController(EstornarLancamento estornarLancamento, JsonMapper json) {
    this.estornarLancamento = estornarLancamento;
    this.leitorDoCorpo = json.readerFor(EstornarLancamentoRequest.class);
  }

  /**
   * Sem {@code consumes}: o corpo é lido como bytes para que a ausência de corpo seja aceita com
   * qualquer {@code Content-Type}, e o tipo só seja exigido quando houver corpo (Q-017).
   */
  @PostMapping(path = "/{id}/estornos", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<LancamentoResponse> estornar(
      @PathVariable("id") String id,
      @RequestHeader(name = EntradaDeLancamento.IDEMPOTENCY_KEY, required = false) String chave,
      @RequestBody(required = false) byte[] corpo,
      JwtAuthenticationToken autenticacao,
      HttpServletRequest requisicao)
      throws HttpMediaTypeNotSupportedException {
    EstornarLancamentoCommand comando =
        EntradaDeEstorno.comando(
            ComercianteJwtConverter.comerciante(autenticacao),
            chave,
            id,
            EntradaDeEstorno.errosDoCorpo(corpo, requisicao, leitorDoCorpo),
            RequestIdFilter.requestIdDe(requisicao));
    EstornarLancamento.Resultado resultado = estornarLancamento.estornar(comando);
    LancamentoResponse resposta = LancamentoResponse.de(resultado.lancamento());
    ResponseEntity.BodyBuilder criado =
        ResponseEntity.created(URI.create("/api/v1/lancamentos/" + resposta.id()));
    if (resultado.replay()) {
      criado.header(LancamentoController.IDEMPOTENT_REPLAYED, "true");
    }
    return criado.body(resposta);
  }
}
