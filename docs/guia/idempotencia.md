# Idempotência: repetir o pedido sem duplicar

> Guia de conceito. Os termos técnicos estão no [glossário](glossario.md).

## Em uma frase

Se o mesmo pedido chegar duas vezes (por um erro de rede, um clique duplo ou uma nova tentativa), o sistema registra **um único** lançamento e devolve a mesma resposta.

## Uma analogia

O número do pedido na lanchonete. Você repete "pedido 42, um sanduíche" e a cozinha entrega o mesmo sanduíche, sem fazer outro. Se você disser "pedido 42, dois sanduíches", a cozinha recusa: aquele número já foi usado para outra coisa.

## O passo a passo, com um exemplo

O cliente envia `POST /api/v1/lancamentos` com o cabeçalho `Idempotency-Key: k1` e o corpo "crédito de 100.00". A chave vale por comerciante: o mesmo `k1` de outro comerciante é independente.

| Situação | O que o serviço faz | Resposta |
|---|---|---|
| Chave nova | Grava a chave, o lançamento e o evento na mesma transação | `201` |
| Mesma chave, mesmo pedido (repetição) | Reconhece a chave, **não grava de novo** e reconstrói a resposta a partir do lançamento já gravado | `201` com o cabeçalho `Idempotent-Replayed: true` e o mesmo `id` |
| Mesma chave, pedido diferente (por exemplo, valor 150.00) | Recusa, sem gravar nada | `422` `idempotency-key-reuse` |
| Duas requisições iguais ao mesmo tempo | O banco faz a segunda esperar a primeira: uma grava e a outra vira repetição. Se a disputa não se resolver em 3 tentativas, devolve conflito | `201` e `201` com repetição; no pior caso `409` `idempotency-conflict`, com `Retry-After: 1` |
| Chave ausente ou fora do formato (`A-Z a-z 0-9 . _ -`, de 1 a 64 caracteres) | Recusa antes de qualquer gravação | `400` |

**Como o serviço sabe que é "o mesmo pedido"?** Ele calcula uma impressão digital (`request_hash`, um SHA-256) do pedido **já validado**, não do JSON bruto. Para o exemplo, o texto que entra no cálculo é `17:POST /lancamentos|7:CREDITO|6:100.00|-|-|-`: o tamanho de cada campo, o texto, e `-` para o que não foi informado. Por isso `"100"` e `"100.00"` dão a mesma impressão, e um texto não consegue imitar outro campo. A chave e o `requestId` **não** entram no cálculo.

**Por que a chave nunca "sobra" sem lançamento?** A ligação entre a chave e o lançamento é conferida só no `COMMIT` (restrição adiada). Se o lançamento não existir naquele instante, a transação inteira falha, e a chave some junto.

**O estorno também é idempotente.** A impressão digital do estorno depende só do lançamento original. Um segundo estorno do mesmo lançamento (com outra chave) é recusado com `409` `lancamento-ja-estornado`, porque só pode existir um estorno por lançamento (garantido por um índice único do banco).

## Onde está no código

| Peça | Ponteiro | O que faz |
|---|---|---|
| Leitura do cabeçalho e resposta | `LancamentoController#registrar` | Lê o `Idempotency-Key` e devolve `201` e o cabeçalho de repetição |
| Validação da chave | `EntradaDeLancamento#validarChave` | Exige a chave e o formato |
| Caso de uso | `RegistrarLancamento#registrar` | Chama a execução idempotente, gera o id antes da transação e conta repetição e conflito |
| Regra da idempotência | `ExecucaoIdempotente#executar` | Insere a chave com `ON CONFLICT DO NOTHING`, decide entre novo, repetição, reuso e disputa, até 3 tentativas |
| Impressão digital | `RequestFingerprint#deRegistro` | Monta o texto canônico e calcula o SHA-256 |
| Impressão do estorno | `RequestFingerprint#deEstorno` | Depende só do lançamento original |
| Estorno | `EstornarLancamento#estornar` | Mesma execução idempotente; estorno único por índice |
| Erros HTTP | `ProblemaHandler` | Mapeia reuso para `422` e disputa para `409` |
| Tabelas, FK adiada e índices | `lancamentos-service/src/main/resources/db/migration/V1__esquema_inicial.sql` | `idempotency_key`, `lancamento` e `outbox_event` |

## Como ver funcionando

A demonstração tem um bloco só de idempotência (repetição, mesmo `id`, `422` com payload diferente e nenhuma duplicata). Com a stack no ar, rode `scripts/demo.sh` (veja o [como funciona](como-funciona.md#como-ver-funcionando)). Depois, para ver a chave gravada no banco:

**bash e PowerShell**

```bash
docker compose --env-file .env -f deploy/docker-compose.yml exec postgres psql -U postgres -d lancamentos -c "SELECT left(chave, 12) AS chave, left(request_hash, 12) AS hash, lancamento_id FROM idempotency_key ORDER BY criado_em DESC LIMIT 5"
```

**O que você deve ver:** até 5 linhas, uma por chave, cada uma apontando para um `lancamento_id`. Repetições não criam linhas novas. (O `-U postgres` é o usuário administrador do seu `.env`.)

## O que pode dar errado

- **A repetição respondeu `201`, não `200`.** É assim por desenho: o sinal de repetição é só o cabeçalho `Idempotent-Replayed: true`.
- **O contador `idempotency_conflicts_total` não sobe no `409`.** Ele conta só o reuso de chave com outro pedido (`422`). Os `409` (`idempotency-conflict`, `identificador-externo-duplicado` e `lancamento-ja-estornado`) não entram nele.
- **A repetição devolve o original mesmo depois que a data dele saiu da janela de retroatividade (30 dias).** As regras de data (retroatividade e futuro) valem só para pedido **novo** (`ReplayForaDaJanelaIT`).
- **`data-futura` e `data-retroativa-excedida` (`422`) desfazem a chave junto**, então corrigir a data e repetir com a mesma chave funciona.

## Ligações

- Decisão: [ADR-003](../architecture/decisions/ADR-003-outbox-publicador-idempotencia.md); algoritmo em [`03 §3.1.1`](../specs/03-design-contracts.md).
- Requisito: RF-007 e RNF-004. Provas: `IdempotenciaIT`, `IdempotenciaConcorrenteIT`, `IdempotenciaRollbackConcorrenteIT`, `ExecucaoIdempotenteTest`, `RequestFingerprintTest`, `IdempotencyKeyFkDiferidaIT`, `EstornoIdempotenteConcorrenteIT` e `PostLancamentoWebTest`. A matriz está em [`traceability.md`](../specs/traceability.md).
- A segunda camada de idempotência, no consumidor (por `eventId`), está em [consumidor e consolidado](consumidor-e-consolidado.md).
