# Fluxo de Caixa

Controle de **lançamentos** (créditos e débitos) e **consolidado diário**, com dois serviços independentes comunicando-se por eventos. Case do desafio de Arquiteto de Soluções.

> Estado: fundação e gates de qualidade (T-001, T-002), infraestrutura local (T-004, T-006), documentação de arquitetura (T-005) e, no serviço de Lançamentos, domínio, persistência, registro idempotente com outbox, API `POST /lancamentos` e estorno (T-010 a T-014). Este README é provisório e será finalizado na T-043.

## Documentação

- [`docs/architecture/architecture.md`](docs/architecture/architecture.md): visão geral, diagramas C4, arquitetura-alvo AWS (**apenas desenho**; o case é implementado e provado só localmente, tarefa a tarefa) e tabela de requisitos não funcionais.
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
docker compose --env-file .env -f deploy/docker-compose.yml up -d --wait --remove-orphans
```

```bash
deploy/verify-local.sh
```

Contêineres do `deploy/docker-compose.yml`:

| Serviço | Imagem | Papel | Porta no host | Profile | Ciclo de vida |
|---|---|---|---|---|---|
| `postgres` | `postgres:17.11-alpine` | Bancos `lancamentos` e `consolidado`, cada um com roles próprios (`deploy/init/`) | `POSTGRES_PORT` (5432) | padrão | Contínuo, com *healthcheck* |
| `kafka` | `apache/kafka:3.9.1` | Broker KRaft de nó único, sem criação automática de tópicos | `KAFKA_PORT` (9092) | padrão | Contínuo, com *healthcheck* |
| `kafka-topicos` | `apache/kafka:3.9.1` | Cria o tópico `cashflow.lancamentos.v1` e a DLT (`deploy/kafka/create-topics.sh`) | — | padrão | Execução única: roda depois que o `kafka` fica saudável e termina com exit 0 |
| `kafka-ui` | `kafbat/kafka-ui:v1.3.0` | UI para inspecionar tópicos e mensagens | `KAFKA_UI_PORT` (8090) | `tools` | Contínuo, opcional |

Os contêineres das aplicações (`lancamentos` e `consolidado`, profile `apps`) e os de observabilidade (profile `observability`) entram nas T-035 e T-032. O `--remove-orphans` remove contêineres de serviços que saíram do compose ou foram renomeados, como o serviço de criação de tópicos na T-006.

O `verify-local.sh` confere se os serviços estão saudáveis, se o `kafka-topicos` terminou com exit 0, se cada serviço só acessa o próprio banco e se os tópicos existem. A UI do Kafka é opcional: acrescente `--profile tools` ao `up` e acesse `http://localhost:8090`. Para apagar tudo, inclusive os volumes, use `docker compose --env-file .env -f deploy/docker-compose.yml down -v`.

## Chamar a API localmente

Com a infraestrutura de pé, suba o serviço de Lançamentos no profile `local` (porta 8081), carregando as senhas do `.env` no ambiente:

```bash
set -a; . ./.env; set +a; ./mvnw -pl lancamentos-service spring-boot:run -Dspring-boot.run.profiles=local
```

Gere um token de desenvolvimento. Na primeira execução, o script cria o par de chaves em `deploy/dev-keys/`, que fica fora do Git e é lido pelo profile `local`:

```bash
TOKEN=$(java scripts/DevToken.java --merchant 0f8fad5b-d9cb-469f-a165-70867728950e --scopes lancamentos:write --ttl 3600)
```

Registre um crédito. Repetir o comando com a mesma `Idempotency-Key` devolve o mesmo corpo, com `Idempotent-Replayed: true`:

```bash
curl -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-1" -d '{"tipo":"CREDITO","valor":"100.00"}'
```

Estorne esse lançamento usando o `id` devolvido. O estorno não tem corpo. Ele cria um lançamento `ESTORNO` com a data de hoje, e um segundo estorno do mesmo lançamento responde 409:

```bash
curl -i -X POST http://localhost:8081/api/v1/lancamentos/<id>/estornos -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: estorno-1"
```

O contrato fica em [`lancamentos-v1.yaml`](lancamentos-service/src/main/resources/openapi/lancamentos-v1.yaml). Com o profile `local`, a Swagger UI fica em `http://localhost:8081/swagger-ui/index.html`.

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

- **Integrado:** build reproduzível e gates de qualidade (T-001, T-002); infraestrutura local com PostgreSQL e Kafka (T-004); documentação de arquitetura com ADRs e arquitetura-alvo (T-005); domínio, persistência e idempotência de Lançamentos (T-010 a T-012).
- **Em andamento:** API `POST /api/v1/lancamentos` com JWT, Problem Details e contrato OpenAPI (T-013).
- **Próximo:** estorno, consultas e publicador da outbox (T-014 a T-016), seguidos do Consolidado. A ordem está em [`docs/specs/06-tasks.md`](docs/specs/06-tasks.md).
