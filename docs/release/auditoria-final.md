# Auditoria final de rastreabilidade (T-044, Q-099)

A auditoria **relata, não conserta** (só documentos). Foi feita em 2026-11-12, sobre o branch `task/T-044-release` (a partir do `main` em `f6a091e`), no Windows 11 com Git Bash e Docker Desktop. Cada linha traz o comando ou o arquivo que a prova, para ser repetida.

## 1. Definição de Pronto global (`00-constitution` §8)

| # | Critério | Como foi conferido | Resultado |
|---|---|---|---|
| 1 | Todos os RF/RNF obrigatórios com ao menos 1 teste rastreado e verde | `scripts/check-traceability.sh`: "Obrigatórios com @Tag sem prova: (nenhum)" (exit 0). A matriz [`traceability.md`](../specs/traceability.md) cobre os 10 RF e os 14 RNF; os testes ficam verdes no item 2 | **Atendido** |
| 2 | `./mvnw verify` verde a partir de clone limpo | Clone novo em `C:\dev\t044-verify` (fora do OneDrive, `git -c core.longpaths=true clone`), `./mvnw -B -ntp verify` com Docker ligado. Clone do commit `80b1976`: `BUILD SUCCESS` em 386 s (6 min 26 s), sem falhas nem erros; o Consolidado fechou com 59 testes de integração, e os quatro módulos do reator terminaram em `SUCCESS` | **Atendido** |
| 3 | `docker compose up` + README em ≤ 5 comandos | O README tem **5 etapas** ("Veja funcionando em 5 etapas"). A última execução de cada etapa, em clone limpo e stack isolada, **uma vez em bash e uma em PowerShell**, está nas evidências da [T-043](../specs/reviews/T-043.evidence.md) e da [T-045](../specs/reviews/T-045.evidence.md); a T-047 repetiu imagens, stack e `demo.sh --kafka-down` em bash ([evidência](../specs/reviews/T-047.evidence.md)). Desde então o README só ganhou links e estados (T-044); nenhum comando mudou | **Atendido**, pelas execuções citadas |
| 4 | Documentação exigida pelo enunciado no repositório | Tabela "O que o case pediu e onde está" do README: 9 obrigatórios, 4 diferenciais e os 2 requisitos não funcionais, cada um com link. `scripts/check-ponteiros.sh` (87 ponteiros nos guias) e `scripts/check-rotulos.sh` (94 blocos rotulados em 15 arquivos) sem erro; os links relativos dos arquivos novos resolvem | **Atendido** |
| 5 | Nenhum número de desempenho ou custo sem origem | Tabela da seção 5 abaixo: cada número do README e das notas do release com o arquivo de origem | **Atendido** |

## 2. Matriz de rastreabilidade

- **Por `@Tag`:** RF-001 a RF-006 e RF-008, RNF-004, RNF-006, RNF-012 a RNF-014.
- **`@Tag` e evidência:** RF-007, RF-009, RF-010, RNF-001, RNF-003, RNF-005, RNF-007, RNF-008 e RNF-009.
- **Só evidência** (sem teste automatizado, de propósito): RNF-002 e RNF-010, medidos pelo k6 e registrados em [`T-033.evidence.md`](../specs/reviews/T-033.evidence.md).
- **Só desenho:** RNF-011 (disponibilidade e DR da arquitetura-alvo; metas, não medidas).
- **Lacunas obrigatórias:** nenhuma.

## 3. Exceções do `sdd-status`

O `sdd-status` (harness do autor, fora do repositório) resume o estado de cada tarefa do backlog (23 `pronta`, 1 `cortada`, a própria T-044 `em-andamento` e as 9 exceções abaixo). As exceções são as que ele aponta; nenhuma bloqueia o release, e **nenhuma foi corrigida pela auditoria**.

