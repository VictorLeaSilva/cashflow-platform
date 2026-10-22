# Fluxo de Caixa

Controle de **lançamentos** (créditos e débitos) e **consolidado diário**, com dois serviços independentes comunicando-se por eventos. Case do desafio de Arquiteto de Soluções.

> Estado: **fundação (T-001)**. A documentação de arquitetura e as especificações estão em [`docs/specs/`](docs/specs/). Este README é provisório e será finalizado na T-043.

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

## Método de desenvolvimento

O projeto utiliza **Spec Driven Development (SDD)**, com implementação e revisão por tarefa.

Antes de implementar uma tarefa, consulte:

1. [`AGENTS.md`](AGENTS.md) — instruções para agentes de código.
2. [`docs/specs/00-constitution.md`](docs/specs/00-constitution.md) — princípios e convenções.
3. A especificação da tarefa correspondente em `docs/specs/tasks/`.
4. [`docs/specs/05-harness.md`](docs/specs/05-harness.md) — processo de verificação e revisão.

O gate automatizado por tarefa será disponibilizado conforme o planejamento definido nas especificações.

## Status

Projeto em desenvolvimento. Esta versão documenta a fundação do repositório; contratos, regras de negócio, testes e infraestrutura serão evoluídos nas próximas tarefas.
