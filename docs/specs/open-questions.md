# Perguntas em aberto

Formato: `Q-nnn | tarefa | pergunta | opções | decisão | data`. Agentes registram aqui e **param**; o humano decide.

| ID | Tarefa | Pergunta | Opções | Decisão | Data |
|---|---|---|---|---|---|
| Q-001 | T-001 | Spring Boot 4.0.x ou 4.1.x GA como base (compatibilidade das libs de teste/contrato)? | 4.0.x / 4.1.x / 3.5.x (fallback com ADR) | 4.1.x; fallback 4.0.x só com ADR. Confirmado pelo autor. T-001 fixa **4.1.1** (patch confirmada em release de 20/08/2026); atualizar para a última patch ao executar é permitido | 2026-10-22 |
| Q-002 | T-013 | Biblioteca de validação de contrato OpenAPI compatível com a versão escolhida? | swagger-request-validator / teste próprio com *golden* | — | — |
| Q-003 | T-040 | Hospedagem do diagrama-alvo AWS: Mermaid apenas ou draw.io exportado? | Só Mermaid / draw.io + PNG | Mermaid; draw.io criado manualmente pelo autor, fora do backlog | 2026-10-22 |
| Q-004 | T-001 | `groupId` e pacote base do código? | `br.com.fluxocaixa` / outro | `br.com.fluxocaixa` (padrão assumido; renomear agora é barato, depois não) | 2026-10-22 |
