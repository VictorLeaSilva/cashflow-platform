# ADR-001 — Monorepo e dois executáveis independentes

**Status:** Aceito · **Data:** 2026-10-22

Decisões cobertas: D-01, D-10

## Contexto
O enunciado pede um serviço de controle de lançamentos e um serviço de consolidado diário, e exige que **Lançamentos não fique indisponível se o Consolidado cair** (RNF-001). Ao mesmo tempo, o case é feito por uma pessoa, com tempo limitado, e cada peça a mais custa operação e documentação (P2).

## Decisão
- **Dois executáveis implantáveis** — `lancamentos-service` e `consolidado-service` — em **um monorepo Maven multi-módulo**, mais o módulo `contracts` (só recursos: JSON Schema e exemplos do evento).
- Os serviços **não dependem um do outro**: o Maven Enforcer barra a dependência entre os módulos e o ArchUnit barra a referência de pacote (T-001, T-002). A única ligação é o evento publicado (ADR-003, ADR-004).
- **O Consolidado é um único executável com API de consulta e consumidor Kafka** (D-10). O consumidor pode ser desligado por configuração (`consolidado.consumer.enabled`), o que permite separá-lo em outro processo sem mudar código.

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| Monólito modular (um executável) | Um único processo compartilha o domínio de falha: queda ou saturação do consolidado derrubaria o registro de lançamentos, contrariando RNF-001 |
| Um microsserviço por entidade/capacidade (ex.: estorno, consulta, publicador) | Fragmentação sem requisito que a justifique; multiplica deploy, observabilidade e contratos (P2) |
| Repositórios separados por serviço | Para um time e um case, aumenta o atrito (versões, CI, contratos) sem ganho; o isolamento é garantido no build |
| API do Consolidado e consumidor em executáveis separados desde já | Mais uma peça para operar; a carga do enunciado (50 req/s) não exige. Fica como evolução medida (abaixo) |

## Consequências
- **Ganho:** domínios de falha separados (RNF-001), escala independente por serviço (RNF-010), uma única pipeline de qualidade.
- **Custo assumido:** consistência eventual entre os serviços (ADR-005) e mais operação que um monólito.
- No Consolidado, API e consumidor dividem CPU e memória; os pools JDBC são separados para reduzir a interferência (`03 §7.1`).

## Revisitar quando
- O time ou o volume exigirem separar mais capacidades.
- O cenário de drenagem sob leitura mostrar interferência do consumidor na API do Consolidado → separar o consumidor em executável próprio. *Atualizado na T-040:* a T-033 mediu a leitura (50, 100 e 200 req/s) com a escrita simultânea em regime, sem interferência; **não** mediu o acúmulo e a drenagem de um backlog sob leitura, então o gatilho segue sem ser exercitado (follow-up).

## Referências
RNF-001, RNF-010; `03 §1` (D-01, D-10), `03 §7.1`; `00 §3.1`; T-001, T-002.
