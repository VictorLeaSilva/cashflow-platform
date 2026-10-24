package br.com.fluxocaixa.consolidado.domain;

/**
 * Tipo do lançamento como publicado no evento ({@code 03 §4}). Duplicado do serviço de Lançamentos
 * de propósito: o Consolidado conforma ao contrato, sem código compartilhado ({@code 01 §3}).
 */
public enum TipoLancamento {
  CREDITO,
  DEBITO,
  ESTORNO
}
