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

# ---------------------------------------------------------------- AC-T043.5

roda_rotulos() { # $@ = argumentos do check-rotulos.sh
  SAIDA="$(bash "$SCRIPTS_SRC/check-rotulos.sh" "$@" 2>&1)"; CODIGO=$?
}

md_rotulos() { # $1 = nome do arquivo; o conteúdo vem da entrada padrão
  mkdir -p "$TMP_ROOT/rotulos"
  cat > "$TMP_ROOT/rotulos/$1"
  MD_ARQ="$TMP_ROOT/rotulos/$1"
}

cenario "check-rotulos: par bash e PowerShell rotulado, na ordem, passa"
md_rotulos par.md <<'MD'
**bash (Linux, macOS e Git Bash)**

```bash
ls
```

**PowerShell (Windows)**

```powershell
Get-ChildItem
```
MD
roda_rotulos "$MD_ARQ"
espera_codigo 0
espera_texto "OK"

cenario "check-rotulos: bloco bash sem rótulo reprova e aponta a linha"
md_rotulos sem.md <<'MD'
Texto antes.

```bash
ls
```
MD
roda_rotulos "$MD_ARQ"
espera_codigo nz
espera_texto "FALTA"
espera_texto "sem.md:3"

cenario "check-rotulos: rótulo do outro sistema reprova"
md_rotulos trocado.md <<'MD'
**bash (Linux, macOS e Git Bash)**

```powershell
Get-ChildItem
```
MD
roda_rotulos "$MD_ARQ"
espera_codigo nz
espera_texto "outro sistema"

cenario "check-rotulos: PowerShell antes de bash reprova (ordem)"
md_rotulos ordem.md <<'MD'
**PowerShell (Windows)**

```powershell
Get-ChildItem
```

**bash (Linux, macOS e Git Bash)**

```bash
ls
```
MD
roda_rotulos "$MD_ARQ"
espera_codigo nz
espera_texto "ordem"

cenario "check-rotulos: rótulo único 'bash e PowerShell' vale para um bloco; linhas em branco entre rótulo e bloco são aceitas"
md_rotulos unico.md <<'MD'
**bash e PowerShell**


```bash
docker compose version
```
MD
roda_rotulos "$MD_ARQ"
espera_codigo 0

cenario "check-rotulos: blocos de outras linguagens não precisam de rótulo"
md_rotulos outros.md <<'MD'
```text
saída qualquer
```

```yaml
a: b
```

```mermaid
flowchart LR
  a --> b
```
MD
roda_rotulos "$MD_ARQ"
espera_codigo 0

cenario "check-rotulos: sem argumentos confere README.md e docs/guia/ da raiz indicada"
mkdir -p "$TMP_ROOT/raiz/docs/guia"
printf '%s\n' '**bash (Linux, macOS e Git Bash)**' '' '```bash' 'ls' '```' > "$TMP_ROOT/raiz/README.md"
printf '%s\n' '```powershell' 'dir' '```' > "$TMP_ROOT/raiz/docs/guia/x.md"
roda_rotulos --raiz "$TMP_ROOT/raiz"
espera_codigo nz
espera_texto "x.md:1"
espera_sem_texto "README.md:"

# ---------------------------------------------------------------- AC-T045.2

roda_ponteiros() { # $@ = argumentos do check-ponteiros.sh
  SAIDA="$(bash "$SCRIPTS_SRC/check-ponteiros.sh" "$@" 2>&1)"; CODIGO=$?
}

# Raiz de teste com uma classe, um arquivo de configuração e guias; $1 = nome do guia, conteúdo na entrada padrão.
mk_raiz_ponteiros() {
  PR="$TMP_ROOT/ponteiros"
  rm -rf "$PR"
  mkdir -p "$PR/servico/src/main/java/a" "$PR/servico/src/test/java/a" "$PR/deploy" "$PR/docs/guia"
  printf '%s\n' 'package a;' 'public class Foo {' '  public void registrar() {}' '  void marcarPublicado() {}' '}' > "$PR/servico/src/main/java/a/Foo.java"
  printf '%s\n' 'package a;' 'class FooTest {}' > "$PR/servico/src/test/java/a/FooTest.java"
  printf '%s\n' 'a: b' > "$PR/deploy/regras.yml"
  cat > "$PR/docs/guia/$1"
  PR_ARQ="$PR/docs/guia/$1"
}

cenario "check-ponteiros: classe, classe#método e caminho existentes passam"
mk_raiz_ponteiros ok.md <<'MD'
## Onde está no código

