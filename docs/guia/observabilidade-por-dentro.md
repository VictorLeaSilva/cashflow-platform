# Observabilidade por dentro: o que cada métrica mede e como é calculada

> Guia de conceito. Para **usar** (subir o Prometheus e o Grafana, ver os alertas), veja [Observabilidade: uso](observabilidade-e-alertas.md). Os termos técnicos estão no [glossário](glossario.md).

## Em uma frase

Cada serviço expõe, numa porta própria, números sobre o que está fazendo (métricas); o Prometheus os lê a cada 5 segundos, as regras de alerta avisam quando algo foge do normal e o Grafana os desenha. Os logs e os identificadores de requisição permitem seguir um pedido de ponta a ponta.

## Uma analogia

O painel de um carro. O velocímetro e o nível de combustível são **métricas** (números que mudam). A luz de óleo é um **alerta** (acende quando um número passa de um limite). O **registro de viagem** são os logs. E o número do pedido que você carrega de uma ponta à outra (`requestId`) é o que permite achar a sua viagem no meio de todas.

## O passo a passo, com exemplos

Há três tipos de métrica, e a diferença importa para ler os números:
- **Contador** (*counter*): só sobe (por exemplo, "lançamentos registrados"). Para saber a velocidade, calcula-se a diferença no tempo.
- **Medidor** (*gauge*): sobe e desce, e mostra o valor **agora** (por exemplo, "eventos pendentes na outbox").
- **Cronômetro** (*timer*): mede durações e guarda a contagem, a soma e o maior valor; com um histograma, permite calcular percentis (por exemplo, "em 95% dos casos, abaixo de X").

### As métricas e como cada uma é calculada

| Métrica | Tipo | O que mede e como é calculada | Onde nasce |
|---|---|---|---|
| `lancamentos_registrados_total` (etiqueta `tipo`) | Contador | Lançamentos **novos** confirmados (crédito, débito, estorno), contados depois do commit. Repetição não conta | `MetricasMicrometer` |
| `idempotency_replays_total` | Contador | Respostas reconstruídas de uma chave já usada (repetição) | `MetricasMicrometer` |
| `idempotency_conflicts_total` | Contador | Reuso de chave com outro pedido (o `422`). **Não** conta os `409` | `MetricasMicrometer` |
| `outbox_pending_events` | Medidor | `SELECT count(*)` dos eventos com `published_at` vazio, a cada raspagem | `PublicadorConfig#metricasDaOutbox` |
| `outbox_oldest_pending_age_seconds` | Medidor | Agora menos o `created_at` do pendente mais antigo, em segundos; **0** se não há pendente | `PublicadorConfig#metricasDaOutbox` |
| `outbox_publish_failures_total` | Contador | Eventos cujo envio ao Kafka falhou (soma o tamanho das falhas de cada lote) | `PublicadorKafka` |
| `outbox_publish_duration_seconds` | Cronômetro | Duração do envio de **cada lote**, incluindo a espera das confirmações; sem histograma (só contagem, soma e máximo) | `PublicadorKafka` |
| `consolidado_events_processed_total` | Contador | Eventos aplicados na projeção | `MetricasDoConsumidor` |
| `consolidado_events_duplicates_total` | Contador | Eventos ignorados por já terem sido processados | `MetricasDoConsumidor` |
| `consolidado_events_dlt_total` | Contador | Eventos publicados na DLT; sobe só **depois** que a DLT confirma | `MetricasDoConsumidor` |
| `consolidado_event_lag_seconds` | Cronômetro com histograma | **Atraso ponta a ponta**, calculado como na seção abaixo | `MetricasDoConsumidor` |
| `hikaricp_connections_active` e `hikaricp_connections_max` (etiqueta `pool`) | Medidor | Conexões em uso e o máximo de cada pool: `api` e `publicador` no Lançamentos; `api` e `consumidor` no Consolidado | Automática (Spring Boot) |
| `kafka_consumer_fetch_manager_records_lag_max` | Medidor | Maior atraso do consumidor, em registros ainda não lidos | Automática (Spring Kafka) |
| `http_server_requests_seconds_count` (etiqueta `status`) | Cronômetro | Requisições HTTP por status; base do alerta de erros 5xx | Automática (Spring Boot) |

### O atraso ponta a ponta, em detalhe

