---
id: SPEC-04
status: draft
---
# 04 · Padrões de Qualidade e Segurança do Repositório

Princípio: **o CI é a autoridade**; hooks locais só aceleram o feedback. Nenhuma ferramenta entra sem função clara (P2). Versões dos plugins são pinadas na T-002 (verificar a última estável na data).

## 1. Gates

| Gate | Quando | O que roda | Bloqueia |
|---|---|---|---|
| **G1** pré-commit | `git commit` | gitleaks (staged), `spotless:check` | Commit |
| **G2** `./mvnw verify` | local, pré-push e CI | Formatação, Checkstyle, SpotBugs+FindSecBugs, enforcer, testes unitários, ArchUnit, testes de integração (Testcontainers), JaCoCo | PR |
| **G3** CI complementar | PR | CodeQL, OWASP Dependency-Check, Trivy (imagem), validação de docs (markdownlint, links, Mermaid) | PR |
| **G4** noturno/manual | agendado / `workflow_dispatch` | PIT (mutação), k6, OWASP ZAP baseline | Relatório; regressão abre issue |
| **G5** release | tag | Imagens em GHCR, SBOM CycloneDX, revisão final de rastreabilidade | Release |

## 2. Ferramentas e limites

### 2.1 Estilo e análise estática
| Ferramenta | Papel | Regra de falha |
|---|---|---|
| Spotless + google-java-format | Formatação única | Qualquer diferença |
| Checkstyle (config mínima do repo) | Nomes, imports, complexidade ciclomática ≤ 10, método ≤ 50 linhas, ≤ 6 parâmetros | Violação bloqueia; complexidade e tamanho admitem **exceção justificada** (§5) em vez de abstração forçada |
| SpotBugs + FindSecBugs | Bugs e vulnerabilidades no bytecode | Severidade ≥ Medium |
| Maven Enforcer | Java 25, Maven ≥ 3.9, convergência de dependências, dependências banidas | Qualquer violação |
| Error Prone + NullAway/JSpecify | Fase 2 opcional; só se não conflitar com a stack | — |
| EditorConfig | Consistência de editor | — |

### 2.2 Regras de arquitetura (ArchUnit) — viram testes
- `domain` não depende de Spring, Jackson, JDBC nem Kafka.
- `application` não depende de `adapter.*`.
- `lancamentos-service` e `consolidado-service` não se referenciam: barrado no classpath pelo enforcer (T-001) e por regra ArchUnit de pacote.
- Proibido `float`/`double` (e seus *wrappers*) em campos e retornos de método de `domain`/`application`.
- Proibido chamar `now()` do `java.time` sem `Clock` e `System.currentTimeMillis()`.
- Proibido `System.out`/`System.err` e `printStackTrace`.
- `@SuppressWarnings` sem justificativa: ArchUnit não lê comentários; verificado na revisão (R3/R8).
- Controllers retornam DTOs (records), nunca entidades de domínio: regra adicionada na T-013, quando existirem controllers.

### 2.3 Testes
| Tipo | Ferramenta | Escopo |
|---|---|---|
| Unitário | JUnit 5, AssertJ | Regras de domínio e casos de uso; Mockito só nas portas |
| Propriedade | JUnit 5 + gerador reproduzível próprio (semente fixa registrada, reproduzível por `-Dpropriedades.semente`; sem *shrinking*; Q-009) | Comutatividade e idempotência da projeção; invariantes de valor |
| Fatia web | `@WebMvcTest` + segurança | Validação, status, scopes |
| Integração | Testcontainers (PostgreSQL, Kafka), Awaitility (sem `sleep`) | Migrações, constraints, outbox, consumidor, concorrência |
| Contrato de evento | JSON Schema nos dois serviços + exemplos *golden* | Produtor gera e consumidor aceita os mesmos exemplos |
| Contrato de API | Validação das respostas contra o OpenAPI | Sem *drift* |
| Resiliência | Testcontainers (parar/pausar containers) | RNF-001, RNF-007 |
| Carga | k6 com *thresholds* em código | RNF-002, RNF-010 |
| Mutação | PIT em `domain`+`application` | ≥ 80 % (G4) |

Nomes: `*Test` (unitário), `*IT` (integração, Failsafe). Todo teste carrega `@Tag` do requisito.

### 2.4 Cobertura (JaCoCo, unit+IT)
**Piso (bloqueia):** `domain`+`application` ≥ 85 % linhas; projeto ≥ 80 % linhas. **Meta (reportada, não bloqueia):** `domain`+`application` ≥ 90 % linhas e ≥ 85 % ramos. Cobertura **não substitui** os testes de risco do §2.7. Exclusões apenas: configuração, classe `main`, código gerado. A regra por pacote (`domain`/`application` ≥ 85 %) é adicionada ao JaCoCo na T-010/T-020, quando os pacotes existirem; até lá vale o piso do projeto (80 %).

