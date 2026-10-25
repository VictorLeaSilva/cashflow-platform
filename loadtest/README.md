# Testes de carga (k6)

| Script | Objetivo | Duração típica |
|---|---|---|
| `smoke.js` | **Teste normal:** valida o contrato ponta a ponta (registro, idempotência, validação, segurança, consulta, convergência do consolidado) | ~10 s |
| `load.js` | **Teste de carga:** Consolidado a 50 req/s + escrita simultânea em Lançamentos (RNF-002, RNF-001) | 5 min + seed |

Resultados, ambiente e leitura dos números: [`docs/performance/relatorio.md`](../docs/performance/relatorio.md).

## Pré-requisitos
Docker, `bash`, JDK (para `scripts/DevToken.java`), `curl`, `jq` e `git`. **O k6 não precisa ser instalado:** o `run.sh` usa a imagem `grafana/k6` (tag fixa) na rede do compose. A stack dos serviços precisa estar no ar com o *profile* `apps` (o [README da raiz](../README.md#carga-e-desempenho-k6) tem o passo a passo, em bash e PowerShell).

## Execução
```bash
loadtest/run.sh inspect               # só a sintaxe dos scripts (não precisa da stack)
loadtest/run.sh smoke
loadtest/run.sh load                  # a rodada oficial: 50 req/s de leitura + 10 req/s de escrita, 5 min
READ_RPS=100 loadtest/run.sh load     # informativo (RNF-010)
READ_RPS=200 loadtest/run.sh load     # informativo (RNF-010)
```
Cada execução grava, em `docs/performance/results/`, o resumo do k6 (`.json`), o ambiente (`.env.txt`) e, no `load`, o lag de projeção (`.lag.json`). **Nenhum número entra na documentação sem esses arquivos** (P8).

Variáveis: `READ_RPS`, `WRITE_RPS`, `DURATION`, `TOKEN_TTL`, `ENV_FILE` (padrão `.env`), `COMPOSE`, `K6_IMAGE`, `PROM_IMAGE`, `K6_NETWORK`, `RESULTS_DIR` e `ENV_NOTE` (texto livre sobre a máquina, gravado no `.env.txt`).

- **Rodada oficial:** `READ_RPS=50`, `WRITE_RPS=10` e `DURATION=5m`. Se um limiar falhar, o `run.sh` termina com erro.
- **Rodada informativa:** qualquer outro valor. O resultado é gravado e o `run.sh` não falha por limiar; o `.env.txt` diz se os limiares passaram.

## Como funciona
- O `run.sh` descobre a rede do compose pelo contêiner do Lançamentos e roda o k6 nela, apontando para `lancamentos:8081` e `consolidado:8082`. As portas do host não importam.
- No `load`, sobe também um Prometheus próprio da rodada (`prometheus.yml`, nesta pasta), que raspa as portas de gestão dos dois serviços pela mesma rede. Assim a medição funciona com a stack isolada de qualquer projeto compose, sem depender do profile `observability`.
- Fim da rodada: espera a outbox drenar e consulta o Prometheus: p50 e p95 de `consolidado_event_lag_seconds` (diferença fim menos início, interpolada pelos buckets) e o máximo (`consolidado_event_lag_seconds_max` na janela). A janela vai do início da rodada, com o seed, até a drenagem.

## Autenticação
- As rotas são protegidas por JWT. `run.sh` gera os tokens com `scripts/DevToken.java` (RS256, chave de desenvolvimento): um com todos os scopes e um **somente leitura** (para provar 403 na escrita).
- O `smoke.js` verifica 401 (sem token) e 403 (sem scope). Token expirado e token inválido ficam nos testes de fatia web da T-013 (determinísticos), não no k6.
- Os testes usam **um comerciante** de propósito: é o cenário do enunciado e o pior caso, com toda a escrita disputando a mesma linha `(comerciante, dia)` da projeção. Multi-comerciante é evolução.
- A validação do JWT (assinatura, `iss`, `aud`, `exp`) acontece em cada requisição e entra na medição. Localmente a chave é estática; em produção seria JWKS em cache do IdP. O IdP em si **não** está no caminho medido.
- `TOKEN_TTL` (padrão 3600 s) precisa exceder seed + `DURATION`; um 401 no meio da carga conta como falha.
- Rate limit de borda (gateway/WAF) não existe localmente; se for medido na AWS, a cota deve ficar acima do pico, pois 429 conta como falha.

## Como ler o resultado
- Falha = HTTP fora de 2xx, erro de conexão ou resposta > 2 s.
- Limiares da rodada oficial (RNF-002): falha da leitura < 5 %, p95 da leitura < 500 ms, falha da escrita < 1 % e `dropped_iterations` = 0. A latência da escrita e os `checks` são só reportados.
- `dropped_iterations > 0` invalida a rodada: o gerador não sustentou a taxa (aumente `maxVUs` ou rode em outra máquina).
- Rodar local mede o código, não a infraestrutura-alvo; gerador e serviços dividem a mesma máquina, e a documentação diz isso.
