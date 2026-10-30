# Bancos de dados e contêineres do ambiente local

> O que cada banco e cada contêiner faz, e o que cada comando faz com os dados.

## Como funcionam os bancos de dados

- **Uma instância de PostgreSQL com dois bancos:** `lancamentos` e `consolidado`. Cada serviço tem dois usuários:
  - um "dono" (`*_owner`), que cria as tabelas;
  - um "aplicação" (`*_app`), que só lê e grava.

  Um serviço não consegue entrar no banco do outro. É isso que o passo 4 confere.
- **Quem cria os bancos e os usuários:** o script `deploy/init/01-bancos-e-roles.sql`, executado pelo PostgreSQL **só na primeira vez** que o volume de dados é criado (o primeiro `up`, ou o primeiro depois de um `down -v`).
- **Quem cria as tabelas:** cada serviço, pelo Flyway, **ao subir** (passos 7 e 8). As migrações ficam em `lancamentos-service/src/main/resources/db/migration/` e `consolidado-service/src/main/resources/db/migration/`. Se o serviço nunca subiu, o banco existe, mas está **vazio**. Nas subidas seguintes, o Flyway só aplica as migrações novas.
- **De onde vêm os dados do consolidado:** dos eventos que o Lançamentos publica no Kafka. Na primeira subida, o Consolidado lê todos os eventos que o Kafka ainda guarda, desde o início.
  - **O Kafka guarda os eventos por 7 dias.** Se o Consolidado subir pela primeira vez mais de 7 dias depois de um lançamento, esse lançamento não entra no resumo. Os 7 dias são o padrão do Kafka, não um valor fixado pelo projeto para o tópico principal (só a DLT tem retenção própria, de 30 dias, em `deploy/kafka/create-topics.sh`). Se a imagem do Kafka mudar esse padrão, este prazo muda junto.
  - Reconstruir o consolidado a partir dos lançamentos é uma evolução prevista ([ADR-006](../../docs/architecture/decisions/ADR-006-estrategia-de-rebuild.md)), fora deste MVP.
- **O que cada comando faz com os dados:**

  | Comando | Contêineres | Dados (bancos e Kafka) | Para voltar a usar |
  |---|---|---|---|
  | `down` | removidos | **mantidos** | passo 3 e passos 7 e 8 |
  | `down -v` | removidos | **apagados** | passo 3 (recria bancos e usuários) e passos 7 e 8 (recriam as tabelas) |

## Contêineres do ambiente local

| Serviço | Imagem | Papel | Porta no host | Profile | Ciclo de vida |
|---|---|---|---|---|---|
| `postgres` | `postgres:17.11-alpine` | Bancos `lancamentos` e `consolidado`, cada um com usuários próprios (`deploy/init/`) | `POSTGRES_PORT` (5432) | padrão | Contínuo, com *healthcheck* |
| `kafka` | `apache/kafka:3.9.1` | Broker KRaft de nó único, sem criação automática de tópicos | `KAFKA_PORT` (9092) | padrão | Contínuo, com *healthcheck* |
| `kafka-topicos` | `apache/kafka:3.9.1` | Cria o tópico `cashflow.lancamentos.v1` e a DLT (`deploy/kafka/create-topics.sh`) | — | padrão | Execução única: roda depois que o `kafka` fica saudável e termina com exit 0 |
| `kafka-ui` | `kafbat/kafka-ui:v1.3.0` | Página para ver tópicos e mensagens | `KAFKA_UI_PORT` (8090) | `tools` | Contínuo, opcional |
| `prometheus` | `prom/prometheus:v3.15.1` | Métricas e alertas dos dois serviços (`deploy/prometheus/`) | `PROMETHEUS_PORT` (9090) | `observability` | Contínuo, opcional, com *healthcheck* |
| `grafana` | `grafana/grafana:13.2.4` | Painel de operação, só leitura e sem login (`deploy/grafana/`) | `GRAFANA_PORT` (3000) | `observability` | Contínuo, opcional, com *healthcheck* |
| `dev-keys` | `eclipse-temurin:25.0.1_8-jdk-noble` | Cria a chave JWT de desenvolvimento em `deploy/dev-keys/` (`scripts/DevToken.java`), se ainda não existir | — | `apps` | Execução única: termina com exit 0 |
| `lancamentos` | `fluxo-caixa/lancamentos:0.1.0-SNAPSHOT` | Serviço de Lançamentos, imagem do `spring-boot:build-image` (usuário não-root) | `LANCAMENTOS_PORT` (8081) e `LANCAMENTOS_MANAGEMENT_PORT` (9081) | `apps` | Contínuo; a saúde é `:9081/actuator/health` |
| `consolidado` | `fluxo-caixa/consolidado:0.1.0-SNAPSHOT` | Serviço de Consolidado, mesma receita | `CONSOLIDADO_PORT` (8082) e `CONSOLIDADO_MANAGEMENT_PORT` (9082) | `apps` | Contínuo; a saúde é `:9082/actuator/health` |

- **Kafka UI:** para ver as mensagens no navegador, acrescente `--profile tools` ao comando do passo 3 e abra <http://localhost:8090>.
- **Observabilidade:** para ver as métricas e o painel, acrescente `--profile observability` ao comando do passo 3 (veja [Observabilidade](observabilidade-e-alertas.md#observabilidade-métricas-alertas-e-painel-opcional)).
- **Serviços em contêiner:** com `--profile apps`, os dois serviços sobem em contêineres (veja [Veja funcionando em 5 etapas](../../README.md#veja-funcionando-em-5-etapas)); sem ele, sobem pelo Maven, como nos passos 7 e 8. Os dois modos usam as mesmas portas, então não rode os dois ao mesmo tempo.
- **`--remove-orphans`:** remove contêineres de serviços que saíram do compose ou foram renomeados, como o de criação de tópicos na T-006.
- **Volumes soltos:** a imagem do Kafka declara volumes próprios. Ao remover um contêiner órfão, ficam volumes anônimos soltos, que o `down -v` não alcança. Liste-os com `docker volume ls -f dangling=true` antes de apagar.
