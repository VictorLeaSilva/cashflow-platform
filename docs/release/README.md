# Release: como é feito e como conferir

O release publica, a partir de uma **tag** `vX.Y.Z`, as duas imagens no GHCR, o SBOM e uma página de release no GitHub. Quem faz é o workflow [`release.yml`](../../.github/workflows/release.yml); a decisão está nas Q-096 a Q-098 de [`open-questions.md`](../specs/open-questions.md).

## O que o release publica

| Artefato | Onde | Observação |
|---|---|---|
| Imagem do Lançamentos | `ghcr.io/victorleasilva/cashflow-platform/lancamentos:0.1.0` | `linux/amd64`, usuário não-root, **sem** tag `latest` |
| Imagem do Consolidado | `ghcr.io/victorleasilva/cashflow-platform/consolidado:0.1.0` | idem |
| SBOM CycloneDX | anexo da página do release (`fluxo-caixa-0.1.0.cdx.json`) | dependências Maven dos dois serviços |
| Notas e *digests* | página do release | notas de [`notas-0.1.0.md`](notas-0.1.0.md) mais o *digest* de cada imagem |

## Como o workflow funciona

1. **`verificar`:** roda `./mvnw verify`, como o `ci.yml`.
2. **`publicar`:** confere que a tag coincide com a versão do `pom.xml` sem `-SNAPSHOT` ([`preparar-versao.sh`](../../scripts/release/preparar-versao.sh), que troca a versão **só no runner**, sem commit); gera as imagens; passa o **Trivy antes de publicar** (achado HIGH ou CRITICAL com correção: nada é publicado); gera e confere o SBOM ([`conferir-sbom.sh`](../../scripts/release/conferir-sbom.sh)); envia as imagens ao GHCR; cria a página do release.

O repositório continua em `0.1.0-SNAPSHOT`: o compose, o `security.yml`, a demo e a carga seguem com as imagens locais.

## Como fazer o release (você)

Pré-requisitos: o `main` com a T-044 mesclada, os checks verdes e o arquivo `docs/release/notas-0.1.0.md` presente.

**1. Criar e enviar a tag**

**bash e PowerShell**

```bash
git switch main
git pull origin main
git tag v0.1.0
git push origin v0.1.0
```

**2. Acompanhar o run:** no GitHub, aba **Actions**, workflow **Release**. Os dois jobs devem ficar verdes (o segundo leva mais: baixa o *builder*, constrói as imagens e roda o Trivy).

**3. Deixar os pacotes públicos:** em *Packages* do seu perfil, abra cada pacote (`cashflow-platform/lancamentos` e `cashflow-platform/consolidado`), *Package settings*, *Change visibility*, *Public*. Sem isso, quem não tem login não consegue baixar a imagem. Na `v0.1.0` os dois pacotes já nasceram **públicos** (conferido em 2026-11-12), então este passo só confirma o estado.

**Conhecido na `v0.1.0`:** a seção "Imagens publicadas" da página do release mostra os *digests* com o prefixo `fluxo-caixa/…@sha256:…`. Os valores `sha256` estão corretos; para baixar pelo *digest*, use `ghcr.io/victorleasilva/cashflow-platform/<serviço>@sha256:<valor>`.

## Como conferir um release

**Baixar a imagem** (fixe pelo *digest* que está nas notas do release)

**bash e PowerShell**

```bash
docker pull ghcr.io/victorleasilva/cashflow-platform/lancamentos:0.1.0
```

**Conferir que o usuário não é root**

**bash e PowerShell**

```bash
docker inspect --format '{{.Config.User}}' ghcr.io/victorleasilva/cashflow-platform/lancamentos:0.1.0
```

O resultado deve ser um número diferente de `0` (na construção feita para esta tarefa, `1002:1001`).

**Contar os componentes do SBOM** (baixe o arquivo na página do release)

**bash e PowerShell**

```bash
jq '.components | length' fluxo-caixa-0.1.0.cdx.json
```

## O que o release não faz (evolução)

- Não publica `latest` nem imagem `arm64` (a regra do projeto é tag pinada).
- Não assina as imagens nem gera *attestation* de proveniência.
- Não gera SBOM da imagem (camadas do *buildpack*): o SBOM atual é o das dependências Maven.
- Não faz o compose usar as imagens do GHCR.

## Se o release falhar

- **Antes de publicar** (versão diferente do `pom.xml`, Trivy, SBOM): nada foi enviado. Corrija no `main`, apague a tag (`git tag -d v0.1.0` e `git push origin :refs/tags/v0.1.0`) e crie-a de novo no commit corrigido.
- **Depois de publicar parte das imagens:** não reaproveite a tag. Suba o `pom.xml` para a próxima versão (`0.1.1-SNAPSHOT`, em todos os `pom.xml`) e crie `v0.1.1`.
- Falha de rede ao baixar o *builder* (`unexpected EOF`): rode o job de novo pela aba **Actions**.
