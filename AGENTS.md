# AGENTS.md — Instruções para agentes de código

Projeto: **Fluxo de Caixa** (serviço de Lançamentos + serviço de Consolidado Diário).
Método: **Spec Driven Development (SDD)** com harness de verificação e revisão por tarefa.

## Ordem de leitura (obrigatória antes de qualquer tarefa)
1. `docs/specs/00-constitution.md` (princípios, stack, convenções, premissas)
2. O arquivo da tarefa em `docs/specs/tasks/T-xxx.md` (ou o item em `docs/specs/06-tasks.md`)
3. Apenas as seções de spec que a tarefa referencia (`02-requirements`, `03-design-contracts`, `04-quality-standards`)
4. `docs/specs/05-harness.md` (como verificar e como será revisado)

## Regras inegociáveis
- **Hierarquia de autoridade:** constituição > requisitos > design/contratos > tarefa. Conflito ou lacuna: **pare**, registre em `docs/specs/open-questions.md` e aguarde decisão. Nunca improvise.
- Altere **somente** os caminhos listados em `allowed_paths` da tarefa.
- Não adicione dependência, endpoint, coluna, tópico, chave de configuração ou módulo que não esteja na spec.
- Teste primeiro: escreva os testes dos critérios de aceite (AC) e veja-os falhar antes de implementar.
- Todo teste referencia o requisito que cobre via `@Tag("RF-001")` (ou `RNF-xxx`).
- Dinheiro: `BigDecimal`/`NUMERIC`. Tempo: sempre via `Clock` injetado. IDs: via porta `IdGenerator`.
- Não registre em log: tokens, segredos, payload financeiro completo.
- Uma tarefa = um branch `task/T-xxx-slug` = um PR. Commits em Conventional Commits.

## Definição de pronto de uma tarefa
`scripts/verify-task.sh T-xxx` verde **e** relatório de revisão independente `docs/specs/reviews/T-xxx.md` com veredito PASS **e** aprovação humana.

**README vivo (Q-054):** se a tarefa muda como subir ou usar a aplicação (comandos, portas, variáveis, *profiles*, rotas, *scopes*, serviços do compose, scripts), o mesmo PR atualiza o `README.md`: `README.md` entra nos `allowed_paths`, os comandos afetados são executados de novo em **bash e em PowerShell** (numa stack isolada, nunca na do autor) e o "o que você deve ver" é conferido com a saída real, registrada na evidência. Se a tarefa não muda nada disso, o checklist diz "N/A" e o porquê.

## Comandos
- `./mvnw -q verify` — gate completo local (formato, lint, testes, arquitetura, cobertura)
- `scripts/verify-task.sh T-xxx` — gate da tarefa (inclui escopo e rastreabilidade)
- `docker compose -f deploy/docker-compose.yml up -d` — infraestrutura local
