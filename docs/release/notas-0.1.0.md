# Fluxo de Caixa 0.1.0

Primeira versão do case: **lançamentos** (créditos e débitos) e **consolidado diário** em dois serviços Spring Boot (Java 25) que conversam só por eventos no Kafka. Se o Consolidado cair, o Lançamentos continua gravando. Tudo foi provado **localmente**; a arquitetura na AWS é **só desenho**.

## O que esta versão entrega
- **Lançamentos:** `POST /lancamentos` com JWT, chave de idempotência e estorno; consulta do detalhe e do histórico, isolada por comerciante.
- **Eventos:** gravação do lançamento e do evento na mesma transação (*outbox*), publicador com tentativas e espera crescente, contrato do evento em JSON Schema.
- **Consolidado:** consumidor idempotente (repetidos ignorados, tentativas e fila de mensagens mortas), saldo do dia e acumulado.
- **Observabilidade:** métricas, 7 alertas e painel no Grafana.
- **Segurança e qualidade:** cabeçalhos e limites de requisição, CodeQL, Dependency-Check e Trivy no pipeline, formatação, Checkstyle, SpotBugs, ArchUnit e cobertura no `./mvnw verify`.
- **Documentação:** requisitos com matriz de rastreabilidade, 11 ADRs, arquitetura-alvo, transição, estimativa de custos e guias para quem não é técnico.
- **Artefatos deste release:** as imagens `lancamentos` e `consolidado` no GHCR (`linux/amd64`, usuário não-root), o SBOM CycloneDX anexado a esta página e, abaixo, o *digest* de cada imagem.

## O que é só desenho
Alvo na AWS (ECS, RDS Multi-AZ, MSK, Cognito, WAF): nada foi provisionado, e as metas de disponibilidade, RPO e RTO são **metas**, não medições. A arquitetura de transição também é só desenho (condicional).

## Limitações conhecidas
- Provedor de identidade real, reconstrução do consolidado, CDC e infraestrutura como código ficaram de fora ([o que eu faria com mais tempo](../../README.md#o-que-eu-faria-com-mais-tempo)).
- ZAP, mutação com PIT e o provedor de identidade local foram cortados por escopo.
- O compose do repositório constrói as imagens localmente; ele ainda não usa as do GHCR.
- O SBOM cobre as dependências Maven dos dois serviços, não as camadas do *buildpack* da imagem.

## Números (case local, não valem para o alvo)
Medidos em 2026-10-25, no mesmo notebook do gerador de carga e dos serviços, uma rodada por taxa ([relatório](../performance/relatorio.md)):
- leitura do consolidado a 50 req/s por 5 min: 15.001 requisições, 0 falhas, p95 de 6,3 ms;
- atraso entre o lançamento e o consolidado: p95 de 0,69 s.

Custo mensal **estimado** do alvo, pelo AWS Pricing Calculator em 2026-10-26 ([estimativa](../cost/estimativa.md)): cerca de 790 USD (MVP) e 1.966 USD (produção resiliente). É estimativa, não medição.