`consolidado_event_lag_seconds` = (instante em que o Consolidado **confirma a soma no banco**) − (`occurredAt` do evento, o instante em que o Lançamentos criou o evento na transação). Se der negativo (relógios diferentes), vale zero. Só eventos **aplicados** entram; repetidos e os da DLT não entram.

*Exemplo (ilustrativo):* evento criado às 10:00:00,000; publicado às 10:00:00,400; somado e confirmado às 10:00:00,700. O atraso é **0,7 s**. Como o tempo de espera na outbox entra na conta, uma queda do Kafka aparece como atraso alto assim que o evento é finalmente consumido. Na medição local de 2026-10-25, o p95 foi de **0,69 s** (meta: até 5 s), no [relatório](../performance/relatorio.md).

### Os 7 alertas

As regras estão em `deploy/prometheus/alertas.yml`; o Prometheus as avalia a cada 15 s. Não há Alertmanager no MVP: os alertas aparecem na aba "Alerts" do Prometheus.

| Alerta | Dispara quando | O que significa e o que fazer |
|---|---|---|
| `OutboxComIdadeAlta` | `outbox_oldest_pending_age_seconds > 60` por 5 min | O publicador não está esvaziando a outbox: Kafka fora ou lento. Veja `last_error` na outbox |
| `ConsolidadoForaDoAr` | `up{job="consolidado"} == 0` por 2 min | O Prometheus não consegue raspar o Consolidado. Suba o serviço |
| `LagDoConsumidorCrescente` | lag do consumidor maior que 0 e crescendo, por 5 min | O consumidor não acompanha o ritmo ou travou |
| `EventoNaDlt` | `consolidado_events_dlt_total` aumentou nos últimos 5 min (**sem espera**) | Há consolidado incompleto: siga o [runbook](../runbooks/dlt-reprocessamento.md) |
| `LagDeProjecaoAlto` | p95 do atraso ponta a ponta acima de 5 s por 5 min | O consolidado está atrasado em relação aos lançamentos |
| `Taxa5xxAlta` | mais de 5% das respostas HTTP com `5xx` por 5 min | Erros no servidor; veja os logs com o `requestId` |
| `PoolJdbcSaturado` | conexões em uso acima de 90% do máximo por 5 min | Algum pool está no limite |

Não existe alerta para o Lançamentos fora do ar. Os limiares são valores iniciais, revistos com a medição local (que operou a cerca de um décimo deles).

### O painel do Grafana

O painel **Fluxo de Caixa - operação** (atualiza a cada 5 s) tem 7 gráficos: lançamentos por tipo por segundo; idade e quantidade de pendentes da outbox; eventos processados, repetidos e na DLT; atraso ponta a ponta (p95); atraso do consumidor Kafka; taxa de erros 5xx; e ocupação dos pools. O terceiro mistura "por segundo" com "contagem em 5 minutos" na mesma figura, então leia as legendas.

### Logs e identificadores: como seguir um pedido

Os logs são em JSON. Três identificadores aparecem neles:
- **`requestId`:** vem do cabeçalho `X-Request-Id` (se válido) ou é gerado, e volta no cabeçalho da resposta. Segue pelo evento, no cabeçalho `x-request-id` da mensagem Kafka, e o consumidor o coloca nos logs dele.
- **`traceId`:** identifica o rastreamento de uma requisição; está nos erros (`problem+json`) e nos logs.
- **Dois rastros diferentes:** o evento carrega no corpo o `traceId` da **requisição de origem**; mas a mensagem Kafka também leva o `traceparent` do **envio** feito pelo publicador, que é outro rastro. O consumidor registra os dois: `traceIdOrigem` (o do corpo) e `traceId` (o do envio).

Para seguir um pedido: pegue o `X-Request-Id` da resposta e procure esse valor nos logs do Lançamentos e do Consolidado. Cada log de acesso traz o método, a rota (o padrão, nunca a URL com ids), o status e a duração; **nunca** traz corpo, token, chave de idempotência nem o comerciante.

As portas de gestão são **9081** (Lançamentos) e **9082** (Consolidado), sem autenticação, e expõem só `health` e `prometheus`. A porta da API não serve o Actuator.

## Onde está no código

