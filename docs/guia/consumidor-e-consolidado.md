# Consumidor e consolidado: como o evento vira saldo

> Guia de conceito. Os termos técnicos estão no [glossário](glossario.md).

## Em uma frase

O Consolidado lê cada evento do Kafka, ignora os que já viu e soma o valor no dia do lançamento; o saldo de cada dia é calculado dessas somas na hora da consulta.

## Uma analogia

A contabilidade que recebe os bilhetes. Para cada bilhete, ela primeiro olha o **carimbo de "já lido"**: se o número do bilhete já está no livro de registros, joga fora. Se é novo, anota o número **e** soma o valor na página do dia, num único movimento, sem poder parar no meio. O saldo de um dia não é guardado: é calculado somando as páginas.

## O passo a passo, com um exemplo

**Leitura.** Um consumidor lê **um registro por vez** do tópico `cashflow.lancamentos.v1` (uma thread, mesmo com 3 partições, e até 100 registros por busca). O deslocamento (*offset*) só é confirmado **depois** que o banco confirma a gravação.

**Conferência do formato.** O evento é validado contra o contrato (JSON Schema). Evento malformado ou fora do contrato vai **direto** para a fila de falhas (DLT), sem tentar de novo.

**Aplicação, num único comando SQL.** Se o `eventId` é novo, o comando registra o `eventId` em `evento_processado` **e** soma o efeito na linha `(comerciante, dia)` de `consolidado_diario`, na mesma transação. Se o `eventId` já existia, nada é somado. Não há "ler, calcular, gravar": a soma é feita pelo próprio banco (`total = total + valor`), por isso eventos simultâneos do mesmo dia não perdem valor.

**Efeito de cada tipo** (a data usada é a do evento, nunca recalculada):

| Tipo do evento | Soma em |
|---|---|
| `CREDITO` | total de créditos do dia |
| `DEBITO` | total de débitos do dia |
| `ESTORNO` de um crédito | total de estornos de crédito, **no dia do estorno** |
| `ESTORNO` de um débito | total de estornos de débito, **no dia do estorno** |

**Saldo do dia** = (créditos − estornos de crédito) − (débitos − estornos de débito). Pode ser negativo.

*Exemplo (os valores da demo):* num dia, crédito 1000,00, débito 250,00 e crédito 80,00: saldo do dia = 1080,00 − 250,00 = **830,00**. Estornar o débito soma 250,00 em "estornos de débito" naquele dia: saldo = (1080,00 − 0) − (250,00 − 250,00) = **1080,00**. Um crédito retroativo de 500,00 de três dias atrás soma no dia dele.

**Consulta (`GET /api/v1/consolidado?dataInicio=...&dataFim=...`).**
- Os dias sem movimento aparecem com zeros (preenchimento de lacunas), de `dataInicio` a `dataFim`, no máximo **366 dias** contando os dois extremos.
- **Saldo anterior** = a soma do saldo do dia de **todos** os dias antes de `dataInicio`.
- **Saldo acumulado** de cada dia = saldo anterior + os saldos dos dias do intervalo até aquele dia.
- *Exemplo:* intervalo de D-3 a D, sem nada antes de D-3: saldo anterior 0,00; D-3: dia 500,00 e acumulado 500,00; D-2 e D-1: dia 0,00 e acumulado 500,00; D: dia 1080,00 e acumulado **1580,00**.
- As duas leituras (os dias e o saldo anterior) usam o mesmo retrato do banco (transação somente de leitura), então o consumidor não altera o resultado no meio da consulta.

**Por que a ordem dos eventos não importa.** Somar é comutativo: 500 + 1080 dá o mesmo que 1080 + 500. Um retroativo só aumenta o dia dele e o acumulado dali em diante; um estorno só soma no dia dele. Por isso o sistema não exige eventos em ordem e aceita repetidos.

**Quando falha.** Erro recuperável (por exemplo, o banco indisponível): **1 tentativa + 3 novas**, com esperas de 1 s, 2 s e 4 s; depois, o evento vai para a DLT, na mesma partição, e o consumo segue. O contador da DLT só sobe depois que a DLT confirma o recebimento. Se a DLT estiver fora, o registro não é confirmado e volta a ser tentado. Evento na DLT significa **consolidado incompleto** até alguém corrigir e reinjetar (veja o [runbook](../runbooks/dlt-reprocessamento.md)).

## Onde está no código

