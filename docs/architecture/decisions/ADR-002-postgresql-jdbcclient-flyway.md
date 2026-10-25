# ADR-002 — PostgreSQL + `JdbcClient` + Flyway; sem cache

**Status:** Aceito · **Data:** 2026-10-22

Decisões cobertas: D-04, D-05, D-12, D-15

## Contexto
Lançamentos é um registro financeiro: precisa de valores exatos, imutabilidade, estorno único e idempotência sob concorrência (P3, P4, RNF-004). O Consolidado precisa de *upsert* aditivo atômico e deduplicação de eventos. A leitura de pico é de 50 req/s (RNF-002).

## Decisão
- **PostgreSQL** nos dois serviços (D-04). As garantias ficam no banco: `NUMERIC(15,2)`, `CHECK`, índice único parcial (`ux_estorno_unico`), `INSERT ... ON CONFLICT`, FK diferida (ADR-003) e `FOR UPDATE SKIP LOCKED` para o publicador.
- **`JdbcClient` do Spring + Flyway, sem JPA/Hibernate** (D-05). SQL explícito para *upsert*, `SKIP LOCKED` e `ON CONFLICT`; o domínio permanece Java puro (ADR-009). Uma pasta de migrações por serviço.
- **Sem cache** (D-12): a consulta do consolidado é leitura por chave primária `(comerciante_id, data_referencia)`, trivial a 50 req/s. A decisão é revista com o resultado do k6 (T-033).
- **Topologia física (D-15): 1 instância, 2 bancos, roles separados.** Cada serviço tem `*_owner` (migra) e `*_app` (opera), com `CONNECT` só no próprio banco — **provado** pelo `deploy/verify-local.sh` (T-004). A imutabilidade por *grants* (`lancamentos_app` sem `UPDATE`/`DELETE` em `lancamento`) chega com as migrações e foi **provada** (T-011): `REVOKE UPDATE, DELETE, TRUNCATE` em `lancamento` na V1 e `MigracaoEPermissoesIT`. *Atualizado na T-040.* No alvo, a mesma topologia em RDS Multi-AZ (ADR-010).

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| DynamoDB | O tipo numérico é decimal exato, mas faltam o resto: índice único parcial (estorno único), `SKIP LOCKED` para o publicador, FK diferida e transação relacional simples entre lançamento, chave de idempotência e outbox; o *upsert* aditivo por dia e as consultas por intervalo exigiriam modelagem própria. Seria reconstruir na aplicação garantias que o PostgreSQL já dá |
| MongoDB | Mesmo problema de garantias transacionais e de unicidade para um domínio financeiro pequeno e relacional |
| JPA/Hibernate | Esconde exatamente o SQL que este desenho precisa controlar (`ON CONFLICT`, `SKIP LOCKED`, FK diferida) e puxa anotações para o domínio |
| Redis/Caffeine na leitura | Sem medição que justifique (A9, P2); acrescenta invalidação e outra peça para operar |
| Instâncias de banco separadas por serviço | Custo maior para um isolamento lógico que já é verificável por roles; reservado a requisito de isolamento físico |

## Consequências
- **Ganho:** integridade financeira garantida pelo banco, provada em testes de integração (`ConstraintsLancamentoIT`, `IdempotenciaConcorrenteIT`, `DeduplicacaoConcorrenteIT`; T-011, T-012, T-021). *Atualizado na T-040.*
- **Custo assumido:** mais SQL manual; os dois bancos compartilham a mesma instância (um incidente de instância afeta ambos — mitigado no alvo por Multi-AZ). A instância compartilhada **não** viola RNF-001: Lançamentos não depende do Consolidado em tempo de execução, e sim do mesmo servidor de banco.
- **Disputa de recursos na instância compartilhada:** uma carga pesada no Consolidado (leituras de pico ou drenagem de eventos acumulados) disputa CPU, I/O e conexões com as escritas de Lançamentos. Mitigação no desenho: pools JDBC com limite fixo por serviço e por função (API, publicador, consumidor — `03 §7.1`), de modo que nenhum serviço esgote as conexões do outro. A carga mista da T-033 (leitura a 50 req/s com escrita simultânea) é a medição que confirma ou refuta essa premissa.

## Revisitar quando
- Escala de escrita que um PostgreSQL não atenda.
- O k6 (T-033) mostrar saturação de leitura → cache ou réplica de leitura.
- Surgir requisito de isolamento físico, ou a carga da T-033 mostrar interferência do Consolidado nas escritas de Lançamentos → instâncias separadas (ou réplica de leitura para o Consolidado).

## Referências
RNF-002, RNF-004; `03 §1` (D-04, D-05, D-12, D-15), `03 §3`; `00` A9; T-004, T-011, T-021, T-033.
