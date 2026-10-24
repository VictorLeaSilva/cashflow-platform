# Runbook · Reprocessamento da DLT do Consolidado

Política em `03 §7.2`; criado na T-022. Um evento na `cashflow.lancamentos.v1.DLT` significa que o **consolidado daquele comerciante está incompleto** até o reprocessamento. A fonte de verdade é a tabela `lancamento`, do serviço de Lançamentos.

Os comandos abaixo valem para o ambiente local (`docker compose -p fluxo-caixa -f deploy/docker-compose.yml`), em **PowerShell**, a partir da raiz do repositório; as ferramentas do Kafka e o `psql` rodam dentro dos contêineres. No alvo (ADR-010), os passos são os mesmos, com as ferramentas do MSK e do RDS.

## 1. Alerta e triagem
O alerta `EventoNaDlt` (`deploy/prometheus/alertas.yml`) dispara quando `consolidado_events_dlt_total` aumenta; o painel do Grafana mostra o contador e o lag da projeção (`--profile observability`, README). Os logs do Consolidado trazem a correlação do evento nas linhas de tentativa e de DLT: `eventId`, `requestId` (o `x-request-id` da requisição de origem), `traceIdOrigem` (o `traceId` da requisição que criou o lançamento, preenchido no evento) e `traceId` (o trace do publicador, propagado pelo cabeçalho `traceparent`). Com qualquer um deles se acha a mesma operação nos logs dos dois serviços. Para cada mensagem na DLT, leia os cabeçalhos:

```powershell
docker compose -p fluxo-caixa -f deploy/docker-compose.yml exec kafka `
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 `
  --topic cashflow.lancamentos.v1.DLT --from-beginning --timeout-ms 10000 `
  --property print.partition=true --property print.offset=true `
  --property print.key=true --property print.headers=true
```

| Cabeçalho | Uso |
|---|---|
| `kafka_dlt-original-topic`, `kafka_dlt-original-partition`, `kafka_dlt-original-offset` | Origem da mensagem. O tópico vem em texto; partição e *offset* vêm em **binário** (`int` e `long` *big-endian*), e o console os mostra ilegíveis. Para a triagem, identifique a mensagem pelo `eventId` do valor e pela partição e *offset* da própria DLT (`print.partition`/`print.offset`) |
| `kafka_dlt-exception-cause-fqcn` | **Causa real**: a classe que o consumidor lançou |
| `kafka_dlt-exception-fqcn` | O *wrapper* do spring-kafka (`ListenerExecutionFailedException`); serve pouco para a triagem |
| `kafka_dlt-exception-message`, `kafka_dlt-exception-stacktrace` | Mensagem e pilha. As exceções do consumidor nunca trazem valores do evento (AC-T022.6) |
| `x-request-id` | O da requisição de origem, se havia; liga a mensagem aos logs dos dois serviços (RNF-012) |
| `traceparent` | O trace do publicador (W3C), de onde vem o `traceId` dos logs do consumidor |

A chave da mensagem é o `comercianteId`. O valor é o evento original, sem alteração.

> A saída deste comando contém o payload, inclusive o valor. Não a cole em chamado nem em chat; registre só `eventId`, causa e origem.

## 2. Causa e correção
| Causa (`kafka_dlt-exception-cause-fqcn`) | O que significa | Correção |
|---|---|---|
| `...adapter.in.kafka.EventoForaDoContratoException` | JSON malformado ou fora do schema de `contracts/` (Q-043). Foi direto para a DLT, sem nova tentativa (Q-045) | Bug do produtor: corrigir o Lançamentos e **reemitir o evento corrigido com o mesmo `eventId`** (`03 §7.2`). A mensagem da DLT não deve ser reinjetada como está, porque voltaria para a DLT |
| `...domain.EventoInvalidoException` | Passou no schema, mas viola o domínio (ex.: `valor` `0.00`) | Igual à linha anterior |
| Qualquer outra (ex.: `org.springframework.dao.*`, estouro `22003`) | Falha recuperável que persistiu depois de 1 + 3 tentativas (1 s, 2 s, 4 s) | Transitória (banco fora): nada a corrigir. Bug do consumidor: *deploy* da correção. Depois, reinjetar (passo 3) |

