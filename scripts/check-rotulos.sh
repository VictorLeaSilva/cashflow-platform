#!/usr/bin/env bash
# Confere o rótulo de cada bloco de comando bash e powershell dos Markdown (T-043, Q-092).
# O GitHub não mostra a linguagem do bloco, então quem lê precisa de um rótulo em negrito
# imediatamente acima de cada bloco copiável:
#   **bash (Linux, macOS e Git Bash)**   antes de um bloco ```bash
#   **PowerShell (Windows)**             antes de um bloco ```powershell
#   **bash e PowerShell**                antes de um bloco cujo comando é igual nos dois
# Quando os dois rótulos aparecem em sequência, o bash vem primeiro.
# Blocos de outras linguagens (text, yaml, mermaid, json, java...) não precisam de rótulo.
#
# Uso: scripts/check-rotulos.sh [--raiz DIR] [arquivo.md ...]
#   sem arquivos: confere README.md e docs/guia/**/*.md da raiz (padrão: a raiz do repositório)
# Saída: uma linha "FALTA arquivo:linha motivo" por problema; exit 1 se houver alguma.
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
if [ ${#ARQS[@]} -eq 0 ]; then
  [ -f "$RAIZ/README.md" ] && ARQS+=("$RAIZ/README.md")
  if [ -d "$RAIZ/docs/guia" ]; then
    while IFS= read -r f; do ARQS+=("$f"); done < <(find "$RAIZ/docs/guia" -name '*.md' | LC_ALL=C sort)
  fi
fi
if [ ${#ARQS[@]} -eq 0 ]; then
  echo "FALTA nenhum arquivo para conferir em $RAIZ" >&2
  exit 1
fi

SAIDA="$(awk '
function rotulo_bash(l)   { return index(l, "**bash (") == 1 }
function rotulo_ps(l)     { return index(l, "**PowerShell (") == 1 }
function rotulo_ambos(l)  { return index(l, "**bash e PowerShell**") == 1 }
FNR == 1 { dentro = 0; nb = 0; ultimo = ""; ultimo_nb = -1; closed_nb = -1; closed_lang = "" }
{
  linha = $0
  sub(/\r$/, "", linha)
  if (dentro) {
    if (linha ~ /^```[[:space:]]*$/) {
      dentro = 0; nb++; closed_nb = nb; closed_lang = lang_atual
      ultimo = "```"; ultimo_nb = nb
    }
    next
  }
  if (linha ~ /^```/) {
    lang = substr(linha, 4); sub(/[[:space:]].*$/, "", lang)
    nb++
    dentro = 1; lang_atual = lang
    if (lang == "bash" || lang == "powershell") {
      blocos++
      if (rotulo_ambos(ultimo)) {
        # vale para os dois
      } else if (lang == "bash" && rotulo_bash(ultimo)) {
        # rótulo certo; o bash não pode vir depois de um PowerShell colado a ele
        if (closed_nb == ultimo_nb - 1 && closed_lang == "powershell") {
          print "FALTA " FILENAME ":" FNR " ordem: o bloco bash deve vir antes do PowerShell"; falhas++
        }
      } else if (lang == "powershell" && rotulo_ps(ultimo)) {
        # rótulo certo
      } else if ((lang == "bash" && rotulo_ps(ultimo)) || (lang == "powershell" && rotulo_bash(ultimo))) {
        print "FALTA " FILENAME ":" FNR " rótulo do outro sistema acima do bloco " lang; falhas++
      } else {
        print "FALTA " FILENAME ":" FNR " bloco " lang " sem rótulo acima (use **bash (Linux, macOS e Git Bash)**, **PowerShell (Windows)** ou **bash e PowerShell**)"; falhas++
      }
    }
    next
  }
  if (linha !~ /^[[:space:]]*$/) { nb++; ultimo = linha; ultimo_nb = nb }
}
END { print "RESUMO " blocos + 0 " " falhas + 0 }
' "${ARQS[@]}")"

PROBLEMAS="$(printf '%s\n' "$SAIDA" | grep '^FALTA ')"
RESUMO="$(printf '%s\n' "$SAIDA" | grep '^RESUMO ')"
BLOCOS="$(printf '%s' "$RESUMO" | awk '{print $2}')"

if [ -n "$PROBLEMAS" ]; then
  printf '%s\n' "$PROBLEMAS"
  exit 1
fi
echo "OK: $BLOCOS blocos bash/powershell rotulados em ${#ARQS[@]} arquivo(s)"
exit 0
