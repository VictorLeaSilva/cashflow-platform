package br.com.fluxocaixa.lancamentos.adapter.in.web;

/**
 * Corpo opcional de {@code POST /api/v1/lancamentos/{id}/estornos} (Q-017): o estorno é total (A5)
 * e não tem campos. Quando enviado, só o objeto vazio {@code {}} é aceito; qualquer propriedade,
 * inclusive {@code comercianteId}, é rejeitada pelo mapper estrito da API.
 */
public record EstornarLancamentoRequest() {}
