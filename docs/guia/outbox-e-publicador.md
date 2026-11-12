# Outbox e publicador: como o dado chega à fila sem se perder

> Guia de conceito. Os termos técnicos estão no [glossário](glossario.md).

## Em uma frase

O serviço grava o lançamento **e** o evento na mesma transação do banco (a *outbox*, a caixa de saída); um publicador, dentro do mesmo serviço, lê essa caixa em ciclos e envia cada evento ao Kafka.

## Uma analogia

A caixa de saída de um escritório. Você assina o contrato e, no mesmo ato, deixa um bilhete de aviso na caixa. Um entregador passa de tempos em tempos, pega os bilhetes, entrega no correio e carimba "entregue". Se o correio estiver fechado, o entregador volta mais tarde; os bilhetes não se perdem. E, se ele cair no meio da rua, na volta entrega de novo os que não foram carimbados.

## Por que existe

Gravar no banco e avisar o Kafka são **duas** operações. Se o serviço gravasse e depois avisasse, uma queda no meio perderia o aviso. Se avisasse e depois gravasse, avisaria sobre algo que não foi gravado. Com a outbox, o aviso é **só mais uma linha** na mesma transação: ou o lançamento e o aviso existem, ou nenhum dos dois.

## O passo a passo, com um exemplo

A linha da outbox (tabela `outbox_event`) guarda o identificador do evento, o tópico, a chave da mensagem (o `comercianteId`), o JSON do evento, quando foi criado e `published_at`, que fica vazio até a publicação.

Um ciclo do publicador, tudo numa transação própria (limite de 15 s):
1. **Trava** os pendentes mais antigos, até 100, com `FOR UPDATE SKIP LOCKED`. O `SKIP LOCKED` faz outra instância do serviço pular as linhas que esta já pegou, então dois publicadores nunca enviam a mesma linha ao mesmo tempo.
2. **Envia** o lote ao Kafka (`acks=all`, com idempotência do produtor) e espera as confirmações.
3. **Marca** `published_at` nos confirmados e, nos que falharam, soma `attempt_count` e guarda o `last_error` (até 500 caracteres).

**Quando o próximo ciclo acontece:**
- Se o lote veio **cheio** (100), o próximo ciclo é **imediato**, para drenar o acúmulo.
- Se veio **parcial**, o publicador espera **500 ms**.
- Se **falhou**, espera o dobro a cada falha seguida, até o teto de 30 s.

*Exemplo (ilustrativo):* 250 eventos pendentes. Ciclo 1 publica 100 (cheio, segue direto), ciclo 2 publica 100 (cheio, segue direto), ciclo 3 publica 50 (parcial, espera 500 ms). Com o Kafka fora, a espera sobe 1 s, 2 s, 4 s, 8 s, 16 s e fica em 30 s; quando o Kafka volta, o publicador volta a 500 ms.

**O que sai na mensagem:** a chave é o `comercianteId` (assim os eventos de um comerciante ficam na mesma partição, em ordem); o corpo é o JSON do evento, sem alteração; e o cabeçalho `x-request-id` leva o identificador da requisição de origem.

**Entrega "pelo menos uma vez" (*at-least-once*).** Se o serviço cair depois de enviar ao Kafka e antes de confirmar a transação, o evento volta a ser pendente e sai **de novo**, com o mesmo `eventId`. Não é defeito: o consumidor ignora repetidos (veja [consumidor e consolidado](consumidor-e-consolidado.md)).

**Limpeza.** Uma vez por hora, o publicador apaga as linhas **já publicadas** com mais de 7 dias, em blocos de 1000. Pendentes nunca são apagadas.

**Isolamento da API.** O publicador usa um **pool de conexões próprio, de 2** (a API usa outro, de 10). Se o Kafka ficar lento, o publicador consome só as conexões dele e a API continua gravando. Além disso, no primeiro tempo esgotado de conexão com o Kafka (3 s), o resto do lote é marcado como falho sem esperar de novo.

## Onde está no código

