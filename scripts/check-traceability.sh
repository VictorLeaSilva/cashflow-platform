#!/usr/bin/env bash
# Matriz de rastreabilidade (T-003, 02 §3): requisito -> testes (@Tag) ou evidência -> tarefas.
#   scripts/check-traceability.sh              lista obrigatórios sem prova (exit 1 se houver)
#   scripts/check-traceability.sh --write      regenera docs/specs/traceability.md
#   scripts/check-traceability.sh --check-file exit 1 se o arquivo versionado difere do gerado
set -u

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$AQUI/.." && pwd)"
MATRIZ="docs/specs/traceability.md"
cd "$RAIZ" || exit 2

arquivos_de_teste() {
  git -c core.quotepath=false ls-files --cached --others --exclude-standard -- '*.java' \
    | LC_ALL=C grep -E '(^|/)src/test/' | LC_ALL=C sort -u
}

case "${1:-}" in
  "")
    arquivos_de_teste | java "$AQUI/CheckTraceability.java" report "$RAIZ"
    ;;
  --write)
    tmp="$(mktemp)"
    if arquivos_de_teste | java "$AQUI/CheckTraceability.java" matrix "$RAIZ" > "$tmp"; then
      mkdir -p "$(dirname "$MATRIZ")"
      mv "$tmp" "$MATRIZ"
      echo "gerado: $MATRIZ"
    else
      rm -f "$tmp"
      echo "falha ao gerar $MATRIZ" >&2
      exit 1
    fi
    ;;
  --check-file)
    tmp="$(mktemp)"
    arquivos_de_teste | java "$AQUI/CheckTraceability.java" matrix "$RAIZ" > "$tmp" || {
      rm -f "$tmp"; echo "falha ao gerar a matriz" >&2; exit 1; }
    if [ -f "$MATRIZ" ] && cmp -s "$tmp" "$MATRIZ"; then
      rm -f "$tmp"
      echo "$MATRIZ está em dia"
    else
      rm -f "$tmp"
      echo "$MATRIZ desatualizado: rode scripts/check-traceability.sh --write e commite" >&2
      exit 1
    fi
    ;;
  *)
    echo "uso: scripts/check-traceability.sh [--write | --check-file]" >&2
    exit 2
    ;;
esac
