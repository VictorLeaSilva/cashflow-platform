# ADR-011 — Arquitetura de transição (condicional)

**Status:** Proposto — condicional, não implementado · **Data:** 2026-10-25

Decisões cobertas: — (Q-087 e Q-088; desdobra `00 §6.1`)

> **Escopo:** desenho para o caso de existir um legado. O enunciado não descreve nenhum, então **nada aqui é implementado, provado ou aceito**: não há legado a analisar. O resumo para leitura está em [`transition.md`](../transition.md).

## Contexto
O enunciado pede a arquitetura de transição como diferencial, "se necessária, considerando uma migração de legado". O MVP assume sistema novo (A3: saldo inicial zero; A5: estorno total e único; A6: saldo negativo permitido). Havendo legado, três coisas mudam: a **posição inicial** dos comerciantes, os **estornos** que o legado permitiu e a **identidade** de usuários e comerciantes. O desenho do MVP já comporta a evolução sem mudança: `identificador_externo` único por comerciante, `saldoAnterior` calculado na leitura e eventos versionados.

## Decisão
- **Migração fora da regra normal.** A API pública nunca cria crédito/débito fictício para representar saldo. A carga usa um **canal de migração** separado (*scope* próprio, fora da API pública), auditável (origem, lote, operador) e idempotente (`identificadorExterno` + lote). A4 vale só para a API pública.
- **Posição inicial.** Preferir **importar o histórico como lançamentos** quando existir e for confiável; se só houver a posição, registrar um **saldo de abertura** com data de corte.
- **Saldo de abertura como evento novo `SaldoAberturaRegistrado.v1`** (Q-088): fato próprio, com `eventId`, `comercianteId`, `dataCorte`, `valor` e dados de auditoria; o `LancamentoRegistrado.v1` não muda. A projeção guarda a abertura por comerciante e corte, idempotente por `eventId`, e a leitura passa a `saldoAnterior = abertura + Σ saldoDia desde o corte`. A comutatividade da projeção (ADR-005) se mantém porque a abertura é um termo aditivo aplicado uma vez. Lançamento da API pública anterior ao corte do comerciante é rejeitado (422).
- **Estornos parciais ou múltiplos do legado:** critério = não distorcer o saldo e não inventar registro; regra padrão proposta = importar como lançamento compensatório simples com referência externa, **sem** o vínculo `ESTORNO`. A regra final depende de um legado real.
- **Reconciliação** por comerciante e dia (posição do legado × `saldoAcumulado`), com **divergência zero ou explicada** como critério de aceite do corte; rollback mantendo o legado como fonte até o aceite.
- **Identidade** migra para o IdP, nunca para um cadastro próprio (ADR-007), por uma de quatro estratégias escolhidas por critério: importação em lote com hash compatível, migração preguiçosa no primeiro login, federação durante a coexistência ou redefinição obrigatória de senha. O código legado do comerciante vira o UUID do `merchant_id` por uma tabela de-para na ACL; integradores recebem novas credenciais *client credentials*, com período de convivência.
- **ACL** traduz o modelo legado e fica fora do domínio (ADR-009). **Strangler Fig / *shadow mode*** só se houver coexistência.

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| `CREDITO` ou `DEBITO` fictício para carregar o saldo | Falsifica o fato financeiro, o extrato e os totais do dia (P4); proibido pela `00 §6.1` |
| Saldo de abertura como **campo opcional** em `LancamentoRegistrado.v1` | A regra de evolução (`03 §4`) permite campos opcionais, mas a abertura não é um lançamento: não tem `lancamentoId`, o `tipo` é um enumerado fechado (valor novo é incompatível para consumidores antigos) e o schema tem `additionalProperties: false`. O campo exigiria obrigatoriedade condicional num evento que já a tem para `ESTORNO` |
| Saldo de abertura numa **`v2`** do `LancamentoRegistrado` | Força os dois lados a versionar por causa de um fato raro e de natureza diferente |
| Substituir a abertura por reemissão (última vence) | Quebra a aditividade e a comutatividade da projeção; a correção é um evento de ajuste aditivo |
| Recalcular o consolidado em cascata a partir do histórico migrado | Torna a ordem dos eventos relevante (rejeitado no ADR-005) |
| Cadastro próprio de usuários na aplicação | Contraria o ADR-007 (identidade é do IdP) |
| Migrar tudo de uma vez, sem reconciliação nem coexistência | Sem como medir divergência nem voltar atrás |

## Consequências
- **Ganho:** o MVP não muda; a transição acrescenta um evento, uma tabela de abertura, o canal de migração e a ACL, sem tocar nos contratos aprovados.
- **Custo assumido:** o saldo de abertura é um número em que se confia (sem extrato antes do corte); a leitura de `saldoAnterior` ganha um termo; a carga histórica pede ritmo controlado para não encher a outbox.
- **Não verificado:** como cada IdP trata hash de senha (o Cognito, por exemplo, não é assumido como capaz de importar senhas; a validar no provedor, na linha do que o ADR-007 já diz sobre o Cognito) e qualquer prazo, volume ou custo de migração.

## Revisitar quando
- Existir um legado real: analisar os estornos e fechar a regra padrão (vira ADR próprio) e a escolha das estratégias de identidade.
- A transição entrar no escopo: promover este ADR a `Aceito`, detalhar o evento e o contrato e estimar o esforço (T-042 trata só de custo da arquitetura-alvo).

## Referências
`00 §6.1` (premissas A3–A6), P4, P8, P9; `03 §3`, `§3.2`, `§4`; ADR-004, ADR-005, ADR-007, ADR-009, ADR-010; `transition.md`; Q-087, Q-088.
