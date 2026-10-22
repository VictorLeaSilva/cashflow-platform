---
id: SPEC-06
status: draft
---
# 06 · Backlog de Tarefas

Cada tarefa vira `docs/specs/tasks/T-xxx.md` (modelo em `05-harness.md §3`). A Fase 0 já está detalhada; as demais são detalhadas **antes** de iniciar cada uma (mesmo nível de rigor), para não congelar decisões cedo demais.

Ordem: *walking skeleton* (fundação e harness) → fatias verticais por serviço → transversais → documentação final.

| ID | Tarefa | Depende de | Cobre | Resultado verificável |
|---|---|---|---|---|
| **Fase 0 — Fundação e harness** |||||
| T-001 | Bootstrap do repositório (Maven multi-módulo, wrapper, Java 25, Boot BOM, enforcer, EditorConfig, LICENSE, README mínimo) | — | RNF-008, RNF-009 | `./mvnw verify` verde em clone limpo |
| T-002 | Gates de qualidade (Spotless, Checkstyle, SpotBugs+FindSecBugs, JaCoCo, esqueleto ArchUnit) + hooks + `ci.yml` | T-001 | RNF-008 | CI verde; violação proposital falha o build |
| T-003 | Harness (verify-task.sh, check-traceability.sh, modelo de revisão, subagente revisor, allowlist de dependências) | T-002 | RNF-008 | Scripts testados com cenários de sucesso/falha |
| T-004 | Infra local (compose: PostgreSQL com 2 bancos/roles, Kafka KRaft; `.env.example`; init SQL) | T-001 | RNF-009 | `compose up` saudável; roles isolados provados |
| **Fase 1 — Lançamentos** |||||
| T-010 | Domínio: `Lancamento`, `Dinheiro`, regras de estorno, `DataReferencia` | T-002 | RF-001..003, RF-009, RNF-004 | Unit + jqwik |
| T-011 | Persistência: Flyway V1, repositórios JDBC, constraints | T-004, T-010 | RF-003, RNF-004 | IT com Testcontainers |
| T-012 | Idempotência + caso de uso `RegistrarLancamento` (transação única com outbox) | T-011 | RF-001, RF-002, RF-007 | IT de concorrência (AC-007.4) |
| T-013 | API `POST /lancamentos` + JWT + Problem Details + OpenAPI contract-first + `scripts/DevToken.java` | T-012 | RF-001, RF-002, RNF-005 | Fatia web + validação de contrato |
| T-014 | Estorno (caso de uso + endpoint) | T-013 | RF-003 | AC-003.x |
| T-015 | Consultas (detalhe e histórico) + isolamento | T-013 | RF-004, RF-010 | AC-004.x, AC-010.x |
| T-016 | Publicador outbox (poller `SKIP LOCKED`, produtor Kafka, métricas) | T-012, T-004 | RNF-001, RNF-007 | IT: falha entre envio e marcação; Kafka fora |
| **Fase 2 — Consolidado** |||||
| T-020 | Domínio da projeção (aplicar evento → deltas) | T-002 | RF-008, RF-009 | jqwik: ordem/duplicatas |
| T-021 | Persistência (Flyway, upsert aditivo, `evento_processado`) | T-004, T-020 | RF-008, RNF-004 | IT de concorrência no mesmo dia |
| T-022 | Consumidor Kafka (schema, tx, ack pós-commit, retry+DLT, métrica de lag) | T-021 | RF-008, RNF-003 | AC-008.x |
| T-023 | API de consulta (zero-fill, saldo anterior/acumulado) + JWT + OpenAPI | T-021 | RF-005, RF-006, RF-010 | AC-005.x, AC-006.x |
| **Fase 3 — Transversais** |||||
| T-030 | Contrato de evento: JSON Schema + *golden files* nos dois lados | T-016, T-022 | RF-008 | Testes de contrato |
| T-031 | Resiliência E2E (Consolidado parado, Kafka fora, consumidor pausado, retroativo) | T-016, T-023 | RNF-001, RNF-007, RF-009 | Testes E2E |
| T-032 | Observabilidade (métricas, logs JSON, tracing via Kafka, Prometheus/Grafana, alertas) | T-016, T-022 | RNF-006 | Dashboard e regras carregam; testes de actuator |
| T-033 | Scripts k6: `smoke.js` (teste normal) e `load.js` (carga: 50 req/s + escrita simultânea; 100/200 informativo) + relatório | T-023, T-032 | RNF-002, RNF-003, RNF-010 | Thresholds passam; resultado em `docs/performance/` |
| T-034 | Hardening e segurança (cabeçalhos, limites, workflows de segurança, OWASP mapping, threat model enxuto) | T-013, T-023 | RNF-005 | Workflows verdes; documento |
| T-035 | Demo guiada `scripts/demo.sh` (idempotência, estorno, retroativo, isolamento, Consolidado parado, opcional Kafka parado) + profile `apps` no compose | T-016, T-023, T-032 | RNF-001, RNF-007, RF-007, RF-009, RF-010 | Execução ponta a ponta com exit 0 |
| **Fase 4 — Documentação e entrega** |||||
| T-040 | `architecture.md` (contexto, contêineres, runtime), diagramas Mermaid, ADR-001..009, draw.io da arquitetura-alvo AWS | T-032 | Obrigatórios do enunciado | Revisão cruzada com as specs |
| T-041 | Arquitetura de transição (enxuta): coexistência com processo legado, importação idempotente por `identificador_externo`, *shadow mode* | T-040 | Diferencial | 1 página + 1 diagrama |
| T-042 | Estimativa de custos (premissas + export da AWS Pricing Calculator, cenário MVP e produção resiliente) | T-040 | Diferencial | `docs/cost/` |
| T-043 | README final (como funciona, como rodar, como testar, decisões, evolução e "o que faria com mais tempo") | todas | RNF-009 | Seguir o README do zero funciona |
| T-044 | Release (imagens, SBOM, tag) e auditoria final de rastreabilidade | T-043 | Todos | `traceability.md` sem lacunas |

## Marcos
- **M0** (T-001..T-004): repositório com gates e harness funcionando.
- **M1** (T-010..T-016): Lançamentos completo, publica eventos.
- **M2** (T-020..T-023): Consolidado completo, consome e consulta.
- **M3** (T-030..T-035): provas de resiliência, carga, segurança e demo guiada.
- **M4** (T-040..T-044): documentação final e release.

## Corte de escopo (se o tempo apertar, nesta ordem)
1. PIT e ZAP (G4). 2. Grafana provisionado (manter métricas e alertas em arquivo). 3. T-041/T-042 em versão mínima. **Nunca cortar:** T-031, T-033, T-035, T-040, T-043.
