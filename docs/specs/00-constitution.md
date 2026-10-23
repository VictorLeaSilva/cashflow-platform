---
id: SPEC-00
status: draft
---
# 00 · Constituição do Projeto

## 1. Propósito
Entregar o case "Desafio Arquiteto de Soluções": serviço de **controle de lançamentos** (débitos e créditos) e serviço de **consolidado diário**, com documentação de arquitetura, código Java, testes e instruções claras de execução local, em repositório público.

## 2. Princípios (usados para desempatar decisões)
| # | Princípio |
|---|-----------|
| P1 | Requisito obrigatório do enunciado vem antes de qualquer diferencial. |
| P2 | Simplicidade comprovável: só entra componente com requisito, risco ou métrica que o justifique. O que foi **rejeitado** e por quê também vai para ADR. |
| P3 | Dinheiro é exato: `BigDecimal`/`NUMERIC`, nunca `float`/`double`. |
| P4 | Fato financeiro é imutável; correção é estorno (lançamento compensatório). |
| P5 | Comandos e consumo são idempotentes; entrega de eventos é *at-least-once*. |
| P6 | Falha do Consolidado nunca bloqueia Lançamentos: **nenhuma chamada síncrona** entre os serviços. |
| P7 | Todo comportamento tem critério de aceite testável e rastreável (requisito → teste). |
| P8 | Número só entra na documentação se medido (com data e ambiente) ou se estiver marcado como **meta**. |
| P9 | A spec manda. Lacuna ou conflito: registrar em `open-questions.md`, não improvisar. |
| P10 | Algo só é declarado **provado** na documentação com evidência reproduzível: o teste, script ou cenário que prova (`classe#método`, comando, k6 com resultado versionado), registrado em `docs/specs/reviews/T-xxx.evidence.md` e confirmado pela revisão independente. Teste de risco conta só na forma exigida em `04 §2.7` (ex.: concorrente, não sequencial). Metas da arquitetura-alvo nunca viram "provado" pelo case local. Fora disso, o estado é **a provar (T-xxx)** ou **só desenho** (`docs/architecture/architecture.md §1.2`). |

## 3. Stack fixada
| Área | Escolha | Observação |
|------|---------|------------|
| Linguagem | Java 25 (LTS) | Fallback Java 21 só com ADR se alguma lib obrigatória não suportar |
| Framework | Spring Boot 4.1.x (última GA/patch) | Confirmar patch na T-001; fallback 4.0.x só com ADR (3.5.x descartado: fim do suporte OSS em 30/06/2026) |
| Build | Maven 3.9+ com wrapper, multi-módulo | Versões pinadas via BOM + enforcer |
| Banco | PostgreSQL | Tag da imagem pinada na T-004 |
| Migrações | Flyway | Uma pasta por serviço |
| Acesso a dados | Spring `JdbcClient` (sem JPA/Hibernate) | ADR-002 |
| Mensageria | Apache Kafka (KRaft, 1 broker local) | ADR-004 |
| Segurança | Spring Security OAuth2 Resource Server (JWT) | ADR-007 |
| Observabilidade | Actuator + Micrometer + OpenTelemetry; Prometheus/Grafana em profile do compose | ADR-008 |
| Testes | JUnit 5, AssertJ, Mockito (só em portas), ArchUnit, testes de propriedade com gerador reproduzível próprio (sem jqwik, Q-009), Testcontainers, Awaitility, k6, PIT | `04-quality-standards.md` |
| Contratos | OpenAPI 3.x *contract-first* + JSON Schema para eventos; Swagger UI servida a partir do YAML versionado (webjar `swagger-ui`, sem springdoc) | `03-design-contracts.md §5` |
| Docs | Markdown + Mermaid (incluindo a arquitetura-alvo AWS) | draw.io, se houver, é criado manualmente pelo autor, fora do backlog |

## 3.1 Arquitetura interna (ADR-009)
Hexagonal enxuta por serviço, empacotada por feature:
`domain` (Java puro: sem Spring/Jackson/JDBC) → `application` (casos de uso + portas) → `adapter.in.*` / `adapter.out.*`. Regras verificadas por ArchUnit.

## 4. Convenções
- Documentação em pt-BR. Termos de domínio em pt-BR no código (`Lancamento`, `Estorno`, `Consolidado`); termos técnicos em inglês (`Controller`, `Repository`).
- Instantes em UTC no armazenamento (`TIMESTAMPTZ`); **fuso de negócio `America/Sao_Paulo`** para calcular `dataReferencia`.
- `dataOcorrencia` é instante ISO-8601 **com offset**; `dataReferencia` segue `03 §4.1`.
- Valores monetários em JSON como **string decimal** (`"123.45"`), em API e em eventos.
- IDs: UUID gerado via porta `IdGenerator`. Relógio: `Clock` injetado.
- Erros HTTP: RFC 9457 (`application/problem+json`).
- Branch `task/T-xxx-slug`; Conventional Commits; um PR por tarefa.

