# ADR-005 — Consistência eventual e projeção comutativa; estorno compensatório

**Status:** Aceito · **Data:** 2026-10-22

Decisões cobertas: D-06, D-07

## Contexto
Com integração assíncrona (ADR-003), o Consolidado é uma **projeção** derivada dos eventos e fica alguns segundos atrás de Lançamentos (meta de frescor: RNF-003). Os eventos podem chegar **duplicados** (*at-least-once*), **fora de ordem** entre comerciantes e **retroativos** (até 30 dias, A4). Correções de lançamento não podem reescrever o passado (P4).

## Decisão
- **Projeção só de movimento diário aditivo** (D-06): `consolidado_diario` guarda, por comerciante e dia, os totais de créditos, débitos, estornos de crédito, estornos de débito e a quantidade. Cada evento soma deltas com um *upsert* atômico (`INSERT ... ON CONFLICT DO UPDATE SET total = total + EXCLUDED.total`), na mesma transação do registro em `evento_processado` (deduplicação por `eventId`).
- **Saldo acumulado calculado na leitura:** `saldoAnterior` = soma dos `saldoDia` anteriores ao intervalo; `saldoAcumulado` = janela sobre o intervalo. Nada de saldo de fechamento materializado.
- **Estorno é um lançamento compensatório na data do estorno** (D-07): o original nunca é alterado e dias passados nunca são reescritos.
- Como somas são comutativas e a deduplicação é atômica, **aplicar os mesmos eventos em qualquer ordem e com duplicatas produz a mesma projeção** (AC-008.3, a ser provado com teste de propriedade na T-020). Essa garantia vale **só** para esta projeção aditiva.

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Saldo de fechamento materializado por dia | Lançamento retroativo exigiria recalcular em cascata todos os dias seguintes e tornaria a ordem dos eventos relevante |
| Reverter o estorno na data do original | Reescreveria dias já consultados/fechados; contraria a prática contábil e P4 |
| Consistência forte (Consolidado lendo Lançamentos na hora) | Acoplamento síncrono; viola RNF-001 |

## Consequências
- **Ganho:** retroativos e reentregas não exigem recálculo nem ordenação global; o consumidor fica simples e idempotente.
- **Custo assumido:** o consolidado pode estar alguns segundos atrás (exposto em `atualizadoEm` e na métrica `consolidado_event_lag_seconds`); a leitura soma o histórico anterior ao intervalo.
- Regras futuras que dependam de sequência (validar débito por saldo, fechamento de dia) **não** herdam a propriedade de comutatividade e exigirão garantias de ordem.

## Revisitar quando
- Histórico longo deixar a soma de `saldoAnterior` lenta → *snapshots* periódicos de saldo.
- Exigência regulatória diferente para estornos.

## Referências
RF-005, RF-006, RF-008, RF-009, RNF-003, RNF-004; `03 §1` (D-06, D-07), `03 §2.2`, `03 §3.2`; `00` P4, A4, A5; T-020, T-021, T-023.
