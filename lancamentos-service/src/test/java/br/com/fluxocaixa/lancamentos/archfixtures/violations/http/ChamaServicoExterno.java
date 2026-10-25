package br.com.fluxocaixa.lancamentos.archfixtures.violations.http;

import java.net.http.HttpClient;

/** Fixture: viola "sem chamadas HTTP de saída". Existe só para provar que a regra falha. */
public class ChamaServicoExterno {

  public HttpClient cliente() {
    return HttpClient.newHttpClient();
  }
}
