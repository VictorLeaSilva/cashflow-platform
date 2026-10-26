# Carga e desempenho (k6)

> Como repetir a medição do relatório de desempenho.

**Para que serve:** medir quanto o Consolidado aguenta (o enunciado pede 50 requisições por segundo, com perda de até 5 %) e quão fresco fica o consolidado enquanto o Lançamentos recebe escrita ao mesmo tempo. Os números e a leitura deles estão no [relatório de desempenho](../../docs/performance/relatorio.md); aqui está como repetir a medição.

**Antes de começar:** a stack dos serviços no ar, como no [Veja funcionando em 5 etapas](../../README.md#veja-funcionando-em-5-etapas) (passos 1 a 4, *profile* `apps`), e o `jq` instalado. Não precisa instalar o k6: o script usa a imagem `grafana/k6` do Docker (a primeira vez baixa a imagem). Para a medição valer, deixe a máquina quieta: feche o que consome CPU, pois o gerador de carga e os serviços dividem a mesma máquina.

**1. Conferir os scripts** (só a sintaxe, não precisa da stack):

**bash (Linux, macOS e Git Bash)**

```bash
loadtest/run.sh inspect
```

**PowerShell (Windows)**

```powershell
& "C:\Program Files\Git\bin\bash.exe" loadtest/run.sh inspect
```

**O que você deve ver:** `✔ k6 inspect: smoke.js` e `✔ k6 inspect: load.js`.

**2. Teste normal** (contrato ponta a ponta, uns 10 segundos):

**bash (Linux, macOS e Git Bash)**

```bash
loadtest/run.sh smoke
```

**PowerShell (Windows)**

```powershell
& "C:\Program Files\Git\bin\bash.exe" loadtest/run.sh smoke
```

**O que você deve ver:** uma lista de `✓` (um por verificação, todas em 100 %), `k6 terminou com exit 0` e `Resultados em docs/performance/results/smoke-...`.

**3. Teste de carga** (50 req/s de leitura do consolidado e 10 req/s de escrita, por 5 minutos, mais o *seed* e a espera da drenagem; leva uns 7 minutos):

**bash (Linux, macOS e Git Bash)**

```bash
loadtest/run.sh load
```

**PowerShell (Windows)**

```powershell
& "C:\Program Files\Git\bin\bash.exe" loadtest/run.sh load
```

**O que você deve ver:** o k6 mostrando as duas cenas (`consolidado_read` a 50 iters/s e `lancamentos_write` a 10 iters/s), limiares com `✓` (os quatro que valem: `dropped_iterations`, p95 da leitura, falha da leitura e falha da escrita; os três `max>=0` só existem para exportar latências e sempre passam), `k6 terminou com exit 0` e uma linha `lag: p95=...s max=...s eventos=...` com o atraso da projeção (a meta é p95 ≤ 5 s e máximo ≤ 30 s). Se algum limiar falhar, o script termina com erro e o resultado fica gravado mesmo assim.

Cada execução deixa arquivos em `docs/performance/results/`: o resumo do k6 (`.json`) e o ambiente (`.env.txt`) e, no teste de carga, também o atraso da projeção e a operação durante a carga (`.lag.json`) e o uso de CPU e memória dos contêineres (`.stats.txt`).

**4. Rodadas informativas** (100 e 200 req/s, para ver até onde vai; não são a meta e o script não falha por limiar):

**bash (Linux, macOS e Git Bash)**

```bash
READ_RPS=100 loadtest/run.sh load
READ_RPS=200 loadtest/run.sh load
```

**PowerShell (Windows)**

```powershell
$env:READ_RPS = "100"; & "C:\Program Files\Git\bin\bash.exe" loadtest/run.sh load
$env:READ_RPS = "200"; & "C:\Program Files\Git\bin\bash.exe" loadtest/run.sh load
Remove-Item Env:READ_RPS
```

**Se a stack usa outro arquivo de configuração ou outro nome de projeto** (por exemplo, uma stack isolada de teste), aponte o script para ela com `ENV_FILE=.env.outro` (no PowerShell, `$env:ENV_FILE = ".env.outro"`). O script descobre a rede do compose pelo contêiner do Lançamentos.

**Cuidados:** `dropped_iterations` maior que zero invalida a rodada (a máquina não sustentou a taxa pedida). O teste usa um comerciante novo a cada execução, então pode ser repetido. Mais detalhes em [`loadtest/README.md`](../../loadtest/README.md).