## 5. Glossário
- **Lançamento:** fato financeiro imutável (`CREDITO`, `DEBITO` ou `ESTORNO`).
- **Estorno:** lançamento compensatório que referencia um lançamento original.
- **Data de referência:** data (no fuso de negócio) em que o lançamento produz efeito no consolidado.
- **Movimento do dia:** soma dos efeitos de um dia. **Saldo acumulado:** soma dos movimentos até o dia.
- **Projeção:** tabela de leitura do Consolidado, derivada de eventos.
- **Outbox:** tabela em que o evento é gravado na mesma transação do lançamento.

## 6. Premissas assumidas (revisar; qualquer mudança exige atualizar spec e ADR)
| # | Premissa |
|---|----------|
| A1 | O `comercianteId` vem do claim `merchant_id` do JWT; não há cadastro de comerciante neste escopo. |
| A2 | Moeda única (BRL). |
| A3 | Saldo inicial de cada comerciante é zero (cenário sem legado; com legado, ver §6.1). |
| A4 | Retroatividade: `dataReferencia` ≥ hoje(SP) − 30 dias (`lancamentos.max-backdate-days`); data futura é proibida (A11). |
| A5 | Estorno é **total** e **único** por lançamento; estorno de estorno é proibido (estornos parciais/múltiplos de um legado: ver §6.1). |
| A6 | Débito não é limitado por saldo (saldo negativo permitido). É decisão de negócio **e** de arquitetura: autorizar débito pelo saldo exigiria consultar o Consolidado de forma síncrona, violando P6/RNF-001. |
| A7 | Valor máximo por lançamento: `10000000.00` (configurável em `lancamentos.max-valor`; Q-007). |
| A8 | Lançamentos nunca são editados nem excluídos. |
| A9 | Sem cache distribuído e sem réplica de leitura até haver medição que justifique. |
| A10 | "Perda de requisições" é definida em RNF-002. |
| A11 | Tolerância de relógio para data futura: 60 s (`lancamentos.max-future-skew`). |

### 6.1 Premissas e arquitetura de transição
O enunciado trata a arquitetura de transição como **diferencial e condicional** ("se necessária, considerando uma migração de legado") e não descreve nenhum sistema anterior. Logo, o MVP **não inventa legado**.

- **Cenário principal (implementado):** sistema novo; A3, A5 e A6 valem; nenhuma dependência de legado para o funcionamento normal.
- **Cenário alternativo (apenas documentado, T-041):** havendo legado, A3 deixa de valer e a transição segue estes princípios:
  1. **Migração não se mistura com a regra normal.** A API pública nunca cria crédito/débito fictício para representar saldo. A carga usa um **canal de migração separado** (scope próprio, fora da API pública), **auditável** (origem, lote, operador) e **idempotente** (`identificadorExterno` único por comerciante + identificador de lote).
  2. **Posição inicial:** preferir importar o **histórico como lançamentos** quando existir; se só houver a posição, registrá-la como fato próprio de **saldo de abertura** com data de corte, usado como base do `saldoAnterior` (hoje zero), e não como `CREDITO`/`DEBITO`.
  3. A4 (retroatividade ≤ 30 dias) é regra da API pública; a carga histórica passa pelo canal de migração.
  4. **Estornos parciais ou múltiplos do legado:** mapear sem distorcer o saldo e sem inventar registros (por exemplo, importar como lançamentos compensatórios simples com referência externa, sem o vínculo `ESTORNO`); a regra final sai da análise do legado e vira ADR.
  5. **Reconciliação:** posição legada × Consolidado por comerciante e dia, no corte, com relatório de divergências antes do *go-live*.
  6. **Camada anticorrupção (ACL)** traduz o modelo legado para os contratos de Lançamentos, isolada do domínio.
  7. **Strangler Fig / *shadow mode*** só se houver coexistência entre os sistemas.
  8. **Identidade legada:** usuários e comerciantes também migram, para o IdP (nunca para um cadastro próprio da aplicação, ADR-007): importação com hash compatível, migração preguiçosa no primeiro login, federação durante a coexistência ou redefinição obrigatória de senha para hashes fracos; o código legado do comerciante é traduzido para o UUID do `merchant_id` na ACL.
- **O desenho do MVP já comporta isso sem mudança:** `identificador_externo` único, `saldoAnterior` calculado na leitura (aceita uma base de abertura sem recálculo em cascata) e eventos versionados.

## 7. Fora de escopo (e onde aparece como evolução)
Rebuild/replay administrativo (ADR-006, evolução), estorno parcial, multi-moeda, cache distribuído, CDC com Debezium, Schema Registry, IaC completa e qualquer recurso de nuvem (a arquitetura-alvo AWS é **apenas desenho** e estimativa de custo; o case é implementado e provado só localmente), **frontend próprio** (decisão do dono do produto: a API é exercitada pela Swagger UI local, por `scripts/demo.sh` e por k6).

## 8. Definição de Pronto global (release)
- Todos os RF/RNF obrigatórios com ≥1 teste rastreado e verde.
- `./mvnw verify` verde a partir de clone limpo.
- `docker compose up` + README permitem subir e exercitar o fluxo completo em ≤ 5 comandos.
- Documentação exigida pelo enunciado presente no repositório (domínios/capacidades, requisitos, arquitetura-alvo, justificativas, README).
- Nenhum número de desempenho/custo sem origem (medição ou meta rotulada).
