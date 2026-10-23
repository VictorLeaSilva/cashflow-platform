# ADR-010 — Plataforma-alvo AWS (desenho)

**Status:** Proposto — alvo, não implementado · **Data:** 2026-10-22

Decisões cobertas: — (Q-005; complementa D-15 no alvo)

> **Escopo:** este ADR descreve **apenas o desenho** da arquitetura-alvo pedido no enunciado. Nada aqui é provisionado: não há conta AWS, IaC nem deploy. O case é implementado e provado **apenas localmente**, tarefa a tarefa (`docker compose`, testes, k6). Números são **metas** (RNF-011), não medições.

## Contexto
O enunciado pede o "desenho da solução completo (arquitetura-alvo)", com escalabilidade, resiliência e segurança. A solução tem dois serviços *stateless*, um banco relacional, um broker e um IdP. As metas de disponibilidade e recuperação estão no RNF-011: Lançamentos 99,9 %/mês, Consolidado (consulta) 99,5 %/mês, RPO 5 min, RTO 30 min.

## Decisão
| Camada | Escolha no alvo | Equivalente no case local |
|---|---|---|
| Borda | Route 53 + **ALB** com TLS (ACM) + **AWS WAF** (regras gerenciadas e *rate limit* por cliente) | Nenhum: serviços expostos direto em `localhost` |
| Computação | **ECS Fargate**, um *service* por executável, mínimo 2 *tasks* em AZs diferentes, *auto scaling* por CPU e por requisições por *task* no ALB | Processos/contêineres locais |
| Banco | **RDS PostgreSQL Multi-AZ**, 1 instância com 2 bancos e roles separados (D-15), backups automáticos com **PITR**, criptografia KMS | PostgreSQL 17 no compose, mesmo esquema de bancos e roles |
| Broker | **Amazon MSK provisionado** em 3 AZs, *replication factor* 3, `min.insync.replicas` 2, criptografia em trânsito e em repouso | Kafka KRaft com 1 broker |
| Identidade | **Amazon Cognito** (*user pool* + *client credentials* para integradores) | Chave de dev + `scripts/DevToken.java` (a provar: T-013) |
| Segredos e chaves | **Secrets Manager** (credenciais de banco) e **KMS** | `.env` local (fora do Git) |
| Observabilidade | **CloudWatch** (métricas, logs, alarmes) via OpenTelemetry/Micrometer (ADR-008) | Prometheus + Grafana (a provar: T-032) |
| Rede | VPC em **3 AZs** com sub-redes públicas (ALB) e privadas (ECS, RDS, MSK): o MSK usa as 3; ECS e RDS Multi-AZ, ao menos 2; *security groups* por serviço | Rede do compose |

**Detalhamento além da Q-005.** A Q-005 decidiu computação, borda (ALB + WAF), banco, broker, IdP, segredos e observabilidade. Route 53, ACM, VPC com sub-redes e *security groups*, e o critério de *auto scaling* (CPU e requisições por *task* no ALB) são **detalhamento convencional do desenho**, sem alternativa relevante a decidir; não mudam nenhuma decisão da spec.

**Metas do RNF-011 e o mecanismo de desenho que as sustenta (metas, não medidas):**
| Meta | Mecanismo |
|---|---|
| Lançamentos 99,9 %/mês | ≥ 2 *tasks* em AZs diferentes atrás do ALB; RDS Multi-AZ com *failover* automático; independência do Consolidado e do broker (outbox, ADR-003) |
| Consolidado 99,5 %/mês | ≥ 2 *tasks* em 2 AZs; leitura direta no RDS; atraso tolerado pela consistência eventual (ADR-005) |
| RPO 5 min | RDS com replicação síncrona Multi-AZ e PITR; MSK com RF 3 e `min.insync` 2; a projeção é reconstruível a partir de `lancamento` (ADR-006) |
| RTO 30 min | *Failover* gerenciado do RDS e do MSK; ECS recria *tasks* automaticamente; restauração por PITR (procedimento a documentar e testar quando houver ambiente) |

## Alternativas rejeitadas
| Alternativa | Por que não |
|---|---|
| EKS (Kubernetes) | Custo e operação de *cluster* não se pagam para dois serviços *stateless* (P2) |
| Lambda | O consumidor Kafka é contínuo e os pools JDBC são limitados por desenho (`03 §7.1`); *cold start* e conexões por invocação atrapalham ambos |
| API Gateway como borda principal | O ALB + WAF cobre TLS e *rate limit* para dois serviços; API Gateway entra se surgir monetização/planos de uso por cliente |
| Aurora PostgreSQL | Melhor *failover* e escala de leitura, mas custo maior sem necessidade medida; RDS Multi-AZ atende as metas |
| MSK Serverless | Mais simples de operar e possivelmente mais barato em carga baixa; mantido como **alternativa de custo** a avaliar na T-042 |
| Instâncias RDS separadas por serviço | Isolamento físico sem requisito; roles separados já isolam (D-15) |
| Keycloak autogerenciado | Mais uma aplicação com estado para operar; Cognito é gerenciado |

## Consequências
- **Ganho:** cada componente com estado tem redundância em mais de uma AZ; os serviços escalam horizontalmente sem mudança de código (são *stateless*).
- **Custo assumido:** MSK provisionado tende a ser o item mais caro em carga baixa (ADR-004, T-042); RDS Multi-AZ aumenta o custo da instância (valores na estimativa da T-042).
- **Só desenho, nunca provado no case:** multi-AZ, *failover*, RPO/RTO, WAF/*rate limit*, Cognito, CloudWatch. O case local se propõe a provar o comportamento da **aplicação** — isolamento de falha (T-031), idempotência (T-012) e carga de 50 req/s (T-033) —, não a infraestrutura.

## Revisitar quando
- A estimativa de custo (T-042) mostrar o MSK dominante → MSK Serverless ou SNS/SQS (ADR-004).
- Houver um ambiente real para medir disponibilidade e testar restauração.

## Referências
RNF-001, RNF-002, RNF-005, RNF-010, RNF-011; Q-005; `03 §1` (D-15, D-16), `03 §6`; ADR-002, ADR-003, ADR-004, ADR-007, ADR-008; T-042.
