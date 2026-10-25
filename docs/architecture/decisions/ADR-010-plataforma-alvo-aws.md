# ADR-010 — Plataforma-alvo AWS (desenho)

**Status:** Proposto — alvo, não implementado · **Data:** 2026-10-22

Decisões cobertas: — (Q-005; complementa D-15 no alvo)

> **Escopo:** este ADR descreve **apenas o desenho** da arquitetura-alvo pedido no enunciado. Nada aqui é provisionado: não há conta AWS, IaC nem deploy. O case é implementado e provado **apenas localmente**, tarefa a tarefa (`docker compose`, testes, k6). Números são **metas** (RNF-011), não medições.

## Contexto
O enunciado pede o "desenho da solução completo (arquitetura-alvo)", com escalabilidade, resiliência e segurança. A solução tem dois serviços *stateless*, um banco relacional, um broker e um IdP. As metas de disponibilidade e recuperação estão no RNF-011: Lançamentos 99,9 %/mês, Consolidado (consulta) 99,5 %/mês, RPO 5 min, RTO 30 min.

## Decisão
| Camada | Escolha no alvo | Equivalente no case local |
|---|---|---|
| Borda | Route 53 + **ALB** com TLS (ACM); **AWS WAF associado ao ALB** (regras gerenciadas e *rate limit* por cliente). CloudFront não é necessário para o case | Nenhum: serviços expostos direto em `localhost` |
| Computação | **ECS Fargate**, um *service* por executável, mínimo 2 *tasks* em AZs diferentes, *auto scaling* por CPU e por requisições por *task* no ALB | Processos/contêineres locais |
| Banco | **RDS PostgreSQL Multi-AZ**, 1 instância com 2 bancos e roles separados (D-15), backups automáticos com **PITR**, criptografia KMS | PostgreSQL 17 no compose, mesmo esquema de bancos e roles |
| Broker | **Amazon MSK provisionado** em 3 AZs, *replication factor* 3, `min.insync.replicas` 2, criptografia em trânsito e em repouso | Kafka KRaft com 1 broker |
| Identidade | **Amazon Cognito User Pool** com resource server e *custom scopes*; *app client* público para pessoas (*authorization code* + PKCE) e um *app client* confidencial por integrador (*client credentials*); `merchant_id` no token (fluxos, validação e pontos a validar no Cognito no ADR-007) | Chave de dev + `scripts/DevToken.java` (`DevTokenIT`; T-013) |
| Segredos e chaves | **Secrets Manager** (credenciais de banco) e **KMS** | `.env` local (fora do Git) |
| Observabilidade | **CloudWatch** (métricas, logs, alarmes) via OpenTelemetry/Micrometer (ADR-008) | Prometheus + Grafana, profile `observability` (T-032) |
| Rede | VPC em **3 AZs** com sub-redes públicas (ALB) e privadas (ECS, RDS, MSK): o MSK usa as 3; ECS e RDS Multi-AZ, ao menos 2; *security groups* por serviço | Rede do compose |

**Detalhamento além da Q-005.** A Q-005 decidiu computação, borda (ALB + WAF), banco, broker, IdP, segredos e observabilidade. Route 53, ACM, VPC com sub-redes e *security groups*, e o critério de *auto scaling* (CPU e requisições por *task* no ALB) são **detalhamento convencional do desenho**, sem alternativa relevante a decidir; não mudam nenhuma decisão da spec.

**Metas do RNF-011 (objetivos a validar, não garantias dadas pela escolha dos serviços):**
| Meta | Mecanismo de desenho que a sustenta |
|---|---|
| Lançamentos 99,9 %/mês | ≥ 2 *tasks* em AZs diferentes atrás do ALB; RDS Multi-AZ com *failover* automático; independência do Consolidado e do broker (outbox, ADR-003) |
| Consolidado 99,5 %/mês | ≥ 2 *tasks* em 2 AZs; leitura direta no RDS; atraso tolerado pela consistência eventual (ADR-005) |
| RPO 5 min / RTO 30 min | Dependem do cenário de falha — tabela abaixo |

**Cenários de falha cobertos pelo desenho:**
| Cenário | Recuperação | Perda de dados esperada | Observação |
|---|---|---|---|
| Falha de processo ou *task* | ECS recria a *task*; o ALB tira a instância do *pool* | Nenhuma: o que foi confirmado está no banco; eventos pendentes ficam na outbox ou no tópico | Coberto também pelo case local (T-031) |
| Falha de um broker MSK | Partições continuam com as réplicas restantes (RF 3, `min.insync` 2) | Nenhuma para mensagens confirmadas com `acks=all` | Com 2 brokers fora, a produção para e a outbox acumula (ADR-003) |
| Falha de uma AZ | RDS promove a standby síncrona na outra AZ; ECS e MSK seguem nas AZs restantes | Nenhuma esperada no banco (replicação síncrona) | O RTO é o tempo de *failover* do RDS mais a reconexão dos pools; a validar |
| Corrupção lógica ou erro operacional no banco | Restauração por PITR para uma **nova instância** RDS e troca de conexão; como os dois bancos dividem a instância (D-15), a restauração afeta os dois serviços | Até o último log de transação arquivado; a meta de 5 min se apoia na cadência de arquivamento do RDS, a validar | O *failover* Multi-AZ não protege disto: a standby replica o erro. A projeção pode ser reconstruída a partir de `lancamento` (ADR-006) |
| Perda da região | **Não coberto** | — | Exigiria cópia de backups e réplica entre regiões (e MSK em outra região). O enunciado não pede DR regional; fica como limitação e evolução |

O procedimento de restauração por PITR fica a documentar e testar quando houver ambiente.

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
- Surgir exigência de recuperação após perda de região → backups e réplicas entre regiões.

## Referências
RNF-001, RNF-002, RNF-005, RNF-010, RNF-011; Q-005; `03 §1` (D-15, D-16), `03 §6`; ADR-002, ADR-003, ADR-004, ADR-007, ADR-008; T-042.
