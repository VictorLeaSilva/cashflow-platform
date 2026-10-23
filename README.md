# Fluxo de Caixa

Controle de **lançamentos** (créditos e débitos) e **consolidado diário**, com dois serviços independentes comunicando-se por eventos. Case do desafio de Arquiteto de Soluções.

> Estado: fundação e gates de qualidade (T-001, T-002), infraestrutura local (T-004) e documentação de arquitetura (T-005). Este README é provisório e será finalizado na T-043.

## Documentação

- [`docs/architecture/architecture.md`](docs/architecture/architecture.md): visão geral, diagramas C4, arquitetura-alvo AWS (**apenas desenho**; o case roda e é provado localmente) e tabela de requisitos não funcionais.
- [`docs/architecture/decisions/`](docs/architecture/decisions/): decisões de arquitetura (ADR-001 a ADR-010), com alternativas rejeitadas e trade-offs.
- [`docs/specs/`](docs/specs/): especificação (constituição, domínios e capacidades, requisitos, contratos, padrões de qualidade, backlog).

## Módulos

- **`contracts/`** — schemas e contratos dos eventos compartilhados.
- **`lancamentos-service/`** — serviço responsável pelos lançamentos financeiros.
- **`consolidado-service/`** — serviço responsável pelo consolidado diário.

## Pré-requisitos

- JDK 25.
- Docker em execução para os testes de compatibilidade com Testcontainers.
- Maven 3.9.11, caso seja necessário gerar novamente o Maven Wrapper.

## Como validar o projeto

O Maven Wrapper deve usar o modo `only-script`, sem JAR binário do Wrapper versionado no repositório.

Na raiz do projeto, execute:

**Windows (PowerShell)**

```powershell
.\mvnw.cmd -q verify
```

**Linux ou macOS**

```bash
./mvnw -q verify
```

Os testes de compatibilidade iniciam containers PostgreSQL e Kafka via Testcontainers, portanto precisam de um ambiente Docker funcional.

## Infraestrutura local

PostgreSQL (bancos `lancamentos` e `consolidado`, cada um com roles próprios) e Kafka em modo KRaft (tópico `cashflow.lancamentos.v1` e a DLT, com 3 partições). Na raiz do projeto:

```bash
cp .env.example .env
```

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --wait
```

```bash
deploy/verify-local.sh
```

O `verify-local.sh` confere se os serviços estão saudáveis, se cada serviço só acessa o próprio banco e se os tópicos existem. A UI do Kafka é opcional: acrescente `--profile tools` ao `up` e acesse `http://localhost:8090`. Para apagar tudo, inclusive os volumes, use `docker compose --env-file .env -f deploy/docker-compose.yml down -v`.

## Qualidade e hooks locais

O `./mvnw verify` é o gate G2: formatação (Spotless), estilo (Checkstyle), regras de arquitetura (ArchUnit), testes, SpotBugs/FindSecBugs e cobertura (JaCoCo). Se a formatação falhar, corrija com:

```bash
./mvnw spotless:apply
```

Ative os hooks versionados (uma vez por clone):

```bash
git config core.hooksPath .githooks
```

O `pre-commit` roda gitleaks (se instalado) e a checagem de formatação; o `pre-push` roda `./mvnw -q verify -DskipITs`. Os hooks são contornáveis, por isso o CI repete tudo.

## Método de desenvolvimento

O projeto utiliza **Spec Driven Development (SDD)**, com implementação e revisão por tarefa.

Antes de implementar uma tarefa, consulte:

1. [`AGENTS.md`](AGENTS.md) — instruções para agentes de código.
2. [`docs/specs/00-constitution.md`](docs/specs/00-constitution.md) — princípios arquiteturais e convenções do projeto.
3. [`docs/specs/tasks/`](docs/specs/tasks) — tarefas disponíveis. Consulte o arquivo correspondente à tarefa que será implementada, por exemplo, `T-002.md`.
4. [`docs/specs/05-harness.md`](docs/specs/05-harness.md) — processo de verificação, testes e revisão.

O gate automatizado por tarefa (`scripts/verify-task.sh`) chega com a T-003; até lá, a verificação de cada tarefa é o `./mvnw verify`, os scripts da própria tarefa e a revisão independente.

## Status

Projeto em desenvolvimento.

- **Pronto:** build reproduzível e gates de qualidade (T-001, T-002); infraestrutura local com PostgreSQL e Kafka (T-004); documentação de arquitetura com ADRs e arquitetura-alvo (T-005).
- **Próximo:** domínio, persistência e idempotência de Lançamentos (T-010 a T-012), seguidos das APIs, do publicador e do Consolidado. A ordem está em [`docs/specs/06-tasks.md`](docs/specs/06-tasks.md).
- Os serviços ainda não expõem endpoints.
