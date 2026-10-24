#!/usr/bin/env bash
# Leitura do frontmatter das tarefas (T-003). Biblioteca: use com `source`.

# fm_lista ARQUIVO CHAVE: um item por linha, de lista inline ([a, b]) ou em bloco (- item).
# Comentários `# ...` são ignorados.
fm_lista() {
  awk -v chave="$2" '
    { sub(/\r$/, "") }
    /^---[ \t]*$/ { n++; if (n >= 2) exit; next }
    n != 1 { next }
    {
      linha = $0
      sub(/[ \t]+#.*$/, "", linha)
      sub(/[ \t]+$/, "", linha)
    }
    index(linha, chave ":") == 1 {
      resto = substr(linha, length(chave) + 2)
      gsub(/^[ \t]+|[ \t]+$/, "", resto)
      emLista = (resto == "" || substr(resto, 1, 1) == "#")
      if (substr(resto, 1, 1) == "[") {
        gsub(/^\[|\]$/, "", resto)
        m = split(resto, partes, ",")
        for (k = 1; k <= m; k++) {
          gsub(/^[ \t]+|[ \t]+$/, "", partes[k])
          if (partes[k] != "") print partes[k]
        }
      }
      next
    }
    emLista {
      if (linha ~ /^[ \t]*-[ \t]+/) {
        sub(/^[ \t]*-[ \t]+/, "", linha)
        gsub(/^[ \t]+|[ \t]+$/, "", linha)
        if (linha != "") print linha
      } else if (linha ~ /^[^ \t]/) {
        emLista = 0
      }
    }
  ' "$1"
}

# fm_saidas ARQUIVO: caminhos entre crases da seção "Saídas" que parecem arquivos
# (têm `/`, extensão e nenhum caractere de glob).
fm_saidas() {
  awk '
    { sub(/\r$/, "") }
    /^## / { dentro = ($0 ~ /^## Sa[^ ]*das/); next }
    dentro {
      resto = $0
      while (match(resto, /`[^`]+`/)) {
        print substr(resto, RSTART + 1, RLENGTH - 2)
        resto = substr(resto, RSTART + RLENGTH)
      }
    }
  ' "$1" | LC_ALL=C grep -E '/' | LC_ALL=C grep -E '\.[A-Za-z0-9]+$' | LC_ALL=C grep -Ev '[*?{}<> ]'
}