| Peça | Onde |
|---|---|
| Registro | `Foo#registrar` |
| Publicação | `Foo#marcarPublicado` e `Foo` |
| Regras | `deploy/regras.yml` |

## Ligações

Teste: `FooTest`.
MD
roda_ponteiros --raiz "$PR" "$PR_ARQ"
espera_codigo 0
espera_texto "OK"

cenario "check-ponteiros: classe inexistente reprova e aponta a linha"
mk_raiz_ponteiros classe.md <<'MD'
## Onde está no código

| Peça | Onde |
|---|---|
| Registro | `ClasseQueNaoExiste` |
MD
roda_ponteiros --raiz "$PR" "$PR_ARQ"
espera_codigo nz
espera_texto "FALTA"
espera_texto "classe.md:5"
espera_texto "ClasseQueNaoExiste"

cenario "check-ponteiros: método inexistente reprova"
mk_raiz_ponteiros metodo.md <<'MD'
## Onde está no código

| Peça | Onde |
|---|---|
| Registro | `Foo#naoExiste` |
MD
roda_ponteiros --raiz "$PR" "$PR_ARQ"
espera_codigo nz
espera_texto "método"
espera_texto "naoExiste"

cenario "check-ponteiros: caminho inexistente reprova"
mk_raiz_ponteiros caminho.md <<'MD'
## Onde está no código

| Peça | Onde |
|---|---|
| Regras | `deploy/outro.yml` |
MD
roda_ponteiros --raiz "$PR" "$PR_ARQ"
espera_codigo nz
espera_texto "outro.yml"

cenario "check-ponteiros: fora das seções 'Onde está no código' e 'Ligações' nada é conferido; tokens que não são ponteiros são ignorados"
mk_raiz_ponteiros fora.md <<'MD'
## Em uma frase

Texto com `ClasseQueNaoExiste` e `Foo#naoExiste` fora da seção.

| Coluna | Outra |
|---|---|
| `ClasseQueNaoExiste` | `x` |

## Onde está no código

| Peça | Onde |
|---|---|
| SQL | `FOR UPDATE SKIP LOCKED` e `published_at IS NULL` |
| Cabeçalho | `Idempotency-Key` e `outbox_event` |
| Registro | `Foo#registrar` |
MD
roda_ponteiros --raiz "$PR" "$PR_ARQ"
espera_codigo 0

cenario "check-ponteiros: sem argumentos confere docs/guia/ da raiz; sem nenhum ponteiro reprova (não passa vazio)"
mk_raiz_ponteiros nada.md <<'MD'
## Em uma frase

Sem tabela de ponteiros.
MD
roda_ponteiros --raiz "$PR"
espera_codigo nz
espera_texto "nenhum ponteiro"

cenario "check-ponteiros: sem argumentos, guia com ponteiro válido passa"
mk_raiz_ponteiros bom.md <<'MD'
## Onde está no código

| Peça | Onde |
|---|---|
| Registro | `Foo#registrar` |
MD
roda_ponteiros --raiz "$PR"
espera_codigo 0

# ---------------------------------------------------------------- AC-T044.2 e AC-T044.3

roda_versao() { # $1 = tag; $2 = raiz
  SAIDA="$(bash "$SCRIPTS_SRC/release/preparar-versao.sh" "$1" --raiz "$2" 2>&1)"; CODIGO=$?
}

# Repositório Git descartável com 4 pom.xml na versão $1 e, se $2 = notas, o arquivo de notas de 0.1.0.
mk_raiz_release() {
  RR="$TMP_ROOT/release"
  rm -rf "$RR"
  mkdir -p "$RR/contracts" "$RR/lancamentos-service" "$RR/consolidado-service" "$RR/docs/release" "$RR/lancamentos-service/target"
  printf '%s\n' '<project>' '  <parent>' '    <artifactId>spring-boot-starter-parent</artifactId>' '    <version>4.1.2</version>' '  </parent>' \
    '  <artifactId>fluxo-caixa</artifactId>' "  <version>$1</version>" '</project>' > "$RR/pom.xml"
  for m in contracts lancamentos-service consolidado-service; do
    printf '%s\n' '<project>' '  <parent>' '    <artifactId>fluxo-caixa</artifactId>' "    <version>$1</version>" '  </parent>' \
      "  <artifactId>$m</artifactId>" '</project>' > "$RR/$m/pom.xml"
  done
  printf '%s\n' '<project><version>0.1.0-SNAPSHOT</version></project>' > "$RR/lancamentos-service/target/pom.xml"
  [ "${2:-}" = notas ] && printf '%s\n' '# Notas' > "$RR/docs/release/notas-0.1.0.md"
  git -C "$RR" init -q && git -C "$RR" add -A -f . >/dev/null 2>&1
  git -C "$RR" rm -q --cached -r lancamentos-service/target >/dev/null 2>&1
  return 0
}