| Tarefa | O que o script mostra | Causa real | Decisão |
|---|---|---|---|
| T-013 | `revisao-fail` | **Falso positivo.** O script lê o último `Veredito:` do arquivo, e o apêndice da rodada 1 (linha 76, FAIL) vem depois do veredito final (linha 2, PASS_WITH_NOTES) | Nenhuma ação no repositório |
| T-005 | `revisao-fail` | **Real, e já registrada.** A rodada 3 reprovou por um erro de sinal no ADR-005 (B3); foi corrigido sem nova rodada, por decisão do autor, e a T-040 fez a revisão cruzada ADRs × código depois. O checklist da tarefa diz isso | Registrada; revisão retroativa é decisão do autor |
| T-002 | `sem-revisao` | **Real.** Entrou antes do revisor independente existir; tem evidência e o checklist deixa "CI verde" e "revisão independente PASS" desmarcados | Registrada; revisão retroativa é decisão do autor |
| T-004 | `sem-revisao` | **Real.** Idem (checklist com "revisão independente PASS" desmarcado) | Registrada; idem |
| T-001 | `pronta-com-pendencia`, 0/0 | **Formato.** O checklist usa `[ x ]` (com espaços), que o script não conta | Cosmético; nenhuma ação |
| T-010 | `pronta-com-pendencia`, 0/3 | **Real.** Revisão PASS_WITH_NOTES existe, mas o checklist do arquivo da tarefa nunca foi marcado | O autor decide se marca (é ele quem registra a aprovação) |
| T-035 | `pronta-com-pendencia`, 0/5 | **Real.** Idem: revisão PASS_WITH_NOTES e evidência existem, checklist sem marcação | Idem |
| T-045 | `pronta-com-pendencia`, 0/5 | **Real.** O PR #69 foi mesclado sem o commit `e352044`, que marcava o checklist após a sua aprovação; ele existe só no branch local `task/T-045-guias` | Marcação pronta para publicar (ver abaixo) |
| T-047 | `pronta-com-pendencia`, 0/5 | **Real.** O PR #70 foi mesclado sem o commit `ee75685` (checklist marcado após a sua aprovação); ele existe só no branch local `task/T-047-builder-paketo` | Idem |

**Sobre T-045 e T-047:** as duas tarefas foram aprovadas por você no chat, e a revisão de cada uma é PASS_WITH_NOTES. O que falta é só o commit de marcação chegar ao `main`. Como esses arquivos estão fora dos `allowed_paths` da T-044, a marcação foi deixada para um PR pequeno à parte, se você quiser.

## 4. Pendentes do GitHub

| Item | Estado | Falta |
|---|---|---|
| `loadtest.yml` | **Provado** no run informado pelo autor: [43230211929](https://github.com/VictorLeaSilva/cashflow-platform/actions/runs/43230211929) | O link foi informado por você; a auditoria não consegue abrir o GitHub para conferir |
| `demo.yml` | **Provado** no run informado pelo autor: [43296061612](https://github.com/VictorLeaSilva/cashflow-platform/actions/runs/43296061612) | Idem |
| `security.yml` (CodeQL, Dependency-Check, Trivy) | **Provado** no run informado pelo autor (PR #70): [43284923724](https://github.com/VictorLeaSilva/cashflow-platform/actions/runs/43284923724) | O link foi informado por você; a auditoria não consegue abrir o GitHub. O README e o `architecture.md` foram atualizados depois da auditoria, num PR à parte |
| `release.yml` na tag `v0.1.0` | Preparado e provado localmente (versão, imagens `0.1.0`, SBOM); o run só existe depois da tag | O autor cria a tag e informa o link ([passo a passo](README.md)) |
| Visibilidade pública dos dois pacotes no GHCR | Não verificável aqui | O autor confere em *Package settings* depois do primeiro release |

## 5. Origem dos números do README e das notas do release

| Número | Onde aparece | Origem |
|---|---|---|
| 15.001 requisições, 0 falhas, p95 de 6,3 ms (50 req/s por 5 min) | README, notas | [`relatorio.md`](../performance/relatorio.md), tabela de requisitos (rodada de 2026-10-25) |
| 0 falhas a 100 e a 200 req/s | README | `relatorio.md`, linha do RNF-010 |
| Atraso entre o lançamento e o consolidado, p95 de 0,69 s | README, notas | `relatorio.md`, linha do RNF-003 |
| Cerca de 790 USD (MVP) e 1.966 USD (produção resiliente) por mês | README, notas | [`estimativa.md`](../cost/estimativa.md), linha "Total mensal" (790,26 e 1.966,35); export do Pricing Calculator de 2026-10-26 |
| 11 ADRs, 7 alertas | README, notas | `ls docs/architecture/decisions` (11 arquivos) e `deploy/prometheus/alertas.yml` (7 regras) |
| 5 etapas | README | contagem das etapas "Veja funcionando em 5 etapas" |

Todos os números de desempenho são do **case local**; os de custo são **estimativa**, não medição.
