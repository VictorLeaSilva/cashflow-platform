# Observabilidade: métricas, alertas e painel (opcional)

> Como **usar** a observabilidade do projeto. Para entender o que cada métrica mede e como é calculada, veja o guia de conceito da T-045 (quando existir) e o [ADR-008](../architecture/decisions/ADR-008-observabilidade.md).

**Para que serve:** ver o que os serviços estão fazendo (quantos lançamentos, idade da fila de eventos, atraso do consolidado, erros) e ser avisado quando algo foge do normal. Tudo é local; nada sai da sua máquina.

**1. Conferir as métricas dos serviços.** Com os serviços de pé (passos 7 e 8), cada um tem uma **porta de gestão**, separada da API, que não pede token: `9081` no Lançamentos e `9082` no Consolidado. Só `health` e `prometheus` estão disponíveis; a porta da API (8081 e 8082) não serve `/actuator`.

**bash (Linux, macOS e Git Bash)**

```bash
curl -s http://localhost:9081/actuator/health
curl -s http://localhost:9081/actuator/prometheus | grep -E '^(lancamentos_registrados_total|outbox_pending_events)'
curl -s http://localhost:9082/actuator/prometheus | grep -E '^(consolidado_events_processed_total|consolidado_event_lag_seconds_count)'
```

**PowerShell (Windows)**

```powershell
curl.exe -s http://localhost:9081/actuator/health
curl.exe -s http://localhost:9081/actuator/prometheus | Select-String '^(lancamentos_registrados_total|outbox_pending_events)'
curl.exe -s http://localhost:9082/actuator/prometheus | Select-String '^(consolidado_events_processed_total|consolidado_event_lag_seconds_count)'
```

**O que você deve ver:** `{"groups":["liveness","readiness"],"status":"UP"}` (ou `"status":"DOWN"`, se o banco estiver fora) e linhas de métricas como `lancamentos_registrados_total{tipo="CREDITO",...} 3.0`, `outbox_pending_events 0.0` e `consolidado_events_processed_total 3.0`. Se os números estiverem em zero, registre um lançamento (passo 9) e consulte de novo.

**2. Subir o Prometheus e o Grafana.** Eles só sobem com o *profile* `observability` e leem as portas de gestão dos serviços que estão rodando na sua máquina:

**bash e PowerShell**

```bash
docker compose --env-file .env -f deploy/docker-compose.yml --profile observability up -d --wait --remove-orphans
```

**O que você deve ver:** as linhas `Healthy` do passo 3 e mais duas: `Container fluxo-caixa-prometheus-1 Healthy` e `Container fluxo-caixa-grafana-1 Healthy`.

**3. Olhar os resultados.**
- <http://localhost:9090/targets> (Prometheus): os alvos `lancamentos` e `consolidado` devem estar `UP`. Um alvo `DOWN` quer dizer que o serviço não está rodando ou que a porta de gestão foi trocada.
- <http://localhost:9090/alerts>: as sete regras de alerta (`deploy/prometheus/alertas.yml`). Todas devem estar `inactive` em operação normal; `OutboxComIdadeAlta` e `ConsolidadoForaDoAr` são as que acendem se você derrubar o Kafka ou o Consolidado. Se o Prometheus ficou de pé por mais de 2 minutos antes de os serviços subirem, `ConsolidadoForaDoAr` aparece `firing` até a próxima raspagem com o serviço no ar; isso é o alerta funcionando.
- <http://localhost:3000> (Grafana, só leitura e sem login): o painel **Fluxo de Caixa - operação** mostra lançamentos por tipo, a fila da outbox, os eventos do Consolidado, o atraso da projeção (p95), o atraso do consumidor, os erros 5xx e a ocupação dos bancos de conexões.

As portas do Prometheus e do Grafana podem ser trocadas no `.env` (`PROMETHEUS_PORT` e `GRAFANA_PORT`). Se você trocar as portas de gestão dos serviços (`LANCAMENTOS_MANAGEMENT_PORT` e `CONSOLIDADO_MANAGEMENT_PORT`), troque também os alvos em `deploy/prometheus/prometheus.yml`.

**4. Seguir uma requisição pelos logs.** Todo erro da API traz `traceId` e `requestId` no corpo (`problem+json`), e a mesma linha de acesso nos logs do serviço leva os dois campos. O evento publicado leva o `traceId` da requisição, que o Consolidado registra como `traceIdOrigem` na linha do evento aplicado. Procure o valor nos logs dos dois terminais.

**5. Testar as regras de alerta** (sem os serviços, só com o Docker; roda a partir da raiz do projeto):

**bash (Linux, macOS e Git Bash)**

```bash
MSYS_NO_PATHCONV=1 docker run --rm --entrypoint promtool -v "$PWD/deploy/prometheus:/work:ro" prom/prometheus:v3.15.1 test rules /work/alertas.test.yml
```

(O `MSYS_NO_PATHCONV=1` evita que o Git Bash do Windows converta `/work` num caminho do Windows; no Linux e no macOS ele não faz diferença.)

**PowerShell (Windows)**

```powershell
docker run --rm --entrypoint promtool -v "${PWD}/deploy/prometheus:/work:ro" prom/prometheus:v3.15.1 test rules /work/alertas.test.yml
```

**O que você deve ver:** `SUCCESS`. Cada alerta tem um caso que dispara e um que não dispara.

**6. Desligar.** O `down` do passo 14 só enxerga os serviços do *profile* que você informar; para desligar também o Prometheus e o Grafana, acrescente `--profile observability`:

**bash (Linux, macOS e Git Bash)**

```bash
docker compose --env-file .env -f deploy/docker-compose.yml --profile observability down
```

**PowerShell (Windows)**

```powershell
docker compose --env-file .env -f deploy/docker-compose.yml --profile observability down
```
