# ADR-006 — Estratégia de rebuild da projeção (fora do MVP)

**Status:** Aceito — fora do MVP · **Data:** 2026-10-22

Decisões cobertas: D-14

## Contexto
Projeções derivadas podem divergir da fonte (bug no consumidor, evento perdido na DLT sem reprocessamento, novo consumidor que precisa do histórico). A tentação é expor um *endpoint* administrativo de "reconstruir o consolidado".

## Decisão
**Não implementar rebuild administrativo no MVP.** Registrar a estratégia para quando houver necessidade:
1. A **fonte de verdade é a tabela `lancamento`**, não o tópico Kafka: a retenção do tópico é finita, então replay do Kafka não reconstrói um histórico completo.
2. Rebuild = reemitir eventos a partir de `lancamento` (por comerciante e intervalo) com os **mesmos `eventId`** originais para uma projeção nova ou limpa, e trocar a leitura quando a reconciliação fechar.
3. A recuperação **pontual** já está coberta no MVP pela política da DLT (`03 §7.2`): triagem, correção, reinjeção, conferência com a soma dos lançamentos do dia.

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Endpoints `/admin/rebuilds` no MVP | Escopo e risco altos (concorrência com o consumo normal, autorização administrativa, troca de projeção) para um ganho que o case não exige |
| Replay a partir do início do tópico | Depende da retenção do Kafka; não garante o histórico completo |

## Consequências
- **Ganho:** MVP menor e sem uma operação perigosa exposta.
- **Custo assumido:** uma divergência ampla exige procedimento manual guiado pelo runbook da DLT ou a implementação desta estratégia.

## Revisitar quando
- Ocorrer divergência real entre projeção e lançamentos.
- Surgir um novo consumidor que precise do histórico.

## Referências
RF-008; `03 §1` (D-14), `03 §7.2`; `00 §7`; T-022 (runbook da DLT).
