# Rodar os testes e as verificações de qualidade

> Como rodar os testes, o que as verificações conferem e como verificar uma tarefa pelo gate do método.

O comando abaixo compila o projeto e roda todos os testes, inclusive os de integração. Esses testes sobem seus próprios bancos e Kafka temporários com o Docker, sem tocar nos contêineres do passo a passo. Ele também roda as verificações de qualidade:
- formatação (Spotless) e estilo (Checkstyle);
- regras de arquitetura (ArchUnit);
- busca de bugs (SpotBugs/FindSecBugs);
- cobertura de testes (JaCoCo).

Leva alguns minutos.

**bash (Linux, macOS e Git Bash)**

```bash
./mvnw verify
```

**PowerShell (Windows)**

```powershell
.\mvnw.cmd verify
```

**O que você deve ver:** no fim, `BUILD SUCCESS`. Se a formatação falhar, corrija com `./mvnw spotless:apply` (bash) ou `.\mvnw.cmd spotless:apply` (PowerShell).

O Maven Wrapper usa o modo `only-script`: nenhum JAR binário do Wrapper é versionado. Para ativar os *hooks* de Git do projeto, uma vez por clone:

**bash e PowerShell**

```bash
git config core.hooksPath .githooks
```

O `pre-commit` roda o gitleaks (se instalado) e a checagem de formatação. O `pre-push` roda `./mvnw -q verify -DskipITs`. Os *hooks* podem ser contornados, por isso o CI repete tudo.

### Verificar uma tarefa (gate do método)

Quem implementa uma tarefa do projeto usa três scripts (precisam de Git, Java 25 e, para o gate completo, do Docker). No PowerShell, chame o `bash` do Git (o `bash` solto pode ser o do WSL).

**1. Testar os próprios scripts** (leva de um a alguns minutos, conforme a máquina; não usa Docker nem altera a sua pasta de trabalho):

**bash (Linux, macOS e Git Bash)**

```bash
scripts/test/run-tests.sh
```

**PowerShell (Windows)**

```powershell
& "C:\Program Files\Git\bin\bash.exe" scripts/test/run-tests.sh
```

**O que você deve ver:** uma linha por cenário e, no fim, `OK: 84 conferências, 0 falhas` (o número de conferências cresce quando entram cenários novos).

**2. Listar os requisitos sem teste** (sai com erro enquanto houver requisito obrigatório sem prova) e regenerar a matriz [`docs/specs/traceability.md`](../../docs/specs/traceability.md):

**bash (Linux, macOS e Git Bash)**

```bash
scripts/check-traceability.sh
scripts/check-traceability.sh --write
```

**PowerShell (Windows)**

```powershell
& "C:\Program Files\Git\bin\bash.exe" scripts/check-traceability.sh
& "C:\Program Files\Git\bin\bash.exe" scripts/check-traceability.sh --write
```

**O que você deve ver:** a primeira lista `FALTA RNF-xxx (tarefas: ...)` para cada requisito obrigatório que ainda não tem teste, e os que são provados por evidência, com o arquivo; ela termina com erro enquanto houver `FALTA`. A segunda imprime `gerado: docs/specs/traceability.md`.

**3. Rodar o gate de uma tarefa**, a partir do branch da tarefa. `BASE` é o branch contra o qual o *diff* é calculado (o padrão é `main`; se o seu `main` local estiver defasado, use o branch base da tarefa):

**bash (Linux, macOS e Git Bash)**

```bash
BASE=main scripts/verify-task.sh T-003
```

**PowerShell (Windows)**

```powershell
$env:BASE = "main"
& "C:\Program Files\Git\bin\bash.exe" scripts/verify-task.sh T-003
Remove-Item Env:BASE
```

**O que você deve ver:** uma linha `OK item N (...)` ou `FALHA item N (...)` para cada um dos seis itens (build, escopo, dependências, rastreabilidade, padrões proibidos e artefatos), seguida de `RESULTADO: OK` ou `RESULTADO: FALHA`. O resultado também fica em `docs/specs/reviews/T-xxx.verify.log` (arquivo local, ignorado pelo Git). O gate lê os *commits*: faça o commit antes de rodar.

## Segurança no CI

Os gates de segurança do CI (CodeQL, Dependency-Check, Trivy) estão em [`.github/workflows/security.yml`](../../.github/workflows/security.yml); o Dependency-Check precisa do segredo `NVD_API_KEY` no GitHub. O [mapeamento OWASP API Top 10](../../docs/security/owasp-mapping.md) (controle e teste de cada risco), o [modelo de ameaças](../../docs/security/ameacas.md) e a [lista ASVS nível 1](../../docs/security/asvs-l1.md) estão em [`docs/security/`](../../docs/security/).
