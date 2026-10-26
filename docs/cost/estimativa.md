# Estimativa de custos da arquitetura-alvo

> **Não é medição, nem orçamento.** É a estimativa mensal do **desenho** da arquitetura-alvo AWS (ADR-010, `architecture.md` §6), gerada no AWS Pricing Calculator em **2026-10-25**, região `sa-east-1` (São Paulo), em USD e sem impostos. Todos os valores vêm dos arquivos de [`pricing-calculator/`](pricing-calculator/); nada foi provisionado e nenhum valor foi tirado de outra fonte (P8, Q-089). O [relatório de desempenho](../performance/relatorio.md) mede o código no notebook local e **não dimensiona** instâncias da AWS. Os preços da AWS mudam: vale a data do export.

## 1. Cenários e premissas
Os itens e as premissas foram definidos antes do export em [`itens-para-o-calculator.md`](itens-para-o-calculator.md). Resumo:

| | **MVP** | **Produção resiliente** |
|---|---|---|
| O que é | Menor topologia que roda os dois serviços | Desenho do ADR-010 |
| Redundância | 1 *task* por serviço, RDS de instância única (`db.t4g.small`), 3 brokers MSK `kafka.t3.small` | 2 *tasks* por serviço (4 na faixa base), RDS Multi-AZ (`db.m6g.large`), 3 brokers MSK `kafka.m5.large` |
| O que **não** garante | As metas do RNF-011 (há um único ponto de falha por camada) | Nenhuma meta é garantida pela estimativa: são objetivos a validar |

**Origem dos números de entrada.** Da spec (RNF-002): 50 req/s de leitura e 10 req/s de escrita no pico. **Premissas do autor, sem medição:** horas de pico por dia, tráfego fora do pico, volume de eventos, tamanho do banco e dos logs, usuários ativos e tamanho das *tasks* (1 vCPU e 2 GB). As faixas baixa, base e alta variam só os itens que dependem de volume:

| Premissa | Baixa | **Base** | Alta |
|---|---|---|---|
| Requisições por mês (ALB, WAF e Route 53) | 17,82 milhões | **22,68 milhões** | 32,4 milhões |
| Saída de dados para a internet | 36 GB | **45 GB** | 65 GB |
| Banco | 20 GB | **100 GB** | 500 GB (com 12.000 IOPS e 500 MiB/s) |
| Logs no CloudWatch | 5 GB | **20 GB** | 100 GB |
| Usuários ativos por mês (Cognito) | 1.000 | **10.000** | 100.000 |
| *Tasks* do Fargate (total) | 4 | **4** | 8 |

O MVP foi estimado só na faixa base. A faixa baixa e a alta existem só para o cenário resiliente com MSK provisionado.

## 2. Custo mensal por cenário (USD)
Linhas do export; as somas conferem com o total de cada arquivo (conferência na evidência).

| Serviço | MVP (base) | Resiliente, baixa | **Resiliente, base** (MSK prov.) | Resiliente, base (MSK Serverless) | Resiliente, alta |
|---|---:|---:|---:|---:|---:|
| Route 53 | 9,70 | 7,70 | 9,70 | 9,70 | 13,30 |
| Application Load Balancer | 25,31 | 24,95 | 25,31 | 25,31 | 26,80 |
| AWS WAF | 22,80 | 19,80 | 22,80 | 22,80 | 28,20 |
| ECS Fargate | 123,81 | 247,61 | 247,61 | 247,61 | 495,23 |
| ECR | 0,10 | 0,10 | 0,10 | 0,10 | 0,10 |
| RDS PostgreSQL | 134,32 | 374,49 | 409,53 | 409,53 | 584,73 |
| Amazon MSK | 218,18 | 790,65 | 790,65 | 885,70 | 790,65 |
| Cognito | 0,01 | 0,01 | 0,01 | 0,01 | 495,01 |
| Secrets Manager | 1,60 | 1,60 | 1,60 | 1,60 | 1,60 |
| KMS | 9,00 | 9,00 | 9,00 | 9,00 | 9,00 |
| CloudWatch | 34,07 | 20,48 | 34,07 | 34,07 | 106,56 |
| NAT Gateway | 204,61 | 408,10 | 409,22 | 409,22 | 411,44 |
| Transferência de dados (saída) | 6,75 | 5,40 | 6,75 | 6,75 | 9,75 |
| **Total mensal** | **790,26** | **1.909,89** | **1.966,35** | **2.061,40** | **2.972,37** |