| Peça | Ponteiro | O que faz |
|---|---|---|
| Contadores do Lançamentos | `MetricasMicrometer` | `lancamentos_registrados_total`, repetições e conflitos |
| Medidores da outbox | `PublicadorConfig#metricasDaOutbox` | Pendentes e idade do mais antigo |
| Falhas e duração do envio | `PublicadorKafka` | `outbox_publish_failures_total` e `outbox_publish_duration_seconds` |
| Métricas do consumidor | `MetricasDoConsumidor` | Processados, repetidos, DLT e o atraso ponta a ponta |
| Registro do atraso | `ConsumidorDeLancamentos#consumir` | Calcula o atraso depois do commit da soma |
| Identificador da requisição | `RequestIdFilter` | Aceita ou gera o `X-Request-Id` e coloca no log |
| Log de acesso | `LogDeAcessoFilter` | Uma linha por requisição, sem dados sensíveis |
| Correlação em erro do consumidor | `TratamentoDeErroComCorrelacao` | `requestId`, `eventId`, `traceIdOrigem` e `traceId` |
| Regras de alerta | `deploy/prometheus/alertas.yml` | Os 7 alertas |
| Teste das regras | `deploy/prometheus/alertas.test.yml` | Casos que disparam e que não disparam |
| Raspagem | `deploy/prometheus/prometheus.yml` | A cada 5 s, portas 9081 e 9082 |
| Painel | `deploy/grafana/dashboards/fluxo-caixa.json` | Os 7 gráficos |

## Como ver funcionando

Com a stack no ar e algum lançamento registrado (a demo serve), veja as métricas de cada serviço:

**bash (Linux, macOS e Git Bash)**

```bash
curl -s http://localhost:9081/actuator/prometheus | grep -E '^(lancamentos_registrados_total|idempotency_replays_total|idempotency_conflicts_total|outbox_pending_events)'
curl -s http://localhost:9082/actuator/prometheus | grep -E '^(consolidado_events_processed_total|consolidado_event_lag_seconds_count|consolidado_event_lag_seconds_sum)'
```

**PowerShell (Windows)**

```powershell
curl.exe -s http://localhost:9081/actuator/prometheus | Select-String '^(lancamentos_registrados_total|idempotency_replays_total|idempotency_conflicts_total|outbox_pending_events)'
curl.exe -s http://localhost:9082/actuator/prometheus | Select-String '^(consolidado_events_processed_total|consolidado_event_lag_seconds_count|consolidado_event_lag_seconds_sum)'
```

**O que você deve ver:** `lancamentos_registrados_total{tipo="CREDITO",...}` maior que zero depois dos registros, `idempotency_replays_total` e `idempotency_conflicts_total` em `1.0` depois da demo (ela faz uma repetição e um reuso de chave de propósito), `outbox_pending_events 0.0`, e `consolidado_event_lag_seconds_sum` dividido por `_count` dando o atraso médio em segundos.

**Atenção ao ler logo depois da demo.** A demo religa o Consolidado, e os contadores dele zeram nesse momento. No teste, restou só 1 evento no `_count`, com média de **13,3 s**: era o evento registrado enquanto o Consolidado estava parado, e o atraso inclui a espera até ele voltar. É o atraso funcionando como descrito, não um defeito. Com o Consolidado de pé, o atraso fica em décimos de segundo. Para testar as regras de alerta sem os serviços, use o comando `promtool` do [guia de uso](observabilidade-e-alertas.md).

## O que pode dar errado

- **`idempotency_conflicts_total` não subiu com um `409`.** Ele conta só o `422` de reuso de chave.
- **Não aparece a série `hikaricp_connections_*` de um pool.** Ela só surge depois da primeira conexão desse pool.
- **O gráfico de erros mostra números sem sentido logo depois de subir.** Faltam amostras: espere alguns minutos.
- **`ConsolidadoForaDoAr` acende ao subir o Prometheus antes dos serviços.** É o alerta funcionando; some na raspagem seguinte.
- **A medição local não vale para a AWS.** O atraso e o resto foram medidos num notebook; veja a [ressalva](../performance/relatorio.md).

## Ligações

- Decisão: [ADR-008](../architecture/decisions/ADR-008-observabilidade.md); métricas e limiares em [`03 §8`](../specs/03-design-contracts.md) e [`03 §7.1`](../specs/03-design-contracts.md).
- Requisitos: RNF-003, RNF-006 e RNF-012. Provas: `MetricasDeNegocioWebTest`, `MetricasOutboxIT`, `MetricasDoConsumidorIT`, `HistogramaDoLagTest`, `RastroDoEventoIT`, `RastroDoConsumoIT` e `GestaoActuatorWebTest`. A matriz está em [`traceability.md`](../specs/traceability.md).
