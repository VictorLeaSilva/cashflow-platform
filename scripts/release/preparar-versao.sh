#!/usr/bin/env bash
# Prepara a versão do release no runner (T-044, Q-096): valida a tag e troca X.Y.Z-SNAPSHOT por X.Y.Z
# nos pom.xml rastreados pelo Git. Não faz commit.
#   scripts/release/preparar-versao.sh vX.Y.Z [--raiz DIR]
# Saídas: 0 (imprime a versão, sem o v); 2 (causa em uma linha, nenhum arquivo alterado).
set -euo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$AQUI/../.." && pwd)"

die() { echo "preparar-versao: $*" >&2; exit 2; }

tag="${1:-}"
[ -n "$tag" ] || die "informe a tag (vX.Y.Z)"
shift
while [ $# -gt 0 ]; do
  case "$1" in
    --raiz) RAIZ="$(cd "${2:?--raiz precisa de um diretório}" && pwd)"; shift 2 ;;
    *) die "argumento desconhecido: $1" ;;
  esac
done

[[ "$tag" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]] || die "tag '$tag' inválida: use vX.Y.Z"
versao="${BASH_REMATCH[1]}"

[ -f "$RAIZ/pom.xml" ] || die "pom.xml não encontrado em $RAIZ"
# A versão do projeto é o primeiro <version> depois do </parent>.
atual="$(awk '/<\/parent>/ { p = 1; next } p && /<version>/ { gsub(/.*<version>|<\/version>.*/, ""); print; exit }' "$RAIZ/pom.xml" | tr -d '\r')"
[ -n "$atual" ] || die "não achei a versão do projeto no pom.xml"
[[ "$atual" == *-SNAPSHOT ]] || die "a versão do pom.xml ($atual) não termina em -SNAPSHOT"
[ "${atual%-SNAPSHOT}" = "$versao" ] || die "a tag $tag não coincide com a versão do pom.xml ($atual)"
[ -f "$RAIZ/docs/release/notas-$versao.md" ] || die "falta docs/release/notas-$versao.md"

padrao="${atual//./\\.}"
while IFS= read -r pom; do
  [ -n "$pom" ] || continue
  sed -i "s#<version>${padrao}</version>#<version>${versao}</version>#" "$RAIZ/$pom"
done < <(git -C "$RAIZ" ls-files -- '*pom.xml')

echo "$versao"
