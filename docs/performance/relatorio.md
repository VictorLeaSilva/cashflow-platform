# Relatório de desempenho

Medição de **RNF-002** (capacidade do Consolidado), **RNF-003** (frescor da projeção) e **RNF-010** (escala, rodadas informativas), feita na T-033 em **2026-10-25**. Todo número abaixo vem dos arquivos de `docs/performance/results/` citados na [seção 7](#7-arquivos-das-rodadas) (regra P8: número só entra na documentação com medição, data e ambiente).

## 1. Resumo

| Requisito | Meta | Resultado medido | Veredito |
|---|---|---|---|
| RNF-002 leitura, 50 req/s por 5 min | falha < 5 % (meta interna 1 %), p95 ≤ 500 ms | 0 falhas em 15 001 requisições; p95 de 6,3 ms | Atende, com folga |
| RNF-002 escrita simultânea, 10 req/s | sucesso ≥ 99 % | 0 falhas em 3 001 requisições; 100 % de sucesso | Atende |
| RNF-002 validade da rodada | `dropped_iterations` = 0 | 0 | Rodada válida |
| RNF-003 frescor da projeção sob a carga | p95 ≤ 5 s, máximo ≤ 30 s | p95 de 0,69 s; máximo de 0,98 s | Atende, com folga |
| RNF-010 informativo, 100 e 200 req/s | só registrar | Os limiares do 50 req/s também passaram (p95 de leitura de 4,7 ms e 4,9 ms; lag p95 de 0,69 s e 0,61 s) | Registrado; o teto do sistema **não** foi encontrado |

**Leitura honesta:** neste ambiente, com este cenário (um comerciante, 30 dias de histórico), o Consolidado ficou muito longe do limite, mesmo a 200 req/s, quatro vezes o pedido pelo enunciado. Isso mostra folga, **não** a capacidade máxima: não houve teste de estresse até a quebra (fora do escopo da T-033).

## 2. Ambiente

| Item | Valor |
|---|---|
| Data | 2026-10-25 (UTC, de 09:56 a 10:14) |
| Máquina | Notebook com Intel Core i5-1334U (10 núcleos, 12 processadores lógicos), 15,7 GB de RAM, Windows 11 Home |
| Docker | Docker Desktop 29.9.2 (WSL2), daemon `linux/amd64` com **12 CPUs e 8,2 GB** disponíveis para os contêineres |
| Alvo | Profile `apps` do compose (T-035): `postgres`, `kafka`, `lancamentos` e `consolidado` em contêineres, sem limite de CPU ou memória por contêiner. Imagens `fluxo-caixa/*:0.1.0-SNAPSHOT` (Paketo, `linux/amd64`) |
| Gerador | `grafana/k6:1.7.0` em contêiner, na mesma rede do compose (`lancamentos:8081` e `consolidado:8082`) |
| Lag | Prometheus `v3.15.1` próprio da rodada, raspando as portas de gestão a cada 5 s (`loadtest/prometheus.yml`) |
| Isolamento | Stack de teste `demo-t033-bash`, criada do zero antes da sequência (`down -v` e `up`). Nenhum outro processo do projeto rodava (stack `fluxo-caixa` e Maven parados) |
| Código | Base `c816d35` (o campo `git_sha` dos `.env.txt`) mais os scripts de `loadtest/` desta tarefa, ainda sem commit na hora da medição |

**Limitações do ambiente (parte do resultado):**
1. **Gerador e serviços dividem a mesma máquina** (e o mesmo Docker). Os números medem o código, não a infraestrutura-alvo da AWS.
2. **Uma rodada por taxa.** Não há repetição, então a variação entre rodadas é desconhecida. As três taxas dão resultados consistentes entre si.
3. **Um único comerciante**, de propósito: é o pior caso para a projeção, com toda a escrita disputando a mesma linha `(comerciante, dia)`. Multi-comerciante não foi medido.
4. **Dados pequenos:** 30 dias com 2 lançamentos cada (60 lançamentos de *seed*). A leitura de 30 dias devolve 30 linhas.
5. A sequência rodou sem reiniciar a stack (smoke, 50, 100, 200), então as rodadas maiores encontraram a JVM mais aquecida. A rodada de 50 req/s foi a primeira carga depois do smoke e já passou com folga.
6. Os picos de pool e de fila vêm de raspagens a cada 5 s: picos mais curtos podem não aparecer.

## 3. Como foi medido

Comandos (a partir da raiz, com a stack `apps` no ar; o README tem o passo a passo em bash e PowerShell):

```bash
loadtest/run.sh inspect
loadtest/run.sh smoke
loadtest/run.sh load                  # rodada oficial: 50 req/s de leitura + 10 req/s de escrita, 5 min
READ_RPS=100 loadtest/run.sh load     # informativa
READ_RPS=200 loadtest/run.sh load     # informativa
```

- **Cenário do `load`:** `constant-arrival-rate` de leitura em `GET /api/v1/consolidado` (80 % intervalo de 30 dias, 20 % um dia) e de escrita em `POST /api/v1/lancamentos` (10 req/s), por 5 minutos, depois de um *seed* de 60 lançamentos (30 dias) que espera a projeção convergir. O comerciante é novo a cada rodada.
- **Falha** = HTTP fora de 2xx, erro de conexão ou resposta acima de 2 s (RNF-002).
- **Limiares da rodada oficial:** falha da leitura < 5 %, p95 da leitura < 500 ms, falha da escrita < 1 % e `dropped_iterations` < 1. A latência da escrita, a de cada consulta e os `checks` são só reportados (Q-073).
- **Lag de projeção:** histograma `consolidado_event_lag_seconds` (commit da projeção menos `occurredAt`, 03 §8). A janela vai do início da rodada (com o *seed*) até a outbox drenar. O p50 e o p95 saem da diferença entre os buckets no fim e no início da janela, interpolada pelos buckets do histograma (aproximação); o máximo vem do gauge `consolidado_event_lag_seconds_max` (Q-072).
- **Verificação de sintaxe:** `loadtest/run.sh inspect` roda `k6 inspect` nos dois scripts, pela imagem do k6.

## 4. Resultados

### 4.1 Teste normal (`smoke`)

90 verificações, **100 % passaram**, 71 requisições, p95 de 52 ms, `k6` com exit 0. Cobre registro, idempotência (replay e conflito), validação (400), segurança (401 e 403), consultas e convergência do consolidado em até 20 s. Arquivos: `smoke-20261025T095655Z.*`.

### 4.2 Carga a 50 req/s (rodada oficial, RNF-002)

| Métrica | Leitura (`flow:read`) | Escrita (`flow:write`) |
|---|---|---|
| Requisições | 15 001 | 3 001 |
| Falhas | 0 (0,00 %) | 0 (0,00 %) |
| Latência p50 | 3,4 ms | 34,0 ms |
| Latência p95 | **6,3 ms** | 117,6 ms |
| Latência p99 | 9,4 ms | 171,1 ms |
| Latência máxima | 22,3 ms | 333,0 ms |

Por consulta, o p95 foi de 6,4 ms no intervalo de 30 dias e de 6,2 ms no dia único. `dropped_iterations` = 0 e 18 002 verificações (`checks`) passaram, 100 %. Exit do k6 = 0 (todos os limiares passaram). Arquivos: `load-50rps-20261025T095711Z.*`.

### 4.3 Rodadas informativas (RNF-010)

Não são metas; o `run.sh` grava o resultado e registra se os limiares passaram (todos passaram nas duas).

| Taxa de leitura | Falhas na leitura | p95 leitura | p99 leitura | Máx. leitura | Falhas na escrita | p95 escrita | `dropped_iterations` |
|---|---|---|---|---|---|---|---|
| 100 req/s | 0 de 30 001 | 4,7 ms | 7,0 ms | 19,5 ms | 0 de 3 001 | 111,8 ms | 0 |
| 200 req/s | 0 de 60 001 | 4,9 ms | 7,1 ms | 32,9 ms | 0 de 3 001 | 91,2 ms | 0 |

A latência de leitura não cresceu com a taxa (a de 50 req/s, a primeira após o smoke, foi até um pouco maior, com a JVM menos aquecida). Taxa efetiva medida a 200 req/s de leitura: 209 iterações por segundo no total (leitura mais escrita). Arquivos: `load-100rps-20261025T100305Z.*` e `load-200rps-20261025T100856Z.*`.

### 4.4 Frescor da projeção (RNF-003)

Eventos aplicados em cada janela: 3 061 (60 do *seed* e 3 001 da escrita simultânea). A escrita foi de 10 req/s nas três rodadas; só a leitura mudou.

| Rodada | p50 | p95 | Máximo | Meta |
|---|---|---|---|---|
| 50 req/s | 0,40 s | **0,69 s** | 0,98 s | p95 ≤ 5 s, máximo ≤ 30 s |
| 100 req/s | 0,38 s | 0,69 s | 2,22 s | idem |
| 200 req/s | 0,35 s | 0,61 s | 0,88 s | idem |

**Atende a meta nas três rodadas**, com o p95 cerca de sete vezes abaixo do limite e o máximo de 2,2 s contra 30 s. O p50 de cerca de 0,4 s é compatível com o intervalo de 500 ms do publicador. A leitura pesada não atrasou a projeção: o p95 ficou estável de 50 a 200 req/s, que era a pergunta do limite "Isolamento de capacidade do Consolidado" (03 §7.1).

### 4.5 Operação durante a carga

Do `.lag.json` e do `.stats.txt` de cada rodada (picos de raspagens a cada 5 s):

| Item | 50 req/s | 100 req/s | 200 req/s |
|---|---|---|---|
| Eventos pendentes na outbox (pico) | 7 | 7 | 6 |
| Idade máxima de um evento pendente | 0,73 s | 0,74 s | 0,62 s |
| Lag do consumidor Kafka (pico) | 0 registros | 0 registros | 0 registros |
| Falhas de publicação | 0 | 0 | 0 |
| Eventos na DLT | 0 | 0 | 0 |
| Conexões ativas (pico) por pool (máximo do pool) | API Lançamentos 1 (10), publicador 1 (2), API Consolidado 0 (10), consumidor 1 (4) | idem | idem |
| Conexões pendentes por pool (pico) | 0 | 0 | 0 |
| CPU do contêiner do Consolidado: média e pico (em núcleos) | 0,21 e 0,52 | 0,23 e 0,40 | 0,39 e 0,67 |
| CPU do contêiner do Lançamentos: média e pico (em núcleos) | 0,09 e 0,23 | 0,07 e 0,36 | 0,05 e 0,29 |
| Memória dos contêineres (pico) | Lançamentos 327 MiB, Consolidado 368 MiB | 337 e 369 MiB | 365 e 371 MiB |

O Consolidado é o contêiner mais carregado e ainda usou menos de 0,7 núcleo a 200 req/s. Isso, com a latência estável, indica que o gargalo não estava nos serviços nesta faixa.

## 5. Leitura dos resultados

- **RNF-002 atendido.** A falha da leitura foi zero, contra o limite de 5 % do enunciado e a meta interna de 1 %. O p95 de 6,3 ms está muito abaixo dos 500 ms. A escrita simultânea não teve nenhuma falha, o que sustenta o isolamento (RNF-001): ler a 50 req/s não derrubou a escrita.
- **RNF-003 atendido.** O frescor ponta a ponta ficou em menos de 1 s no p95, e o máximo observado foi de 2,2 s.
- **RNF-010:** nesta máquina, 100 e 200 req/s passaram nos mesmos limiares. A afirmação "escala" segue a documentação (serviços *stateless*, escala horizontal, ADR); o que esta medição acrescenta é que o ponto de partida tem folga de pelo menos 4x sobre o enunciado.
- **Por que tão rápido (pelo desenho, 03):** a leitura é uma consulta a uma tabela de projeção pequena (uma linha por comerciante e dia). O número deve ser lido como "o código e o desenho não são o gargalo nesta escala", não como previsão para a infraestrutura-alvo nem para uma base com muitos comerciantes e anos de histórico.
- **Latência de escrita** (p95 de 91 a 118 ms): bem acima da leitura, como esperado (transação com idempotência e outbox), sem meta própria no RNF-002. É informação para a evolução, não um problema.

## 6. Decisões e acompanhamento

- **AC-T033.4:** nenhum limiar deixou de passar, então não há revisão de meta nem ADR. Nenhum limiar foi alterado para passar: os limiares do `load.js` são os do RNF-002 e do AC-T033.2. Os limiares extras do rascunho (latência da escrita e `checks`) foram **retirados** por não terem base no requisito (Q-073).
- **AC-T033.6:** `03 §7.1` revisto com estes números: nenhum limite mudou (cada linha tem a medição que a sustenta). Os limiares de alerta do ADR-008 e de `deploy/prometheus/alertas.yml` também ficam como estão: o sistema operou a cerca de um décimo dos limiares (lag p95 de 0,7 s contra 5 s; outbox de menos de 1 s contra 60 s) e sem falhas (Q-071).
- **Para repetir com mais rigor:** em outra máquina e com o gerador separado dos serviços, com várias rodadas por taxa; com vários comerciantes; com a base crescida (milhões de lançamentos); e a 400 req/s ou mais, até a quebra, para achar o teto. Nada disso foi feito aqui.

## 7. Arquivos das rodadas

Todos em `docs/performance/results/` (`.json` = resumo do k6, `.env.txt` = ambiente, `.lag.json` = lag e operação, `.stats.txt` = CPU e memória dos contêineres):

| Rodada | Arquivos |
|---|---|
| smoke | `smoke-20261025T095655Z.json` e `.env.txt` |
| load, 50 req/s (oficial) | `load-50rps-20261025T095711Z.json`, `.env.txt`, `.lag.json` e `.stats.txt` |
| load, 100 req/s (informativa) | `load-100rps-20261025T100305Z.json`, `.env.txt`, `.lag.json` e `.stats.txt` |
| load, 200 req/s (informativa) | `load-200rps-20261025T100856Z.json`, `.env.txt`, `.lag.json` e `.stats.txt` |