pom_hash() { (cd "$RR" && cat pom.xml contracts/pom.xml lancamentos-service/pom.xml consolidado-service/pom.xml | cksum); }

cenario "preparar-versao: v0.1.0 troca 0.1.0-SNAPSHOT por 0.1.0 nos quatro pom.xml e não mexe em target/"
mk_raiz_release 0.1.0-SNAPSHOT notas
roda_versao v0.1.0 "$RR"
espera_codigo 0
espera_texto "0.1.0"
confere; [ "$(grep -h -c '<version>0.1.0</version>' "$RR/pom.xml" "$RR/contracts/pom.xml" "$RR/lancamentos-service/pom.xml" "$RR/consolidado-service/pom.xml" | tr -d '\n')" = 1111 ] || falhou "os quatro pom.xml deviam ter <version>0.1.0</version>"
confere; ! grep -rq 'SNAPSHOT' "$RR/pom.xml" "$RR/contracts/pom.xml" "$RR/lancamentos-service/pom.xml" "$RR/consolidado-service/pom.xml" || falhou "sobrou SNAPSHOT nos pom.xml"
confere; grep -q 'SNAPSHOT' "$RR/lancamentos-service/target/pom.xml" || falhou "target/ não devia ser alterado"
confere; grep -q '<version>4.1.2</version>' "$RR/pom.xml" || falhou "a versão do parent não devia mudar"

cenario "preparar-versao: tag de outra versão reprova (exit 2) e não altera nenhum arquivo"
mk_raiz_release 0.1.0-SNAPSHOT notas
printf '%s\n' '# Notas' > "$RR/docs/release/notas-0.2.0.md"   # só a checagem da tag pode barrar o v0.2.0
ANTES="$(pom_hash)"
roda_versao v0.2.0 "$RR"
confere; [ "$CODIGO" -eq 2 ] || falhou "esperava exit 2, veio $CODIGO"
espera_texto "0.2.0"
confere; [ "$(pom_hash)" = "$ANTES" ] || falhou "pom.xml alterado numa falha"

cenario "preparar-versao: tag sem o v reprova (exit 2)"
mk_raiz_release 0.1.0-SNAPSHOT notas
ANTES="$(pom_hash)"
roda_versao 0.1.0 "$RR"
confere; [ "$CODIGO" -eq 2 ] || falhou "esperava exit 2, veio $CODIGO"
confere; [ "$(pom_hash)" = "$ANTES" ] || falhou "pom.xml alterado numa falha"

cenario "preparar-versao: pom.xml já sem -SNAPSHOT reprova (exit 2)"
mk_raiz_release 0.1.0 notas
ANTES="$(pom_hash)"
roda_versao v0.1.0 "$RR"
confere; [ "$CODIGO" -eq 2 ] || falhou "esperava exit 2, veio $CODIGO"
espera_texto "SNAPSHOT"
confere; [ "$(pom_hash)" = "$ANTES" ] || falhou "pom.xml alterado numa falha"

cenario "preparar-versao: sem docs/release/notas-X.Y.Z.md reprova (exit 2) e não altera nenhum arquivo"
mk_raiz_release 0.1.0-SNAPSHOT
ANTES="$(pom_hash)"
roda_versao v0.1.0 "$RR"
confere; [ "$CODIGO" -eq 2 ] || falhou "esperava exit 2, veio $CODIGO"
espera_texto "notas-0.1.0.md"
confere; [ "$(pom_hash)" = "$ANTES" ] || falhou "pom.xml alterado numa falha"

# O passo "Preparar a versão" do release.yml, extraído do próprio workflow e rodado com `bash -e`, como o runner faz.
passo_preparar_versao() { # $1 = tag; usa a raiz $RR (com scripts/release copiado)
  mkdir -p "$RR/scripts"; cp -r "$SCRIPTS_SRC/release" "$RR/scripts/release"
  local passo
  passo="$(awk '/- name: Preparar a versão/ { f = 1; next } f && /^      - / { exit } f && !/^        run:/' "$SCRIPTS_SRC/../.github/workflows/release.yml" | sed 's/^          //')"
  : > "$RR/env.txt"
  SAIDA="$(cd "$RR" && GITHUB_REF_NAME="$1" GITHUB_ENV="$RR/env.txt" bash -e -c "$passo" 2>&1)"; CODIGO=$?
}

