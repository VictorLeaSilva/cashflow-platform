# ADR-002 — PostgreSQL + `JdbcClient` + Flyway; sem cache

**Status:** Aceito · **Data:** 2026-10-22

Decisões cobertas: D-04, D-05, D-12, D-15

## Contexto
Lançamentos é um registro financeiro: precisa de valores exatos, imutabilidade, estorno único e idempotência sob concorrência (P3, P4, RNF-004). O Consolidado precisa de *upsert* aditivo atômico e deduplicação de eventos. A leitura de pico é de 50 req/s (RNF-002).

## Decisão
- **PostgreSQL** nos dois serviços (D-04). As garantias ficam no banco: `NUMERIC(15,2)`, `CHECK`, índice único parcial (`ux_estorno_unico`), `INSERT ... ON CONFLICT`, FK diferida (ADR-003) e `FOR UPDATE SKIP LOCKED` para o publicador.
- **`JdbcClient` do Spring + Flyway, sem JPA/Hibernate** (D-05). SQL explícito para *upsert*, `SKIP LOCKED` e `ON CONFLICT`; o domínio permanece Java puro (ADR-009). Uma pasta de migrações por serviço.
- **Sem cache** (D-12): a consulta do consolidado é leitura por chave primária `(comerciante_id, data_referencia)`, trivial a 50 req/s. A decisão é revista com o resultado do k6 (T-033).
- **Topologia física (D-15): 1 instância, 2 bancos, roles separados.** Cada serviço tem `*_owner` (migra) e `*_app` (opera), com `CONNECT` só no próprio banco; `lancamentos_app` não tem `UPDATE`/`DELETE` em `lancamento`. Provado localmente pelo `deploy/verify-local.sh` (T-004). No alvo, a mesma topologia em RDS Multi-AZ (ADR-010).

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| DynamoDB | Sem `NUMERIC` exato, sem índice único parcial nem transação relacional simples entre lançamento, chave de idempotência e outbox; exigiria reconstruir essas garantias na aplicação |
| MongoDB | Mesmo problema de garantias transacionais e de unicidade para um domínio financeiro pequeno e relacional |
| JPA/Hibernate | Esconde exatamente o SQL que este desenho precisa controlar (`ON CONFLICT`, `SKIP LOCKED`, FK diferida) e puxa anotações para o domínio |
| Redis/Caffeine na leitura | Sem medição que justifique (A9, P2); acrescenta invalidação e outra peça para operar |
| Instâncias de banco separadas por serviço | Custo maior para um isolamento lógico que já é verificável por roles; reservado a requisito de isolamento físico |

## Consequências
- **Ganho:** integridade financeira garantida pelo banco e provada em testes de integração (T-011, T-012, T-021).
- **Custo assumido:** mais SQL manual; os dois bancos compartilham a mesma instância (um incidente de instância afeta ambos — mitigado no alvo por Multi-AZ). A instância compartilhada **não** viola RNF-001: Lançamentos não depende do Consolidado em tempo de execução, e sim do mesmo servidor de banco.

## Revisitar quando
- Escala de escrita que um PostgreSQL não atenda.
- O k6 (T-033) mostrar saturação de leitura → cache ou réplica de leitura.
- Surgir requisito de isolamento físico → instâncias separadas.

## Referências
RNF-002, RNF-004; `03 §1` (D-04, D-05, D-12, D-15), `03 §3`; `00` A9; T-004, T-011, T-021, T-033.
