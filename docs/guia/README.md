# Guias

Documentos de apoio ao [README](../../README.md). O README é a porta de entrada; aqui está o detalhe, para quem quer rodar passo a passo, entender uma parte ou verificar.

**Comandos de copiar e colar:** cada bloco traz o rótulo do terminal. **bash** vale para Linux, macOS e Git Bash; **PowerShell** vale para o Windows; **bash e PowerShell** quer dizer que o comando é o mesmo nos dois.

| Guia | Para quê | Quando ler |
|---|---|---|
| [Passo a passo](passo-a-passo.md) | Subir e usar a aplicação do zero, com três terminais, token e chamadas à API | Quer rodar à mão e ver cada peça |
| [Observabilidade: uso](observabilidade-e-alertas.md) | Ver métricas, alertas e o painel; seguir uma requisição pelos logs | Quer ver o que os serviços estão fazendo |
| [Carga e desempenho](carga-e-desempenho.md) | Repetir a medição de 50, 100 e 200 req/s | Quer reproduzir os números do relatório |
| [Bancos de dados e contêineres](bancos-de-dados-e-conteineres.md) | O que cada banco e cada contêiner faz e o que cada comando faz com os dados | Quer entender o ambiente local |
| [Testes e qualidade](testes-e-qualidade.md) | Rodar os testes e as verificações; verificar uma tarefa pelo gate | Quer conferir a qualidade do código |
| [Se algo der errado](solucao-de-problemas.md) | Erros comuns, causa provável e o que fazer | Algo não funcionou |
| [Glossário](glossario.md) | Para que serve cada peça (terminal, Docker, Kafka, token...) | Um termo não ficou claro |
| [Método de desenvolvimento](metodo-de-desenvolvimento.md) | Como o projeto foi construído: especificação, tarefas, gate e revisão | Quer saber como foi feito |

Os guias de **conceito**, para entender a aplicação por dentro:

| Guia | Para quê | Quando ler |
|---|---|---|
| [Como funciona](como-funciona.md) | O caminho de um lançamento, do `POST` ao saldo, e o que acontece quando algo falha | Quer a visão geral antes dos detalhes |
| [Idempotência](idempotencia.md) | Como repetir um pedido não duplica o lançamento | Quer entender a chave de idempotência |
| [Outbox e publicador](outbox-e-publicador.md) | Como o evento chega ao Kafka sem se perder | Quer entender a caixa de saída e o publicador |
| [Consumidor e consolidado](consumidor-e-consolidado.md) | Como o evento vira saldo e como o saldo é calculado | Quer entender a leitura da fila e as somas |
| [Observabilidade por dentro](observabilidade-por-dentro.md) | O que cada métrica e cada alerta mede, e como seguir um pedido nos logs | Quer entender os números do painel |

## Módulos do repositório

- **`contracts/`:** o formato (JSON Schema) e os exemplos do evento `LancamentoRegistrado.v1`, sem código.
- **`lancamentos-service/`:** registra, estorna e consulta lançamentos; publica os eventos (outbox).
- **`consolidado-service/`:** consome os eventos, mantém o resumo diário e responde as consultas.
- Contratos das APIs: [`lancamentos-v1.yaml`](../../lancamentos-service/src/main/resources/openapi/lancamentos-v1.yaml) e [`consolidado-v1.yaml`](../../consolidado-service/src/main/resources/openapi/consolidado-v1.yaml); contrato do evento: [`lancamento-registrado.v1.schema.json`](../../contracts/src/main/resources/events/lancamento-registrado.v1.schema.json).
- [Runbook da DLT](../../docs/runbooks/dlt-reprocessamento.md): o que fazer quando um evento não pode ser aplicado no Consolidado.
