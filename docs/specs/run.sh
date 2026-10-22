#!/usr/bin/env bash
# Executa os testes k6 com token de desenvolvimento e registra resultado + ambiente.
# Uso: loadtest/run.sh smoke|load
# Variáveis: LANC_URL, CONS_URL, MERCHANT_ID, READ_RPS, WRITE_RPS, DURATION
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-}"
case "$MODE" in smoke|load) ;; *) echo "Uso: $0 smoke|load" >&2; exit 2 ;; esac
for bin in k6 java git; do
  command -v "$bin" >/dev/null || { echo "Dependência ausente: $bin" >&2; exit 2; }
done

MERCHANT_ID="${MERCHANT_ID:-$(uuidgen | tr 'A-Z' 'a-z')}"   # comerciante novo = base limpa
TOKEN="$(java scripts/DevToken.java --merchant "$MERCHANT_ID" \
  --scopes lancamentos:write,lancamentos:read,consolidado:read --ttl 3600)"

OUT="docs/performance/results"
mkdir -p "$OUT"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

# Registro do ambiente (P8: número só vale com data e ambiente).
{
  echo "data_utc=$STAMP"
  echo "modo=$MODE"
  echo "git_sha=$(git rev-parse --short HEAD)"
  echo "k6=$(k6 version | head -1)"
  echo "so=$(uname -sr)"
  echo "cpus=$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo desconhecido)"
  echo "java=$(java -version 2>&1 | head -1)"
  echo "READ_RPS=${READ_RPS:-50} WRITE_RPS=${WRITE_RPS:-10} DURATION=${DURATION:-5m}"
  echo "LANC_URL=${LANC_URL:-http://localhost:8081} CONS_URL=${CONS_URL:-http://localhost:8082}"
} > "$OUT/$MODE-$STAMP.env.txt"

k6 run \
  -e TOKEN="$TOKEN" \
  -e LANC_URL="${LANC_URL:-http://localhost:8081}" \
  -e CONS_URL="${CONS_URL:-http://localhost:8082}" \
  -e READ_RPS="${READ_RPS:-50}" \
  -e WRITE_RPS="${WRITE_RPS:-10}" \
  -e DURATION="${DURATION:-5m}" \
  --summary-export "$OUT/$MODE-$STAMP.json" \
  "loadtest/$MODE.js"
