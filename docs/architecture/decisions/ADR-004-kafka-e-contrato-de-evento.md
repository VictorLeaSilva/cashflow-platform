# ADR-004 — Kafka vs alternativas; contrato JSON + JSON Schema

**Status:** Aceito · **Data:** 2026-10-22

Decisões cobertas: D-03, D-13

## Contexto
O evento `LancamentoRegistrado.v1` liga os dois serviços (ADR-003). O Consolidado precisa receber os eventos mesmo depois de ficar fora do ar (acúmulo e consumo posterior, RNF-001), com ordem por comerciante e possibilidade de reprocessar (DLT, `03 §7.2`). O volume do enunciado é baixo: 50 req/s de **leitura** no pico, com escrita bem menor.

## Decisão
- **Apache Kafka** (D-03): tópico `cashflow.lancamentos.v1` com chave `comercianteId` e 3 partições; DLT `cashflow.lancamentos.v1.DLT` com retenção de 30 dias. Localmente, 1 broker em modo KRaft; no alvo, MSK (ADR-010).
- **Contrato JSON + JSON Schema versionado** (D-13), em `contracts/`, com `additionalProperties: false`, valores monetários como *string* decimal e exemplos *golden*. Os dois lados validam o mesmo schema. Evolução: só campos opcionais na `v1`; mudança incompatível → novo tópico/versão.

## O que o broker resolve e o que não resolve
| Resolve | Não resolve |
|---|---|
| **Desacoplamento temporal e de disponibilidade:** o evento fica no tópico durante o prazo de retenção, consumido ou não, e o Consolidado o lê quando voltar, mesmo depois de horas fora do ar (RNF-001, junto com a outbox do ADR-003) | **Separação dos dados:** vem de cada serviço ter o seu banco e o seu role (ADR-002), não do broker |
| **Transporte dos fatos entre contextos:** o Consolidado monta a própria projeção a partir dos eventos e nunca consulta Lançamentos (*event-carried state transfer*) | **Replicação de banco:** o evento não é cópia do banco de Lançamentos; a projeção é um dado derivado |
| **Reprocessamento pontual** dentro da retenção (DLT, reconciliação de um dia). Reconstruir o histórico completo **não** é papel do broker: a fonte é a tabela `lancamento` (ADR-006) | **Falha da instância de banco compartilhada (D-15):** os dois serviços param; no alvo, mitigado por RDS Multi-AZ (ADR-010) |

A replicação do **próprio** Kafka (3 brokers, RF 3) serve para o broker não ser ponto único de falha e existe só no alvo (ADR-010); localmente, 1 broker basta para provar o comportamento da aplicação.

## Por que não SQS/SNS nem RabbitMQ para 50 req/s
O volume **não** é o motivo da escolha: SQS/SNS ou RabbitMQ atenderiam 50 req/s com folga. O que pesou foi o **modelo de consumo**:
- **Retenção e replay:** no Kafka a mensagem consumida continua no log; dá para reposicionar o *offset* e reprocessar dentro da retenção (DLT, reconciliação; um novo consumidor recebe só o que ainda está retido, e o histórico completo vem de `lancamento`, ADR-006). Em SQS/RabbitMQ a mensagem consumida é removida; replay exige guardar cópia em outro lugar.
- **Ordem por chave, se for preciso:** a partição por `comercianteId` entrega ao consumidor os eventos de um comerciante na ordem em que foram **publicados**. A projeção atual não depende disso (é comutativa) e o publicador não garante a ordem de criação (ADR-003); o valor está em não ter de trocar de broker se surgir um consumidor que precise de ordem. SQS FIFO tem limites de vazão por grupo e custo maior; RabbitMQ só ordena por fila.
- **Acúmulo durante a queda do Consolidado:** é o caso normal do Kafka (o lag cresce e é drenado). A retenção é finita, mas configurável por tópico e independente do consumo: a mensagem não some por ter sido lida, e o prazo deve cobrir com folga a maior indisponibilidade do Consolidado que se queira tolerar sem reemissão. A spec não fixa esse tempo (a definir com o negócio); hoje o tópico local usa o padrão do broker (7 dias; **não** é configuração versionada, pois o `create-topics.sh` só fixa a retenção da DLT, em 30 dias; *atualizado na T-040*). Uma fila SQS também retém por dias (até 14), porém apaga a mensagem ao ser consumida, o que impede o replay.

**Quando a troca valeria:** se replay e ordenação deixarem de ser necessários e o custo pesar (MSK tende a dominar o custo em carga baixa), **SNS + SQS** é a troca natural na AWS; a outbox e o consumidor idempotente continuam iguais, só muda o adapter de mensageria.

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| RabbitMQ | Sem retenção/replay nativos no modelo de filas clássico; ordenação só por fila |
| SQS/SNS | Sem replay; FIFO com limites e custo; viável se o requisito mudar (acima) |
| Avro + Schema Registry | Mais infraestrutura para 1 produtor e 1 consumidor; JSON Schema resolve (validado em runtime no consumidor e por testes de contrato no produtor; *atualizado na T-040*, Q-086) |
| Chamada síncrona | Violaria RNF-001 (ADR-003) |

## Consequências
- **Ganho:** replay, ordem por comerciante e acúmulo seguro durante falhas.
- **Custo assumido:** operação mais pesada que uma fila simples (localmente é um contêiner; no alvo, o MSK tende a ser o item de custo dominante em carga baixa, a estimar na T-042). Sem registro central de schemas: compatibilidade garantida por testes de contrato (T-030).

## Revisitar quando
- Replay/ordenação deixarem de ser requisito e o custo do MSK pesar → SNS/SQS.
- Surgirem muitos produtores/consumidores do evento → Schema Registry.

## Referências
RNF-001, RNF-003, RF-008; `03 §1` (D-03, D-13), `03 §4`, `03 §7.2`; T-004, T-016, T-022, T-030.
