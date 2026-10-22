# Perguntas em aberto

Formato: `Q-nnn | tarefa | pergunta | opções | decisão | data`. Agentes registram aqui e **param**; o humano decide.

| ID | Tarefa | Pergunta | Opções | Decisão | Data |
|---|---|---|---|---|---|
| Q-001 | T-001 | Spring Boot 4.0.x ou 4.1.x GA como base (compatibilidade das libs de teste/contrato)? | 4.0.x / 4.1.x / 3.5.x (fallback com ADR) | 4.1.x; fallback 4.0.x só com ADR. Confirmado pelo autor. T-001 fixa **4.1.1** (patch confirmada em release de 20/08/2026); atualizar para a última patch ao executar é permitido | 2026-10-22 |
| Q-002 | T-013 | Biblioteca de validação de contrato OpenAPI compatível com a versão escolhida? | swagger-request-validator / teste próprio com *golden* | — | — |
| Q-003 | T-040 | Hospedagem do diagrama-alvo AWS: Mermaid apenas ou draw.io exportado? | Só Mermaid / draw.io + PNG | Mermaid; draw.io criado manualmente pelo autor, fora do backlog | 2026-10-22 |
| Q-004 | T-001 | `groupId` e pacote base do código? | `br.com.fluxocaixa` / outro | `br.com.fluxocaixa` (padrão assumido; renomear agora é barato, depois não) | 2026-10-22 |
| Q-005 | T-005 | Computação e borda da arquitetura-alvo AWS | (a) ECS Fargate + ALB + WAF, *rate limit* na borda (API Gateway HTTP ou WAF); (b) EKS + Ingress; (c) Lambda | **Proposta:** (a). Dois serviços *stateless* não justificam o custo operacional de Kubernetes (P2); Lambda conflita com o consumidor Kafka contínuo e com os pools JDBC limitados (`03 §7.1`). Aguardando decisão do autor | — |
| Q-006 | T-016, T-022, T-030 | Biblioteca de validação de JSON Schema (draft 2020-12, com `if/then/else`) nos dois serviços | `com.networknt:json-schema-validator` / outra | **Proposta:** networknt (suporta 2020-12, mantida). Decidir antes da T-016 | — |
| Q-007 | T-010 | Nome da chave de configuração do valor máximo (A7) | `lancamentos.max-valor` / outro | `lancamentos.max-valor`, no padrão de `max-backdate-days` e `max-future-skew`; vale com a aprovação do PR das tarefas T-005..T-012 | 2026-10-22 |