- A produção resiliente base custa **2,49 vezes** o MVP (1.966,35 contra 790,26). É o preço de ter o desenho do ADR-010 (Multi-AZ, broker em 3 AZs, o dobro de *tasks*) em vez de um ponto único por camada.
- Entre a faixa baixa e a alta, o total varia 1,56 vez (1.909,89 contra 2.972,37): **a maior parte do custo é de infraestrutura fixa**, não de volume. No resiliente, a faixa alta só se destaca em *tasks* (8), banco, logs e Cognito.

## 3. MSK provisionado × MSK Serverless
O ADR-010 mantinha o MSK Serverless como "alternativa de custo a avaliar na T-042". Na faixa base do cenário resiliente:

| | MSK provisionado | MSK Serverless |
|---|---:|---:|
| Configuração do export | 3 brokers `kafka.m5.large`, 100 GB por broker | retenção de 7 dias, 4 KB/s de entrada e de saída, 6 partições (3 do tópico e 3 da DLT, só líderes) |
| Custo mensal | 790,65 | 885,70 |
| Total do cenário | 1.966,35 | 2.061,40 |

**Resultado: o Serverless saiu 95,05 USD por mês mais caro (12,0 %) nesta estimativa.** Recomendação para o alvo: **manter o MSK provisionado** como no ADR-010. Isso vale **só** para este dimensionamento e esta data:
- o provisionado foi estimado nas três faixas (790,65 em todas, porque o armazenamento ficou fixo em 100 GB) e o Serverless só na base; não há como afirmar o que acontece com menos volume;
- o export do Serverless é uma linha única e **não mostra a composição** do custo, então não afirmo qual parcela domina;
- o throughput de 4 KB/s vem da premissa de 10 milhões de eventos por mês com 1 KB cada, que **não foi medida**;
- operação, limites de configuração e comportamento sob carga não aparecem no export e não entram na recomendação. O Serverless dispensa dimensionar brokers, o que o ADR-010 já apontava como vantagem; o custo não a compensou aqui.

## 4. O que mais pesa e alavancas
| Cenário | Três maiores linhas (participação no total) |
|---|---|
| MVP | MSK 27,6 % · NAT Gateway 25,9 % · RDS 17,0 % |
| Resiliente, baixa | MSK 41,4 % · NAT Gateway 21,4 % · RDS 19,6 % |
| Resiliente, base | MSK 40,2 % · RDS 20,8 % · NAT Gateway 20,8 % |
| Resiliente, alta | MSK 26,6 % · RDS 19,7 % · Fargate 16,7 % (Cognito 16,7 %, NAT 13,8 %) |

Alavancas **a avaliar** (nenhuma foi estimada, então não há economia prometida):
- **MSK:** é a maior linha em todos os cenários. Tamanho e quantidade de brokers e armazenamento por broker são o que se pode mexer; o provisionado não variou entre as faixas porque o armazenamento ficou fixo.
- **NAT Gateway:** 20 a 26 % do total em quase todos os cenários. A configuração estimada foi o **NAT regional com 2 zonas ativas** (1 NAT no MVP e 2 no resiliente); a lista de itens previa o NAT zonal. O autor manteve o regional, e a comparação com o zonal **não foi estimada**. Veja a lacuna L1.
- **RDS:** instância e armazenamento; desconto por reserva não foi estimado.
- **Cognito:** na faixa alta custa 495,01 USD (16,7 %); os recursos avançados de segurança ficaram **desabilitados** em todos os exports, e habilitá-los muda o custo (não estimado).
- **Fargate:** o tamanho das *tasks* (1 vCPU e 2 GB) é premissa, não medição.

