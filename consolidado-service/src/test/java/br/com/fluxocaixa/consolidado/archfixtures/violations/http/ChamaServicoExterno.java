package br.com.fluxocaixa.consolidado.archfixtures.violations.http;

import java.net.http.HttpClient;

/** Fixture: viola "sem chamadas HTTP de saída". Existe só para provar que a regra falha. */
public class ChamaServicoExterno {

  public HttpClient cliente() {
    return HttpClient.newHttpClient();
  }
}
