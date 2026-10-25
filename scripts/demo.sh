#!/usr/bin/env bash
# Demonstração guiada: prova, passo a passo, os requisitos centrais do case, com os dois serviços de
# verdade ao mesmo tempo (profile "apps" do compose).
#   RF-007 idempotência · RF-003 estorno · RF-009 retroativo · RF-010 isolamento
#   RNF-001 Lançamentos segue de pé com o Consolidado parado
#   RNF-007 (opcional, --kafka-down) Lançamentos segue de pé com o Kafka parado
# Uso: scripts/demo.sh [--kafka-down]
# Pré-requisitos: bash, curl, jq, java e docker; stack no ar com o profile "apps" (ver README).
# Roda no Linux, no macOS e no Git Bash do Windows (Q-065). Cada execução usa comerciantes novos.
# Variáveis opcionais: LANC_URL, CONS_URL, MGMT_LANC, MGMT_CONS, COMPOSE, ENV_FILE.
set -euo pipefail
cd "$(dirname "$0")/.."

LANC_URL="${LANC_URL:-http://localhost:8081}"
CONS_URL="${CONS_URL:-http://localhost:8082}"
MGMT_LANC="${MGMT_LANC:-http://localhost:9081}"
MGMT_CONS="${MGMT_CONS:-http://localhost:9082}"
COMPOSE="${COMPOSE:-docker compose --env-file ${ENV_FILE:-.env} -f deploy/docker-compose.yml --profile apps}"
SCOPES="lancamentos:write,lancamentos:read,consolidado:read"
KAFKA_DOWN=false
if [[ "${1:-}" == "--kafka-down" ]]; then KAFKA_DOWN=true; fi

for bin in curl jq java docker awk; do
  command -v "$bin" >/dev/null || { echo "Dependência ausente: $bin (veja o README)" >&2; exit 2; }
done

BOLD=$'\033[1m'; GREEN=$'\033[32m'; RED=$'\033[31m'; DIM=$'\033[2m'; RESET=$'\033[0m'
step() { printf '\n%s== %s%s\n' "$BOLD" "$*" "$RESET"; }
info() { printf '%s   %s%s\n' "$DIM" "$*" "$RESET"; }
pass() { printf '%s ✔ %s%s\n' "$GREEN" "$*" "$RESET"; }
die()  { printf '%s ✘ %s%s\n' "$RED" "$*" "$RESET" >&2; exit 1; }
expect() { if [[ "$2" == "$3" ]]; then pass "$1 (= $3)"; else die "$1: esperado '$2', obtido '$3'"; fi; }
num() { awk -v v="$1" 'BEGIN { printf "%.2f", v }'; }

BODY_F="$(mktemp)"; HDR_F="$(mktemp)"; UUID_F="$(mktemp)"
cleanup() {
  rm -f "$BODY_F" "$HDR_F" "$UUID_F"
  # Nunca deixar a stack parada, mesmo se o script falhar no meio (AC-T035.4).
  $COMPOSE start consolidado kafka >/dev/null 2>&1 || true
}
trap cleanup EXIT

# UUIDs novos: uuidgen, /proc ou, no Git Bash, uma lista gerada de uma vez pelo java (sem subshell
# que perca o estado: a fila fica num arquivo).
if ! command -v uuidgen >/dev/null && [[ ! -r /proc/sys/kernel/random/uuid ]]; then
  UUIDS_JAVA="$(mktemp -d)/Uuids.java"
  printf 'public class Uuids { public static void main(String[] a) { for (int i = 0; i < 80; i++) System.out.println(java.util.UUID.randomUUID()); } }\n' > "$UUIDS_JAVA"
  java "$UUIDS_JAVA" > "$UUID_F"
  rm -rf "$(dirname "$UUIDS_JAVA")"
fi
novo_uuid() {
  local linha
  if command -v uuidgen >/dev/null; then
    uuidgen | tr '[:upper:]' '[:lower:]'
  elif [[ -r /proc/sys/kernel/random/uuid ]]; then
    cat /proc/sys/kernel/random/uuid
  else
    linha="$(head -n 1 "$UUID_F")"
    tail -n +2 "$UUID_F" > "$UUID_F.novo" && mv "$UUID_F.novo" "$UUID_F"
    printf '%s\n' "$linha"
  fi
}

# Instante UTC de N dias atrás e data N dias antes de uma data (GNU e BSD/macOS).
if date -d '1 day ago' >/dev/null 2>&1; then
  inst_ago()  { date -u -d "$1 days ago" +%Y-%m-%dT%H:%M:%SZ; }
  data_menos() { date -d "$1 -$2 days" +%F; }
else
  inst_ago()  { date -u -v-"$1"d +%Y-%m-%dT%H:%M:%SZ; }
  data_menos() { date -j -v-"$2"d -f %F "$1" +%F; }
fi

