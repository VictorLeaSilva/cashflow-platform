# Itens para montar a estimativa no AWS Pricing Calculator

> **Etapa 1 da T-042.** Este documento é a lista de entrada para o [AWS Pricing Calculator](https://calculator.aws/). **Não traz nenhum preço**: os valores vêm só do export que o autor gera (Q-089, P8). A arquitetura-alvo é só desenho (`architecture.md` §6, ADR-010) e nada é provisionado.

## 1. Como usar
1. Abra o Calculator, escolha a região **`sa-east-1` (São Paulo)** e deixe a moeda em USD, sem impostos.
2. Monte **cinco estimativas**, uma por linha da tabela abaixo, com os itens das seções 3 e 4. Só o cenário **produção resiliente** tem faixa baixa e alta, porque a sensibilidade mexe nos itens que dependem de volume; o MVP é estimado só na faixa base.
3. Para cada uma, **salve e compartilhe** (link) e **exporte** (PDF ou CSV). Guarde em `docs/cost/pricing-calculator/` com o nome indicado e a data de geração.
4. **Antes de commitar**, abra os arquivos e remova o que identifique a conta, a sessão ou o e-mail (AC-T042.8). O link compartilhado do Calculator não leva credenciais, mas confira.

| # | Estimativa | Nome do arquivo |
|---|---|---|
| 1 | MVP, faixa base | `mvp-base` |
| 2 | Produção resiliente, faixa base, **MSK provisionado** | `resiliente-base-msk-provisionado` |
| 3 | Produção resiliente, faixa base, **MSK Serverless** | `resiliente-base-msk-serverless` |
| 4 | Produção resiliente, faixa baixa (MSK provisionado) | `resiliente-baixa-msk-provisionado` |
| 5 | Produção resiliente, faixa alta (MSK provisionado) | `resiliente-alta-msk-provisionado` |

**Rótulo de origem em tudo que segue:** **[spec]** = número de `02-requirements`; **[premissa]** = premissa do autor, criada para esta estimativa, **sem medição**; **[derivado]** = conta feita a partir dos dois anteriores. O `relatorio.md` mede o código no notebook local e **não dimensiona** instâncias da AWS. Os tamanhos de instância abaixo são pontos de partida a **confirmar no Calculator** (o que existir e for o menor viável vale mais que esta lista).

## 2. Cenários
| | **MVP** | **Produção resiliente** |
|---|---|---|
| O que é | Menor topologia que roda os dois serviços | Desenho do ADR-010 |
| O que **não** garante | As metas do RNF-011 (99,9 % e 99,5 % por mês, RPO 5 min e RTO 30 min): há um único ponto de falha por camada | Nenhuma meta é garantida pela estimativa; são objetivos a validar |
| Redundância | 1 *task* por serviço, RDS de instância única, broker mínimo | ≥ 2 *tasks* por serviço em 2+ AZs, RDS Multi-AZ, MSK em 3 AZs |

## 3. Premissas de volume (faixas)
| Premissa | Origem | Baixa | **Base** | Alta |
|---|---|---|---|---|
| Pico de leitura no Consolidado | [spec] RNF-002 | 50 req/s | 50 req/s | 50 req/s |
| Escrita simultânea no pico | [spec] RNF-002 | 10 req/s | 10 req/s | 10 req/s |
| Horas de pico por dia (H) | [premissa] | 1 | 2 | 4 |
| Tráfego fora do pico (leitura + escrita) | [premissa] | 5 req/s | 5 req/s | 5 req/s |
| Requisições por mês no ALB e no WAF, `(50 × 3600 × H + 5 × (24 − H) × 3600) × 30` | [derivado] | 17 820 000 | **22 680 000** | 32 400 000 |
| Lançamentos (eventos) por mês | [premissa] | 1 milhão | **10 milhões** | 100 milhões |
| Tamanho médio da resposta HTTP | [premissa] | 2 KB | 2 KB | 2 KB |
| Saída de dados para a internet por mês (requisições × 2 KB) | [derivado] | ≈ 36 GB | **≈ 45 GB** | ≈ 65 GB |
| Tamanho do banco (um PostgreSQL, dois bancos) | [premissa] | 20 GB | **100 GB** | 500 GB |
| Usuários ativos por mês no Cognito (pessoas) | [premissa] | 1 000 | **10 000** | 100 000 |
| Integradores com credencial *client credentials* | [premissa] | 5 | 5 | 5 |
| Logs enviados ao CloudWatch por mês | [premissa] | 5 GB | **20 GB** | 100 GB |

Cada evento é publicado uma vez e consumido uma vez; o tamanho do evento é da ordem de centenas de bytes (`03 §4`), mas **não há medição** do tamanho em bytes, então o tráfego do broker é **[premissa]** de 1 KB por evento para as contas do MSK Serverless.

## 4. Itens por componente
Região `sa-east-1`. Quantidades de horas: 730 por mês quando o recurso fica sempre ligado.

### 4.1 Produção resiliente (ADR-010)
| Componente | Configuração | Quantidade e uso | Origem |
|---|---|---|---|
| Route 53 | 1 zona hospedada | Consultas: [premissa] igual às requisições do ALB (faixa) | [premissa] |
| ACM | Certificado público para o ALB | 1 (sem custo; registrar no export) | ADR-010 |
| Application Load Balancer | Multi-AZ | 1 ALB, 730 h; requisições da faixa (§3) para o cálculo de LCU | ADR-010 |
| AWS WAF | 1 Web ACL no ALB; regras gerenciadas e *rate limit* | 1 Web ACL, 3 regras gerenciadas, 1 regra de *rate limit* [premissa]; requisições da faixa | ADR-010 |
| ECS Fargate | Linux/x86, 1 vCPU e 2 GB por *task* [premissa] | Lançamentos: 2 *tasks*; Consolidado: 2 *tasks*; 730 h cada. Faixa alta: 4 + 4 | ADR-010 (≥ 2 por serviço); tamanho [premissa] |
| ECR | 2 repositórios | ≈ 1 GB armazenado [premissa] | [premissa] |
| RDS PostgreSQL | **Multi-AZ**, `db.m6g.large` [premissa], gp3 | Armazenamento da faixa (§3); retenção de backup de 7 dias [premissa] para o PITR | ADR-010 |
| Amazon MSK **provisionado** | 3 brokers em 3 AZs, `kafka.m5.large` [premissa], RF 3 | Armazenamento por broker: 100 GB [premissa] | ADR-010 |
| Amazon MSK **Serverless** (só na estimativa #3) | Cluster serverless | Horas de cluster, partições (3 do tópico + 3 da DLT) e dados de entrada e saída (1 KB por evento × eventos da faixa) | ADR-010 (alternativa de custo) |
| Cognito User Pool | Pessoas + integradores | Usuários ativos e *app clients* da faixa | ADR-010 |
| Secrets Manager | 4 segredos (roles `owner` e `app` dos dois serviços) | 4 segredos [premissa], 1 chamada por *task* na partida | ADR-010 |
| KMS | Chaves gerenciadas pelo cliente | 3 chaves (RDS, MSK, segredos e logs) [premissa] | ADR-010 |
| CloudWatch | Logs, métricas personalizadas, alarmes, 1 painel | Logs da faixa; 50 métricas personalizadas [premissa]; 7 alarmes (as 7 regras de `deploy/prometheus/alertas.yml`); 1 painel | ADR-008, ADR-010 |

### 4.2 MVP
| Componente | Configuração | Quantidade e uso |
|---|---|---|
| ALB, Route 53, ACM, Cognito, Secrets Manager, KMS, ECR | Como na produção resiliente | Mesmas quantidades da faixa base |
| AWS WAF | Igual à produção resiliente, ou omitir | **Decisão do autor**: rodar a estimativa com o WAF e anotar a diferença se for omitido |
| ECS Fargate | 1 vCPU e 2 GB por *task* [premissa] | 1 *task* por serviço, 730 h |
| RDS PostgreSQL | **Instância única** (Single-AZ), `db.t4g.small` [premissa], gp3 | 100 GB (faixa base); backup padrão |
| Amazon MSK provisionado | Menor configuração viável que o Calculator aceitar (por exemplo `kafka.t3.small`) [premissa] | Número mínimo de brokers e armazenamento de 100 GB por broker [premissa]; **anote o mínimo que o Calculator exige** |
| CloudWatch | Como na produção resiliente | 20 GB de logs; 50 métricas; 7 alarmes; 1 painel |

### 4.3 Rede e custos que o ADR-010 não lista (lacunas do desenho)
O ADR-010 coloca ECS, RDS e MSK em sub-redes privadas, mas **não diz como as *tasks* alcançam a internet ou os serviços da AWS** (imagens do ECR, Secrets Manager, CloudWatch, chaves públicas do IdP). Isso custa dinheiro e precisa de uma escolha de desenho. A estimativa inclui, como **premissa declarada**:
- **NAT Gateway:** 1 no MVP e 1 por AZ (2) na produção resiliente, mais 5 GB de dados processados por mês [premissa];
- **Alternativa a registrar** (sem estimar, a menos que o autor peça): *endpoints* de VPC para ECR, CloudWatch Logs e Secrets Manager.

Também ficam **declarados fora da estimativa**: impostos, plano de suporte da AWS, créditos e descontos (reserva, *savings plans*), transferência entre AZs além do que o Calculator calcular, e o custo do ambiente de desenvolvimento.

## 5. O que acontece depois que o export estiver no repositório
Com os cinco exports em `docs/cost/pricing-calculator/`, a etapa 2 escreve `docs/cost/estimativa.md` com os valores, a comparação MSK provisionado × Serverless e as alavancas de custo, e a revisão independente confere as somas contra os exports (AC-T042.4). Tudo que não estiver no export fica como lacuna declarada, não como número.
