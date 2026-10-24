#!/usr/bin/env bash
# Gate automático da tarefa (T-003, 05 §4).
#   BASE=<rev> scripts/verify-task.sh T-xxx      (BASE padrão: main)
# Roda todos os itens, imprime o resultado de cada um e grava
# docs/specs/reviews/T-xxx.verify.log. Exit 0 só se todos passarem.
set -u

if [ "${BASH_VERSINFO[0]}" -lt 4 ]; then
  echo "bash 4 ou superior é necessário (mapfile); no macOS: brew install bash" >&2
  exit 2
fi

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$AQUI/.." && pwd)"
# shellcheck source=lib/frontmatter.sh
. "$AQUI/lib/frontmatter.sh"
# shellcheck source=lib/pom.sh
. "$AQUI/lib/pom.sh"

TAREFA="${1:-}"
if ! [[ "$TAREFA" =~ ^T-[0-9]{3}$ ]]; then
  echo "uso: BASE=<rev> scripts/verify-task.sh T-xxx" >&2
  exit 2
fi
cd "$RAIZ" || exit 2

ARQ_TAREFA="docs/specs/tasks/$TAREFA.md"
if [ ! -f "$ARQ_TAREFA" ]; then
  echo "tarefa $TAREFA não encontrada: $ARQ_TAREFA" >&2
  exit 2
fi
BASE="${BASE:-main}"
if ! git rev-parse --verify -q "$BASE^{commit}" > /dev/null; then
  echo "BASE inválida: $BASE (use BASE=<branch base da tarefa>)" >&2
  exit 2
fi

LOG="docs/specs/reviews/$TAREFA.verify.log"
RESULTADOS=()
FALHOU=0

ok() { RESULTADOS+=("OK item $1 ($2): $3"); }
falha() { # $1 = número, $2 = nome, demais = linhas de detalhe
  local n="$1" nome="$2"; shift 2
  FALHOU=1
  RESULTADOS+=("FALHA item $n ($nome): $1")
  shift
  local d
  for d in "$@"; do RESULTADOS+=("    $d"); done
}

if ! NOMES_ALTERADOS="$(git -c core.quotepath=false diff --no-renames --name-only "$BASE"...HEAD)"; then
  echo "não foi possível calcular o diff contra BASE=$BASE (sem base de merge em comum?)" >&2
  exit 2
fi
ALTERADOS=()
if [ -n "$NOMES_ALTERADOS" ]; then
  mapfile -t ALTERADOS <<< "$NOMES_ALTERADOS"
fi
mapfile -t COVERS < <(fm_lista "$ARQ_TAREFA" covers)
mapfile -t EVID < <(fm_lista "$ARQ_TAREFA" evidence_only)
mapfile -t PERMITIDOS < <(fm_lista "$ARQ_TAREFA" allowed_paths)
mapfile -t DEPS_OK < <(fm_lista "$ARQ_TAREFA" allowed_new_dependencies)

# --- item 1: build
item1() {
  local tmp; tmp="$(mktemp)"
  if ./mvnw -q verify > "$tmp" 2>&1; then
    ok 1 build "./mvnw -q verify verde"
  else
    mapfile -t fim < <(tail -n 20 "$tmp")
    falha 1 build "./mvnw -q verify falhou (últimas linhas abaixo)" "${fim[@]}"
  fi
  rm -f "$tmp"
}

# --- item 2: escopo
glob_para_regex() {
  printf '%s' "$1" | sed -e 's/[][$^+?(){}|.]/\\&/g' -e 's/\*\+/.*/g'
}

item2() {
  local regex="docs/specs/reviews/.*|docs/specs/traceability\.md" p
  for p in "${PERMITIDOS[@]}"; do
    [ -n "$p" ] && regex="$regex|$(glob_para_regex "$p")"
  done
  local fora=()
  if [ "${#ALTERADOS[@]}" -gt 0 ]; then
    mapfile -t fora < <(printf '%s\n' "${ALTERADOS[@]}" | LC_ALL=C grep -Ev "^($regex)\$")
  fi
  if [ "${#fora[@]}" -eq 0 ]; then
    ok 2 escopo "${#ALTERADOS[@]} arquivo(s) do diff dentro de allowed_paths"
  else
    falha 2 escopo "arquivo(s) fora de allowed_paths:" "${fora[@]}"
  fi
}