# call METHOD URL TOKEN [KEY] [JSON] → imprime o status HTTP (000 se sem conexão)
call() {
  local method=$1 url=$2 token=$3 key=${4:-} data=${5:-}
  local args=(-sS -o "$BODY_F" -D "$HDR_F" -w '%{http_code}' -X "$method" --max-time 5
             -H "Authorization: Bearer $token")
  if [[ -n "$key" ]];  then args+=(-H "Idempotency-Key: $key"); fi
  if [[ -n "$data" ]]; then args+=(-H 'Content-Type: application/json' -d "$data"); fi
  curl "${args[@]}" "$url" || true
}

token() { java scripts/DevToken.java --merchant "$1" --scopes "$SCOPES" --ttl 3600; }
newkey() { novo_uuid; }

# post_lanc TOKEN TIPO VALOR DATA_OCORRENCIA_OU_VAZIO KEY
post_lanc() {
  local body
  if [[ -n "$4" ]]; then
    body=$(jq -nc --arg t "$2" --arg v "$3" --arg d "$4" '{tipo:$t,valor:$v,dataOcorrencia:$d}')
  else
    body=$(jq -nc --arg t "$2" --arg v "$3" '{tipo:$t,valor:$v}')
  fi
  call POST "$LANC_URL/api/v1/lancamentos" "$1" "$5" "$body" 2>/dev/null
}

