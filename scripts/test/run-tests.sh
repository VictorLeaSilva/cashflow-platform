#!/usr/bin/env bash
# Testes dos scripts do harness (T-003, RNF-008): bash puro, sem bats (Q-058).
# Cada cenário cria um repositório Git temporário com fixtures, copia os scripts
# reais para dentro dele e confere o exit code e a mensagem.
# Uso: scripts/test/run-tests.sh
set -u

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS_SRC="$(cd "$AQUI/.." && pwd)"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT

FALHAS=0
TOTAL=0
CENARIO=""
SAIDA=""
CODIGO=0

export GIT_AUTHOR_NAME=teste GIT_AUTHOR_EMAIL=teste@example.com
export GIT_COMMITTER_NAME=teste GIT_COMMITTER_EMAIL=teste@example.com
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null

cenario() { CENARIO="$1"; echo "- $1"; }

falhou() {
  FALHAS=$((FALHAS + 1))
  echo "    FALHOU [$CENARIO]: $1"
  [ -n "$SAIDA" ] && printf '%s\n' "$SAIDA" | sed 's/^/      | /'
}

confere() { TOTAL=$((TOTAL + 1)); }

espera_codigo() { # $1 = esperado (0 ou nz)
  confere
  if [ "$1" = 0 ] && [ "$CODIGO" -ne 0 ]; then falhou "esperava exit 0, veio $CODIGO"; fi
  if [ "$1" = nz ] && [ "$CODIGO" -eq 0 ]; then falhou "esperava exit diferente de 0"; fi
}

espera_texto() {
  confere
  if ! printf '%s\n' "$SAIDA" | grep -qF -- "$1"; then falhou "saída sem o texto: $1"; fi
}

espera_sem_texto() {
  confere
  if printf '%s\n' "$SAIDA" | grep -qF -- "$1"; then falhou "saída não devia conter: $1"; fi
}

espera_arquivo() {
  confere
  [ -f "$1" ] || falhou "arquivo ausente: $1"
}

# ---------------------------------------------------------------- fixtures

