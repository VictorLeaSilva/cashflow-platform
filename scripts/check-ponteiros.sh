#!/usr/bin/env bash
# Confere os ponteiros para o código dos guias de conceito (T-045, Q-093).
# Nas seções "Onde está no código" e "Ligações" de cada guia, todo trecho entre crases que seja um ponteiro
# precisa existir de verdade:
#   `Classe#metodo`        Classe.java em src/main ou src/test, e o nome do método presente no arquivo
#   `Classe`               Classe.java em src/main ou src/test (nome começando por maiúscula, com alguma minúscula)
#   `pasta/arquivo.ext`    caminho existente a partir da raiz
# Qualquer outro trecho (SQL, nomes com espaço ou hífen, métricas) é ignorado, e fora dessas seções nada é conferido.
# Ponteiros são por NOME, nunca por número de linha, para não apodrecer.
#
# Uso: scripts/check-ponteiros.sh [--raiz DIR] [guia.md ...]
#   sem guias: confere docs/guia/*.md da raiz (padrão: a raiz do repositório)
# Saída: uma linha "FALTA arquivo:linha ponteiro (motivo)" por problema; exit 1 se houver alguma ou se não houver
# nenhum ponteiro (não passa vazio).
set -u

RAIZ=""
ARQS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --raiz) RAIZ="$2"; shift 2 ;;
    *) ARQS+=("$1"); shift ;;
  esac
done
if [ -z "$RAIZ" ]; then
  RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fi
if [ ${#ARQS[@]} -eq 0 ] && [ -d "$RAIZ/docs/guia" ]; then
  while IFS= read -r f; do ARQS+=("$f"); done < <(find "$RAIZ/docs/guia" -name '*.md' | LC_ALL=C sort)
fi

# arquivo:linha:tipo:ponteiro, uma linha por trecho entre crases nas seções conferidas
EXTRAIDOS="$(awk '
FNR == 1 { sec = 0; fence = 0 }
{
  linha = $0
  sub(/\r$/, "", linha)
  if (linha ~ /^```/) { fence = !fence; next }
  if (fence) next
  if (linha ~ /^#+[[:space:]]/) { sec = (linha ~ /nde está no código/ || linha ~ /Ligaç/) ? 1 : 0; next }
  if (!sec) next
  resto = linha
  while (match(resto, /`[^`]+`/)) {
    tok = substr(resto, RSTART + 1, RLENGTH - 2)
    resto = substr(resto, RSTART + RLENGTH)
    if (tok ~ /[[:space:]]/) continue
    if (tok ~ /^[A-Z][A-Za-z0-9]*#[A-Za-z_][A-Za-z0-9_]*$/) { print FILENAME ":" FNR ":M:" tok; continue }
    if (tok ~ /\// && tok ~ /\.[A-Za-z0-9]+$/) { print FILENAME ":" FNR ":P:" tok; continue }
    if (tok ~ /^[A-Z][A-Za-z0-9]*$/ && tok ~ /[a-z]/) { print FILENAME ":" FNR ":C:" tok; continue }
  }
}
' "${ARQS[@]}" 2>/dev/null)"

achar_classe() { find "$RAIZ" -name "$1.java" ! -path '*/target/*' -print -quit 2>/dev/null; }

TOTAL=0
FALHAS=0
if [ -n "$EXTRAIDOS" ]; then
  while IFS= read -r reg; do
    arq="${reg%%:*}"; resto="${reg#*:}"
    linha="${resto%%:*}"; resto="${resto#*:}"
    tipo="${resto%%:*}"; tok="${resto#*:}"
    TOTAL=$((TOTAL + 1))
    case "$tipo" in
      C)
        if [ -z "$(achar_classe "$tok")" ]; then
          echo "FALTA $arq:$linha \`$tok\` (classe não encontrada)"; FALHAS=$((FALHAS + 1))
        fi ;;
      M)
        classe="${tok%%#*}"; metodo="${tok#*#}"
        f="$(achar_classe "$classe")"
        if [ -z "$f" ]; then
          echo "FALTA $arq:$linha \`$tok\` (classe não encontrada)"; FALHAS=$((FALHAS + 1))
        elif ! grep -qw -- "$metodo" "$f"; then
          echo "FALTA $arq:$linha \`$tok\` (método não encontrado em $classe.java)"; FALHAS=$((FALHAS + 1))
        fi ;;
      P)
        if [ ! -e "$RAIZ/$tok" ]; then
          echo "FALTA $arq:$linha \`$tok\` (caminho não encontrado)"; FALHAS=$((FALHAS + 1))
        fi ;;
    esac
  done <<< "$EXTRAIDOS"
fi

if [ "$TOTAL" -eq 0 ]; then
  echo "FALTA nenhum ponteiro encontrado nas seções 'Onde está no código' e 'Ligações' (${#ARQS[@]} arquivo(s))"
  exit 1
fi
if [ "$FALHAS" -gt 0 ]; then
  exit 1
fi
echo "OK: $TOTAL ponteiros conferidos em ${#ARQS[@]} arquivo(s)"
exit 0