### 2.5 Segurança
| Ferramenta | Papel | Regra |
|---|---|---|
| OWASP Dependency-Check | CVEs em dependências (chave da API do NVD em segredo do CI; cache de dados) | Falha com CVSS ≥ 7; supressões só com justificativa e validade |
| Dependabot | Atualizações (maven, actions, docker) | PRs semanais |
| gitleaks | Segredos | Qualquer achado |
| CodeQL | SAST (gratuito em repositório público) | Alertas de alta severidade |
| Trivy | Imagem do container | HIGH/CRITICAL com correção disponível |
| OWASP ZAP baseline | DAST contra o compose (G4) | Relatório |
| CycloneDX | SBOM | Publicado no release |
| Imagens | Usuário não-root, tags pinadas, sem shell desnecessário | Revisão + Trivy |

### 2.6 Mapeamento OWASP API Security Top 10 (2023)
| Risco | Controle neste projeto | Teste |
|---|---|---|
| API1 BOLA | Comerciante vem do token; toda query filtra; cross-tenant → 404 | AC-010.x |
| API2 Autenticação quebrada | JWT validado (assinatura, iss, aud, exp); sem login próprio | Testes de segurança |
| API3 Exposição de propriedades | DTOs explícitos; propriedades desconhecidas rejeitadas | Teste de contrato |
| API4 Consumo irrestrito de recursos | `size ≤ 100`, intervalo ≤ 366 dias, payload máximo, timeouts, *rate limit* na borda | AC-004.2, AC-005.3 |
| API5 Autorização por função quebrada | Scopes por endpoint | Testes 403 |
| API6 Fluxos de negócio sensíveis | Idempotência, estorno único, valor máximo | RF-003, RF-007 |
| API7 SSRF | Sem chamadas de saída com URL do usuário | Revisão |
| API8 Má configuração | Cabeçalhos seguros, actuator restrito, sem *stack trace*, CORS fechado, Swagger UI apenas em `dev`/`local` | Testes de actuator, erros e de profile (Swagger indisponível em `prod`) |
| API9 Inventário | OpenAPI como fonte única; endpoint não documentado falha o teste | Teste de contrato |
| API10 Consumo inseguro de APIs | Evento validado por schema; DLT | AC-008.4 |

Referência complementar: OWASP ASVS nível 1 como lista de verificação de baseline em `docs/security/`.

### 2.7 Testes de risco obrigatórios (valem mais que cobertura)
| Risco | Teste | Tarefa |
|---|---|---|
| Idempotência concorrente | N chamadas paralelas, mesma chave → 1 lançamento, 1 evento, mesma resposta | T-012 |
| Chave órfã | Falha injetada após inserir a chave → nada persiste; retry funciona | T-012 |
| Atomicidade lançamento + outbox | Falha na outbox desfaz o lançamento | T-012 |
| Estorno concorrente | Duas requisições → um 201 e um 409 | T-014 |
| Fronteiras de data | Tabela de `03 §4.1` e limites ±30 dias | T-010 |
| Isolamento por comerciante | A × B em ambos os serviços | T-015, T-023 |
| Deduplicação concorrente | Mesmo evento em duas *threads*; upsert concorrente no mesmo dia | T-021 |
| DLT e reprocessamento | Evento vai à DLT; reinjeção aplica uma vez | T-022 |
| Kafka fora por período prolongado | POST estável, pool da API saudável, outbox cresce e drena | T-016, T-031 |
| Consolidado parado | POST estável, acúmulo no Kafka, convergência ao voltar | T-031 |
| Carga de pico | 50 req/s de leitura + escrita simultânea | T-033 |
A revisão (R1/R7) verifica que cada risco da tarefa tem teste.

### 2.8 Prioridade das ferramentas (evita virar "projeto de pipeline")
| Prioridade | Itens |
|---|---|
| **Essencial desde o início** (T-001..T-004 e toda tarefa de código) | Build reproduzível, Spotless, Checkstyle, SpotBugs, testes unitários, ArchUnit, integração com PostgreSQL/Kafka, JWT, idempotência, gitleaks |
| **Essencial antes de concluir** (M3) | Testes de falha, concorrência, isolamento e carga de 50 req/s; CodeQL, Dependency-Check, Trivy (T-034) |
| **Complementar / noturno** (G4) | PIT, ZAP, relatórios sofisticados, SBOM |
Dependências transversais (observabilidade, segurança de pipeline) entram na tarefa que as exige, via `allowed_new_dependencies`, não no primeiro commit.

## 3. Hooks locais (`.githooks/`, ativados por `git config core.hooksPath .githooks`)
- `pre-commit`: gitleaks nos arquivos *staged* + `spotless:check`.
- `pre-push`: `./mvnw -q verify -DskipITs`.
Hooks são contornáveis; por isso o CI repete tudo (G2/G3).

## 4. Pipeline (GitHub Actions)
- `ci.yml` (PR/push): *build + verify* (com Testcontainers), docs-lint.
- `security.yml` (PR + diário): CodeQL, Dependency-Check, gitleaks, Trivy.
- `loadtest.yml` (manual/noturno): compose + k6 + relatório como artefato.
- `release.yml` (tag): imagens (Spring Boot *build-image*), SBOM, publicação em GHCR.
- Proteção do `main`: PR obrigatório, checks obrigatórios, histórico linear.
- **Deploy em AWS:** apenas desenho (CD) na documentação; sem IaC no MVP.

## 5. Política de exceção
Supressão de qualquer regra exige comentário com motivo e referência de tarefa; o revisor da tarefa trata supressão nova como achado.