## 3. Reinjeção no tópico principal
**Antes de reinjetar, confirme que o `eventId` não está em `evento_processado`:**

```powershell
docker compose -p fluxo-caixa -f deploy/docker-compose.yml exec postgres `
  psql -U postgres -d consolidado -c "SELECT event_id, processado_em FROM evento_processado WHERE event_id = '<eventId>'"
```

- Se **estiver**, não reinjete: o evento já foi aplicado e a nova cópia seria ignorada.
- Pior ainda: se o conteúdo tiver sido "corrigido", a correção também seria ignorada, porque a deduplicação é só por `eventId` (ADR-005, Q-036). Nesse caso, há divergência: vá para a reconciliação (passo 4) e registre o incidente.

Para reinjetar, publique a mesma chave, o mesmo valor e o mesmo `x-request-id`, **sem** os cabeçalhos `kafka_dlt-*`.

Monte o arquivo `reinjecao.tsv` com uma mensagem por linha, nesta ordem: `x-request-id:<valor>`, TAB, a chave, TAB e o valor em JSON numa linha só. O TAB já é o separador padrão do `kafka-console-producer` para cabeçalhos e chave.

O PowerShell não aceita `<` para redirecionar a entrada. Por isso o arquivo vai pelo *pipe*, com `-T`, para o `exec` aceitar a entrada:

```powershell
Get-Content reinjecao.tsv | docker compose -p fluxo-caixa -f deploy/docker-compose.yml exec -T kafka `
  /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:29092 `
  --topic cashflow.lancamentos.v1 --property parse.headers=true --property parse.key=true
```

Sem `x-request-id` na origem, use `--property parse.headers=false` e linhas `chave<TAB>valor`.

> **Atenção:** estes comandos de console ainda não foram executados numa stack real, porque a T-022 não podia tocar na stack `fluxo-caixa`.
> - A mecânica (mesma chave, mesmo valor e mesmo `x-request-id`, sem os `kafka_dlt-*`) está provada por `DltIT#reinjecaoDaDltAplicaUmaVezEReinjetarDeNovoNaoMuda`.
> - Na primeira execução real, confira o resultado pelo passo 4 e corrija este runbook se algo divergir.

O consumidor aplica o evento uma única vez: o evento falho não entrou em `evento_processado`. Uma segunda reinjeção é ignorada, e o contador `consolidado_events_duplicates_total` sobe (AC-008.5).

## 4. Conferência e reconciliação do dia
**Evento aplicado:** o `eventId` aparece em `evento_processado` (consulta do passo 3).

**Reconciliação:** compare a soma em Lançamentos com a projeção, por comerciante e dia. São bancos diferentes, então há uma consulta em cada um:

```sql
-- Banco lancamentos
SELECT data_referencia,
       sum(valor) FILTER (WHERE tipo = 'CREDITO')                              AS creditos,
       sum(valor) FILTER (WHERE tipo = 'DEBITO')                               AS debitos,
       sum(valor) FILTER (WHERE tipo = 'ESTORNO' AND tipo_original = 'CREDITO') AS estornos_credito,
       sum(valor) FILTER (WHERE tipo = 'ESTORNO' AND tipo_original = 'DEBITO')  AS estornos_debito,
       count(*)                                                                AS qtd
FROM lancamento
WHERE comerciante_id = '<comercianteId>' AND data_referencia = DATE '<dia>'
GROUP BY data_referencia;
```

```sql
-- Banco consolidado
SELECT data_referencia, total_creditos, total_debitos, total_estornos_credito,
       total_estornos_debito, qtd_lancamentos, atualizado_em
FROM consolidado_diario
WHERE comerciante_id = '<comercianteId>' AND data_referencia = DATE '<dia>';
```

Os totais devem bater, com `NULL` contando como `0`. Uma diferença do tamanho de eventos que ainda estão na outbox ou no tópico é normal por alguns segundos (RNF-003). Uma diferença que persiste é divergência real, e o *rebuild* é evolução (ADR-006).

## 5. Registro do incidente
Registre:
- data e janela;
- `eventId`s e origem (tópico, partição, *offset*);
- causa e correção;
- quando a reinjeção foi feita e o resultado da reconciliação.

A DLT retém as mensagens por 30 dias (`create-topics.sh`). Esse é o prazo para concluir este procedimento.
