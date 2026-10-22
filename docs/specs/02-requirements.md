---
id: SPEC-02
status: draft
---
# 02 · Requisitos Refinados e Critérios de Aceite

Convenção: cada critério tem ID `AC-<req>.<n>` em formato Dado/Quando/Então. Cada teste referencia o requisito com `@Tag("RF-001")` ou `@Tag("RNF-002")`. O script `scripts/check-traceability.sh` falha se um requisito obrigatório não tiver ao menos um teste.

## 1. Requisitos funcionais

### RF-001 · Registrar crédito
- **AC-001.1** Dado um JWT com scope `lancamentos:write` e uma `Idempotency-Key` nova, quando `POST /api/v1/lancamentos` com `{tipo:"CREDITO", valor:"100.00"}`, então 201 com `Location`, corpo com `id`, `comercianteId` (do token), `dataReferencia`, e existem **1 linha em `lancamento` e 1 em `outbox_event`, gravadas na mesma transação**.
- **AC-001.2** Valor ausente, `<= 0`, com mais de 2 casas decimais ou acima do máximo (A7), ou `tipo` inválido/`ESTORNO` → 400 `problem+json` com lista de erros; nada é persistido.
- **AC-001.3** `dataOcorrencia` futura (além da tolerância A11) ou com `dataReferencia` anterior ao limite de retroatividade (A4) → 422.
- **AC-001.5** `dataOcorrencia` sem offset → 400.
- **AC-001.4** Sem token → 401; token sem scope → 403.

### RF-002 · Registrar débito
- **AC-002.1** Idêntico a AC-001.x com `tipo:"DEBITO"`.
- **AC-002.2** Débito maior que o saldo é aceito (A6) e o saldo do dia pode ficar negativo.

### RF-003 · Estornar lançamento
- **AC-003.1** `POST /api/v1/lancamentos/{id}/estornos` cria um lançamento `ESTORNO` com `referenciaLancamentoId`, `tipoOriginal` e `valor` iguais aos do original; o original **não é alterado**.
- **AC-003.2** A `dataReferencia` do estorno é a data do estorno (fuso de negócio), não a do original.
- **AC-003.3** Original inexistente ou de outro comerciante → 404 (sem revelar existência).
- **AC-003.4** Original já estornado → 409. Alvo é um `ESTORNO` → 422.
- **AC-003.5** Duas requisições concorrentes de estorno para o mesmo original (chaves diferentes) → exatamente um 201 e um 409 (garantido por índice único parcial).

### RF-004 · Consultar histórico e detalhe
- **AC-004.1** `GET /lancamentos/{id}` → 200 ou 404.
- **AC-004.2** `GET /lancamentos?dataInicio&dataFim&tipo&page&size` retorna página ordenada por `dataReferencia desc, id`; `size` máximo 100 (acima disso → 400).
- **AC-004.3** Somente lançamentos do comerciante do token são retornados.

### RF-005 · Consolidado diário
- **AC-005.1** `GET /api/v1/consolidado?dataInicio&dataFim` retorna **um item por dia do intervalo (dias sem movimento com zeros)** com `totalCreditos`, `totalDebitos`, `totalEstornosCredito`, `totalEstornosDebito`, `saldoDia`, `qtdLancamentos`, `atualizadoEm`.
- **AC-005.2** `saldoDia = (créditos − estornosCrédito) − (débitos − estornosDébito)`.
- **AC-005.3** Intervalo inválido (`inicio > fim`) ou maior que 366 dias → 400.
- **AC-005.4** `GET /consolidado/{data}` retorna o dia; sem movimento retorna zeros (200).

### RF-006 · Saldo acumulado
- **AC-006.1** A resposta traz `saldoAnterior` (soma dos `saldoDia` anteriores a `dataInicio`) e `saldoAcumulado` por dia.
- **AC-006.2** `saldoAcumulado` do último dia = `saldoAnterior + Σ saldoDia` do intervalo.

### RF-007 · Idempotência de comandos
- **AC-007.1** `Idempotency-Key` é obrigatória em `POST` (ausente → 400).
- **AC-007.2** Mesma chave e mesmo payload → mesmo status e corpo originais, com header `Idempotent-Replayed: true`; nenhum novo lançamento/evento.
- **AC-007.3** Mesma chave e payload diferente → 422 (`idempotency-key-reuse`).
- **AC-007.4** N requisições simultâneas com a mesma chave → exatamente 1 lançamento, 1 evento e a mesma resposta para todas (a resposta é reconstruída do lançamento imutável).
- **AC-007.5** O escopo da chave é (`comercianteId`, chave): comerciantes distintos podem usar a mesma chave.
- **AC-007.6** Se a transação falhar depois de inserir a chave (falha injetada), nenhum registro de chave, lançamento ou evento permanece, e o retry com a mesma chave é processado normalmente.

