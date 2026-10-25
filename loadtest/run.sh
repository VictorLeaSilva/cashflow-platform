#!/usr/bin/env bash
# Executa os testes k6 contra a stack "apps" do compose e registra resultado, ambiente e lag (T-033).
# Uso: loadtest/run.sh smoke|load|inspect
#   smoke    teste normal (contrato ponta a ponta)
#   load     carga: READ_RPS de leitura no Consolidado + WRITE_RPS de escrita em Lançamentos
#   inspect  só valida a sintaxe dos scripts (k6 inspect); não precisa da stack
# O k6 roda em contêiner (imagem com tag fixa) na rede do compose; ninguém instala o k6 (Q-066).
# O lag de projeção vem de um Prometheus próprio da rodada, na mesma rede (Q-070, Q-072).
# Rodada "oficial" = READ_RPS=50, WRITE_RPS=10 e DURATION=5m; qualquer outro valor é "informativa"
# (RNF-010): o resultado é gravado e o script não falha por limiar (Q-073).
# Variáveis: READ_RPS, WRITE_RPS, DURATION, TOKEN_TTL, COMPOSE, ENV_FILE, K6_IMAGE, PROM_IMAGE,
#            K6_NETWORK, RESULTS_DIR, ENV_NOTE (texto livre gravado no .env.txt).
# Pré-requisitos: bash, docker, java, curl, jq e git; stack no ar com o profile "apps".
set -euo pipefail
cd "$(dirname "$0")/.."

K6_IMAGE="${K6_IMAGE:-grafana/k6:1.7.0}"
PROM_IMAGE="${PROM_IMAGE:-prom/prometheus:v3.15.1}"
COMPOSE="${COMPOSE:-docker compose --env-file ${ENV_FILE:-.env} -f deploy/docker-compose.yml --profile apps}"
RESULTS_DIR="${RESULTS_DIR:-docs/performance/results}"
READ_RPS="${READ_RPS:-50}"
WRITE_RPS="${WRITE_RPS:-10}"
DURATION="${DURATION:-5m}"
TOKEN_TTL="${TOKEN_TTL:-3600}"   # deve exceder seed + DURATION; um 401 no meio da carga conta como falha

MODE="${1:-}"
case "$MODE" in smoke | load | inspect) ;; *) echo "Uso: $0 smoke|load|inspect" >&2; exit 2 ;; esac

die() { echo "✘ $*" >&2; exit 1; }
# docker sem a conversão de caminhos do Git Bash (MSYS); no Linux e no macOS a variável não faz nada.
dk() { MSYS_NO_PATHCONV=1 docker "$@"; }
# Caminho absoluto no formato que o docker entende (no Git Bash, o do Windows).
ROOT="$(pwd -W 2>/dev/null || pwd)"

BINS="docker"
if [[ "$MODE" != "inspect" ]]; then BINS="docker java curl jq git"; fi
for bin in $BINS; do
  command -v "$bin" >/dev/null || { echo "Dependência ausente: $bin (veja o README)" >&2; exit 2; }
done

if [[ "$MODE" == "inspect" ]]; then
  for s in smoke load; do
    # Os scripts exigem os tokens ao carregar; valores de mentira bastam para inspecionar.
    dk run --rm -v "$ROOT/loadtest:/loadtest:ro" \
      "$K6_IMAGE" inspect -e TOKEN=inspect -e TOKEN_READONLY=inspect "/loadtest/$s.js" >/dev/null \
      || die "k6 inspect falhou em $s.js"
    echo "✔ k6 inspect: $s.js"
  done
  exit 0
fi

# Rede do compose: a mesma em que os serviços "lancamentos" e "consolidado" respondem pelo nome.
CID="$($COMPOSE ps -q lancamentos 2>/dev/null || true)"
[[ -n "$CID" ]] || die "Lançamentos não está no ar. Suba o profile apps (README: Carga e desempenho)."
NET="${K6_NETWORK:-$(dk inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' "$CID" | awk '{print $1}')}"
[[ -n "$NET" ]] || die "Rede do compose não encontrada (defina K6_NETWORK)."

# UUID do comerciante: novo a cada rodada = base limpa. uuidgen, /proc ou, no Git Bash, um java mínimo.
if command -v uuidgen >/dev/null; then
  MERCHANT_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"
elif [[ -r /proc/sys/kernel/random/uuid ]]; then
  MERCHANT_ID="$(cat /proc/sys/kernel/random/uuid)"