cenario "release.yml: o passo 'Preparar a versão' com tag divergente derruba o passo e não grava VERSAO"
mk_raiz_release 0.1.0-SNAPSHOT notas
printf '%s\n' '# Notas' > "$RR/docs/release/notas-0.2.0.md"
passo_preparar_versao v0.2.0
espera_codigo nz
confere; [ ! -s "$RR/env.txt" ] || falhou "VERSAO não devia ser gravada numa falha"

cenario "release.yml: o passo 'Preparar a versão' com tag certa grava VERSAO=0.1.0"
mk_raiz_release 0.1.0-SNAPSHOT notas
passo_preparar_versao v0.1.0
espera_codigo 0
confere; [ "$(cat "$RR/env.txt")" = "VERSAO=0.1.0" ] || falhou "env.txt devia ter VERSAO=0.1.0"

cenario "scripts/release: os scripts têm o bit de execução no Git (o workflow os chama pelo caminho)"
confere
if git -C "$SCRIPTS_SRC/.." ls-files -s -- scripts/release/preparar-versao.sh scripts/release/conferir-sbom.sh | grep -qv '^100755'; then
  falhou "scripts/release/*.sh precisam estar 100755 no Git (git update-index --chmod=+x)"
fi

roda_sbom() { # $1 = arquivo
  SAIDA="$(bash "$SCRIPTS_SRC/release/conferir-sbom.sh" "$1" 2>&1)"; CODIGO=$?
}

# SBOM sintético: $1 = nome do arquivo; $2 = bomFormat; $3 = quantidade de componentes; $4 = 1 inclui spring-boot e postgresql.
mk_sbom() {
  mkdir -p "$TMP_ROOT/sbom"
  SB="$TMP_ROOT/sbom/$1"
  jq -n --arg f "$2" --argjson n "$3" --argjson sp "$4" '
    {bomFormat: $f, specVersion: "1.6",
     components: ([range(0; $n)] | map({type: "library", name: ("lib-" + (. | tostring))}))
       + (if $sp == 1 then [{type: "library", name: "spring-boot"}, {type: "library", name: "postgresql"}] else [] end)}' > "$SB"
}

cenario "conferir-sbom: CycloneDX com 50 ou mais componentes, spring-boot e postgresql passa"
mk_sbom ok.json CycloneDX 50 1
roda_sbom "$SB"
espera_codigo 0
espera_texto "OK"

cenario "conferir-sbom: sem bomFormat CycloneDX reprova (exit 2)"
mk_sbom formato.json SPDX 50 1
roda_sbom "$SB"
confere; [ "$CODIGO" -eq 2 ] || falhou "esperava exit 2, veio $CODIGO"
espera_texto "bomFormat"

cenario "conferir-sbom: menos de 50 componentes reprova (exit 2)"
mk_sbom pouco.json CycloneDX 10 1
roda_sbom "$SB"
confere; [ "$CODIGO" -eq 2 ] || falhou "esperava exit 2, veio $CODIGO"
espera_texto "componentes"

cenario "conferir-sbom: sem spring-boot reprova (exit 2)"
mk_sbom semspring.json CycloneDX 60 0
roda_sbom "$SB"
confere; [ "$CODIGO" -eq 2 ] || falhou "esperava exit 2, veio $CODIGO"
espera_texto "spring-boot"

cenario "conferir-sbom: arquivo inexistente ou que não é JSON reprova (exit 2)"
roda_sbom "$TMP_ROOT/sbom/nao-existe.json"
confere; [ "$CODIGO" -eq 2 ] || falhou "esperava exit 2, veio $CODIGO"
printf 'isto nao e json\n' > "$TMP_ROOT/sbom/lixo.json"
roda_sbom "$TMP_ROOT/sbom/lixo.json"
confere; [ "$CODIGO" -eq 2 ] || falhou "esperava exit 2, veio $CODIGO"

# ---------------------------------------------------------------- resumo

echo
if [ "$FALHAS" -eq 0 ]; then
  echo "OK: $TOTAL conferências, 0 falhas"
  exit 0
fi
echo "FALHOU: $FALHAS de $TOTAL conferências"
exit 1
