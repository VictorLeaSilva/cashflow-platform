# Fluxo de Caixa

Controle de **lançamentos** (créditos e débitos) e **consolidado diário** (o resumo de cada dia), com dois serviços independentes que conversam por eventos. Case do desafio de Arquiteto de Soluções.

> **Estado:** os dois serviços funcionam de ponta a ponta. O de **Lançamentos** registra, estorna e consulta lançamentos e publica um evento para cada um. O de **Consolidado** recebe esses eventos e responde o resumo diário com saldos. Faltam as provas de resiliência e carga, a observabilidade e a demo guiada (Fase 3). O README final é da T-043; até lá, este documento é atualizado a cada tarefa (veja [Manter este README vivo](#manter-este-readme-vivo)).

## Sumário

- [Para que serve cada peça](#para-que-serve-cada-peça)
- [O que você precisa instalar](#o-que-você-precisa-instalar)
- [Passo a passo: subir e usar a aplicação do zero](#passo-a-passo-subir-e-usar-a-aplicação-do-zero)
- [Como funcionam os bancos de dados](#como-funcionam-os-bancos-de-dados)
- [Se algo der errado](#se-algo-der-errado)
- [Rodar os testes e as verificações de qualidade](#rodar-os-testes-e-as-verificações-de-qualidade)
- [Contêineres do ambiente local](#contêineres-do-ambiente-local)
- [Documentação, método e status](#documentação)

## Para que serve cada peça

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

## O que você precisa instalar

| Programa | Onde baixar | Como conferir (bash e PowerShell) | O que deve aparecer |
|---|---|---|---|
| **JDK 25** (Java) | [Adoptium](https://adoptium.net/) ou [Oracle](https://www.oracle.com/java/technologies/downloads/) | `java -version` | `java version "25...` ou `openjdk version "25...` |
| **Docker Desktop** (ou Docker Engine com Compose v2) | [docker.com](https://www.docker.com/products/docker-desktop/) | `docker compose version` | `Docker Compose version v2...` ou superior |
| **curl** | Já vem no Windows 10 e 11, no macOS e na maioria dos Linux | bash: `curl --version`; PowerShell: `curl.exe --version` | `curl 8...` |
| **Git** (no Windows, inclui o **Git Bash**) | [git-scm.com](https://git-scm.com/downloads) | `git --version` | `git version 2...` |

> **Docker aberto.** No Windows e no macOS, abra o Docker Desktop e espere o status "Engine running" antes de começar.

> **No PowerShell, use `curl.exe`, nunca só `curl`.** No Windows PowerShell, `curl` é outro comando (um apelido do `Invoke-WebRequest`) e não aceita as opções deste README.

## Passo a passo: subir e usar a aplicação do zero

Siga na ordem. Cada passo traz o comando em **bash** e em **PowerShell**: use só o do seu terminal. Os comandos foram executados de verdade nos dois terminais, e as saídas mostradas são as reais, só resumidas.

Você vai usar **três terminais abertos ao mesmo tempo**, todos na pasta do projeto:
- **Terminal 1:** infraestrutura e serviço de Lançamentos.
- **Terminal 2:** serviço de Consolidado.
- **Terminal 3:** token e chamadas à API.

> Os números de porta e os valores de exemplo (comerciante, valores) podem ficar como estão.

### 1. Baixar o projeto e abrir o terminal na pasta dele (Terminal 1)

**Para que serve:** todos os comandos partem da pasta raiz do projeto, onde fica este README. Se você ainda não tem o projeto no computador, o `git clone` baixa uma cópia dele.

Se você **ainda não tem** o projeto, abra o terminal na pasta onde quer guardá-lo e rode:

```bash
git clone https://github.com/VictorLeaSilva/cashflow-platform.git
cd cashflow-platform
```

```powershell
git -c core.longpaths=true clone https://github.com/VictorLeaSilva/cashflow-platform.git
cd cashflow-platform
```

> **No Windows**, o `-c core.longpaths=true` evita o erro `Filename too long`: alguns caminhos do projeto passam do limite antigo de 260 caracteres do Windows, principalmente se a pasta escolhida já tiver um caminho longo (por exemplo, dentro do OneDrive). No Git Bash do Windows, use o mesmo comando do PowerShell.

Se você **já tem** o projeto, só entre na pasta dele:

```bash
cd caminho/para/cashflow-platform
```

```powershell
cd C:\caminho\para\cashflow-platform
```

**O que você deve ver:** o `git clone` mostra `Cloning into 'cashflow-platform'...` e termina sem erro. Para conferir que está na pasta certa, `ls` (bash) ou `dir` (PowerShell) lista, entre outros, `README.md`, `deploy`, `lancamentos-service` e `consolidado-service`.

### 2. Criar o arquivo de configuração `.env` (só na primeira vez)

**Para que serve:** o `.env` guarda as senhas e as portas do ambiente local. O projeto traz um modelo, o `.env.example`, com valores próprios para desenvolvimento. O `.env` não vai para o Git.

```bash
cp .env.example .env
```

```powershell
Copy-Item .env.example .env
```

**O que você deve ver:** nada. O arquivo `.env` passa a existir na pasta.

> Se a porta 5432 (PostgreSQL) ou 9092 (Kafka) já estiver em uso no seu computador, troque `POSTGRES_PORT` ou `KAFKA_PORT` no `.env` antes do próximo passo.

### 3. Subir a infraestrutura (banco de dados e Kafka)

**Para que serve:** liga, no Docker, o PostgreSQL (com os bancos `lancamentos` e `consolidado`) e o Kafka (com o tópico de eventos). O `--wait` só devolve o terminal quando tudo está pronto. Na primeira vez, o Docker baixa as imagens, o que pode levar alguns minutos.

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --wait --remove-orphans
```

```powershell
docker compose --env-file .env -f deploy/docker-compose.yml up -d --wait --remove-orphans
```

**O que você deve ver:** ao final, três linhas `Healthy`:

```text
 Container fluxo-caixa-postgres-1 Healthy
 Container fluxo-caixa-kafka-1 Healthy
 Container fluxo-caixa-kafka-topicos-1 Healthy
```

> No PowerShell, essas mensagens do Docker podem aparecer em vermelho. É normal: o Docker escreve o progresso na saída de erro. O que importa é o `Healthy` no fim.

> **Já subiu este projeto antes nesta máquina?** O nome da stack é sempre `fluxo-caixa`. Se ela já existe, este passo **reaproveita** os bancos e as mensagens que ela já tem, e o `down -v` do passo 14 os **apaga**. Para fazer o passo a passo sem tocar nesses dados, use uma stack separada, com outro nome e outras portas:
> 1. no `.env`, troque `POSTGRES_PORT` e `KAFKA_PORT` (por exemplo, `55432` e `59092`);
> 2. em **cada** terminal, antes dos comandos, defina o nome da stack: bash `export COMPOSE_PROJECT_NAME=fluxo-caixa-teste`; PowerShell `$env:COMPOSE_PROJECT_NAME = "fluxo-caixa-teste"`;
> 3. se os serviços da outra stack estiverem rodando, suba estes com outra porta (veja `SERVER_PORT` em [Se algo der errado](#se-algo-der-errado)) e use essa porta nas chamadas.
>
> Os comandos `docker compose` deste README passam a valer para a stack `fluxo-caixa-teste`, inclusive o `down -v`.

### 4. Conferir a infraestrutura

**Para que serve:** um script confere que o banco e o Kafka estão saudáveis, que cada serviço só acessa o próprio banco e que os tópicos existem. O script é em bash. No PowerShell, ele é chamado pelo Git Bash.

```bash
deploy/verify-local.sh
```

```powershell
& "C:\Program Files\Git\bin\bash.exe" deploy/verify-local.sh
```

**O que você deve ver:** só linhas com `✔`, terminando assim:

```text
 ✔ cashflow.lancamentos.v1 com 3 partições
 ✔ cashflow.lancamentos.v1.DLT com 3 partições
Infraestrutura local OK.
```

> **No PowerShell, não use só `bash`.** No Windows, `bash` costuma abrir o bash do WSL, que não enxerga o Docker do mesmo jeito. Use o caminho completo do Git Bash, como acima. Se o Git estiver instalado em outro lugar, ajuste o caminho.

### 5. Compilar o projeto (só na primeira vez e depois de atualizar o código)

**Para que serve:** compila os módulos e os guarda no repositório local do Maven. Sem isso, o serviço de Consolidado não sobe, porque depende do módulo `contracts`, onde fica o formato do evento. O `-DskipTests` pula os testes para ir mais rápido (os testes estão em [Rodar os testes](#rodar-os-testes-e-as-verificações-de-qualidade)).

```bash
./mvnw -q install -DskipTests
```

```powershell
.\mvnw.cmd -q install "-DskipTests"
```

**O que você deve ver:** depois de 1 a 2 minutos, o terminal volta sem mensagem de erro. Na primeira vez, o Maven baixa as bibliotecas e demora mais.

> Avisos que começam com `WARNING: A terminally deprecated method in sun.misc.Unsafe` são do próprio Maven e podem ser ignorados.

### 6. Gerar a chave e o token de acesso (Terminal 3)

**Para que serve:** os serviços só aceitam chamadas com um **token** válido. O script `DevToken.java` gera esse token só para o ambiente local. Na **primeira execução**, ele também cria um par de chaves em `deploy/dev-keys/`, que os serviços usam para conferir o token.

> **Rode este passo antes de subir os serviços**, pelo menos uma vez. Sem a pasta `deploy/dev-keys/`, os serviços não sobem.

Abra um **novo terminal (Terminal 3)** na pasta do projeto e rode:

```bash
TOKEN=$(java scripts/DevToken.java --merchant 0f8fad5b-d9cb-469f-a165-70867728950e --scopes lancamentos:write,lancamentos:read,consolidado:read --ttl 3600)
echo "$TOKEN"
```

```powershell
$TOKEN = java scripts/DevToken.java --merchant 0f8fad5b-d9cb-469f-a165-70867728950e --scopes lancamentos:write,lancamentos:read,consolidado:read --ttl 3600
$TOKEN
```

O que cada opção quer dizer:
- **`--merchant`:** o comerciante, ou seja, de quem são os dados. Cada comerciante só vê os próprios.
- **`--scopes`:** as permissões. `lancamentos:write` registra e estorna, `lancamentos:read` consulta lançamentos, `consolidado:read` consulta o resumo.
- **`--ttl 3600`:** a validade, em segundos (1 hora).

**O que você deve ver:** na primeira vez, a linha `par de chaves gerado em ...\deploy\dev-keys` (no Linux e no macOS, `.../deploy/dev-keys`). Depois, um texto longo que começa com `eyJhbGciOiJSUzI1NiIs...`: é o token, guardado na variável `TOKEN` deste terminal.

> O token vale 1 hora. Depois disso, as chamadas respondem **401**: rode este passo de novo para gerar outro. Se você fechar o terminal, a variável `TOKEN` se perde, e é preciso gerar de novo.

### 7. Subir o serviço de Lançamentos (Terminal 1)

**Para que serve:** liga o serviço de Lançamentos na porta 8081, usando as senhas do `.env`. A primeira linha carrega o `.env` neste terminal; repita-a sempre que abrir um terminal novo para subir um serviço. Na primeira subida, o Flyway cria as tabelas do banco `lancamentos`.

Volte ao **Terminal 1**:

```bash
set -a; . ./.env; set +a
./mvnw -pl lancamentos-service spring-boot:run -Dspring-boot.run.profiles=local
```

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object { $nome, $valor = $_ -split '=', 2; Set-Item -Path "env:$($nome.Trim())" -Value $valor.Trim() }
.\mvnw.cmd -pl lancamentos-service spring-boot:run "-Dspring-boot.run.profiles=local"
```

**O que você deve ver:** o terminal fica ocupado mostrando o log do serviço, em linhas de JSON. Ele está pronto quando aparecer uma linha com:

```text
"message":"Started LancamentosApplication in 6.146 seconds ..."
```

**Deixe este terminal aberto:** fechar o terminal desliga o serviço.

### 8. Subir o serviço de Consolidado (Terminal 2)

**Para que serve:** liga o serviço de Consolidado na porta 8082. Ele tem duas partes no mesmo programa:
- o **consumidor**, que lê os eventos do Kafka e atualiza o resumo;
- a **API de consulta**.

Na primeira subida, o Flyway cria as tabelas do banco `consolidado`. O consumidor começa lendo todos os eventos que o Kafka ainda guarda.

Abra um **novo terminal (Terminal 2)** na pasta do projeto:

```bash
set -a; . ./.env; set +a
./mvnw -pl consolidado-service spring-boot:run -Dspring-boot.run.profiles=local
```

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object { $nome, $valor = $_ -split '=', 2; Set-Item -Path "env:$($nome.Trim())" -Value $valor.Trim() }
.\mvnw.cmd -pl consolidado-service spring-boot:run "-Dspring-boot.run.profiles=local"
```

**O que você deve ver:** linhas como estas, nesta ordem:

```text
"message":"Successfully applied 1 migration to schema \"public\" ..."      (só na primeira vez: tabelas criadas)
"message":"Started ConsolidadoApplication in 6.509 seconds ..."
"message":"consolidado-projecao: partitions assigned: [cashflow.lancamentos.v1-0, cashflow.lancamentos.v1-1, cashflow.lancamentos.v1-2]"
```

A última linha quer dizer que o consumidor está ligado ao Kafka. **Deixe este terminal aberto.**

### 9. Registrar lançamentos (Terminal 3)

**Para que serve:** criar dados de verdade. Toda criação leva um cabeçalho **`Idempotency-Key`**, uma etiqueta escolhida por você. Se a mesma requisição for enviada duas vezes com a mesma etiqueta (por exemplo, numa falha de rede), o lançamento não é duplicado.

> **No PowerShell, o corpo JSON vai num arquivo**, criado com `Set-Content` na pasta temporária do Windows (`$env:TEMP`, fora do projeto), e é enviado com `-d "@arquivo.json"`. No Windows PowerShell 5.1, um JSON escrito direto na linha chega ao serviço sem as aspas, e a resposta é `400` com `json-malformado`.

> **Use textos sem acento nos exemplos.** No Git Bash do Windows, acentos digitados no terminal podem não chegar como UTF-8, e a resposta é `400` com `json-malformado`.

**9.1. Registrar um crédito de R$ 100,00 e guardar o `id` dele:**

```bash
RESPOSTA=$(curl -s -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-credito-1" -d '{"tipo":"CREDITO","valor":"100.00","descricao":"Venda do dia"}')
echo "$RESPOSTA"
ID=$(echo "$RESPOSTA" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
echo "ID=$ID"
```

```powershell
Set-Content -Path "$env:TEMP\credito.json" -Encoding ascii -Value '{"tipo":"CREDITO","valor":"100.00","descricao":"Venda do dia"}'
$RESPOSTA = curl.exe -s -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-credito-1" -d "@$env:TEMP\credito.json"
$RESPOSTA
$ID = ($RESPOSTA | ConvertFrom-Json).id
"ID=$ID"
```

**O que você deve ver:** o lançamento criado e, por último, o `id` dele (o valor muda a cada execução):

```text
{"id":"cb7c671c-ad5a-444d-b6db-01934763fe3d","comercianteId":"0f8fad5b-d9cb-469f-a165-70867728950e","tipo":"CREDITO","valor":"100.00","descricao":"Venda do dia","dataOcorrencia":"2026-10-24T12:24:59.191966Z","dataReferencia":"2026-10-24",...}
ID=cb7c671c-ad5a-444d-b6db-01934763fe3d
```

A `dataReferencia` é o dia em que o lançamento conta no resumo, sempre no fuso de São Paulo.

**9.2. Repetir o mesmo crédito (teste de idempotência):**

```bash
curl -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-credito-1" -d '{"tipo":"CREDITO","valor":"100.00","descricao":"Venda do dia"}'
```

```powershell
curl.exe -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-credito-1" -d "@$env:TEMP\credito.json"
```

**O que você deve ver:** `HTTP/1.1 201`, o mesmo `id` do passo anterior e o cabeçalho `Idempotent-Replayed: true`. Nenhum lançamento novo foi criado.

**9.3. Registrar um débito de R$ 30,00:**

```bash
curl -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-debito-1" -d '{"tipo":"DEBITO","valor":"30.00","descricao":"Fornecedor"}'
```

```powershell
Set-Content -Path "$env:TEMP\debito.json" -Encoding ascii -Value '{"tipo":"DEBITO","valor":"30.00","descricao":"Fornecedor"}'
curl.exe -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-debito-1" -d "@$env:TEMP\debito.json"
```

**O que você deve ver:** `HTTP/1.1 201` e um JSON com `"tipo":"DEBITO"` e `"valor":"30.00"`.

**9.4. Estornar (desfazer) o crédito.** Um lançamento nunca é apagado nem editado: o estorno é um lançamento novo, que compensa o original. Este comando usa o `ID` guardado no passo 9.1, então rode-o no mesmo terminal.

```bash
curl -i -X POST http://localhost:8081/api/v1/lancamentos/$ID/estornos -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: exemplo-estorno-1"
```

```powershell
curl.exe -i -X POST "http://localhost:8081/api/v1/lancamentos/$ID/estornos" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: exemplo-estorno-1"
```

**O que você deve ver:** `HTTP/1.1 201` e um JSON com `"tipo":"ESTORNO"`, `"tipoOriginal":"CREDITO"` e `"referenciaLancamentoId"` igual ao `ID` do crédito. O estorno vale no dia de hoje, mesmo que o original seja de outro dia.

> Se aparecer `401` aqui, o `ID` provavelmente ficou vazio, porque o passo 9.1 falhou. Rode `echo "$ID"` (bash) ou `$ID` (PowerShell) para conferir.

**9.5. Tentar estornar o mesmo crédito de novo**, com outra etiqueta:

```bash
curl -i -X POST http://localhost:8081/api/v1/lancamentos/$ID/estornos -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: exemplo-estorno-2"
```

```powershell
curl.exe -i -X POST "http://localhost:8081/api/v1/lancamentos/$ID/estornos" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: exemplo-estorno-2"
```

**O que você deve ver:** `HTTP/1.1 409` e `"type":"/erros/lancamento-ja-estornado"`. Cada lançamento só pode ser estornado uma vez.

**9.6. Registrar um crédito retroativo de R$ 50,00, com data de 3 dias atrás.** O sistema aceita lançamentos de até 30 dias atrás e nunca do futuro.

```bash
TRES_DIAS_ATRAS=$(TZ=America/Sao_Paulo date -d "3 days ago" +%F)
curl -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-passado-1" -d "{\"tipo\":\"CREDITO\",\"valor\":\"50.00\",\"dataOcorrencia\":\"${TRES_DIAS_ATRAS}T12:00:00-03:00\"}"
```

```powershell
$agoraSP = [System.TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, "E. South America Standard Time")
$TRES_DIAS_ATRAS = $agoraSP.AddDays(-3).ToString("yyyy-MM-dd")
Set-Content -Path "$env:TEMP\retroativo.json" -Encoding ascii -Value "{`"tipo`":`"CREDITO`",`"valor`":`"50.00`",`"dataOcorrencia`":`"$($TRES_DIAS_ATRAS)T12:00:00-03:00`"}"
curl.exe -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-passado-1" -d "@$env:TEMP\retroativo.json"
```

**O que você deve ver:** `HTTP/1.1 201`, com `"dataReferencia"` igual à data de 3 dias atrás.

> **No macOS**, troque `date -d "3 days ago" +%F` por `date -v-3d +%F`.

### 10. Consultar os lançamentos (Terminal 3)

**Para que serve:** ver um lançamento pelo `id` e o histórico do comerciante.

```bash
curl -i http://localhost:8081/api/v1/lancamentos/$ID -H "Authorization: Bearer $TOKEN"
curl -s "http://localhost:8081/api/v1/lancamentos?size=10" -H "Authorization: Bearer $TOKEN"
```

```powershell
curl.exe -i "http://localhost:8081/api/v1/lancamentos/$ID" -H "Authorization: Bearer $TOKEN"
curl.exe -s "http://localhost:8081/api/v1/lancamentos?size=10" -H "Authorization: Bearer $TOKEN"
```

**O que você deve ver:**
- **o detalhe:** `HTTP/1.1 200` com o crédito do passo 9.1;
- **o histórico:** `{"itens":[...],"page":0,"size":10,"total":4}`, com os 4 lançamentos (crédito, débito, estorno e retroativo). Eles vêm ordenados pela `dataReferencia`, do dia mais recente para o mais antigo: primeiro os três de hoje, depois o retroativo. Dentro do mesmo dia, a ordem é fixa, mas não é a ordem em que você os registrou.

O histórico aceita os filtros `dataInicio`, `dataFim` (datas `AAAA-MM-DD`) e `tipo` (`CREDITO`, `DEBITO` ou `ESTORNO`), mais `page` (a partir de 0) e `size` (até 100). Por exemplo: `?tipo=CREDITO&dataInicio=2026-10-01`.

### 11. Consultar o consolidado diário (Terminal 3)

**Para que serve:** ver o resumo de cada dia. Para cada dia, a API mostra:
- os totais de créditos, débitos e estornos;
- o **saldo do dia**, que é (créditos − estornos de crédito) − (débitos − estornos de débito);
- o **saldo acumulado**, que é tudo o que entrou e saiu até aquele dia.

Dias sem movimento aparecem zerados.

> Espere **1 a 2 segundos** depois de registrar um lançamento antes de consultar. O consolidado é atualizado a partir dos eventos e leva um instante para refletir o que acabou de acontecer.

**11.1. O período de 3 dias atrás até hoje:**

```bash
HOJE=$(TZ=America/Sao_Paulo date +%F)
TRES_DIAS_ATRAS=$(TZ=America/Sao_Paulo date -d "3 days ago" +%F)
curl -s "http://localhost:8082/api/v1/consolidado?dataInicio=$TRES_DIAS_ATRAS&dataFim=$HOJE" -H "Authorization: Bearer $TOKEN"
```

```powershell
$agoraSP = [System.TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, "E. South America Standard Time")
$HOJE = $agoraSP.ToString("yyyy-MM-dd")
$TRES_DIAS_ATRAS = $agoraSP.AddDays(-3).ToString("yyyy-MM-dd")
$periodo = curl.exe -s "http://localhost:8082/api/v1/consolidado?dataInicio=$TRES_DIAS_ATRAS&dataFim=$HOJE" -H "Authorization: Bearer $TOKEN" | ConvertFrom-Json
"saldoAnterior=$($periodo.saldoAnterior)"
$periodo.dias | Format-Table data, totalCreditos, totalDebitos, totalEstornosCredito, saldoDia, saldoAcumulado, qtdLancamentos -AutoSize
```

**O que você deve ver:** quatro dias. Este é o resultado real do teste, com hoje em 24/10 (os seus dias vão ser outros):

```text
saldoAnterior=0.00

data       totalCreditos totalDebitos totalEstornosCredito saldoDia saldoAcumulado qtdLancamentos
2026-10-21 50.00         0.00         0.00                 50.00    50.00                       1
2026-10-22 0.00          0.00         0.00                 0.00     50.00                       0
2026-10-23 0.00          0.00         0.00                 0.00     50.00                       0
2026-10-24 100.00        30.00        100.00               -30.00   20.00                       3
```

Como ler esse resultado:
- **3 dias atrás:** só o crédito retroativo de 50,00.
- **Os dois dias do meio:** sem movimento, aparecem zerados.
- **Hoje:** crédito de 100, débito de 30 e o estorno de 100. O saldo do dia é (100 − 100) − 30 = **−30,00**, e o acumulado é 50 − 30 = **20,00**.

No bash, a resposta vem como um JSON numa linha só, com os mesmos números.

**11.2. Um dia só:** o saldo acumulado do dia inclui tudo desde o início.

```bash
curl -i "http://localhost:8082/api/v1/consolidado/$HOJE" -H "Authorization: Bearer $TOKEN"
```

```powershell
curl.exe -i "http://localhost:8082/api/v1/consolidado/$HOJE" -H "Authorization: Bearer $TOKEN"
```

**O que você deve ver:** `HTTP/1.1 200` e o dia de hoje, com `"saldoDia":"-30.00"`, `"saldoAcumulado":"20.00"` e `"qtdLancamentos":3`.

Regras da consulta:
- `dataInicio` e `dataFim` são obrigatórias, no formato `AAAA-MM-DD`;
- o período tem no máximo 366 dias;
- um parâmetro a mais ou errado responde `400`, explicando o problema no campo `errors`.

Por exemplo, sem `dataFim` a resposta é `400` com `"campo":"dataFim","codigo":"obrigatorio"`.

### 12. Explorar a API pelo navegador (Swagger UI)

**Para que serve:** ver todas as operações, os campos e exemplos, e testar pelo navegador.

- Lançamentos: <http://localhost:8081/swagger-ui/index.html>
- Consolidado: <http://localhost:8082/swagger-ui/index.html>

Para chamar uma operação, clique em **Authorize**, cole o token do passo 6 (só o texto, sem `Bearer`) e confirme.

**O que você deve ver:** a página da API, com as operações listadas.

### 13. Conferir os dados direto nos bancos (opcional)

**Para que serve:** ver com os próprios olhos as tabelas e os dados que os serviços gravaram. O comando roda o `psql`, o cliente do PostgreSQL, dentro do contêiner do banco; não é preciso instalar nada. Os comandos são iguais em bash e em PowerShell.

```bash
docker compose --env-file .env -f deploy/docker-compose.yml exec postgres psql -U postgres -d consolidado -c "\dt"
docker compose --env-file .env -f deploy/docker-compose.yml exec postgres psql -U postgres -d consolidado -c "SELECT data_referencia, total_creditos, total_debitos, total_estornos_credito, qtd_lancamentos FROM consolidado_diario ORDER BY data_referencia"
docker compose --env-file .env -f deploy/docker-compose.yml exec postgres psql -U postgres -d lancamentos -c "SELECT tipo, valor, data_referencia FROM lancamento ORDER BY criado_em"
```

```powershell
docker compose --env-file .env -f deploy/docker-compose.yml exec postgres psql -U postgres -d consolidado -c "\dt"
docker compose --env-file .env -f deploy/docker-compose.yml exec postgres psql -U postgres -d consolidado -c "SELECT data_referencia, total_creditos, total_debitos, total_estornos_credito, qtd_lancamentos FROM consolidado_diario ORDER BY data_referencia"
docker compose --env-file .env -f deploy/docker-compose.yml exec postgres psql -U postgres -d lancamentos -c "SELECT tipo, valor, data_referencia FROM lancamento ORDER BY criado_em"
```

**O que você deve ver:**
- **o `\dt` do consolidado:** as tabelas `consolidado_diario`, `evento_processado` e `flyway_schema_history`. No banco `lancamentos`, as tabelas são `lancamento`, `outbox_event`, `idempotency_key` e `flyway_schema_history`;
- **o consolidado:** uma linha por dia com movimento (no exemplo, 3 dias atrás e hoje);
- **os lançamentos:** os 4 lançamentos, na ordem em que foram gravados: crédito, débito, estorno e retroativo.

> O `-U postgres` é o usuário administrador definido em `POSTGRES_SUPERUSER` no `.env`. Se você trocou esse valor, use o seu.

### 14. Parar e limpar

**Para que serve:** desligar tudo quando terminar.

**Desligar os serviços:** nos Terminais 1 e 2, aperte **Ctrl+C**. No PowerShell, se aparecer `Deseja finalizar o arquivo em lotes (S/N)?`, responda **S**.

Os comandos abaixo podem ser rodados em qualquer terminal aberto na pasta do projeto (por exemplo, o Terminal 1, já livre). Se você usou uma stack separada (aviso do passo 3), o terminal precisa ter o mesmo `COMPOSE_PROJECT_NAME`.

**Desligar a infraestrutura e guardar os dados** para a próxima vez:

```bash
docker compose --env-file .env -f deploy/docker-compose.yml down
```

```powershell
docker compose --env-file .env -f deploy/docker-compose.yml down
```

**O que você deve ver:** linhas `Removed` para os contêineres e a rede. Os dados continuam nos volumes do Docker, e na próxima vez basta voltar ao passo 3.

**Apagar tudo, inclusive os dados:**

> **Cuidado:** apaga os dois bancos e as mensagens do Kafka, sem volta. Isso vale para **toda** a stack `fluxo-caixa` desta máquina, inclusive dados de antes deste passo a passo (veja o aviso do passo 3).

```bash
docker compose --env-file .env -f deploy/docker-compose.yml down -v
```

```powershell
docker compose --env-file .env -f deploy/docker-compose.yml down -v
```

**O que você deve ver:** as linhas `Removed`, mais `Volume fluxo-caixa_postgres-data Removed` e `Volume fluxo-caixa_kafka-data Removed`.

**Para recomeçar depois de um `down -v`:**
1. refaça o passo 3, que recria os bancos e os usuários;
2. refaça os passos 7 e 8, que recriam as tabelas.

A compilação (passo 5) e a chave (passo 6) não se perdem.

**Apagar os arquivos de exemplo do PowerShell (opcional):** os JSON do passo 9 ficaram na pasta temporária do Windows, fora do projeto. Para apagá-los:

```powershell
Remove-Item "$env:TEMP\credito.json", "$env:TEMP\debito.json", "$env:TEMP\retroativo.json" -ErrorAction SilentlyContinue
```

No bash não há nada a apagar: o corpo vai direto no comando.

## Como funcionam os bancos de dados

- **Uma instância de PostgreSQL com dois bancos:** `lancamentos` e `consolidado`. Cada serviço tem dois usuários:
  - um "dono" (`*_owner`), que cria as tabelas;
  - um "aplicação" (`*_app`), que só lê e grava.

  Um serviço não consegue entrar no banco do outro. É isso que o passo 4 confere.
- **Quem cria os bancos e os usuários:** o script `deploy/init/01-bancos-e-roles.sql`, executado pelo PostgreSQL **só na primeira vez** que o volume de dados é criado (o primeiro `up`, ou o primeiro depois de um `down -v`).
- **Quem cria as tabelas:** cada serviço, pelo Flyway, **ao subir** (passos 7 e 8). As migrações ficam em `lancamentos-service/src/main/resources/db/migration/` e `consolidado-service/src/main/resources/db/migration/`. Se o serviço nunca subiu, o banco existe, mas está **vazio**. Nas subidas seguintes, o Flyway só aplica as migrações novas.
- **De onde vêm os dados do consolidado:** dos eventos que o Lançamentos publica no Kafka. Na primeira subida, o Consolidado lê todos os eventos que o Kafka ainda guarda, desde o início.
  - **O Kafka guarda os eventos por 7 dias.** Se o Consolidado subir pela primeira vez mais de 7 dias depois de um lançamento, esse lançamento não entra no resumo. Os 7 dias são o padrão do Kafka, não um valor fixado pelo projeto para o tópico principal (só a DLT tem retenção própria, de 30 dias, em `deploy/kafka/create-topics.sh`). Se a imagem do Kafka mudar esse padrão, este prazo muda junto.
  - Reconstruir o consolidado a partir dos lançamentos é uma evolução prevista ([ADR-006](docs/architecture/decisions/ADR-006-estrategia-de-rebuild.md)), fora deste MVP.
- **O que cada comando faz com os dados:**

  | Comando | Contêineres | Dados (bancos e Kafka) | Para voltar a usar |
  |---|---|---|---|
  | `down` | removidos | **mantidos** | passo 3 e passos 7 e 8 |
  | `down -v` | removidos | **apagados** | passo 3 (recria bancos e usuários) e passos 7 e 8 (recriam as tabelas) |

## Se algo der errado

| O que aparece | Causa provável | O que fazer |
|---|---|---|
| `Cannot connect to the Docker daemon` ou `error during connect` | Docker parado | Abra o Docker Desktop e espere "Engine running" |
| `port is already allocated` no passo 3, ou `Port 8081 was already in use` | A porta já está em uso por outro programa | Feche o outro programa ou troque a porta: `POSTGRES_PORT`/`KAFKA_PORT` no `.env`; para os serviços, defina `SERVER_PORT` antes de subir (bash: `export SERVER_PORT=18081`; PowerShell: `$env:SERVER_PORT = "18081"`) e use a nova porta nas chamadas |
| `Could not find artifact br.com.fluxocaixa:contracts` ao subir o Consolidado | O projeto não foi compilado | Rode o passo 5 |
| `chave pública JWT ilegível em file:../deploy/dev-keys/jwt-public.pem` | A chave ainda não foi gerada | Rode o passo 6 uma vez e suba o serviço de novo |
| Erro de senha do banco no log do serviço (`password authentication failed` ou `no password was provided`) | O `.env` não foi carregado neste terminal | Rode a linha que carrega o `.env` (passos 7 e 8) no mesmo terminal e suba de novo |
| Resposta `401` | Token ausente, vencido (1 hora) ou com o `ID` vazio na URL | Gere outro token (passo 6) e confira `$ID` |
| Resposta `403` | O token não tem a permissão (*scope*) da operação | Gere o token com os três *scopes* do passo 6 |
| Resposta `400` com `json-malformado` | JSON escrito direto na linha no PowerShell 5.1, ou com acento no Git Bash | Use o arquivo com `-d "@arquivo.json"` no PowerShell e textos sem acento |
| Resposta `422` com `data-retroativa-excedida` ou `data-futura` | Data de mais de 30 dias atrás ou no futuro | Use uma data dentro dos últimos 30 dias |
| O consolidado não mostra um lançamento recente | Ele ainda está chegando pelo Kafka, ou o serviço de Consolidado não está rodando | Espere 1 a 2 segundos e consulte de novo; confira o Terminal 2 |
| O `verify-local.sh` falha ou não encontra o Docker quando chamado com `bash` no PowerShell | O `bash` do WSL foi usado no lugar do Git Bash | Use `& "C:\Program Files\Git\bin\bash.exe" deploy/verify-local.sh` |

## Rodar os testes e as verificações de qualidade

O comando abaixo compila o projeto e roda todos os testes, inclusive os de integração. Esses testes sobem seus próprios bancos e Kafka temporários com o Docker, sem tocar nos contêineres do passo a passo. Ele também roda as verificações de qualidade:
- formatação (Spotless) e estilo (Checkstyle);
- regras de arquitetura (ArchUnit);
- busca de bugs (SpotBugs/FindSecBugs);
- cobertura de testes (JaCoCo).

Leva alguns minutos.

```bash
./mvnw verify
```

```powershell
.\mvnw.cmd verify
```

**O que você deve ver:** no fim, `BUILD SUCCESS`. Se a formatação falhar, corrija com `./mvnw spotless:apply` (bash) ou `.\mvnw.cmd spotless:apply` (PowerShell).

O Maven Wrapper usa o modo `only-script`: nenhum JAR binário do Wrapper é versionado. Para ativar os *hooks* de Git do projeto, uma vez por clone:

```bash
git config core.hooksPath .githooks
```

```powershell
git config core.hooksPath .githooks
```

O `pre-commit` roda o gitleaks (se instalado) e a checagem de formatação. O `pre-push` roda `./mvnw -q verify -DskipITs`. Os *hooks* podem ser contornados, por isso o CI repete tudo.

### Verificar uma tarefa (gate do método)

Quem implementa uma tarefa do projeto usa três scripts (precisam de Git, Java 25 e, para o gate completo, do Docker). No PowerShell, chame o `bash` do Git (o `bash` solto pode ser o do WSL).

**1. Testar os próprios scripts** (leva cerca de um minuto; não usa Docker nem a sua pasta de trabalho):

```bash
scripts/test/run-tests.sh
```

```powershell
& "C:\Program Files\Git\bin\bash.exe" scripts/test/run-tests.sh
```

**O que você deve ver:** uma linha por cenário e, no fim, `OK: 75 conferências, 0 falhas` (o número de conferências cresce quando entram cenários novos).

**2. Listar os requisitos sem teste** (sai com erro enquanto houver requisito obrigatório sem prova) e regenerar a matriz [`docs/specs/traceability.md`](docs/specs/traceability.md):

```bash
scripts/check-traceability.sh
scripts/check-traceability.sh --write
```

```powershell
& "C:\Program Files\Git\bin\bash.exe" scripts/check-traceability.sh
& "C:\Program Files\Git\bin\bash.exe" scripts/check-traceability.sh --write
```

**O que você deve ver:** a primeira lista `FALTA RNF-xxx (tarefas: ...)` para cada requisito obrigatório que ainda não tem teste, e os que são provados por evidência, com o arquivo; ela termina com erro enquanto houver `FALTA`. A segunda imprime `gerado: docs/specs/traceability.md`.

**3. Rodar o gate de uma tarefa**, a partir do branch da tarefa. `BASE` é o branch contra o qual o *diff* é calculado (o padrão é `main`; se o seu `main` local estiver defasado, use o branch base da tarefa):

```bash
BASE=main scripts/verify-task.sh T-003
```

```powershell
$env:BASE = "main"
& "C:\Program Files\Git\bin\bash.exe" scripts/verify-task.sh T-003
```

**O que você deve ver:** uma linha `OK item N (...)` ou `FALHA item N (...)` para cada um dos seis itens (build, escopo, dependências, rastreabilidade, padrões proibidos e artefatos), seguida de `RESULTADO: OK` ou `RESULTADO: FALHA`. O resultado também fica em `docs/specs/reviews/T-xxx.verify.log` (arquivo local, ignorado pelo Git). O gate lê os *commits*: faça o commit antes de rodar.

## Contêineres do ambiente local

| Serviço | Imagem | Papel | Porta no host | Profile | Ciclo de vida |
|---|---|---|---|---|---|
| `postgres` | `postgres:17.11-alpine` | Bancos `lancamentos` e `consolidado`, cada um com usuários próprios (`deploy/init/`) | `POSTGRES_PORT` (5432) | padrão | Contínuo, com *healthcheck* |
| `kafka` | `apache/kafka:3.9.1` | Broker KRaft de nó único, sem criação automática de tópicos | `KAFKA_PORT` (9092) | padrão | Contínuo, com *healthcheck* |
| `kafka-topicos` | `apache/kafka:3.9.1` | Cria o tópico `cashflow.lancamentos.v1` e a DLT (`deploy/kafka/create-topics.sh`) | — | padrão | Execução única: roda depois que o `kafka` fica saudável e termina com exit 0 |
| `kafka-ui` | `kafbat/kafka-ui:v1.3.0` | Página para ver tópicos e mensagens | `KAFKA_UI_PORT` (8090) | `tools` | Contínuo, opcional |

- **Kafka UI:** para ver as mensagens no navegador, acrescente `--profile tools` ao comando do passo 3 e abra <http://localhost:8090>.
- **Serviços em contêiner:** os contêineres dos dois serviços (profile `apps`) e os de observabilidade (profile `observability`) entram nas T-035 e T-032. Até lá, os serviços sobem pelo Maven, como nos passos 7 e 8.
- **`--remove-orphans`:** remove contêineres de serviços que saíram do compose ou foram renomeados, como o de criação de tópicos na T-006.
- **Volumes soltos:** a imagem do Kafka declara volumes próprios. Ao remover um contêiner órfão, ficam volumes anônimos soltos, que o `down -v` não alcança. Liste-os com `docker volume ls -f dangling=true` antes de apagar.

## Documentação

- [`docs/architecture/architecture.md`](docs/architecture/architecture.md): visão geral, diagramas C4, arquitetura-alvo AWS (**apenas desenho**; o case é implementado e provado só localmente) e tabela de requisitos não funcionais.
- [`docs/architecture/decisions/`](docs/architecture/decisions/): decisões de arquitetura (ADR-001 a ADR-010), com alternativas rejeitadas e *trade-offs*.
- [`docs/specs/`](docs/specs/): especificação (constituição, domínios e capacidades, requisitos, contratos, padrões de qualidade, backlog).
- Contratos das APIs: [`lancamentos-v1.yaml`](lancamentos-service/src/main/resources/openapi/lancamentos-v1.yaml) e [`consolidado-v1.yaml`](consolidado-service/src/main/resources/openapi/consolidado-v1.yaml). Contrato do evento: [`lancamento-registrado.v1.schema.json`](contracts/src/main/resources/events/lancamento-registrado.v1.schema.json).
- [Runbook da DLT](docs/runbooks/dlt-reprocessamento.md): o que fazer quando um evento não pode ser aplicado no Consolidado.

## Módulos

- **`contracts/`:** o formato (JSON Schema) e os exemplos do evento `LancamentoRegistrado.v1`, sem código.
- **`lancamentos-service/`:** registra, estorna e consulta lançamentos; publica os eventos (outbox).
- **`consolidado-service/`:** consome os eventos, mantém o resumo diário e responde as consultas.

## Método de desenvolvimento

O projeto usa **Spec Driven Development (SDD)**, com implementação e revisão independente por tarefa. Antes de implementar uma tarefa, consulte:

1. [`AGENTS.md`](AGENTS.md): instruções para agentes de código.
2. [`docs/specs/00-constitution.md`](docs/specs/00-constitution.md): princípios e convenções.
3. [`docs/specs/tasks/`](docs/specs/tasks): o arquivo da tarefa, por exemplo `T-023.md`.
4. [`docs/specs/05-harness.md`](docs/specs/05-harness.md): verificação, testes e revisão.

Cada tarefa é verificada pelo mesmo comando, `scripts/verify-task.sh T-xxx` (veja [Verificar uma tarefa](#verificar-uma-tarefa-gate-do-método)), e depois revisada por um subagente independente ([`.claude/agents/reviewer.md`](.claude/agents/reviewer.md)), que escreve o relatório em `docs/specs/reviews/T-xxx.md`. Os modelos da tarefa e da revisão estão em [`docs/specs/templates/`](docs/specs/templates/), o inventário de dependências em [`dependency-allowlist.md`](docs/specs/dependency-allowlist.md) e a matriz requisito → teste em [`traceability.md`](docs/specs/traceability.md).

### Manter este README vivo

Este README é parte do produto. Toda tarefa que muda **como subir ou usar a aplicação** atualiza o README no mesmo PR. Isso inclui:
- comandos, portas e variáveis;
- *profiles*, rotas e *scopes*;
- serviços do compose e scripts.

Os comandos afetados são **executados de novo, em bash e em PowerShell**, e o "o que você deve ver" é conferido com a saída real. A regra está no [`AGENTS.md`](AGENTS.md) e no [`05-harness.md`](docs/specs/05-harness.md), e a revisão independente a verifica (critério R10).

## Status

- **Concluído:**
  - **Fundação:** build reproduzível e gates de qualidade (T-001, T-002); infraestrutura local (T-004, T-006); documentação de arquitetura (T-005); este README executável (T-007); gate por tarefa, matriz de rastreabilidade e subagente revisor (T-003).
  - **Lançamentos (M1):** domínio, persistência, registro idempotente, API, estorno, consultas e publicador da outbox (T-010 a T-016).
  - **Consolidado (M2):** domínio da projeção, persistência, consumidor Kafka com DLT e API de consulta (T-020 a T-023).
- **Parcial:** o caminho do README ainda tem mais que os 5 comandos da RNF-009; a demo em um comando chega com a T-035, e a prova final, com a T-043.
- **Próximo:** contrato do evento validado nos dois lados (T-030), seguido dos testes de resiliência, da observabilidade, da carga e da demo guiada (Fase 3). A ordem está em [`docs/specs/06-tasks.md`](docs/specs/06-tasks.md).
