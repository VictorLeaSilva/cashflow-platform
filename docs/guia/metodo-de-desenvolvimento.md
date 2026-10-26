# Método de desenvolvimento

> Como o projeto foi construído: especificação, tarefas, gate e revisão independente. Não é necessário para rodar a aplicação.

O projeto usa **Spec Driven Development (SDD)**, com implementação e revisão independente por tarefa. Antes de implementar uma tarefa, consulte:

1. [`AGENTS.md`](../../AGENTS.md): instruções para agentes de código.
2. [`docs/specs/00-constitution.md`](../../docs/specs/00-constitution.md): princípios e convenções.
3. [`docs/specs/tasks/`](../../docs/specs/tasks): o arquivo da tarefa, por exemplo `T-023.md`.
4. [`docs/specs/05-harness.md`](../../docs/specs/05-harness.md): verificação, testes e revisão.

Cada tarefa é verificada pelo mesmo comando, `scripts/verify-task.sh T-xxx` (veja [Verificar uma tarefa](testes-e-qualidade.md#verificar-uma-tarefa-gate-do-método)), e depois revisada por um subagente independente ([`.claude/agents/reviewer.md`](../../.claude/agents/reviewer.md)), que escreve o relatório em `docs/specs/reviews/T-xxx.md`. Os modelos da tarefa e da revisão estão em [`docs/specs/templates/`](../../docs/specs/templates/), o inventário de dependências em [`dependency-allowlist.md`](../../docs/specs/dependency-allowlist.md) e a matriz requisito → teste em [`traceability.md`](../../docs/specs/traceability.md).

### Manter o README e o passo a passo vivos

O [`README.md`](../../README.md) e o [passo a passo](passo-a-passo.md) são parte do produto. Toda tarefa que muda **como subir ou usar a aplicação** atualiza os dois no mesmo PR (Q-054 e Q-094). Isso inclui:
- comandos, portas e variáveis;
- *profiles*, rotas e *scopes*;
- serviços do compose e scripts.

Os comandos afetados são **executados de novo, em bash e em PowerShell**, e o "o que você deve ver" é conferido com a saída real. A regra está no [`AGENTS.md`](../../AGENTS.md) e no [`05-harness.md`](../../docs/specs/05-harness.md), e a revisão independente a verifica (critério R10).

## Marcos
O backlog completo, com as dependências e o resultado verificável de cada tarefa, está em [`docs/specs/06-tasks.md`](../../docs/specs/06-tasks.md); a prova de cada requisito está na matriz [`traceability.md`](../../docs/specs/traceability.md) e nos arquivos `docs/specs/reviews/T-xxx.evidence.md`.

| Marco | O que entrega | Tarefas |
|---|---|---|
| M0 | Fundação: repositório, gates de qualidade, harness, infraestrutura local, documentação de arquitetura e README executável | T-001 a T-007 |
| M1 | Lançamentos completo: domínio, persistência, idempotência, API, estorno, consultas e publicador da outbox | T-010 a T-016 |
| M2 | Consolidado completo: projeção, persistência, consumidor Kafka com DLT e API de consulta | T-020 a T-023 |
| M3 | Provas e transversais: contrato do evento, resiliência, observabilidade, demo guiada, carga, *hardening* e correção de vulnerabilidades | T-030 a T-035, T-037 e T-038 (a T-036, Keycloak local, foi cortada) |
| M4 | Documentação final e release: arquitetura final, arquitetura de transição, estimativa de custos, README e guias, release | T-040 a T-045 |
