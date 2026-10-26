# Passo a passo: subir e usar a aplicação do zero

> Guia detalhado, com os comandos em **bash** e em **PowerShell**. O caminho curto, de 5 etapas, está no [README](../../README.md#veja-funcionando-em-5-etapas). Esta é a versão manual, com três terminais, que explica cada peça. A regra de README vivo (Q-054) vale para este arquivo (veja [método de desenvolvimento](metodo-de-desenvolvimento.md#manter-o-readme-e-o-passo-a-passo-vivos)).

## Caminho curto: os 5 comandos do README, com os detalhes

**Para que serve:** subir os dois serviços e a infraestrutura em contêineres e ver, com um script, as garantias centrais do projeto: **idempotência**, **estorno**, **lançamento retroativo**, **isolamento entre comerciantes** e o **Consolidado parado** (o Lançamentos continua gravando e o Consolidado converge quando volta). Com a opção `--kafka-down`, também o **Kafka parado**. O passo a passo detalhado, pelo Maven, continua [mais abaixo](#passo-a-passo-subir-e-usar-a-aplicação-do-zero).

**Antes de começar** (uma vez): Git, Docker (ligado), Java 25 e o `jq`. O `jq` é o leitor de JSON que o script usa:
- Windows: `winget install jqlang.jq` e depois **feche e abra o terminal** (o `PATH` só vale em terminais novos);
- macOS: `brew install jq`; Linux: `sudo apt install jq` (ou o gerenciador da sua distribuição).

No Windows, o script roda pelo **Git Bash** (instalado junto com o Git). As portas `8081`, `8082`, `9081` e `9082` precisam estar livres, assim como as do banco e do Kafka (veja o aviso do [passo 3](#3-subir-a-infraestrutura-banco-de-dados-e-kafka) para usar outras).

**1. Baixar o projeto:**

**bash (Linux, macOS e Git Bash)**

```bash
git clone https://github.com/VictorLeaSilva/cashflow-platform.git
cd cashflow-platform
```

**PowerShell (Windows)**

```powershell
git -c core.longpaths=true clone https://github.com/VictorLeaSilva/cashflow-platform.git
cd cashflow-platform
```

**2. Criar o arquivo de configuração `.env`:**

**bash (Linux, macOS e Git Bash)**

```bash
cp .env.example .env
```

**PowerShell (Windows)**

```powershell
Copy-Item .env.example .env
```

**3. Compilar e gerar as imagens dos serviços** (a primeira vez baixa o *builder* do Paketo, centenas de MB, e leva vários minutos):

**bash (Linux, macOS e Git Bash)**

```bash
./mvnw -q install -DskipTests -Pimagens
```

**PowerShell (Windows)**

```powershell
.\mvnw.cmd -q install "-DskipTests" "-Pimagens"
```

**O que você deve ver:** o terminal volta sem erro, e `docker images fluxo-caixa/*` lista `fluxo-caixa/lancamentos` e `fluxo-caixa/consolidado` na versão `0.1.0-SNAPSHOT`. Se aparecer `Builder lifecycle 'creator' failed` ou `unexpected EOF`, foi uma falha de rede ao baixar camadas: rode o comando de novo.

**4. Subir tudo** (banco, Kafka, a chave de desenvolvimento e os dois serviços):

**bash e PowerShell**

```bash
docker compose --env-file .env -f deploy/docker-compose.yml --profile apps up -d --wait --remove-orphans
```

**O que você deve ver:** linhas `Healthy` para `postgres`, `kafka`, `lancamentos` e `consolidado`, e `Started` para `dev-keys` e `kafka-topicos` (que terminam sozinhos). Os serviços levam cerca de meio minuto para ficar prontos; o script espera por eles.

**5. Rodar a demonstração:**

**bash (Linux, macOS e Git Bash)**

```bash
scripts/demo.sh
```

**PowerShell (Windows)**

```powershell
& "C:\Program Files\Git\bin\bash.exe" scripts/demo.sh
```

**O que você deve ver:** sete blocos, cada um com linhas `✔` (verde) e o valor conferido, e no fim:

```text
Demonstração concluída: RF-007, RF-003, RF-009, RF-010 e RNF-001
```

Para incluir a prova do Kafka parado (leva mais um ou dois minutos), use `scripts/demo.sh --kafka-down`; o final passa a citar também o `RNF-007`. A demo usa **comerciantes novos a cada execução**, então pode ser repetida. Se ela falhar no meio, o script religa o Consolidado e o Kafka antes de sair.

**Para desligar:**

**bash e PowerShell**

```bash
docker compose --env-file .env -f deploy/docker-compose.yml --profile apps down
```

(Acrescente `-v` para apagar também os dados, como no [passo 14](#14-parar-e-limpar).) Os contêineres dos serviços rodam como usuário comum (não-root) e as portas de gestão (`9081` e `9082`) são as mesmas da [Observabilidade](observabilidade-e-alertas.md#observabilidade-métricas-alertas-e-painel-opcional), então o Prometheus e o Grafana funcionam sem mudança: acrescente `--profile observability` ao comando do passo 4.

## O que você precisa instalar

| Programa | Onde baixar | Como conferir (bash e PowerShell) | O que deve aparecer |
|---|---|---|---|
| **JDK 25** (Java) | [Adoptium](https://adoptium.net/) ou [Oracle](https://www.oracle.com/java/technologies/downloads/) | `java -version` | `java version "25...` ou `openjdk version "25...` |
| **Docker Desktop** (ou Docker Engine com Compose v2) | [docker.com](https://www.docker.com/products/docker-desktop/) | `docker compose version` | `Docker Compose version v2...` ou superior |
| **curl** | Já vem no Windows 10 e 11, no macOS e na maioria dos Linux | bash: `curl --version`; PowerShell: `curl.exe --version` | `curl 8...` |
| **Git** (no Windows, inclui o **Git Bash**) | [git-scm.com](https://git-scm.com/downloads) | `git --version` | `git version 2...` |

> **Docker aberto.** No Windows e no macOS, abra o Docker Desktop e espere o status "Engine running" antes de começar.

> **No PowerShell, use `curl.exe`, nunca só `curl`.** No Windows PowerShell, `curl` é outro comando (um apelido do `Invoke-WebRequest`) e não aceita as opções deste guia.


Siga na ordem. Cada passo traz o comando em **bash** e em **PowerShell**: use só o do seu terminal. Os comandos foram executados de verdade nos dois terminais, e as saídas mostradas são as reais, só resumidas.

Você vai usar **três terminais abertos ao mesmo tempo**, todos na pasta do projeto:
- **Terminal 1:** infraestrutura e serviço de Lançamentos.
- **Terminal 2:** serviço de Consolidado.
- **Terminal 3:** token e chamadas à API.

> Os números de porta e os valores de exemplo (comerciante, valores) podem ficar como estão.

### 1. Baixar o projeto e abrir o terminal na pasta dele (Terminal 1)

**Para que serve:** todos os comandos partem da pasta raiz do projeto, onde fica o `README.md`. Se você ainda não tem o projeto no computador, o `git clone` baixa uma cópia dele.

Se você **ainda não tem** o projeto, abra o terminal na pasta onde quer guardá-lo e rode:

**bash (Linux, macOS e Git Bash)**

```bash
git clone https://github.com/VictorLeaSilva/cashflow-platform.git
cd cashflow-platform
```

**PowerShell (Windows)**

```powershell
git -c core.longpaths=true clone https://github.com/VictorLeaSilva/cashflow-platform.git
cd cashflow-platform
```

> **No Windows**, o `-c core.longpaths=true` evita o erro `Filename too long`: alguns caminhos do projeto passam do limite antigo de 260 caracteres do Windows, principalmente se a pasta escolhida já tiver um caminho longo (por exemplo, dentro do OneDrive). No Git Bash do Windows, use o mesmo comando do PowerShell.

Se você **já tem** o projeto, só entre na pasta dele:

**bash (Linux, macOS e Git Bash)**

```bash
cd caminho/para/cashflow-platform
```

**PowerShell (Windows)**

```powershell
cd C:\caminho\para\cashflow-platform
```

**O que você deve ver:** o `git clone` mostra `Cloning into 'cashflow-platform'...` e termina sem erro. Para conferir que está na pasta certa, `ls` (bash) ou `dir` (PowerShell) lista, entre outros, `README.md`, `deploy`, `lancamentos-service` e `consolidado-service`.

### 2. Criar o arquivo de configuração `.env` (só na primeira vez)

**Para que serve:** o `.env` guarda as senhas e as portas do ambiente local. O projeto traz um modelo, o `.env.example`, com valores próprios para desenvolvimento. O `.env` não vai para o Git.

**bash (Linux, macOS e Git Bash)**

```bash
cp .env.example .env
```

**PowerShell (Windows)**

```powershell
Copy-Item .env.example .env
```

**O que você deve ver:** nada. O arquivo `.env` passa a existir na pasta.

> Se a porta 5432 (PostgreSQL) ou 9092 (Kafka) já estiver em uso no seu computador, troque `POSTGRES_PORT` ou `KAFKA_PORT` no `.env` antes do próximo passo.

### 3. Subir a infraestrutura (banco de dados e Kafka)

**Para que serve:** liga, no Docker, o PostgreSQL (com os bancos `lancamentos` e `consolidado`) e o Kafka (com o tópico de eventos). O `--wait` só devolve o terminal quando tudo está pronto. Na primeira vez, o Docker baixa as imagens, o que pode levar alguns minutos.

**bash e PowerShell**

```bash
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
> 3. se os serviços da outra stack estiverem rodando, suba estes com outra porta (veja `SERVER_PORT` em [Se algo der errado](solucao-de-problemas.md#se-algo-der-errado)) e use essa porta nas chamadas.
>
> Os comandos `docker compose` deste guia passam a valer para a stack `fluxo-caixa-teste`, inclusive o `down -v`.

### 4. Conferir a infraestrutura

**Para que serve:** um script confere que o banco e o Kafka estão saudáveis, que cada serviço só acessa o próprio banco e que os tópicos existem. O script é em bash. No PowerShell, ele é chamado pelo Git Bash.

**bash (Linux, macOS e Git Bash)**

```bash
deploy/verify-local.sh
```

**PowerShell (Windows)**

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

**Para que serve:** compila os módulos e os guarda no repositório local do Maven. Sem isso, o serviço de Consolidado não sobe, porque depende do módulo `contracts`, onde fica o formato do evento. O `-DskipTests` pula os testes para ir mais rápido (os testes estão em [Rodar os testes](testes-e-qualidade.md#rodar-os-testes-e-as-verificações-de-qualidade)).

**bash (Linux, macOS e Git Bash)**

```bash
./mvnw -q install -DskipTests
```

**PowerShell (Windows)**

```powershell
.\mvnw.cmd -q install "-DskipTests"
```

**O que você deve ver:** depois de 1 a 2 minutos, o terminal volta sem mensagem de erro. Na primeira vez, o Maven baixa as bibliotecas e demora mais.

> Avisos que começam com `WARNING: A terminally deprecated method in sun.misc.Unsafe` são do próprio Maven e podem ser ignorados.

### 6. Gerar a chave e o token de acesso (Terminal 3)

**Para que serve:** os serviços só aceitam chamadas com um **token** válido. O script `DevToken.java` gera esse token só para o ambiente local. Na **primeira execução**, ele também cria um par de chaves em `deploy/dev-keys/`, que os serviços usam para conferir o token.

> **Rode este passo antes de subir os serviços**, pelo menos uma vez. Sem a pasta `deploy/dev-keys/`, os serviços não sobem.

Abra um **novo terminal (Terminal 3)** na pasta do projeto e rode:

**bash (Linux, macOS e Git Bash)**

```bash
TOKEN=$(java scripts/DevToken.java --merchant 0f8fad5b-d9cb-469f-a165-70867728950e --scopes lancamentos:write,lancamentos:read,consolidado:read --ttl 3600)
echo "$TOKEN"
```

**PowerShell (Windows)**

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

**bash (Linux, macOS e Git Bash)**

```bash
set -a; . ./.env; set +a
./mvnw -pl lancamentos-service spring-boot:run -Dspring-boot.run.profiles=local
```

**PowerShell (Windows)**

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

**bash (Linux, macOS e Git Bash)**

```bash
set -a; . ./.env; set +a
./mvnw -pl consolidado-service spring-boot:run -Dspring-boot.run.profiles=local
```

**PowerShell (Windows)**

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

**bash (Linux, macOS e Git Bash)**

```bash
RESPOSTA=$(curl -s -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-credito-1" -d '{"tipo":"CREDITO","valor":"100.00","descricao":"Venda do dia"}')
echo "$RESPOSTA"
ID=$(echo "$RESPOSTA" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
echo "ID=$ID"
```

**PowerShell (Windows)**

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

**bash (Linux, macOS e Git Bash)**

```bash
curl -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-credito-1" -d '{"tipo":"CREDITO","valor":"100.00","descricao":"Venda do dia"}'
```

**PowerShell (Windows)**

```powershell
curl.exe -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-credito-1" -d "@$env:TEMP\credito.json"
```

**O que você deve ver:** `HTTP/1.1 201`, o mesmo `id` do passo anterior e o cabeçalho `Idempotent-Replayed: true`. Nenhum lançamento novo foi criado.

**9.3. Registrar um débito de R$ 30,00:**

**bash (Linux, macOS e Git Bash)**

```bash
curl -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-debito-1" -d '{"tipo":"DEBITO","valor":"30.00","descricao":"Fornecedor"}'
```

**PowerShell (Windows)**

```powershell
Set-Content -Path "$env:TEMP\debito.json" -Encoding ascii -Value '{"tipo":"DEBITO","valor":"30.00","descricao":"Fornecedor"}'
curl.exe -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-debito-1" -d "@$env:TEMP\debito.json"
```

**O que você deve ver:** `HTTP/1.1 201` e um JSON com `"tipo":"DEBITO"` e `"valor":"30.00"`.

**9.4. Estornar (desfazer) o crédito.** Um lançamento nunca é apagado nem editado: o estorno é um lançamento novo, que compensa o original. Este comando usa o `ID` guardado no passo 9.1, então rode-o no mesmo terminal.

**bash (Linux, macOS e Git Bash)**

```bash
curl -i -X POST http://localhost:8081/api/v1/lancamentos/$ID/estornos -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: exemplo-estorno-1"
```

**PowerShell (Windows)**

```powershell
curl.exe -i -X POST "http://localhost:8081/api/v1/lancamentos/$ID/estornos" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: exemplo-estorno-1"
```

**O que você deve ver:** `HTTP/1.1 201` e um JSON com `"tipo":"ESTORNO"`, `"tipoOriginal":"CREDITO"` e `"referenciaLancamentoId"` igual ao `ID` do crédito. O estorno vale no dia de hoje, mesmo que o original seja de outro dia.

> Se aparecer `401` aqui, o `ID` provavelmente ficou vazio, porque o passo 9.1 falhou. Rode `echo "$ID"` (bash) ou `$ID` (PowerShell) para conferir.

**9.5. Tentar estornar o mesmo crédito de novo**, com outra etiqueta:

**bash (Linux, macOS e Git Bash)**

```bash
curl -i -X POST http://localhost:8081/api/v1/lancamentos/$ID/estornos -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: exemplo-estorno-2"
```

**PowerShell (Windows)**

```powershell
curl.exe -i -X POST "http://localhost:8081/api/v1/lancamentos/$ID/estornos" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: exemplo-estorno-2"
```

**O que você deve ver:** `HTTP/1.1 409` e `"type":"/erros/lancamento-ja-estornado"`. Cada lançamento só pode ser estornado uma vez.

**9.6. Registrar um crédito retroativo de R$ 50,00, com data de 3 dias atrás.** O sistema aceita lançamentos de até 30 dias atrás e nunca do futuro.

**bash (Linux, macOS e Git Bash)**

```bash
TRES_DIAS_ATRAS=$(TZ=America/Sao_Paulo date -d "3 days ago" +%F)
curl -i -X POST http://localhost:8081/api/v1/lancamentos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: exemplo-passado-1" -d "{\"tipo\":\"CREDITO\",\"valor\":\"50.00\",\"dataOcorrencia\":\"${TRES_DIAS_ATRAS}T12:00:00-03:00\"}"
```

**PowerShell (Windows)**

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

**bash (Linux, macOS e Git Bash)**

```bash
curl -i http://localhost:8081/api/v1/lancamentos/$ID -H "Authorization: Bearer $TOKEN"
curl -s "http://localhost:8081/api/v1/lancamentos?size=10" -H "Authorization: Bearer $TOKEN"
```

**PowerShell (Windows)**

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

**bash (Linux, macOS e Git Bash)**

```bash
HOJE=$(TZ=America/Sao_Paulo date +%F)
TRES_DIAS_ATRAS=$(TZ=America/Sao_Paulo date -d "3 days ago" +%F)
curl -s "http://localhost:8082/api/v1/consolidado?dataInicio=$TRES_DIAS_ATRAS&dataFim=$HOJE" -H "Authorization: Bearer $TOKEN"
```

**PowerShell (Windows)**

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

**bash (Linux, macOS e Git Bash)**

```bash
curl -i "http://localhost:8082/api/v1/consolidado/$HOJE" -H "Authorization: Bearer $TOKEN"
```

**PowerShell (Windows)**

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

**bash e PowerShell**

```bash
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

Os comandos abaixo podem ser rodados em qualquer terminal aberto na pasta do projeto (por exemplo, o Terminal 1, já livre). Se você usou uma stack separada (aviso do passo 3), o terminal precisa ter o mesmo `COMPOSE_PROJECT_NAME`. Se subiu também o Prometheus e o Grafana, acrescente `--profile observability` aos comandos `down` abaixo (veja [Observabilidade](observabilidade-e-alertas.md#observabilidade-métricas-alertas-e-painel-opcional)); sem isso, esses dois contêineres continuam de pé.

**Desligar a infraestrutura e guardar os dados** para a próxima vez:

**bash e PowerShell**

```bash
docker compose --env-file .env -f deploy/docker-compose.yml down
```

**O que você deve ver:** linhas `Removed` para os contêineres e a rede. Os dados continuam nos volumes do Docker, e na próxima vez basta voltar ao passo 3.

**Apagar tudo, inclusive os dados:**

> **Cuidado:** apaga os dois bancos e as mensagens do Kafka, sem volta. Isso vale para **toda** a stack `fluxo-caixa` desta máquina, inclusive dados de antes deste passo a passo (veja o aviso do passo 3).

**bash e PowerShell**

```bash
docker compose --env-file .env -f deploy/docker-compose.yml down -v
```

**O que você deve ver:** as linhas `Removed`, mais `Volume fluxo-caixa_postgres-data Removed` e `Volume fluxo-caixa_kafka-data Removed`.

**Para recomeçar depois de um `down -v`:**
1. refaça o passo 3, que recria os bancos e os usuários;
2. refaça os passos 7 e 8, que recriam as tabelas.

A compilação (passo 5) e a chave (passo 6) não se perdem.

**Apagar os arquivos de exemplo do PowerShell (opcional):** os JSON do passo 9 ficaram na pasta temporária do Windows, fora do projeto. Para apagá-los:

**PowerShell (Windows)**

```powershell
Remove-Item "$env:TEMP\credito.json", "$env:TEMP\debito.json", "$env:TEMP\retroativo.json" -ErrorAction SilentlyContinue
```

No bash não há nada a apagar: o corpo vai direto no comando.
