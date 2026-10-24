#!/usr/bin/env bash
# Coordenadas Maven dos <dependency> de pom.xml (T-003). Biblioteca: use com `source`.

# pom_coordenadas: lê um pom.xml no stdin e imprime `groupId:artifactId`, um por linha.
pom_coordenadas() {
  tr '\n' ' ' | sed -E 's/<!--([^-]|-[^-])*-->//g' | awk -v RS='</dependency>' '
    {
      n = split($0, partes, "<dependency>")
      if (n < 2) next
      bloco = partes[n]
      gsub(/<exclusions>.*<\/exclusions>/, "", bloco)
      g = ""; a = ""
      if (match(bloco, /<groupId>[^<]*<\/groupId>/)) g = substr(bloco, RSTART + 9, RLENGTH - 19)
      if (match(bloco, /<artifactId>[^<]*<\/artifactId>/)) a = substr(bloco, RSTART + 12, RLENGTH - 25)
      gsub(/^[ \t]+|[ \t]+$/, "", g); gsub(/^[ \t]+|[ \t]+$/, "", a)
      if (g != "" && a != "") print g ":" a
    }
  '
}

# pom_coordenadas_em REV: coordenadas de todos os pom.xml da revisão REV (único, ordenado).
pom_coordenadas_em() {
  local rev="$1" caminho
  git ls-tree -r --name-only "$rev" | LC_ALL=C grep -E '(^|/)pom\.xml$' | while IFS= read -r caminho; do
    git show "$rev:$caminho" | pom_coordenadas
  done | LC_ALL=C sort -u
}
