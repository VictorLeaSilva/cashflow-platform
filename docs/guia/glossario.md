# Glossário: para que serve cada peça

> Você não precisa conhecer as ferramentas abaixo para seguir o passo a passo. Termos de conceito (outbox, idempotência, projeção) entram com os guias da T-045.

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
