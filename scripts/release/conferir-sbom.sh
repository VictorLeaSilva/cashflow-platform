#!/usr/bin/env bash
# Confere o SBOM CycloneDX do release (T-044, Q-097).
#   scripts/release/conferir-sbom.sh <arquivo.json>
# Saídas: 0 (OK, com o resumo); 2 (causa em uma linha).
# Regras: bomFormat "CycloneDX", specVersion presente, 50 ou mais componentes, e entre eles
# spring-boot e postgresql (nomes dos componentes).
set -uo pipefail

die() { echo "conferir-sbom: $*" >&2; exit 2; }

arquivo="${1:-}"
[ -n "$arquivo" ] || die "informe o arquivo do SBOM"
[ -f "$arquivo" ] || die "arquivo inexistente: $arquivo"
command -v jq >/dev/null 2>&1 || die "jq não encontrado"
jq -e . "$arquivo" >/dev/null 2>&1 || die "$arquivo não é JSON válido"

[ "$(jq -r '.bomFormat // ""' "$arquivo")" = "CycloneDX" ] || die "bomFormat diferente de CycloneDX"
spec="$(jq -r '.specVersion // ""' "$arquivo")"
[ -n "$spec" ] || die "specVersion ausente"
total="$(jq '[.components[]?] | length' "$arquivo")"
[ "$total" -ge 50 ] || die "só $total componentes (mínimo 50)"
for nome in spring-boot postgresql; do
  jq -e --arg n "$nome" 'any(.components[]?; .name == $n)' "$arquivo" >/dev/null || die "componente $nome ausente"
done

echo "OK: CycloneDX $spec com $total componentes (spring-boot e postgresql presentes)"