| Peça | Ponteiro | O que faz |
|---|---|---|
| Leitura do tópico | `ConsumidorDeLancamentos#consumir` | Um registro por vez; aplica e registra métricas e log |
| Tentativas e DLT | `ConsumidorConfig` | 1 + 3 tentativas com 1, 2 e 4 s; DLT na mesma partição; confirmação por registro |
| Conferência do contrato | `LeitorDeEvento#ler` | Valida o JSON Schema e monta o evento |
| Correlação em erro | `TratamentoDeErroComCorrelacao` | Coloca `requestId` e `eventId` nos logs de retry e DLT |
| Aplicação | `AplicarEvento#aplicar` | Devolve aplicado ou duplicado |
| Regra de efeito | `Efeito#de` | Converte o evento em soma por tipo |
| Soma e saldo do dia | `MovimentoDiario#saldoDia` | Calcula o saldo do dia |
| SQL único | `ProjecaoJdbcRepository#aplicarSeNovo` | Deduplica e soma, atomicamente |
| Caso de uso da consulta | `ConsultarConsolidado#intervalo` | Preenche dias, calcula saldo anterior e acumulado |
| SQL da consulta | `ConsultaConsolidadoJdbcRepository#ler` | Lê os dias e soma o histórico anterior |
| Rotas | `ConsolidadoController` | `GET /api/v1/consolidado` e `/{data}` |
| Tabelas | `consolidado-service/src/main/resources/db/migration/V1__esquema_inicial.sql` | `consolidado_diario` e `evento_processado` |

## Como ver funcionando

Com a stack no ar e a demonstração já rodada (veja o [como funciona](como-funciona.md#como-ver-funcionando)), veja as somas e a lista de eventos já vistos:

**bash e PowerShell**

```bash
docker compose --env-file .env -f deploy/docker-compose.yml exec postgres psql -U postgres -d consolidado -c "SELECT data_referencia, total_creditos, total_debitos, total_estornos_debito, qtd_lancamentos FROM consolidado_diario ORDER BY data_referencia DESC LIMIT 5"
docker compose --env-file .env -f deploy/docker-compose.yml exec postgres psql -U postgres -d consolidado -c "SELECT count(*) AS eventos_vistos FROM evento_processado"
```

**bash (Linux, macOS e Git Bash)**

```bash
curl -s http://localhost:9082/actuator/prometheus | grep -E '^(consolidado_events_processed_total|consolidado_events_duplicates_total|consolidado_events_dlt_total)'
```

**PowerShell (Windows)**

```powershell
curl.exe -s http://localhost:9082/actuator/prometheus | Select-String '^(consolidado_events_processed_total|consolidado_events_duplicates_total|consolidado_events_dlt_total)'
```

**O que você deve ver:** uma linha por dia com movimento (hoje e, se rodou a demo, três dias atrás) e `eventos_vistos` igual ao total de eventos aplicados desde que o banco existe. Depois da demo, os totais de hoje incluem o débito de 10,00 que ela registra enquanto o Consolidado está parado (por isso 260,00 de débitos e 250,00 de estornos de débito, 5 lançamentos), e `eventos_vistos` é 6. `consolidado_events_dlt_total` fica em `0.0`.

**Os contadores zeram quando o serviço reinicia.** A demo para e religa o Consolidado, então logo depois dela `consolidado_events_processed_total` mostra só os eventos aplicados desde a religação (no teste foi **1**, o evento que esperou), muito menos que os 6 de `eventos_vistos`. Quem guarda a verdade é o banco; a métrica serve para ver o ritmo.

## O que pode dar errado

- **O saldo anterior soma todo o histórico.** Com muitos anos de dados a consulta fica mais cara; a evolução prevista (saldos periódicos) está no [ADR-005](../architecture/decisions/ADR-005-consistencia-eventual-e-projecao.md). Só foi medida com 60 lançamentos de teste.
- **A deduplicação é só por `eventId`.** Dois eventos diferentes do mesmo lançamento somariam duas vezes; o sistema não os produz.
- **O estorno aparece no dia do estorno, não no do original.** O dia do original continua igual, e o saldo do dia do estorno pode ficar negativo.
- **O Consolidado parece "atrasado".** O saldo está sempre alguns instantes atrás do Lançamentos (consistência eventual); veja o atraso em [observabilidade por dentro](observabilidade-por-dentro.md).

## Ligações

- Decisão: [ADR-005](../architecture/decisions/ADR-005-consistencia-eventual-e-projecao.md) e [ADR-004](../architecture/decisions/ADR-004-kafka-e-contrato-de-evento.md); modelo e leitura em [`03 §3.2`](../specs/03-design-contracts.md).
- Requisitos: RF-005, RF-006, RF-008, RF-009 e RNF-004. Provas: `DltIT`, `DeduplicacaoConcorrenteIT`, `EfeitoTest`, `LeitorDeEventoTest`, `ConsultarConsolidadoTest` e `ConfiguracaoDoConsumidorIT`. A matriz está em [`traceability.md`](../specs/traceability.md).
