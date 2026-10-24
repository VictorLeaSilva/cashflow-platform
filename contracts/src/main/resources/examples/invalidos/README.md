# Exemplos inválidos de `LancamentoRegistrado.v1`

Cada arquivo viola o schema (`events/lancamento-registrado.v1.schema.json`) de **um** jeito. O Lançamentos prova que o schema os rejeita; o Consolidado prova que eles vão para a DLT na primeira tentativa, sem alterar a projeção (T-030, AC-T030.3). Toda violação nova ganha um arquivo aqui e uma linha nesta tabela.

| Arquivo | Violação |
|---|---|
| `estorno-sem-tipo-original.json` | `tipo` `ESTORNO` sem `tipoOriginal` |
| `estorno-sem-referencia.json` | `tipo` `ESTORNO` sem `referenciaLancamentoId` |
| `credito-com-tipo-original.json` | `tipoOriginal` em lançamento que não é estorno |
| `valor-com-tres-casas.json` | `valor` com 3 casas decimais |
| `valor-numerico.json` | `valor` como número JSON, não como string |
| `propriedade-extra.json` | propriedade fora do contrato (`additionalProperties: false`) |
| `data-referencia-invalida.json` | `dataReferencia` que não é uma data (`2026-13-45`) |
| `tipo-invalido.json` | `tipo` fora de `CREDITO`, `DEBITO` e `ESTORNO` |
| `trace-id-vazio.json` | `traceId` vazio (`minLength: 1`) |
| `schema-version-2.json` | `schemaVersion` diferente de 1 |
| `event-type-diferente.json` | `eventType` diferente de `LancamentoRegistrado` |
| `comerciante-id-ausente.json` | campo obrigatório ausente (`comercianteId`) |
| `event-id-nao-uuid.json` | `eventId` que não é UUID (`format: uuid`) |
| `occurred-at-sem-offset.json` | `occurredAt` sem fuso (`format: date-time`) |
