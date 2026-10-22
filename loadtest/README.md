# Testes de carga (k6)

| Script | Objetivo | Duração típica |
|---|---|---|
| `smoke.js` | **Teste normal:** valida o contrato ponta a ponta (registro, idempotência, validação, segurança, consulta, convergência do consolidado) | ~1 min |
| `load.js` | **Teste de carga:** Consolidado a 50 req/s + escrita simultânea em Lançamentos (RNF-002, RNF-001) | 5 min + seed |

## Pré-requisitos
`k6`, JDK (para `scripts/DevToken.java`), serviços no ar (`scripts/demo.sh` documenta como subir).

## Execução
```bash
loadtest/run.sh smoke
loadtest/run.sh load
READ_RPS=100 loadtest/run.sh load     # informativo (RNF-010)
READ_RPS=200 loadtest/run.sh load     # informativo (RNF-010)
```
Cada execução grava, em `docs/performance/results/`, o resumo (`.json`) e o ambiente (`.env.txt`). **Nenhum número entra na documentação sem esses dois arquivos** (P8).

## Autenticação
- As rotas são protegidas por JWT. `run.sh` gera os tokens com `scripts/DevToken.java` (RS256, chave de desenvolvimento): um com todos os scopes e um **somente leitura** (para provar 403 na escrita).
- O `smoke.js` verifica 401 (sem token) e 403 (sem scope). Token expirado e token inválido ficam nos testes de fatia web da T-013 (determinísticos), não no k6.
- Os testes usam **um comerciante** de propósito: é o cenário do enunciado e o pior caso, com toda a escrita disputando a mesma linha `(comerciante, dia)` da projeção. Multi-comerciante é evolução.
- A validação do JWT (assinatura, `iss`, `aud`, `exp`) acontece em cada requisição e entra na medição. Localmente a chave é estática; em produção seria JWKS em cache do IdP. O IdP em si **não** está no caminho medido.
- `TOKEN_TTL` (padrão 3600 s) precisa exceder seed + `DURATION`; um 401 no meio da carga conta como falha.
- Rate limit de borda (gateway/WAF) não existe localmente; se for medido na AWS, a cota deve ficar acima do pico, pois 429 conta como falha.

## Como ler o resultado
- Falha = HTTP fora de 2xx, erro de conexão ou resposta > 2 s.
- `dropped_iterations > 0` invalida a rodada: o gerador não sustentou a taxa (aumente `maxVUs` ou rode em outra máquina).
- Lag de projeção (RNF-003) é lido em `consolidado_event_lag_seconds` (Prometheus/Grafana), não no k6.
- Rodar local mede o código, não a infraestrutura-alvo; a documentação deve dizer isso.

## Status
Rascunho especificado na T-033; será validado e ajustado contra os serviços reais.
