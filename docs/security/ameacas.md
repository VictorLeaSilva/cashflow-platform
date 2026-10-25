# Modelo de ameaças (STRIDE enxuto)

Uma tabela por fronteira de confiança. Cada linha traz a ameaça, o controle que existe hoje e o que fica como evolução. Os controles citam os documentos de decisão (`ADR-007` segurança, `ADR-003` outbox, `ADR-004` evento) e o mapeamento de testes em [`owasp-mapping.md`](owasp-mapping.md). Escopo: o case local; a borda (gateway, WAF, TLS, IdP) é desenho do alvo (`ADR-010`) e aparece como controle "da borda".

```text
Cliente (comerciante, integrador)
   │  HTTPS (borda) ── JWT ──►  [F1] Lançamentos API ──[F3]──► PostgreSQL (lancamentos)
   │                            [F1] Consolidado API ─[F3]──► PostgreSQL (consolidado)
   └────────────────────────    Lançamentos ──[F2]── Kafka ──[F2]──► Consolidado
```

Legenda STRIDE: **S** falsificação, **T** adulteração, **R** repúdio, **I** vazamento de informação, **D** negação de serviço, **E** elevação de privilégio.

## F1. Cliente → API (Lançamentos e Consolidado)
| STRIDE | Ameaça | Controle hoje | Evolução |
|---|---|---|---|
| S | Token forjado, de outro emissor ou de outra API | RS256 com a chave pública configurada; `iss`, `aud`, `exp` e `nbf` validados; `alg: none` e HS256 rejeitados (ADR-007) | JWKS com cache do IdP gerenciado (Cognito) no alvo |
| S | Comerciante informado no corpo ou na query para se passar por outro | O comerciante vem só do claim `merchant_id`; `comercianteId` na entrada é 400 | — |
| T | Corpo adulterado ou com campos extras (mass assignment) | Jackson estrito (propriedade desconhecida ou duplicada → 400), texto livre sem controle e normalizado em NFC, datas e valores estritos | — |
| T | Replay de POST que duplicaria um lançamento | `Idempotency-Key` obrigatória; replay devolve a mesma resposta; mesma chave com outro corpo → 422 | — |
| R | Negar ter registrado ou estornado um lançamento | `request_id` e `traceId` em log de acesso, na outbox e no evento; estorno é um lançamento novo e imutável referenciando o original (`AuditoriaEstornoWebTest`) | Trilha de auditoria imutável fora do banco da aplicação |
| I | BOLA: ler ou estornar dado de outro comerciante | Toda consulta filtra pelo comerciante do token; dado de outro → 404 igual ao inexistente | — |
| I | Vazamento por erro (stack trace, classe, mensagem interna), por cache ou por cabeçalho | `problem+json` genérico, `include-*` desligados, `Cache-Control: no-store`, `nosniff`, CSP `default-src 'none'`, `Referrer-Policy: no-referrer`; Actuator só em porta de gestão com `health` e `prometheus` | HSTS e TLS na borda |
| I | CORS aberto deixa um site de terceiros ler a API pelo navegador do usuário | CORS fechado: nenhuma origem permitida; `OPTIONS` em `/api/v1/**` → 405 | — |
| D | Corpo ou cabeçalho enormes, consultas gigantes, conexões penduradas | Corpo ≤ 16 KiB (413), cabeçalho ≤ 8 KiB (o Tomcat recusa, Q-080), `size` ≤ 100, intervalo ≤ 366 dias, *timeout* de conexão de 5 s, pools JDBC limitados | *Rate limit* e WAF na borda (ADR-010) |
| E | Escrita com token só de leitura, ou leitura de outro serviço | *Scopes* por operação (`lancamentos:write`, `lancamentos:read`, `consolidado:read`); 403 sem o *scope* | — |
| E | Swagger UI ou contrato expostos em ambiente real | Só no profile `local`; sem o profile as rotas nem existem (404) | — |

## F2. Lançamentos → Kafka → Consolidado
| STRIDE | Ameaça | Controle hoje | Evolução |
|---|---|---|---|
| S | Produtor falso publicando eventos no tópico | Broker local sem exposição fora da máquina; evento validado por JSON Schema antes de qualquer efeito | ACLs e autenticação do broker (MSK/IAM) no alvo |
| T | Evento adulterado ou fora do contrato | Schema do artefato `contracts` (único, sem cópia no serviço); inválido vai direto para a DLT sem alterar a projeção | Assinatura do evento, se o broker deixar de ser confiável |
| T | Entrega duplicada ou fora de ordem altera o saldo | Deduplicação por `eventId` (`evento_processado` na mesma transação do efeito); projeção por soma, comutativa | — |
| R | Dúvida sobre de onde veio um evento | `eventId`, `occurredAt`, `x-request-id` e `traceparent` nos cabeçalhos; o Consolidado registra a origem no log | — |
| I | Dado financeiro demais no log ou no evento | Logs sem token, segredo nem payload financeiro completo; mensagem de violação do schema não traz valores | — |
| D | Kafka fora, consumidor parado ou evento "veneno" travando a partição | Outbox absorve (POST segue 201), produtor com *timeouts* curtos, pool próprio do publicador, DLT após as tentativas, alertas e runbook de reprocessamento (T-016, T-022, T-031) | — |
| E | Consumidor aplicando evento de um comerciante em outro | O comerciante vem do evento validado e a projeção é por `(comerciante, dia)`; teste de isolamento | — |

## F3. Serviços → banco
| STRIDE | Ameaça | Controle hoje | Evolução |
|---|---|---|---|
| S | Uso de credencial de outro serviço | Um banco e usuários próprios por serviço (`lancamentos_app`, `consolidado_app`), senhas só por variável de ambiente, nada no repositório (gitleaks) | Segredos no Secrets Manager no alvo |
| T | SQL injection | Só consultas parametrizadas (`JdbcClient` com parâmetros nomeados); nenhum SQL montado com entrada | — |
| T | Alteração ou DDL pela aplicação | A aplicação opera sem DDL (`*_app`: SELECT/INSERT/UPDATE conforme a tabela); migrações pelo `*_owner` | — |
| I | Vazamento de dado entre comerciantes por consulta sem filtro | Todo acesso leva o comerciante; teste de isolamento nos dois serviços | RLS no banco, se o modelo multi-inquilino crescer |
| D | Esgotamento de conexões por carga ou por falha do Kafka | Pools separados (API, publicador, consumidor) com limite e métricas de ocupação; medição em `docs/performance/relatorio.md` | — |
| E | Imagem ou dependência vulnerável dando acesso ao host | Contêineres não-root, tags fixas, Trivy nas imagens e Dependency-Check nos jars (`security.yml`), Dependabot semanal | SBOM no release (`release.yml`) |

## Riscos aceitos
- **Rodar sem TLS localmente** (HTTP): o case é local; TLS e HSTS são da borda.
- **Chave JWT estática de desenvolvimento** (`scripts/DevToken.java`): só no profile `local`; o alvo usa o IdP gerenciado.
- **Um comerciante por token, sem papéis dentro do comerciante:** operadores e integradores do mesmo comerciante enxergam o mesmo conjunto (evolução se surgirem papéis).
