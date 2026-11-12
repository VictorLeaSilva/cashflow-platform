# Glossário: para que serve cada peça

> Você não precisa conhecer as ferramentas abaixo para seguir o passo a passo. Os termos de conceito (outbox, idempotência, projeção) estão na seção "Termos de conceito", no fim.

Você não precisa conhecer as ferramentas abaixo para seguir o passo a passo. Esta seção só explica, em poucas palavras, o que cada uma faz.

| Peça | O que é | Para que serve aqui |
|---|---|---|
| **Terminal** | Janela onde você digita comandos. No Windows, use o **PowerShell**. No Linux, no macOS ou no Windows com o **Git Bash**, use o **bash** | Rodar todos os comandos deste README |
| **Docker** | Programa que roda outros programas em "caixas" isoladas, chamadas **contêineres** | Rodar o banco de dados e o Kafka sem instalar os dois no seu computador |
| **PostgreSQL** | Banco de dados | Guardar os lançamentos (banco `lancamentos`) e o resumo diário (banco `consolidado`) |
| **Kafka** | "Correio" de mensagens entre programas | Levar cada lançamento do serviço de Lançamentos para o de Consolidado |
| **Maven** (`mvnw`) | Ferramenta que compila e roda programas Java. O `mvnw` já vem no projeto, sem instalar nada | Compilar o projeto e subir os dois serviços |
| **Serviço de Lançamentos** | Programa Java, na porta **8081** | Registrar créditos, débitos e estornos e consultar o histórico |
| **Serviço de Consolidado** | Programa Java, na porta **8082** | Mostrar o resumo de cada dia: totais, saldo do dia e saldo acumulado |
| **Token (JWT)** | Uma "senha temporária" em forma de texto longo | Provar para os serviços quem é você (o comerciante) e o que pode fazer |
| **Flyway** | Parte de cada serviço que cria as tabelas | Na primeira vez que cada serviço sobe, criar as tabelas do banco dele |

O fluxo é este:
1. você manda um lançamento para o **Lançamentos**;
2. ele grava o lançamento no banco e publica um evento no **Kafka**;
3. o **Consolidado** recebe o evento e atualiza o resumo do dia;
4. você consulta esse resumo.

Os dois serviços nunca chamam um ao outro diretamente. Por isso, o resumo pode levar cerca de 1 segundo para refletir um lançamento novo.

## Termos de conceito

Os termos abaixo aparecem nos guias [como funciona](como-funciona.md), [idempotência](idempotencia.md), [outbox e publicador](outbox-e-publicador.md), [consumidor e consolidado](consumidor-e-consolidado.md) e [observabilidade por dentro](observabilidade-por-dentro.md).

| Termo | Em português simples |
|---|---|
| **Transação** | Um conjunto de gravações no banco que vale "tudo ou nada": se uma falha, todas são desfeitas. *Commit* é confirmar; *rollback* é desfazer |
| **Evento** | Um aviso de que algo aconteceu ("um lançamento foi registrado"), com os dados do fato, enviado pelo Kafka |
| **`eventId`** | O número único de cada evento; é o "carimbo" que permite reconhecer um evento repetido |
| **Outbox** | A "caixa de saída": uma tabela onde o evento é gravado na mesma transação do lançamento, esperando ser publicado |
| **Publicador** | O processo, dentro do serviço de Lançamentos, que lê a outbox e envia os eventos ao Kafka |
| **Polling** | Perguntar de tempos em tempos "há algo novo?", em vez de esperar um aviso; é como o publicador lê a outbox |
| **`SKIP LOCKED`** | Um recurso do banco que faz quem chega depois pular as linhas que outro já pegou, para dois publicadores não enviarem a mesma linha |
| **Pool de conexões** | Um conjunto limitado de conexões abertas com o banco, reutilizadas; cada parte do serviço tem o seu, para uma não esgotar a outra |
| ***At-least-once*** | "Pelo menos uma vez": a entrega de um evento pode se repetir, nunca se perde; por isso o consumidor precisa ignorar repetidos |
| **Idempotência** | Repetir a mesma operação tem o mesmo efeito de fazê-la uma vez |
| **`Idempotency-Key`** | O cabeçalho com o "número do pedido" que o cliente envia para o serviço reconhecer uma repetição |
| **DLT** (fila de falhas) | Onde vão os eventos que não puderam ser aplicados, para alguém analisar e reinjetar |
| **Projeção** | Uma tabela de leitura calculada a partir dos eventos; aqui, os totais por dia do Consolidado |
| ***Upsert* aditivo** | "Insere ou, se já existe, soma": o banco soma o valor novo ao total do dia numa só operação |
| **Comutativo** | Que dá o mesmo resultado em qualquer ordem (500 + 1080 = 1080 + 500); é por isso que a ordem dos eventos não importa |
| **Saldo do dia / anterior / acumulado** | Saldo do dia: o que entrou menos o que saiu naquele dia. Anterior: a soma de todos os dias antes do período consultado. Acumulado: o anterior mais os dias do período até aquele dia |
| **Consistência eventual** | O Consolidado pode estar alguns instantes atrás do Lançamentos; ele alcança, mas não é instantâneo |
| **Métrica: contador, medidor, cronômetro** | Contador só sobe; medidor mostra o valor agora; cronômetro mede durações |
| **Percentil (p95)** | "Em 95% dos casos, o valor ficou abaixo deste número" |
| **Alerta** | Uma regra que "acende" quando uma métrica passa de um limite por um tempo |
| **`requestId` e `traceId`** | Identificadores que seguem um pedido pelos serviços e pelo evento, para achá-lo nos logs |
