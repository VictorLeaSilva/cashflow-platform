# ADR-009 — Hexagonal enxuta + ArchUnit

**Status:** Aceito · **Data:** 2026-10-22

Decisões cobertas: — (decisão da constituição, `00 §3.1`)

## Contexto
As regras financeiras (valor exato, datas no fuso de negócio, estorno, idempotência) precisam ser testáveis sem banco nem framework, e a integração com PostgreSQL, Kafka e HTTP precisa ser trocável sem tocar nessas regras (ex.: trocar Kafka por SNS/SQS, ADR-004). Ao mesmo tempo, camadas demais viram cerimônia num serviço pequeno.

## Decisão
Arquitetura **hexagonal enxuta por serviço, empacotada por feature**:
- `domain`: Java puro — sem Spring, Jackson, JDBC nem Kafka. Recebe `Clock` e IDs prontos.
- `application`: casos de uso e portas (`port.out`). Pode usar o gerenciamento de transação do Spring.
- `adapter.in.*` (HTTP, consumidor Kafka) e `adapter.out.*` (JDBC, produtor Kafka).

As regras são **verificadas por ArchUnit** a cada `./mvnw verify` (T-002): domínio sem frameworks; `application` sem `adapter`; serviços sem referência cruzada; sem `float`/`double` em `domain`/`application`; sem `now()` sem `Clock`; sem `System.out`/`printStackTrace`. Testes negativos provam que cada regra falha quando violada.

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Camadas tradicionais (controller → service → repository) com entidades JPA | Regras acopladas ao framework e ao banco; o domínio vira anotação |
| Hexagonal/Clean "completa" (mapeadores por camada, *use case* por classe, módulos Maven por camada) | Cerimônia desproporcional a dois serviços pequenos (P2) |
| Regras de arquitetura só por convenção/revisão | Erodem silenciosamente; ArchUnit as torna um gate |

## Consequências
- **Ganho:** domínio testável com teste unitário e de propriedade; adapters substituíveis; regras de dinheiro e tempo garantidas pelo build.
- **Custo assumido:** algumas portas e mapeamentos a mais; regras ArchUnit duplicadas nos dois serviços (não há *shared kernel* de código, por decisão — `01 §3`).

## Revisitar quando
- Os serviços crescerem a ponto de pedir módulos separados por camada, ou encolherem a ponto de as portas não se pagarem.

## Referências
RNF-008; `00 §3.1`, `00` P3; `04 §2.2`; `01 §3`; T-002.