### RF-008 · Consumo idempotente de eventos
- **AC-008.1** Evento válido atualiza a projeção e registra `evento_processado` **na mesma transação**; o offset só é confirmado após o commit.
- **AC-008.2** Evento duplicado (mesmo `eventId`) não altera a projeção.
- **AC-008.3** Aplicar os mesmos eventos em **qualquer ordem** e com **duplicatas** produz a mesma projeção (propriedade, jqwik). *Escopo:* vale para esta projeção de totais aditivos com deduplicação atômica; regras futuras que dependam de sequência (ex.: validação por saldo, fechamento de dia) exigirão garantias de ordem e não herdam esta propriedade.
- **AC-008.4** Evento inválido (schema) ou não processável após 3 tentativas com *backoff* vai para a DLT e incrementa `consolidado_events_dlt_total`; o consumo prossegue.
- **AC-008.5** Dado um evento que foi para a DLT, quando a causa é corrigida e o evento é reinjetado no tópico principal, então a projeção o aplica exatamente uma vez; reinjetá-lo novamente não altera a projeção (política em `03 §7.2`).

### RF-009 · Lançamento retroativo
- **AC-009.1** Lançamento com `dataOcorrencia` D-3 (dentro do limite) altera o movimento de D-3 e o `saldoAcumulado` de D-3 em diante, sem reescrever outros dias.
- **AC-009.2** Estorno de lançamento de dia passado produz efeito **no dia do estorno**; o movimento do dia original permanece inalterado.
- **AC-009.3** `dataReferencia` é derivada do **instante convertido para o fuso de negócio**, nunca da data UTC: os casos da tabela de `03 §4.1` (23:30-03:00, 02:59:59Z, 03:00:00Z, offset +01:00, omitida) produzem exatamente os valores listados.
- **AC-009.4** O consumidor usa a `dataReferencia` do evento e não a recalcula (teste com evento cuja `occurredAt` UTC cairia em outro dia).
- **AC-009.5** Limites de retroatividade: dia −30 aceito; dia −31 rejeitado (422).

### RF-010 · Isolamento por comerciante
- **AC-010.1** Com tokens de A e B, A nunca lê, estorna nem enxerga existência de dados de B (404), em ambos os serviços.
- **AC-010.2** `comercianteId` nunca é aceito em corpo, query ou path; vem exclusivamente do token.

## 2. Requisitos não funcionais

| ID | Requisito | Critério mensurável | Como é verificado |
|---|---|---|---|
| **RNF-001** | Isolamento de falha (enunciado) | Com o Consolidado **parado**, `POST /lancamentos` mantém 201; a outbox continua drenando e os eventos acumulam **no Kafka**, sendo consumidos quando o Consolidado volta | Teste E2E de resiliência (T-031) |
| **RNF-002** | Capacidade do Consolidado (enunciado: 50 req/s, perda ≤ 5 %) | Em 5 min a 50 req/s constantes em `GET /consolidado`: **falha < 5 %** (meta interna ≤ 1 %) e p95 ≤ 500 ms no ambiente local documentado. *Falha* = HTTP ≥ 500, erro de conexão ou resposta > 2 s. Cenário misto: com 10 req/s de escrita simultânea em Lançamentos, sucesso de escrita ≥ 99 % | `loadtest/smoke.js` (normal) e `loadtest/load.js` (carga) com *thresholds* (T-033); resultado e ambiente em `docs/performance/results/` |
| **RNF-003** | Frescor da projeção (meta) | `consolidado_event_lag_seconds` p95 ≤ 5 s e máx ≤ 30 s em operação normal | Teste de integração + métrica; medido na carga |
| **RNF-004** | Integridade e não-duplicidade | Nenhuma duplicidade financeira sob retentativa HTTP, reentrega Kafka ou concorrência | Testes de idempotência, concorrência e propriedade |
| **RNF-005** | Segurança | JWT validado (assinatura, `iss`, `aud`, `exp`); autorização por *scope* e por comerciante; sem segredos no repo; dependências sem CVE ≥ 7 | Testes de segurança + gates (`04-quality-standards.md`) |
| **RNF-006** | Observabilidade | Métricas, logs estruturados com `traceId` e alertas definidos em `03-design-contracts.md §8` | Teste de actuator/Prometheus + revisão |
| **RNF-007** | Resiliência a falha do broker | Com Kafka indisponível, `POST` continua 201; a **outbox cresce** de forma controlada (pool da API saudável, latência do POST estável); após recuperação drena sem perda, com possíveis duplicatas absorvidas | Teste E2E (T-031) |
| **RNF-008** | Qualidade e manutenibilidade | Gates G1–G3 verdes; cobertura e mutação nos limites de `04-quality-standards.md` | CI |
| **RNF-009** | Operabilidade local | Clone limpo → serviços de pé e fluxo exercitado em ≤ 5 comandos documentados | Teste do README (T-043) |
| **RNF-010** | Escalabilidade | Serviços *stateless*; escala horizontal documentada; carga informativa a 100 e 200 req/s registrada | ADR + k6 |
| **RNF-011** | Disponibilidade e DR na arquitetura-alvo (**metas**, não medidas) | Lançamentos 99,9 %/mês; Consolidado (consulta) 99,5 %/mês; RPO 5 min; RTO 30 min | Desenho AWS + restauração testada quando houver ambiente |

## 3. Matriz de rastreabilidade (preenchida ao longo das tarefas)
Gerada por `scripts/check-traceability.sh` em `docs/specs/traceability.md`: requisito → testes (`classe#método`) → tarefa.
