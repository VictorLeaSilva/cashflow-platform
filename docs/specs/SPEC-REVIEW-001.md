# SPEC-REVIEW-001 — Revisão externa da especificação

Origem: revisão técnica de terceiro sobre `00`–`06` (out/2026). Disposição de cada ponto e onde a spec mudou.

| # | Ponto | Disposição | Mudança |
|---|---|---|---|
| 1 | `idempotency_key` com `response_*` NOT NULL antes de a resposta existir; conflito sem inutilizar a transação | **Aceito, solução adaptada.** Em vez de colunas anuláveis, a tabela guarda `lancamento_id` (gerado antes) com FK **diferida**, a resposta é reconstruída do lançamento imutável e o conflito usa `ON CONFLICT DO NOTHING` | `03 §3.1`, `§3.1.1`, D-08; AC-007.4/.6; T-011, T-012 |
| 2 | Publicador pode segurar conexões se o Kafka degradar | **Aceito parcialmente.** Mitigação simples (pool dedicado de 2 conexões, timeouts, lote, *backoff*, purge). *Claim/lease* fora do MVP, registrado como evolução (ADR-003) | `03 §7.1`, D-09; T-016; teste de Kafka fora por período prolongado |
| 3 | Consequência de negócio da DLT sem procedimento | **Aceito** | `03 §7.2`; AC-008.5; runbook na T-022 |
| 4 | Semântica de datas e exemplos de fronteira | **Aceito** | `03 §4.1`; AC-001.3/.5, AC-009.3–.5; A4, A11; T-010 |
| 5 | Propriedade de ordem vale só para esta projeção | **Aceito** | Ressalva em AC-008.3 (e ADR-005) |
| 6 | Isolamento de capacidade no Consolidado; outbox × Kafka | **Aceito** | Pools e concorrência limitados; cenário de drenagem (AC-T033.7); RNF-001/RNF-007 e alertas distinguem as duas falhas; T-031 |
| 7 | Peso do processo de qualidade | **Aceito em parte.** Ferramentas mantidas (já faseadas e cortáveis); adicionados priorização, testes de risco obrigatórios, cobertura como piso/meta e exceção justificada para complexidade | `04 §2.4`, `§2.7`, `§2.8` |
| 8 | Stack ambiciosa; compatibilidade | **Aceito** (a versão do Boot já foi atualizada para 4.1.x) | AC-T001.4; dependências transversais só na tarefa que as exige |
| 9 | Premissas de negócio A3 (saldo inicial zero), A6 (débito pode negativar), A5 (estorno total e único) | **Adotadas como premissas do MVP** (cenário sem legado), com seção de transição para o cenário com legado e A6 justificada também como decisão de arquitetura | `00 §6.1`, A3/A5/A6; T-041 |
| 10 | Próximos passos | Alinhado ao backlog; passos 1–3 atendidos por esta revisão | — |
