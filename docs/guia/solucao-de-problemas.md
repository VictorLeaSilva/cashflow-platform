# Se algo der errado

> Erros comuns ao subir e usar a aplicação, com a causa provável e o que fazer.

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
