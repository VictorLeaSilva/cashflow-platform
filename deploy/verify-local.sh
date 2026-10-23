#!/usr/bin/env bash
# Verifica a infraestrutura local (T-004, T-006): serviços saudáveis, isolamento entre bancos e tópicos.
# Uso (na raiz, com a stack no ar): deploy/verify-local.sh
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1 # Git Bash (Windows): não converter caminhos do contêiner (/opt/...)

COMPOSE=(docker compose --env-file "${ENV_FILE:-.env}" -f deploy/docker-compose.yml)
FAILS=0
pass() { printf ' ✔ %s\n' "$*"; }
fail() { printf ' ✘ %s\n' "$*" >&2; FAILS=$((FAILS + 1)); }

# Conecta pela rede (127.0.0.1) para exigir senha; a senha é lida do ambiente do contêiner.
connect() { # role, variável da senha, banco
  "${COMPOSE[@]}" exec -T postgres sh -c \
    "PGPASSWORD=\"\$$2\" psql -h 127.0.0.1 -U $1 -d $3 -tAc 'select current_user' 2>&1"
}
expect_ok() {
  local out; out="$(connect "$1" "$2" "$3")" || true
  [[ "$out" == "$1" ]] && pass "$1 conecta em $3" || fail "$1 deveria conectar em $3: $out"
}
expect_denied() {
  local out; out="$(connect "$1" "$2" "$3")" || true
  [[ "$out" == *"permission denied for database"* ]] \
    && pass "$1 NÃO conecta em $3" || fail "$1 não deveria conectar em $3: $out"
}

echo "== AC-T004.1 serviços saudáveis"
for svc in postgres kafka; do
  status="$("${COMPOSE[@]}" ps --format '{{.Health}}' "$svc")"
  [[ "$status" == "healthy" ]] && pass "$svc healthy" || fail "$svc: '$status'"
done
# O "up --wait" libera o kafka-topicos ainda em execução, e um contêiner rodando também tem
# ExitCode 0: espera o término (até 120 s) e exige o estado "exited" com exit 0.
timeout 120 "${COMPOSE[@]}" wait kafka-topicos >/dev/null 2>&1 || true
topicos="$("${COMPOSE[@]}" ps -a --format '{{.State}} {{.ExitCode}}' kafka-topicos)"
[[ "$topicos" == "exited 0" ]] && pass "kafka-topicos terminou com exit 0" || fail "kafka-topicos: '$topicos'"

echo "== AC-T004.2 isolamento entre bancos"
expect_ok lancamentos_app LANCAMENTOS_APP_PASSWORD lancamentos
expect_ok lancamentos_owner LANCAMENTOS_OWNER_PASSWORD lancamentos
expect_ok consolidado_app CONSOLIDADO_APP_PASSWORD consolidado
expect_ok consolidado_owner CONSOLIDADO_OWNER_PASSWORD consolidado
expect_denied consolidado_app CONSOLIDADO_APP_PASSWORD lancamentos
expect_denied consolidado_owner CONSOLIDADO_OWNER_PASSWORD lancamentos
expect_denied lancamentos_app LANCAMENTOS_APP_PASSWORD consolidado
expect_denied lancamentos_owner LANCAMENTOS_OWNER_PASSWORD consolidado
expect_denied lancamentos_app LANCAMENTOS_APP_PASSWORD postgres
expect_denied consolidado_app CONSOLIDADO_APP_PASSWORD postgres

echo "== AC-T004.3 tópico e DLT com 3 partições"
for topic in cashflow.lancamentos.v1 cashflow.lancamentos.v1.DLT; do
  parts="$("${COMPOSE[@]}" exec -T kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server kafka:29092 --describe --topic "$topic" 2>/dev/null \
    | grep -o 'PartitionCount: *[0-9]*' | grep -o '[0-9]*$' || true)"
  [[ "$parts" == "3" ]] && pass "$topic com 3 partições" || fail "$topic: partições '$parts'"
done

if (( FAILS > 0 )); then
  echo "$FAILS verificação(ões) falharam." >&2
  exit 1
fi
echo "Infraestrutura local OK."
