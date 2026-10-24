# Inventário de dependências diretas

Lista todas as dependências diretas do projeto (`<dependency>` de cada `pom.xml`) e a tarefa ou decisão que as aprovou. O gate (`scripts/verify-task.sh`, item 3) barra coordenadas novas fora de `allowed_new_dependencies`; este inventário é atualizado **depois do *merge*** da tarefa que as adiciona (`05-harness.md §4.1`). Versões vêm do BOM do Spring Boot ou das propriedades do `pom.xml` raiz.

Estado em 2026-10-24 (após a T-023).

## Raiz (`pom.xml`: `dependencyManagement` e dependência de plugin)
| Coordenada | Escopo | Aprovada por |
|---|---|---|
| `com.tngtech.archunit:archunit` | test | T-002 |
| `org.webjars:swagger-ui` | compile | T-013 |
| `com.networknt:json-schema-validator` | test | T-013 (Q-006 para o Consolidado, T-022) |
| `com.puppycrawl.tools:checkstyle` | dependência do plugin Checkstyle | T-002 |

## `lancamentos-service`
| Coordenada | Aprovada por |
|---|---|
| `org.springframework.boot:spring-boot-starter` | T-001 |
| `org.springframework.boot:spring-boot-starter-test` | T-001 |
| `org.testcontainers:testcontainers-postgresql` | T-001 |
| `org.testcontainers:testcontainers-kafka` | T-001 |
| `com.tngtech.archunit:archunit` | T-002 |
| `org.springframework.boot:spring-boot-starter-jdbc` | T-011 |
| `org.springframework.boot:spring-boot-starter-flyway` | T-011 |
| `org.flywaydb:flyway-database-postgresql` | T-011 |
| `org.postgresql:postgresql` | T-011 |
| `org.springframework.boot:spring-boot-starter-jackson` | T-012 |
| `org.springframework.boot:spring-boot-starter-webmvc` | T-013 |
| `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server` | T-013 |
| `org.springframework.boot:spring-boot-starter-webmvc-test` | T-013 |
| `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test` | T-013 |
| `org.webjars:swagger-ui` | T-013 |
| `org.webjars:webjars-locator-lite` | T-013 |
| `com.networknt:json-schema-validator` | T-013 |
| `org.springframework.boot:spring-boot-starter-kafka` | T-016 (Q-027) |
| `org.springframework.boot:spring-boot-starter-micrometer-metrics` | T-016 (Q-027) |

## `consolidado-service`
| Coordenada | Aprovada por |
|---|---|
| `org.springframework.boot:spring-boot-starter` | T-001 |
| `org.springframework.boot:spring-boot-starter-test` | T-001 |
| `org.testcontainers:testcontainers-postgresql` | T-001 |
| `org.testcontainers:testcontainers-kafka` | T-001 |
| `com.tngtech.archunit:archunit` | T-002 |
| `org.springframework.boot:spring-boot-starter-jdbc` | T-021 |
| `org.springframework.boot:spring-boot-starter-flyway` | T-021 |
| `org.flywaydb:flyway-database-postgresql` | T-021 |
| `org.postgresql:postgresql` | T-021 |
| `org.springframework.boot:spring-boot-starter-kafka` | T-022 |
| `org.springframework.boot:spring-boot-starter-jackson` | T-022 |
| `org.springframework.boot:spring-boot-starter-micrometer-metrics` | T-022 (Q-027) |
| `com.networknt:json-schema-validator` | T-022 (Q-006) |
| `br.com.fluxocaixa:contracts` | T-022 (Q-043; módulo do próprio projeto, só recursos) |
| `org.springframework.boot:spring-boot-starter-webmvc` | T-023 |
| `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server` | T-023 |
| `org.springframework.boot:spring-boot-starter-webmvc-test` | T-023 |
| `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test` | T-023 |
| `org.webjars:swagger-ui` | T-023 |
| `org.webjars:webjars-locator-lite` | T-023 |

## `contracts`
Nenhuma dependência direta.