# --- item 3: dependências
item3() {
  local novas=() c
  mapfile -t novas < <(LC_ALL=C comm -13 <(pom_coordenadas_em "$BASE") <(pom_coordenadas_em HEAD))
  local nao_autorizadas=()
  for c in "${novas[@]}"; do
    [ -z "$c" ] && continue
    printf '%s\n' "${DEPS_OK[@]}" | grep -qxF -- "$c" || nao_autorizadas+=("$c")
  done
  if [ "${#nao_autorizadas[@]}" -eq 0 ]; then
    ok 3 dependencias "nenhuma coordenada nova fora de allowed_new_dependencies"
  else
    falha 3 dependencias "coordenada(s) nova(s) fora de allowed_new_dependencies:" "${nao_autorizadas[@]}"
  fi
}

# --- item 4: rastreabilidade
item4() {
  local detalhes=() testes=() f precisa=() id
  for f in "${ALTERADOS[@]}"; do
    [[ "$f" =~ (^|/)src/test/.*\.java$ ]] && [ -f "$f" ] && testes+=("$f")
  done
  for id in "${COVERS[@]}"; do
    [ -z "$id" ] && continue
    printf '%s\n' "${EVID[@]}" | grep -qxF -- "$id" || precisa+=("$id")
  done
  local presentes=""
  if [ "${#testes[@]}" -gt 0 ]; then
    presentes="$(java "$AQUI/CheckTraceability.java" tags "${testes[@]}" | cut -f1 | sort -u)"
  fi
  for id in "${precisa[@]}"; do
    printf '%s\n' "$presentes" | grep -qxF -- "$id" \
      || detalhes+=("$id sem @Tag em teste criado ou alterado no diff")
  done
  if ! bash "$AQUI/check-traceability.sh" --check-file > /dev/null 2>&1; then
    detalhes+=("docs/specs/traceability.md desatualizado: rode scripts/check-traceability.sh --write")
  fi
  if [ "${#detalhes[@]}" -eq 0 ]; then
    ok 4 rastreabilidade "covers com @Tag no diff (ou evidence_only) e traceability.md em dia"
  else
    falha 4 rastreabilidade "${detalhes[0]}" "${detalhes[@]:1}"
  fi
}

# --- item 5: padrões proibidos nas linhas adicionadas de *.java
item5() {
  local achados=()
  mapfile -t achados < <(
    git -c core.quotepath=false diff --no-renames -U0 "$BASE"...HEAD -- '*.java' | awk '
      /^\+\+\+ / { arq = substr($0, 7); next }
      /^@@/ { s = $3; sub(/^\+/, "", s); split(s, p, ","); linha = p[1]; next }
      /^\+/ {
        txt = substr($0, 2)
        teste = (arq ~ /(^|\/)src\/test\//)
        if (txt ~ /TODO/) print arq ":" linha ": TODO"
        if (txt ~ /FIXME/) print arq ":" linha ": FIXME"
        if (txt ~ /System\.out/) print arq ":" linha ": System.out"
        if (txt ~ /printStackTrace/) print arq ":" linha ": printStackTrace"
        if (txt ~ /@Disabled/) print arq ":" linha ": @Disabled"
        if (teste && txt ~ /Thread\.sleep/) print arq ":" linha ": Thread.sleep em teste"
        linha++
      }'
  )
  if [ "${#achados[@]}" -eq 0 ]; then
    ok 5 padroes-proibidos "nenhum padrão proibido nas linhas adicionadas"
  else
    falha 5 padroes-proibidos "padrão proibido em linha adicionada:" "${achados[@]}"
  fi
}

# --- item 6: artefatos de "Saídas"
item6() {
  local ausentes=() a
  while IFS= read -r a; do
    [ -z "$a" ] && continue
    [ -e "$a" ] || ausentes+=("$a")
  done < <(fm_saidas "$ARQ_TAREFA")
  if [ "${#ausentes[@]}" -eq 0 ]; then
    ok 6 artefatos "todos os artefatos de Saídas existem"
  else
    falha 6 artefatos "artefato(s) de Saídas ausente(s):" "${ausentes[@]}"
  fi
}

item1; item2; item3; item4; item5; item6

{
  echo "tarefa: $TAREFA"
  echo "base: $BASE ($(git rev-parse --short "$BASE"))"
  echo "head: $(git rev-parse --short HEAD)"
  echo "data (UTC): $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '%s\n' "${RESULTADOS[@]}"
  if [ "$FALHOU" -eq 0 ]; then echo "RESULTADO: OK"; else echo "RESULTADO: FALHA"; fi
} > "$LOG"
printf '%s\n' "${RESULTADOS[@]}"
echo "log: $LOG"
if [ "$FALHOU" -eq 0 ]; then echo "RESULTADO: OK"; exit 0; fi
echo "RESULTADO: FALHA"
exit 1
