# ADR-008 — Observabilidade

**Status:** Aceito · **Data:** 2026-10-22

Decisões cobertas: D-16

## Contexto
Com integração assíncrona, uma falha não aparece como erro para o usuário: o lançamento é aceito e o consolidado simplesmente fica atrasado. É preciso **ver** o atraso e **distinguir** as duas falhas principais (Kafka fora × Consolidado parado) para agir certo (RNF-006). Monitoramento e observabilidade também são diferencial do enunciado.

## Decisão
- **Instrumentação:** Spring Boot Actuator + Micrometer + OpenTelemetry nos dois serviços; logs JSON com `traceId`/`correlationId`; *trace* propagado por cabeçalhos Kafka do produtor ao consumidor.
- **Request ID (`03 §5.2`, RNF-012):** filtro HTTP aceita `X-Request-Id` válido ou gera um; o valor volta em toda resposta, vai para o MDC, para a outbox e para o cabeçalho Kafka `x-request-id`. É o identificador que o cliente informa ao suporte; o `traceId` é o técnico, para seguir a chamada entre serviços. Um log de acesso JSON por requisição (rota padrão, status, duração, sem corpo nem token).
- **Metadados nos cabeçalhos, não num envelope:** em sucesso, `X-Request-Id` e `Date`; em erro, `requestId`, `traceId`, `timestamp` e `instance` como extensões do `problem+json`.
- **Case local:** Prometheus + Grafana em profile do compose, com regras de alerta em arquivo (T-032).
- **Alvo:** CloudWatch (métricas, logs e alarmes) — ADR-010.
- **Métricas de negócio e de fluxo** (`03 §8`): `lancamentos_registrados_total{tipo}`, `idempotency_replays_total`, `idempotency_conflicts_total`, `outbox_pending_events`, `outbox_oldest_pending_age_seconds`, `outbox_publish_failures_total`, `outbox_publish_duration_seconds`, `consolidado_events_processed_total`, `consolidado_events_duplicates_total`, `consolidado_events_dlt_total`, `consolidado_event_lag_seconds`, ocupação de cada pool JDBC, além das métricas HTTP/JVM/Kafka padrão.
- **Como distinguir as falhas:**
  - **Kafka fora** → `outbox_oldest_pending_age_seconds` sobe (a outbox não drena).
  - **Consolidado parado** → `up{job="consolidado"} == 0` e o lag do consumidor sobe, com a outbox drenando normalmente.
- **Alertas** (valores iniciais, revisados após a T-033): idade da outbox > 60 s por 5 min; Consolidado fora por 2 min; lag do consumidor crescente; qualquer evento na DLT; lag de projeção p95 > 5 s por 5 min; taxa de 5xx > 5 %; saturação de qualquer pool JDBC.

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Só logs | Não mostram tendência (lag, idade da outbox) nem permitem alertar com antecedência |
| Stack completa de APM comercial | Custo e dependência externa sem necessidade para o case; OpenTelemetry mantém a porta aberta |
| Envelope `{requestId, timestamp, data}` em toda resposta | Quebra o AC-007.2 (o replay idempotente devolve o mesmo corpo; `requestId` e horário mudariam a cada chamada), duplica o que o HTTP já transporta (status, `Date`) e foge do RFC 9457 nos erros |
| Prometheus/Grafana gerenciados no alvo (AMP/AMG) | CloudWatch já cobre o necessário com menos peças; AMP/AMG ficam como evolução se o time padronizar PromQL |

## Consequências
- **Ganho:** operação visível; cada alerta aponta a ação (ver runbook da DLT, `03 §7.2`).
- **Custo assumido:** dependências de observabilidade nos serviços e um profile a mais no compose.
- Os limiares são **iniciais**: não são medições (P8) e serão ajustados com a carga da T-033.

## Revisitar quando
- Volume de logs/métricas no alvo tornar o CloudWatch caro → amostragem ou AMP/AMG.

## Referências
RNF-003, RNF-006; `03 §1` (D-16), `03 §8`, `03 §7.2`; T-022, T-032, T-033.