else
  UDIR="$(mktemp -d)"
  echo 'public class Uuid { public static void main(String[] a) { System.out.println(java.util.UUID.randomUUID()); } }' > "$UDIR/Uuid.java"
  MERCHANT_ID="$(java "$UDIR/Uuid.java")"
  rm -rf "$UDIR"
fi
SCOPES="lancamentos:write,lancamentos:read,consolidado:read"
TOKEN="$(java scripts/DevToken.java --merchant "$MERCHANT_ID" --scopes "$SCOPES" --ttl "$TOKEN_TTL")"
TOKEN_READONLY="$(java scripts/DevToken.java --merchant "$MERCHANT_ID" \
  --scopes lancamentos:read,consolidado:read --ttl "$TOKEN_TTL")"

OFICIAL=true
if [[ "$READ_RPS" != "50" || "$WRITE_RPS" != "10" || "$DURATION" != "5m" ]]; then OFICIAL=false; fi
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
if [[ "$MODE" == "load" ]]; then PREFIX="load-${READ_RPS}rps-$STAMP"; else PREFIX="smoke-$STAMP"; fi
mkdir -p "$RESULTS_DIR"
OUT_DIR="$ROOT/$RESULTS_DIR"
case "$RESULTS_DIR" in /* | ?:*) OUT_DIR="$RESULTS_DIR" ;; esac

PROM_NAME="loadtest-prom-$STAMP"
cleanup() { dk rm -f "$PROM_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

prom_query() {   # prom_query EXPR TEMPO_EPOCH → valor (ou n/d)
  curl -fsS --max-time 10 --get --data-urlencode "query=$1" --data-urlencode "time=$2" \
    "http://127.0.0.1:$PROM_PORT/api/v1/query" | jq -r '.data.result[0].value[1] // "n/d"'
}

if [[ "$MODE" == "load" ]]; then
  dk run -d --rm --name "$PROM_NAME" --network "$NET" -p 127.0.0.1::9090 \
    -v "$ROOT/loadtest/prometheus.yml:/etc/prometheus/prometheus.yml:ro" "$PROM_IMAGE" >/dev/null
  PROM_PORT="$(dk port "$PROM_NAME" 9090/tcp | head -n 1 | sed 's/.*://')"
  for _ in $(seq 1 30); do
    curl -fsS --max-time 2 "http://127.0.0.1:$PROM_PORT/-/ready" >/dev/null 2>&1 && break
    sleep 1
  done
  # Linha de base dos contadores: espera os dois alvos raspados pelo menos uma vez antes da rodada.
  for _ in $(seq 1 30); do
    [[ "$(prom_query 'sum(up)' "$(date +%s)")" == "2" ]] && break
    sleep 2
  done
  [[ "$(prom_query 'sum(up)' "$(date +%s)")" == "2" ]] || die "O Prometheus da rodada não alcançou os dois serviços."
  sleep 6
fi

# No Linux e no macOS o k6 grava em /out com o usuário de quem roda (a imagem usa um uid fixo).
USER_ARGS=()
case "$(uname -s)" in Linux* | Darwin*) USER_ARGS=(--user "$(id -u):$(id -g)") ;; esac

START_EPOCH="$(date +%s)"
# Uso de CPU e memória dos serviços durante a carga (explica o resultado; amostra a cada ~10 s).
SAMPLER=""
if [[ "$MODE" == "load" ]]; then
  CID_CONS="$($COMPOSE ps -q consolidado 2>/dev/null || true)"
  (while true; do
    echo "$(date -u +%H:%M:%S) $(dk stats --no-stream --format '{{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' "$CID" "$CID_CONS" | tr '\n' ' ')"
    sleep 8
  done > "$RESULTS_DIR/$PREFIX.stats.txt" 2>/dev/null) &
  SAMPLER=$!
fi
set +e
dk run --rm "${USER_ARGS[@]}" --network "$NET" \
  -v "$ROOT/loadtest:/loadtest:ro" -v "$OUT_DIR:/out" \
  -e TOKEN="$TOKEN" -e TOKEN_READONLY="$TOKEN_READONLY" \
  -e LANC_URL="${LANC_URL:-http://lancamentos:8081}" -e CONS_URL="${CONS_URL:-http://consolidado:8082}" \
  -e READ_RPS="$READ_RPS" -e WRITE_RPS="$WRITE_RPS" -e DURATION="$DURATION" \
  "$K6_IMAGE" run --summary-export "/out/$PREFIX.json" "/loadtest/$MODE.js"
K6_EXIT=$?
set -e
if [[ -n "$SAMPLER" ]]; then kill "$SAMPLER" 2>/dev/null || true; fi
echo "k6 terminou com exit $K6_EXIT"
[[ -s "$RESULTS_DIR/$PREFIX.json" ]] || die "O k6 não gravou $RESULTS_DIR/$PREFIX.json (permissão em $RESULTS_DIR?)"

# Lag de projeção (RNF-003): a janela vai do início da rodada (seed incluído) até a outbox drenar.
LAG_NOTE=""
if [[ "$MODE" == "load" ]]; then
  for _ in $(seq 1 45); do
    [[ "$(prom_query 'sum(outbox_pending_events)' "$(date +%s)")" == "0" ]] && break
    sleep 2
  done
  sleep 12   # duas raspagens depois da drenagem
  END_EPOCH="$(date +%s)"
  WIN=$((END_EPOCH - START_EPOCH))
  # Diferença entre o fim e o início da rodada (modificador @): contagem exata, sem a extrapolação
  # do increase().
  M="consolidado_event_lag_seconds"
  B_FIM="sum by (le) (${M}_bucket @ ${END_EPOCH})"; B_INI="sum by (le) (${M}_bucket @ ${START_EPOCH})"
  Q_P50="histogram_quantile(0.5, $B_FIM - $B_INI)"
  Q_P95="histogram_quantile(0.95, $B_FIM - $B_INI)"
  Q_MAX="max_over_time(${M}_max[${WIN}s])"
  Q_CNT="sum(${M}_count @ ${END_EPOCH}) - sum(${M}_count @ ${START_EPOCH})"
  Q_SUM="sum(${M}_sum @ ${END_EPOCH}) - sum(${M}_sum @ ${START_EPOCH})"
  P50="$(prom_query "$Q_P50" "$END_EPOCH")"; P95="$(prom_query "$Q_P95" "$END_EPOCH")"
  MAX="$(prom_query "$Q_MAX" "$END_EPOCH")"; CNT="$(prom_query "$Q_CNT" "$END_EPOCH")"
  SUM="$(prom_query "$Q_SUM" "$END_EPOCH")"
  PENDENTES="$(prom_query 'sum(outbox_pending_events)' "$END_EPOCH")"
  # Operação durante a janela (pico por pool e por fila): sustenta a revisão dos limites de 03 §7.1.
  prom_pool() {   # prom_pool EXPR → {"job/pool": valor}
    curl -fsS --max-time 10 --get --data-urlencode "query=$1" --data-urlencode "time=$END_EPOCH" \
      "http://127.0.0.1:$PROM_PORT/api/v1/query" \
      | jq -c '.data.result | map({key: ((.metric.job // "?") + "/" + (.metric.pool // "?")), value: .value[1]}) | from_entries'
  }
  POOL_ATIVAS="$(prom_pool "max by (job, pool) (max_over_time(hikaricp_connections_active[${WIN}s]))")"
  POOL_PENDENTES="$(prom_pool "max by (job, pool) (max_over_time(hikaricp_connections_pending[${WIN}s]))")"
  POOL_MAX="$(prom_pool "max by (job, pool) (hikaricp_connections_max)")"
  OUTBOX_PICO="$(prom_query "max_over_time(sum(outbox_pending_events)[${WIN}s:5s])" "$END_EPOCH")"
  OUTBOX_IDADE="$(prom_query "max_over_time(max(outbox_oldest_pending_age_seconds)[${WIN}s:5s])" "$END_EPOCH")"
  LAG_KAFKA="$(prom_query "max_over_time(max(kafka_consumer_fetch_manager_records_lag_max{job=\"consolidado\"})[${WIN}s:5s])" "$END_EPOCH")"
  FALHAS_PUB="$(prom_query "sum(outbox_publish_failures_total @ ${END_EPOCH}) - sum(outbox_publish_failures_total @ ${START_EPOCH})" "$END_EPOCH")"
  DLT="$(prom_query "sum(consolidado_events_dlt_total @ ${END_EPOCH}) - sum(consolidado_events_dlt_total @ ${START_EPOCH})" "$END_EPOCH")"
  ate() { awk -v v="$1" -v m="$2" 'BEGIN { print (v != "n/d" && v + 0 <= m) ? "true" : "false" }'; }
  jq -n --arg inicio "$(date -u -d "@$START_EPOCH" +%FT%TZ 2>/dev/null || echo "$START_EPOCH")" \
    --arg fim "$(date -u -d "@$END_EPOCH" +%FT%TZ 2>/dev/null || echo "$END_EPOCH")" \
    --arg win "$WIN" --arg p50 "$P50" --arg p95 "$P95" --arg max "$MAX" --arg cnt "$CNT" --arg sum "$SUM" \
    --arg pend "$PENDENTES" --arg p95ok "$(ate "$P95" 5)" --arg maxok "$(ate "$MAX" 30)" \
    --arg q50 "$Q_P50" --arg q95 "$Q_P95" --arg qmax "$Q_MAX" \
    --argjson ativas "$POOL_ATIVAS" --argjson pendentes "$POOL_PENDENTES" --argjson pmax "$POOL_MAX" \
    --arg opico "$OUTBOX_PICO" --arg oidade "$OUTBOX_IDADE" --arg lagk "$LAG_KAFKA" \
    --arg fpub "$FALHAS_PUB" --arg dlt "$DLT" \
    '{janela_segundos: ($win | tonumber), inicio_utc: $inicio, fim_utc: $fim,
      eventos_aplicados: $cnt, lag_segundos: {p50: $p50, p95: $p95, max: $max, soma: $sum},
      outbox_pendente_no_fim: $pend,
      operacao: {pool_conexoes_ativas_pico: $ativas, pool_conexoes_pendentes_pico: $pendentes, pool_conexoes_max: $pmax,
                 outbox_pendentes_pico: $opico, outbox_idade_maxima_segundos: $oidade,
                 lag_consumidor_kafka_pico_registros: $lagk, falhas_de_publicacao: $fpub, eventos_na_dlt: $dlt},
      meta_rnf003: {p95_max_segundos: 5, max_segundos: 30, p95_dentro: ($p95ok == "true"), max_dentro: ($maxok == "true")},
      consultas: {p50: $q50, p95: $q95, max: $qmax},
      observacao: "p50 e p95 interpolados pelos buckets do histograma (diferença fim menos início); max é o gauge consolidado_event_lag_seconds_max na janela"}' \
    > "$RESULTS_DIR/$PREFIX.lag.json"
  echo "lag: p95=${P95}s max=${MAX}s eventos=${CNT} (meta: p95 ≤ 5 s, máx ≤ 30 s)"
  dk rm -f "$PROM_NAME" >/dev/null 2>&1 || true
fi

# Registro do ambiente (P8: número só vale com data e ambiente).
IMG_LANC="$(dk inspect -f '{{.Image}}' "$CID" 2>/dev/null | cut -c1-19)"
{
  echo "data_utc=$STAMP"
  echo "modo=$MODE rodada=$([[ $OFICIAL == true ]] && echo oficial || echo informativa)"
  echo "git_sha=$(git rev-parse --short HEAD)"
  echo "k6_imagem=$K6_IMAGE"
  echo "prometheus_imagem=$PROM_IMAGE"
  echo "so_host=$(uname -sr) arquitetura=$(uname -m)"
  echo "docker=$(dk info --format 'versao={{.ServerVersion}} cpus={{.NCPU}} memoria_bytes={{.MemTotal}} so={{.OperatingSystem}} arq={{.Architecture}}')"
  echo "imagem_lancamentos=$IMG_LANC"
  echo "READ_RPS=$READ_RPS WRITE_RPS=$WRITE_RPS DURATION=$DURATION"
  echo "alvo=${LANC_URL:-http://lancamentos:8081} ${CONS_URL:-http://consolidado:8082} (rede $NET)"
  echo "gerador_e_servicos_na_mesma_maquina=sim"
  echo "k6_exit=$K6_EXIT ($([[ $K6_EXIT == 0 ]] && echo 'limiares passaram' || echo 'limiares falharam ou erro'))"
  if [[ -n "${ENV_NOTE:-}" ]]; then echo "nota=$ENV_NOTE"; fi
} > "$RESULTS_DIR/$PREFIX.env.txt"
echo "Resultados em $RESULTS_DIR/$PREFIX.*"

if [[ "$OFICIAL" == false && ( "$K6_EXIT" == "0" || "$K6_EXIT" == "99" ) ]]; then exit 0; fi
exit "$K6_EXIT"
