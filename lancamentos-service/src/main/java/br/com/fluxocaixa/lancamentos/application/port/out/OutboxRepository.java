package br.com.fluxocaixa.lancamentos.application.port.out;

/** Grava eventos na outbox, na mesma transação do lançamento (D-02). */
public interface OutboxRepository {

  void inserir(EventoOutbox evento);
}