# wait_json URL TOKEN JQ_EXPR [TIMEOUT_S] → espera a resposta satisfazer a expressão
wait_json() {
  local deadline=$((SECONDS + ${4:-30}))
  while (( SECONDS < deadline )); do
    if [[ "$(call GET "$1" "$2" 2>/dev/null)" == "200" ]] && jq -e "$3" "$BODY_F" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

# wait_health URL NOME [TIMEOUT_S] → espera o /actuator/health responder 200
wait_health() {
  local deadline=$((SECONDS + ${3:-120}))
  until curl -fsS --max-time 3 "$1/actuator/health" >/dev/null 2>&1; do
    (( SECONDS < deadline )) || die "$2 fora do ar ($1)"
    sleep 2
  done
}

outbox_pending() {
  curl -fsS --max-time 3 "$MGMT_LANC/actuator/prometheus" 2>/dev/null \
    | awk '/^outbox_pending_events[ {]/ { v=$NF } END { print (v=="" ? "n/d" : v+0) }'
}

MERCHANT_A="$(novo_uuid)"   # comerciantes novos a cada execução: base limpa
MERCHANT_B="$(novo_uuid)"
TOK_A="$(token "$MERCHANT_A")"
TOK_B="$(token "$MERCHANT_B")"

step "0/7 Saúde dos serviços"
wait_health "$MGMT_LANC" "Lançamentos"
wait_health "$MGMT_CONS" "Consolidado"
pass "Lançamentos e Consolidado saudáveis"
info "Comerciante A: $MERCHANT_A"

step "1/7 Registrar: crédito 1000.00, débito 250.00, crédito 80.00"
K1="$(newkey)"; K2="$(newkey)"; K3="$(newkey)"
expect "crédito 1000.00" 201 "$(post_lanc "$TOK_A" CREDITO 1000.00 "" "$K1")"; ID_C1="$(jq -r .id "$BODY_F")"
# O dia de hoje é o da resposta do serviço (fuso de negócio America/Sao_Paulo, 03 §4.1).
HOJE="$(jq -r .dataReferencia "$BODY_F")"
expect "débito 250.00"   201 "$(post_lanc "$TOK_A" DEBITO 250.00 "" "$K2")";   ID_D="$(jq -r .id "$BODY_F")"
expect "crédito 80.00"   201 "$(post_lanc "$TOK_A" CREDITO 80.00 "" "$K3")"
DIA_URL="$CONS_URL/api/v1/consolidado/$HOJE"
wait_json "$DIA_URL" "$TOK_A" '.qtdLancamentos >= 3' 60 || die "Consolidado não convergiu (3 lançamentos)"
expect "saldo do dia (1080 - 250)" "830.00" "$(num "$(jq -r .saldoDia "$BODY_F")")"

step "2/7 Idempotência (RF-007): mesma chave, mesma resposta; payload diferente é rejeitado"
expect "replay com a mesma chave" 201 "$(post_lanc "$TOK_A" CREDITO 1000.00 "" "$K1")"
expect "mesmo id devolvido" "$ID_C1" "$(jq -r .id "$BODY_F")"
if grep -qi '^idempotent-replayed: true' "$HDR_F"; then pass "header Idempotent-Replayed: true"; else die "header Idempotent-Replayed ausente"; fi
expect "mesma chave com payload diferente" 422 "$(post_lanc "$TOK_A" CREDITO 999.00 "" "$K1")"
sleep 2
call GET "$DIA_URL" "$TOK_A" >/dev/null
expect "nenhum lançamento duplicado (qtd)" 3 "$(jq -r .qtdLancamentos "$BODY_F")"

step "3/7 Estorno (RF-003): estornar o débito de 250.00"
expect "estorno" 201 "$(call POST "$LANC_URL/api/v1/lancamentos/$ID_D/estornos" "$TOK_A" "$(newkey)" '{}' 2>/dev/null)"
expect "estorno repetido (outra chave)" 409 "$(call POST "$LANC_URL/api/v1/lancamentos/$ID_D/estornos" "$TOK_A" "$(newkey)" '{}' 2>/dev/null)"
wait_json "$DIA_URL" "$TOK_A" '.qtdLancamentos >= 4' 30 || die "Consolidado não refletiu o estorno"
expect "estornos de débito" "250.00" "$(num "$(jq -r .totalEstornosDebito "$BODY_F")")"
expect "saldo do dia após estorno" "1080.00" "$(num "$(jq -r .saldoDia "$BODY_F")")"

step "4/7 Lançamento retroativo (RF-009): crédito 500.00 há 3 dias"
expect "crédito retroativo" 201 "$(post_lanc "$TOK_A" CREDITO 500.00 "$(inst_ago 3)" "$(newkey)")"
RANGE_URL="$CONS_URL/api/v1/consolidado?dataInicio=$(data_menos "$HOJE" 3)&dataFim=$HOJE"
wait_json "$RANGE_URL" "$TOK_A" '.dias[0].qtdLancamentos >= 1' 30 || die "Consolidado não refletiu o retroativo"
expect "dias no intervalo" 4 "$(jq -r '.dias | length' "$BODY_F")"
expect "saldo do dia D-3" "500.00" "$(num "$(jq -r '.dias[0].saldoDia' "$BODY_F")")"
expect "saldo acumulado hoje (500 + 1080)" "1580.00" "$(num "$(jq -r '.dias[-1].saldoAcumulado' "$BODY_F")")"

step "5/7 Isolamento entre comerciantes (RF-010)"
expect "B lê lançamento de A" 404 "$(call GET "$LANC_URL/api/v1/lancamentos/$ID_D" "$TOK_B" 2>/dev/null)"
call GET "$RANGE_URL" "$TOK_B" >/dev/null
expect "consolidado de B está vazio" 0 "$(jq -r '[.dias[].qtdLancamentos] | add' "$BODY_F")"

step "6/7 Consolidado PARADO (RNF-001): Lançamentos continua gravando"
info "docker compose stop consolidado"
$COMPOSE stop consolidado >/dev/null 2>&1 || die "Não foi possível parar o consolidado (profile apps ativo?)"
expect "consulta ao Consolidado falha (sem conexão)" 000 "$(call GET "$DIA_URL" "$TOK_A" 2>/dev/null)"
expect "POST /lancamentos segue 201" 201 "$(post_lanc "$TOK_A" DEBITO 10.00 "" "$(newkey)")"
info "docker compose start consolidado — o backlog do Kafka é consumido"
$COMPOSE start consolidado >/dev/null 2>&1
wait_health "$MGMT_CONS" "Consolidado" 120
wait_json "$DIA_URL" "$TOK_A" '.qtdLancamentos >= 5' 90 || die "Consolidado não convergiu após voltar"
pass "Consolidado convergiu sem perda: qtd=$(jq -r .qtdLancamentos "$BODY_F"), saldoDia=$(jq -r .saldoDia "$BODY_F")"

if $KAFKA_DOWN; then
  step "7/7 Kafka PARADO (RNF-007): a outbox absorve e depois drena"
  info "docker compose stop kafka"
  $COMPOSE stop kafka >/dev/null 2>&1 || die "Não foi possível parar o kafka"
  expect "POST /lancamentos segue 201" 201 "$(post_lanc "$TOK_A" CREDITO 20.00 "" "$(newkey)")"
  sleep 2
  PENDENTES="$(outbox_pending)"
  info "outbox_pending_events = $PENDENTES (esperado > 0 enquanto o Kafka está fora)"
  if [[ ! "$PENDENTES" =~ ^[0-9]+$ ]] || (( PENDENTES == 0 )); then
    die "A outbox não acumulou com o Kafka parado (pendentes: $PENDENTES)"
  fi
  pass "outbox acumulou $PENDENTES evento(s) com o Kafka parado"
  $COMPOSE start kafka >/dev/null 2>&1
  deadline=$((SECONDS + 180))
  until [[ "$(outbox_pending)" == "0" ]] || (( SECONDS >= deadline )); do sleep 2; done
  [[ "$(outbox_pending)" == "0" ]] || die "Outbox não drenou em 180 s"
  wait_json "$DIA_URL" "$TOK_A" '.qtdLancamentos >= 6' 90 || die "Consolidado não convergiu após o Kafka voltar"
  pass "Outbox drenada e Consolidado convergido (qtd=$(jq -r .qtdLancamentos "$BODY_F"))"
else
  step "7/7 (opcional) Kafka parado"
  info "Execute scripts/demo.sh --kafka-down para provar RNF-007."
fi

if $KAFKA_DOWN; then PROVAS=" e RNF-007"; else PROVAS=""; fi
printf '\n%sDemonstração concluída: RF-007, RF-003, RF-009, RF-010 e RNF-001%s%s\n' \
  "$BOLD$GREEN" "$PROVAS" "$RESET"