| Peça | Ponteiro | O que faz |
|---|---|---|
| Gravação do evento | `RegistrarLancamento#registrar` | Insere o evento na outbox na transação do lançamento |
| Formato do evento | `LancamentoRegistradoV1` | Campos do evento (`eventId`, `occurredAt`, `tipo`, `valor`, `dataReferencia`...) e o tópico |
| Ciclo | `PublicarOutbox#publicarLote` | Trava, publica, marca e registra falhas numa transação |
| Limpeza | `PublicarOutbox#limpar` | Apaga publicadas antigas, em blocos |
| Laço e threads | `LacoDoPublicador` | Uma thread por instância; agenda o próximo ciclo |
| Intervalo | `PoliticaDeIntervalo#aposSucesso` | Lote cheio: zero; parcial: 500 ms |
| Recuo após falha | `PoliticaDeIntervalo#aposFalha` | Dobra a espera até o teto de 30 s |
| Envio ao Kafka | `PublicadorKafka#publicar` | Envia o lote e espera as confirmações |
| Mensagem | `PublicadorKafka#mensagem` | Chave, corpo e cabeçalho `x-request-id` |
| SQL da outbox | `OutboxJdbcRepository` | `FOR UPDATE SKIP LOCKED`, marcação, falhas, limpeza e contagens |
| Pool e Kafka | `PublicadorConfig` | Pool de 2 conexões, produtor com `acks=all` e métricas da outbox |
| Propriedades | `OutboxProperties` | Intervalo, lote, teto, retenção |
| Tabela | `lancamentos-service/src/main/resources/db/migration/V1__esquema_inicial.sql` | `outbox_event` e o índice dos pendentes |
| Tópicos | `deploy/kafka/create-topics.sh` | Tópico e fila de falhas, com 3 partições |

## Como ver funcionando

Com a stack no ar e a demonstração já rodada (veja o [como funciona](como-funciona.md#como-ver-funcionando)), veja a outbox no banco e a métrica de pendentes:

**bash e PowerShell**

```bash
docker compose --env-file .env -f deploy/docker-compose.yml exec postgres psql -U postgres -d lancamentos -c "SELECT event_type, published_at IS NOT NULL AS publicado, attempt_count FROM outbox_event ORDER BY created_at DESC LIMIT 5"
```

**bash (Linux, macOS e Git Bash)**

```bash
curl -s http://localhost:9081/actuator/prometheus | grep -E '^(outbox_pending_events|outbox_oldest_pending_age_seconds|outbox_publish_failures_total)'
```

**PowerShell (Windows)**

```powershell
curl.exe -s http://localhost:9081/actuator/prometheus | Select-String '^(outbox_pending_events|outbox_oldest_pending_age_seconds|outbox_publish_failures_total)'
```

**O que você deve ver:** as linhas do banco com `publicado = t` e `attempt_count = 0`, e as três métricas em `0.0` quando tudo foi publicado. Com o Kafka parado (`scripts/demo.sh --kafka-down`), `outbox_pending_events` sobe e `outbox_oldest_pending_age_seconds` cresce até o Kafka voltar.

## O que pode dar errado

- **Há eventos pendentes e o Kafka está de pé.** Veja `last_error` na linha da outbox e o log "Publicador da outbox com falha; recuando" (traz a causa e o próximo ciclo em ms).
- **A idade do evento mais antigo passou de 60 s por 5 min.** É o alerta `OutboxComIdadeAlta` (veja [observabilidade por dentro](observabilidade-por-dentro.md)).
- **O Kafka ficou fora por muito tempo.** A outbox não tem limite de tamanho: uma falha longa cresce o banco. Por isso existe o alerta; a ação é operacional.
- **O mesmo evento apareceu duas vezes no Kafka.** Esperado em falha entre o envio e a confirmação; o consumidor deduplica.

## Ligações

- Decisão: [ADR-003](../architecture/decisions/ADR-003-outbox-publicador-idempotencia.md); limites e valores em [`03 §7.1`](../specs/03-design-contracts.md).
- Requisitos: RNF-001, RNF-004 e RNF-007. Provas: `PublicadorConcorrenteIT` (duas instâncias, cada evento uma vez), `KafkaForaIT`, `PoliticaDeIntervaloTest`, `FalhaEntreEnvioEMarcacaoIT` (reenvio com o mesmo `eventId`), `OutboxJdbcRepositoryIT` e `PublicadorOutboxIT`. A matriz está em [`traceability.md`](../specs/traceability.md).