## 5. Lacunas do desenho encontradas (para o autor decidir)
O ADR-010 **não foi alterado** além da nota da comparação do MSK (AC-T042.7).
- **L1. Saída das *tasks* para a internet e para os serviços da AWS.** O ADR-010 põe ECS, RDS e MSK em sub-redes privadas, mas não diz como as *tasks* baixam imagens do ECR, leem segredos, enviam logs e consultam as chaves do IdP. A estimativa assumiu um **NAT Gateway** (premissa declarada), que pesa 20 a 26 % do total. A alternativa de *endpoints* de VPC (ECR, CloudWatch Logs, Secrets Manager) **não foi estimada**, e a consulta às chaves do IdP pode exigir saída para a internet (não verificado).
- **L2. Transferência entre AZs** (replicação do MSK e tráfego entre *tasks* e brokers) não foi informada no Calculator; só a saída para a internet entrou.
- **L3. Armazenamento de backup** do RDS acima do tamanho do banco (retenção do PITR) não foi informado.

## 6. Limites desta estimativa
- **Estimativa, não medição.** As premissas de volume, tamanho e retenção são do autor. A estimativa **não** prova que o desenho atende o RNF-011.
- **Fora da estimativa:** impostos, plano de suporte da AWS, créditos e descontos (reserva, *savings plans*), ambiente de desenvolvimento e o custo de migração (T-041 é só desenho).
- **Camada gratuita:** o Cognito custou 0,01 USD com 10.000 usuários ativos por mês, o que pode refletir a camada gratuita aplicada pelo Calculator; confira se vale para a conta que for usar.
- **O JSON não traz tudo o que foi digitado:** não mostra o vCPU do Fargate nem as requisições informadas ao ALB e ao WAF. O custo do WAF e o do Route 53 foram conferidos só pela ordem de grandeza: crescem da faixa baixa para a alta como as requisições da tabela, mas o export não traz o preço unitário nem a quantidade digitada, então não afirmo o cálculo.
- **Texto dos exports:** o Calculator gravou os JSON com caracteres de substituição no lugar dos acentos ("N�mero"); os nomes dos serviços e os números estão intactos.
- **Links:** cada export traz o link compartilhado da estimativa (tabela abaixo). Eles não contêm credenciais nem identificador de conta; foram conferidos por `grep` (sem e-mail, número de 12 dígitos ou ARN).
- **Pendência de confirmação do autor:** os links precisam continuar abertos no Calculator para valer como origem; se a AWS os expirar, os JSON versionados continuam sendo a origem dos valores.

## 7. Exports
| Estimativa | Arquivo | Link da estimativa |
|---|---|---|
| MVP, base | `pricing-calculator/mvp-base-2026-10-25.json` | `calculator.aws/#/estimate?id=c1e502a13634169c4feb4178d367a825c0d48ec4` |
| Resiliente, baixa | `pricing-calculator/resiliente-baixa-msk-provisionado-2026-10-25.json` | `calculator.aws/#/estimate?id=eecfd97bd612e8a15c8389dda5bdd598814c56a5` |
| Resiliente, base, MSK provisionado | `pricing-calculator/resiliente-base-msk-provisionado-2026-10-25.json` | `calculator.aws/#/estimate?id=6bfbccf3103ffc08e2041f5f12fff0608feebc5f` |
| Resiliente, base, MSK Serverless | `pricing-calculator/resiliente-base-msk-serverless-2026-10-25.json` | `calculator.aws/#/estimate?id=3a0e109c2d6c21ba3da0b0e392a57fce91bd5a98` |
| Resiliente, alta | `pricing-calculator/resiliente-alta-msk-provisionado-2026-10-25.json` | `calculator.aws/#/estimate?id=1b854cd1c2f3e1637d07d48a3c67b4282bd4a7bc` |