# Variáveis que um cenário pode definir antes de mk_repo.
T_COVERS="[RF-001]"
T_EVID="[]"
T_DEPS="[]"
T_ALLOWED="  - app/src/main/**   # comentário ignorado
  - app/src/test/**
  - docs/specs/tasks/T-900.md"
T_SAIDAS='- `app/src/main/java/Foo.java`'

reseta_tarefa() {
  T_COVERS="[RF-001]"
  T_EVID="[]"
  T_DEPS="[]"
  T_ALLOWED="  - app/src/main/**   # comentário ignorado
  - app/src/test/**
  - docs/specs/tasks/T-900.md"
  T_SAIDAS='- `app/src/main/java/Foo.java`'
}

escreve_tarefa() {
  mkdir -p "$R/docs/specs/tasks"
  cat > "$R/docs/specs/tasks/T-900.md" <<EOF
---
id: T-900
title: Tarefa de teste
depends_on: []
covers: $T_COVERS
evidence_only: $T_EVID          # comentário no frontmatter
allowed_paths:
$T_ALLOWED
allowed_new_dependencies: $T_DEPS
---
## Objetivo
Fixture.
## Saídas
$T_SAIDAS
- um caminho \`sem/extensao\` e um glob \`app/**/*.java\` não são artefatos.
## Critérios de aceite
- AC-T900.1
EOF
}

escreve_requisitos() {
  mkdir -p "$R/docs/specs"
  cat > "$R/docs/specs/02-requirements.md" <<'EOF'
# 02 · Requisitos
### RF-001 · Registrar crédito
### RF-002 · Registrar débito
| ID | Requisito |
|---|---|
| **RNF-001** | Isolamento |
| **RNF-009** | Operabilidade local |
EOF
}

pom() { # $1 = arquivo; demais = coordenadas g:a
  local arq="$1"; shift
  {
    echo '<project>'
    echo '  <dependencies>'
    local c
    for c in "$@"; do
      echo '    <dependency>'
      echo "      <groupId>${c%%:*}</groupId>"
      echo "      <artifactId>${c##*:}</artifactId>"
      echo '    </dependency>'
    done
    echo '  </dependencies>'
    echo '</project>'
  } > "$arq"
}

java_main() { # $1 = classe ; conteúdo extra em $2
  mkdir -p "$R/app/src/main/java"
  printf 'public class %s {\n%s\n}\n' "$1" "${2:-}" > "$R/app/src/main/java/$1.java"
}

java_teste() { # $1 = classe ; $2 = anotação de classe ; $3 = corpo
  mkdir -p "$R/app/src/test/java"
  printf 'import org.junit.jupiter.api.Tag;\n%s\nclass %s {\n%s\n}\n' "$2" "$1" "$3" \
    > "$R/app/src/test/java/$1.java"
}

regenera() { (cd "$R" && bash scripts/check-traceability.sh --write >/dev/null 2>&1); }

commita() {
  [ "${SEM_REGERAR:-0}" = 1 ] || regenera
  (cd "$R" && git add -A && git commit -q -m "$1")
}

mk_repo() {
  R="$TMP_ROOT/repo-$RANDOM-$RANDOM"
  mkdir -p "$R/scripts"
  (cd "$R" && git init -q -b main && git config core.autocrlf false && git config core.quotepath true)
  cp "$SCRIPTS_SRC/verify-task.sh" "$SCRIPTS_SRC/check-traceability.sh" \
     "$SCRIPTS_SRC/CheckTraceability.java" "$R/scripts/"
  cp -r "$SCRIPTS_SRC/lib" "$R/scripts/lib"
  printf '#!/bin/sh\nexit 0\n' > "$R/mvnw"
  chmod +x "$R/mvnw"
  escreve_requisitos
  escreve_tarefa
  pom "$R/app-pom.xml" "org.springframework.boot:spring-boot-starter"
  mkdir -p "$R/app"
  mv "$R/app-pom.xml" "$R/app/pom.xml"
  java_main Foo
  mkdir -p "$R/docs/specs/reviews"
  : > "$R/docs/specs/reviews/.gitkeep"
  commita "base"
  (cd "$R" && git checkout -q -b task/T-900)
}

roda_verify() { # BASE padrão main
  SAIDA="$(cd "$R" && BASE="${BASE_TESTE:-main}" bash scripts/verify-task.sh "${1:-T-900}" 2>&1)"
  CODIGO=$?
}

roda_trace() {
  SAIDA="$(cd "$R" && bash scripts/check-traceability.sh "$@" 2>&1)"
  CODIGO=$?
}

# ---------------------------------------------------------------- AC-T003.2

cenario "sucesso: exit 0 e log com o resultado de cada item"
reseta_tarefa; mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
java_main Foo '  // ok'
commita "ok"
roda_verify
espera_codigo 0
for i in 1 2 3 4 5 6; do espera_texto "OK item $i"; done
espera_arquivo "$R/docs/specs/reviews/T-900.verify.log"
confere
grep -q "OK item 6" "$R/docs/specs/reviews/T-900.verify.log" || falhou "log sem o item 6"
confere
grep -q "RESULTADO: OK" "$R/docs/specs/reviews/T-900.verify.log" || falhou "log sem o resultado final"

cenario "id da tarefa inválido ou tarefa inexistente: exit diferente de 0"
reseta_tarefa; mk_repo
roda_verify "xyz"; espera_codigo nz; espera_texto "uso"
roda_verify "T-999"; espera_codigo nz; espera_texto "T-999"

# ---------------------------------------------------------------- AC-T003.1

cenario "item 1: build vermelho"
reseta_tarefa; mk_repo
printf '#!/bin/sh\necho quebrou\nexit 1\n' > "$R/mvnw"
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
commita "build quebrado"
roda_verify
espera_codigo nz
espera_texto "FALHA item 1"

cenario "item 2: arquivo fora de allowed_paths"
reseta_tarefa; mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
mkdir -p "$R/outro"; echo x > "$R/outro/intruso.txt"
commita "fora de escopo"
roda_verify
espera_codigo nz
espera_texto "FALHA item 2"
espera_texto "outro/intruso.txt"
espera_texto "OK item 6"

cenario "item 2: glob com ** casa qualquer profundidade; reviews e traceability sempre permitidos"
reseta_tarefa; mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
mkdir -p "$R/app/src/main/a/b/c"; echo x > "$R/app/src/main/a/b/c/d.txt"
echo x > "$R/docs/specs/reviews/T-900.evidence.md"
commita "dentro do escopo"
roda_verify
espera_codigo 0
espera_texto "OK item 2"

cenario "item 2: caminho com acento (core.quotepath)"
reseta_tarefa
T_ALLOWED="  - app/src/main/**
  - app/src/test/**
  - docs/specs/tasks/T-900.md
  - docs/ação.md"
mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
echo x > "$R/docs/ação.md"
commita "acento"
roda_verify
espera_codigo 0
espera_texto "OK item 2"

cenario "item 3: dependência nova não autorizada"
reseta_tarefa; mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
pom "$R/app/pom.xml" "org.springframework.boot:spring-boot-starter" "com.exemplo:lib-nova"
commita "dependência"
roda_verify
espera_codigo nz
espera_texto "FALHA item 3"
espera_texto "com.exemplo:lib-nova"

cenario "item 3: dependência nova autorizada passa; dependência existente não conta"
reseta_tarefa
T_ALLOWED="  - app/pom.xml
  - app/src/main/**
  - app/src/test/**
  - docs/specs/tasks/T-900.md"
T_DEPS="
  - com.exemplo:lib-nova"
mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
pom "$R/app/pom.xml" "org.springframework.boot:spring-boot-starter" "com.exemplo:lib-nova"
commita "dependência autorizada"
roda_verify
espera_codigo 0
espera_texto "OK item 3"

cenario "item 3: dependência nova em dependencyManagement também conta"
reseta_tarefa
T_ALLOWED="  - app/pom.xml
  - app/src/main/**
  - app/src/test/**
  - docs/specs/tasks/T-900.md"
mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
cat > "$R/app/pom.xml" <<'XML'
<project>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.exemplo</groupId>
        <artifactId>gerenciada</artifactId>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
  </dependencies>
</project>
XML
commita "dependencyManagement"
roda_verify
espera_codigo nz
espera_texto "FALHA item 3"
espera_texto "com.exemplo:gerenciada"

cenario "item 3: exclusions e dependência comentada no XML não contam; dependência movida entre módulos não é nova"
reseta_tarefa
T_ALLOWED="  - app/pom.xml
  - other/pom.xml
  - app/src/main/**
  - app/src/test/**
  - docs/specs/tasks/T-900.md"
T_DEPS="
  - com.exemplo:com-exclusao"
mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
mkdir -p "$R/other"
cat > "$R/app/pom.xml" <<'XML'
<project>
  <dependencies>
    <dependency>
      <groupId>com.exemplo</groupId>
      <artifactId>com-exclusao</artifactId>
      <exclusions>
        <exclusion>
          <groupId>com.excluida</groupId>
          <artifactId>lib</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <!--
    <dependency>
      <groupId>com.comentada</groupId>
      <artifactId>lib</artifactId>
    </dependency>
    -->
  </dependencies>
</project>
XML
pom "$R/other/pom.xml" "org.springframework.boot:spring-boot-starter"
commita "exclusions, comentário e movimentação"
roda_verify
espera_codigo 0
espera_texto "OK item 3"

cenario "item 2: * simples também casa qualquer sequência, inclusive /"
reseta_tarefa
T_ALLOWED="  - app/src/main/**
  - app/src/test/**
  - docs/specs/tasks/T-900.md
  - extras/*.txt"
mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
mkdir -p "$R/extras/sub"; echo x > "$R/extras/a.txt"; echo x > "$R/extras/sub/b.txt"
commita "glob simples"
roda_verify
espera_codigo 0
espera_texto "OK item 2"

cenario "BASE sem base de merge: o gate falha em vez de passar em vazio"
reseta_tarefa; mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
java_main Foo '  // TODO'
commita "com problema"
(cd "$R" && git checkout -q --orphan outra && git rm -rf -q . && echo x > x.txt && git add x.txt && git commit -q -m orfao && git checkout -q task/T-900)
BASE_TESTE=outra roda_verify
espera_codigo nz
espera_texto "não foi possível calcular o diff"
unset BASE_TESTE

cenario "item 4: covers sem @Tag em teste do diff"
reseta_tarefa; mk_repo
java_teste FooTest '' '  void a() {}'
commita "sem tag"
roda_verify
espera_codigo nz
espera_texto "FALHA item 4"
espera_texto "RF-001"

cenario "item 4: @Tag de outro requisito não cobre"
reseta_tarefa; mk_repo
java_teste FooTest '@Tag("RF-002")' '  void a() {}'
commita "tag errada"
roda_verify
espera_codigo nz
espera_texto "FALHA item 4"

cenario "item 4: @Tag no método e @Tag(value=...) cobrem"
reseta_tarefa; mk_repo
java_teste FooTest '' '  @Tag(value = "RF-001")
  void a() {}'
commita "tag no método"
roda_verify
espera_codigo 0

cenario "item 4: covers em evidence_only dispensa @Tag"
reseta_tarefa
T_COVERS="[RF-001, RNF-009]"
T_EVID="[RNF-009]"
mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
commita "evidência"
roda_verify
espera_codigo 0

cenario "item 4: traceability.md desatualizado"
reseta_tarefa; mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
SEM_REGERAR=1 commita "sem regenerar"
roda_verify
espera_codigo nz
espera_texto "FALHA item 4"
espera_texto "traceability.md"
unset SEM_REGERAR

cenario "item 5: TODO adicionado em .java"
reseta_tarefa; mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
java_main Foo '  // TODO resolver'
commita "todo"
roda_verify
espera_codigo nz
espera_texto "FALHA item 5"
espera_texto "TODO"

cenario "item 5: FIXME, System.out, printStackTrace e @Disabled adicionados"
reseta_tarefa; mk_repo
java_teste FooTest '@Tag("RF-001")' '  @Disabled void a() {}'
java_main Foo '  void f() { System.out.println(1); new Exception().printStackTrace(); } // FIXME'
commita "proibidos"
roda_verify
espera_codigo nz
for p in FIXME System.out printStackTrace @Disabled; do espera_texto "$p"; done

cenario "item 5: Thread.sleep adicionado em teste"
reseta_tarefa; mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() throws Exception { Thread.sleep(10); }'
commita "sleep"
roda_verify
espera_codigo nz
espera_texto "FALHA item 5"
espera_texto "Thread.sleep"

cenario "item 5: Thread.sleep em main e TODO fora de .java não reprovam"
reseta_tarefa; mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
java_main Foo '  void f() throws Exception { Thread.sleep(10); }'
echo "TODO no markdown" > "$R/app/src/main/nota.md"
commita "permitidos"
roda_verify
espera_codigo 0
espera_texto "OK item 5"

cenario "item 6: artefato de Saídas ausente"
reseta_tarefa
T_SAIDAS='- `app/src/main/java/Foo.java`
- `app/src/main/java/Ausente.java`'
mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
commita "sem artefato"
roda_verify
espera_codigo nz
espera_texto "FALHA item 6"
espera_texto "Ausente.java"
espera_sem_texto "sem/extensao"

cenario "falhas independentes: todos os itens rodam e o log registra cada um"
reseta_tarefa; mk_repo
java_main Foo '  // TODO'
mkdir -p "$R/outro"; echo x > "$R/outro/a.txt"
commita "várias falhas"
roda_verify
espera_codigo nz
espera_texto "FALHA item 2"
espera_texto "FALHA item 4"
espera_texto "FALHA item 5"
confere
grep -q "RESULTADO: FALHA" "$R/docs/specs/reviews/T-900.verify.log" || falhou "log sem resultado FALHA"

# ---------------------------------------------------------------- AC-T003.3

cenario "check-traceability: lista obrigatórios sem teste e falha"
reseta_tarefa
T_COVERS="[RF-001, RF-002, RNF-001, RNF-009]"
mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
commita "um teste"
roda_trace
espera_codigo nz
espera_texto "RF-002"
espera_texto "RNF-001"
espera_sem_texto "FALTA RF-001"

cenario "check-traceability: obrigatórios todos cobertos passam; evidência aparece com o arquivo"
reseta_tarefa
T_COVERS="[RF-001, RF-002, RNF-001, RNF-009]"
T_EVID="[RNF-001]"
mk_repo
java_teste FooTest '@Tag("RF-001")' '  void a() {}'
java_teste BarTest '@Tag("RF-002")' '  void b() {}'
echo ev > "$R/docs/specs/reviews/T-900.evidence.md"
commita "tudo"
roda_trace
espera_codigo 0
espera_texto "RNF-009"
espera_texto "docs/specs/reviews/T-900.evidence.md"

cenario "check-traceability: --write gera a matriz com classe#método e --check-file confere"
reseta_tarefa; mk_repo
java_teste FooTest '' '  @Tag("RF-001") void a() {}
  @Tag("RF-002") void b() {}'
commita "métodos"
confere
grep -q 'FooTest#a' "$R/docs/specs/traceability.md" || falhou "matriz sem FooTest#a"
roda_trace --check-file
espera_codigo 0
echo "lixo" >> "$R/docs/specs/traceability.md"
roda_trace --check-file
espera_codigo nz
espera_texto "traceability.md"

cenario "check-traceability: @Tag em comentário e em string não conta; @Tags aninhado conta"
reseta_tarefa; mk_repo
java_teste FooTest '' '  // @Tag("RF-001")
  String s = "@Tag(\"RF-001\")";
  @org.junit.jupiter.api.Tags({@Tag("RF-002"), @Tag("RNF-001")}) void c() {}'
commita "ruído"
confere
grep -q 'FooTest#c' "$R/docs/specs/traceability.md" || falhou "matriz sem FooTest#c"
roda_trace
espera_texto "RF-001"
confere
grep -qF "| RF-001 | FALTA | 0 |" "$R/docs/specs/traceability.md" || falhou "RF-001 devia ter 0 testes (tag em comentário e string não conta)"

# ---------------------------------------------------------------- resumo

echo
if [ "$FALHAS" -eq 0 ]; then
  echo "OK: $TOTAL conferências, 0 falhas"
  exit 0
fi
echo "FALHOU: $FALHAS de $TOTAL conferências"
exit 1
