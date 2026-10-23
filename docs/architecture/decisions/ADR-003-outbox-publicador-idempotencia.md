# ADR-003 — Outbox transacional, publicador por *polling* e idempotência HTTP

**Status:** Aceito · **Data:** 2026-10-22

Decisões cobertas: D-02, D-08, D-09

## Contexto
Cada lançamento precisa chegar ao Consolidado, mas o registro não pode depender da disponibilidade do Consolidado (RNF-001) nem do broker (RNF-007). Gravar no banco e publicar no Kafka em sequência é *dual write*: uma falha entre os dois passos perde ou inventa eventos. Do lado do cliente, retentativas HTTP não podem duplicar dinheiro (RF-007, RNF-004).

## Decisão
- **Outbox transacional + Kafka (D-02):** o lançamento e a linha em `outbox_event` são gravados **na mesma transação**. A publicação acontece depois, de forma assíncrona. Entrega *at-least-once*; o consumidor deduplica por `eventId` (ADR-005).
- **Publicador por *polling* dentro de Lançamentos (D-09):** a cada 500 ms, lote de até 100 linhas com `FOR UPDATE SKIP LOCKED`, produtor `acks=all` idempotente, marcação de `published_at` no mesmo lote. **Pool JDBC dedicado (máx. 2)**, timeouts curtos e *backoff* exponencial até 30 s: se o Kafka degradar, o publicador falha rápido e não consome as conexões da API (`03 §7.1`).
- **Idempotência HTTP (D-08):** `Idempotency-Key` obrigatória nos `POST`. A tabela `idempotency_key` (PK `comerciante_id, chave`) é inserida **na mesma transação** com `INSERT ... ON CONFLICT DO NOTHING`, guardando o `lancamento_id` gerado antes; uma **FK diferida** impede que uma chave seja confirmada sem o lançamento. Em replay, a resposta é **reconstruída do lançamento imutável** (nada da resposta é armazenado). Mesmo payload → mesma resposta com `Idempotent-Replayed: true`; payload diferente → 422.

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Chamada HTTP síncrona Lançamentos → Consolidado | Acopla disponibilidade: queda do Consolidado derruba o registro (viola RNF-001 e P6) |
| *Dual write* (salvar e publicar em sequência) | Perde ou duplica eventos em falhas parciais; inconsistência silenciosa |
| CDC com Debezium | Resolve a latência de publicação, mas traz Kafka Connect e mais operação sem requisito que a justifique no volume do case |
| ETL/consolidação em lote | Consolidado deixaria de refletir o dia corrente; frescor (RNF-003) não seria atendido |
| Publicar *after-commit* em memória | Evento perdido se o processo cair entre o *commit* e o envio |
| Idempotência em Redis | Outra peça a operar e sem atomicidade com o lançamento |
| Guardar o corpo da resposta na chave | A resposta não existe no momento do `INSERT` da chave; colunas anuláveis e reescrita complicam a concorrência |

## Consequências
- **Ganho:** nenhum evento perdido nem inventado; Lançamentos segue respondendo 201 com Consolidado ou Kafka fora (a outbox acumula e drena depois).
- **Custo assumido:** tabela outbox, publicador e limpeza (`outbox.retention-days`, padrão 7); latência de publicação ≈ intervalo de *polling*; duplicatas na entrega, absorvidas pelo consumidor.
- *Claim/lease* (publicar fora da transação) fica fora do MVP: só se a medição mostrar contenção.

## Revisitar quando
- Latência ou volume de publicação virarem problema → CDC (Debezium).
- Contenção observada no publicador → *claim/lease*.
- Volume de chaves exigir TTL/limpeza dedicada.

## Referências
RF-001, RF-002, RF-007, RNF-001, RNF-004, RNF-007; `03 §1` (D-02, D-08, D-09), `03 §2.1`, `03 §3.1.1`, `03 §7`, `03 §7.1`; T-012, T-016, T-031.
