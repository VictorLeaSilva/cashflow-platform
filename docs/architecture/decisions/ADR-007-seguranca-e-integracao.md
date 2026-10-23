# ADR-007 — Modelo de segurança e critérios de integração

**Status:** Aceito · **Data:** 2026-10-22

Decisões cobertas: D-11

## Contexto
As APIs expõem dados financeiros de vários comerciantes e são consumidas por operadores e por sistemas integradores (`01 §4`). O risco principal é um comerciante ler ou estornar dados de outro (OWASP API1, BOLA). O enunciado pede, como diferencial, critérios de segurança para o consumo (integração) de serviços.

## Decisão
**Autenticação (D-11):** OAuth2 Resource Server com **JWT** validado em toda requisição. Não há login nem senha no sistema. A validação cobre:
| Verificação | Regra |
|---|---|
| Assinatura e algoritmo | Assinatura com a chave do emissor; só algoritmo assimétrico permitido (RS256); `alg: none` e algoritmos simétricos rejeitados |
| Emissor e audiência | `iss` igual ao emissor configurado; `aud` contendo a API |
| Validade | `exp` obrigatório; `nbf` respeitado quando presente (tolerância de relógio pequena e configurável) |
| Permissão | *Scope* exigido pelo endpoint (abaixo) |
| Vínculo com o comerciante | Claim `merchant_id` presente e no formato UUID; é a **única** fonte do comerciante na requisição |

- Case local: chave de desenvolvimento e token gerado por `scripts/DevToken.java` (a provar: T-013).
- Alvo (desenho): `issuer-uri` do IdP gerenciado (Cognito, ADR-010), chaves públicas por JWKS em cache.

**Tipos de cliente e fluxo de obtenção do token (alvo, desenho):**
| Cliente | Fluxo | Origem do `merchant_id` |
|---|---|---|
| Pessoa (comerciante/operador) em um front-end | OAuth2 *authorization code* + PKCE no Cognito | Atributo do usuário no IdP, incluído no token |
| Sistema integrador (máquina a máquina) | OAuth2 *client credentials*, uma credencial por cliente | Vínculo do *app client* com um comerciante, incluído no token pelo IdP |

No case local os dois tipos são simulados pelo `DevToken.java`; o case não tem front-end (`00 §7`).

**Autorização:**
- *Scopes* por endpoint: `lancamentos:write`, `lancamentos:read`, `consolidado:read` (403 sem *scope*).
- **Comerciante só pelo token:** `comercianteId` vem exclusivamente do claim `merchant_id`; nunca de corpo, *query* ou *path* (RF-010, AC-010.2). Toda consulta e todo comando filtram por comerciante; recurso de outro comerciante responde **404**, sem revelar existência. Os testes de isolamento cobrem leitura, listagem, consolidado **e** estorno cruzado (AC-010.1), não só o 403 por *scope*.

**Critérios de segurança para consumo (integração) de serviços** (`03 §6`):
| Critério | Como |
|---|---|
| Identidade do sistema integrador | OAuth2 *client credentials*, uma credencial por cliente, *scopes* mínimos |
| Canal | TLS na borda; **mTLS opcional** no *gateway* para integradores que o exijam |
| Abuso e volume | *Rate limit* por cliente na borda, com cota acima do pico do enunciado (50 req/s), para que o limite não vire "perda" |
| Recursos | Payload máximo, `size ≤ 100` na listagem, intervalo ≤ 366 dias no consolidado, timeouts explícitos (OWASP API4) |
| Repetição segura | `Idempotency-Key` obrigatória nos `POST` (ADR-003) |
| Eventos como entrada não confiável | O consumidor valida o JSON Schema; inválido ou não processável vai para a DLT com alerta (OWASP API10) |

**Dados e operação:** segredos fora do Git (variáveis de ambiente localmente; Secrets Manager no alvo); criptografia em repouso com KMS no alvo; logs sem token, segredo nem payload financeiro completo; erros em RFC 9457 sem *stack trace*; Actuator em porta separada expondo só `health` e `prometheus`; Swagger UI só em `dev`/`local`. Mapeamento completo OWASP API Top 10 em `04 §2.6` e `docs/security/` (T-034).

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Login próprio (usuário/senha) | Guardar e proteger senhas sem necessidade; reinventa um IdP |
| API keys estáticas | Sem expiração nem *scopes* padronizados; rotação manual; difícil auditar |
| `comercianteId` no corpo ou na URL | Abre BOLA: basta trocar o identificador para ler dados de outro comerciante |

## Consequências
- **Ganho:** padrão aberto, sem segredo de usuário guardado; isolamento por comerciante testável nos dois serviços (T-015, T-023).
- **Custo assumido:** dependência de um IdP no alvo; validação do JWT em cada requisição entra na latência medida (local: chave estática; alvo: JWKS em cache).
- Localmente não há *gateway*, WAF nem *rate limit*: esses controles são **desenho do alvo**, não provados no case.

## Revisitar quando
- Exigência de autorização mais fina (ex.: por loja ou por operador dentro do comerciante).
- Integradores exigirem mTLS obrigatório.

## Em aberto
- **Token válido sem `merchant_id` (ou com valor que não é UUID):** a spec não define o status. Proposta: rejeitar com **401**, como token inválido para esta API. Decidir antes da T-013 e registrar em `open-questions.md`.
- **Tolerância de relógio para `exp`/`nbf`:** usar o padrão do Spring Security (60 s) ou fixar outro valor; decidir na T-013, sem nova chave de configuração além das previstas na spec.

## Referências
RF-010, RNF-005; `03 §1` (D-11), `03 §5`, `03 §6`; `04 §2.6`; `01 §4`; T-013, T-015, T-023, T-034.
