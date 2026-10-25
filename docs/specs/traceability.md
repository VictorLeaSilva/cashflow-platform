# Matriz de rastreabilidade

Gerada por `scripts/check-traceability.sh --write`; não edite à mão. Requisito → prova → tarefas que o cobrem (`covers`); o detalhe lista `classe#método` (ou só a classe, quando a `@Tag` está nela) e o módulo.

| Requisito | Prova | Testes | Tarefas |
|---|---|---|---|
| RF-001 | @Tag | 9 | T-010, T-012, T-013 |
| RF-002 | @Tag | 7 | T-010, T-012, T-013 |
| RF-003 | @Tag | 10 | T-010, T-011, T-014 |
| RF-004 | @Tag | 6 | T-015 |
| RF-005 | @Tag | 6 | T-020, T-023 |
| RF-006 | @Tag | 5 | T-023 |
| RF-007 | @Tag + evidência | 14; docs/specs/reviews/T-035.evidence.md | T-012, T-013, T-014, T-035 |
| RF-008 | @Tag | 18 | T-020, T-021, T-022, T-030 |
| RF-009 | @Tag + evidência | 8; docs/specs/reviews/T-035.evidence.md | T-010, T-014, T-020, T-022, T-031, T-035 |
| RF-010 | @Tag + evidência | 10; docs/specs/reviews/T-035.evidence.md | T-013, T-014, T-015, T-023, T-035 |
| RNF-001 | @Tag + evidência | 5; docs/specs/reviews/T-035.evidence.md | T-004, T-016, T-031, T-035 |
| RNF-002 | evidência | 0; docs/specs/reviews/T-033.evidence.md | T-033 |
| RNF-003 | @Tag + evidência | 2; docs/specs/reviews/T-033.evidence.md | T-022, T-032, T-033 |
| RNF-004 | @Tag | 28 | T-010, T-011, T-012, T-014, T-016, T-021, T-022 |
| RNF-005 | @Tag + evidência | 23; docs/specs/reviews/T-038.evidence.md | T-013, T-015, T-023, T-034, T-036, T-037, T-038 |
| RNF-006 | @Tag | 10 | T-032 |
| RNF-007 | @Tag + evidência | 12; docs/specs/reviews/T-035.evidence.md | T-016, T-031, T-035 |
| RNF-008 | @Tag + evidência | 4; docs/specs/reviews/T-003.evidence.md | T-001, T-002, T-003 |
| RNF-009 | @Tag + evidência | 2; docs/specs/reviews/T-001.evidence.md; docs/specs/reviews/T-004.evidence.md; docs/specs/reviews/T-006.evidence.md; docs/specs/reviews/T-007.evidence.md; docs/specs/reviews/T-035.evidence.md | T-001, T-004, T-006, T-007, T-035 |
| RNF-010 | evidência | docs/specs/reviews/T-033.evidence.md | T-033 |
| RNF-011 | só desenho | — |  |
| RNF-012 | @Tag | 16 | T-013, T-014, T-015, T-016, T-022, T-023, T-032 |
| RNF-013 | @Tag | 5 | T-013, T-014, T-015, T-023, T-034 |
| RNF-014 | @Tag | 8 | T-013, T-014, T-015, T-023 |

## Detalhe dos testes

### RF-001
- `DataReferenciaTest` (lancamentos-service)
- `DinheiroPropertiesTest` (lancamentos-service)
- `DinheiroTest` (lancamentos-service)
- `LancamentoRegistradoV1Test` (lancamentos-service)
- `LancamentoTest` (lancamentos-service)
- `PoliticaLancamentoPropertiesTest` (lancamentos-service)
- `PostLancamentoIT` (lancamentos-service)
- `PostLancamentoWebTest` (lancamentos-service)
- `RegistrarLancamentoIT` (lancamentos-service)

### RF-002
- `DinheiroPropertiesTest` (lancamentos-service)
- `DinheiroTest` (lancamentos-service)
- `LancamentoRegistradoV1Test` (lancamentos-service)
- `LancamentoTest` (lancamentos-service)
- `PostLancamentoIT` (lancamentos-service)
- `PostLancamentoWebTest` (lancamentos-service)
- `RegistrarLancamentoIT` (lancamentos-service)

### RF-003
- `AuditoriaEstornoWebTest` (lancamentos-service)
- `ConstraintsLancamentoIT` (lancamentos-service)
- `EstornarLancamentoIT` (lancamentos-service)
- `EstornoConcorrenteIT` (lancamentos-service)
- `EstornoIntercaladoIT` (lancamentos-service)
- `EstornoPropertiesTest` (lancamentos-service)
- `EstornoTest` (lancamentos-service)
- `LancamentoJdbcRepositoryIT` (lancamentos-service)
- `PostEstornoIT` (lancamentos-service)
- `PostEstornoWebTest` (lancamentos-service)

### RF-004
- `ConsultarLancamentosTest` (lancamentos-service)
- `GetLancamentosIT` (lancamentos-service)
- `GetLancamentosWebTest` (lancamentos-service)
- `HistoricoJdbcIT` (lancamentos-service)
- `IsolamentoPorComercianteIT` (lancamentos-service)
- `LogDeAcessoWebTest` (lancamentos-service)

### RF-005
- `ConsultaConsolidadoJdbcRepositoryIT` (consolidado-service)
- `ConsultarConsolidadoTest` (consolidado-service)
- `GetConsolidadoIT` (consolidado-service)
- `GetConsolidadoWebTest` (consolidado-service)
- `MovimentoDiarioPropertiesTest#saldoDiaEASomaComSinalDosEventosDistintosDoDia` (consolidado-service)
- `MovimentoDiarioTest` (consolidado-service)

### RF-006
- `ConsultaConsolidadoJdbcRepositoryIT` (consolidado-service)
- `ConsultarConsolidadoPropertiesTest` (consolidado-service)
- `ConsultarConsolidadoTest` (consolidado-service)
- `GetConsolidadoIT` (consolidado-service)
- `GetConsolidadoWebTest` (consolidado-service)

### RF-007
- `ChaveOrfaEAtomicidadeIT` (lancamentos-service)
- `EstornoIdempotenteConcorrenteIT` (lancamentos-service)
- `ExecucaoIdempotenteTest` (lancamentos-service)
- `IdempotenciaConcorrenteIT` (lancamentos-service)
- `IdempotenciaIT` (lancamentos-service)
- `IdempotenciaRollbackConcorrenteIT` (lancamentos-service)
- `IdempotencyKeyFkDiferidaIT` (lancamentos-service)
- `PostEstornoIT` (lancamentos-service)
- `PostEstornoWebTest` (lancamentos-service)
- `PostLancamentoIT` (lancamentos-service)
- `PostLancamentoWebTest` (lancamentos-service)
- `RegistrarLancamentoCommandTest` (lancamentos-service)
- `ReplayForaDaJanelaIT` (lancamentos-service)
- `RequestFingerprintTest` (lancamentos-service)

### RF-008
- `AplicarEventoTest` (consolidado-service)
- `ConfiguracaoDoConsumidorIT` (consolidado-service)
- `ConsumidorDeLancamentosIT` (consolidado-service)
- `ContratoDoConsumidorIT` (consolidado-service)
- `ContratoDoEventoIT` (consolidado-service)
- `ContratoDoEventoIT` (lancamentos-service)
- `ContratoDoSchemaTest` (lancamentos-service)
- `ContratoDosExemplosTest` (consolidado-service)
- `DeduplicacaoConcorrenteIT` (consolidado-service)
- `DltIT` (consolidado-service)
- `EfeitoTest` (consolidado-service)
- `LancamentoRegistradoTest` (consolidado-service)
- `LeitorDeEventoTest` (consolidado-service)
- `MovimentoDiarioPropertiesTest` (consolidado-service)
- `MovimentoDiarioTest` (consolidado-service)
- `ProjecaoJdbcRepositoryIT` (consolidado-service)
- `ProjecaoPostgresPropertiesIT` (consolidado-service)
- `ProjecaoPropertiesTest` (consolidado-service)

### RF-009
- `ContratoDoConsumidorIT` (consolidado-service)
- `DataReferenciaTest` (lancamentos-service)
- `EstornarLancamentoIT` (lancamentos-service)
- `EstornoPropertiesTest` (lancamentos-service)
- `ProjecaoPropertiesTest#efeitoAfetaUmaUnicaChave` (consolidado-service)
- `ReplayForaDaJanelaIT` (lancamentos-service)
- `RetroatividadeTest` (consolidado-service)
- `RetroativoPontaAPontaIT` (consolidado-service)

### RF-010
- `ConsultaConsolidadoJdbcRepositoryIT` (consolidado-service)
- `ConsultarLancamentosTest` (lancamentos-service)
- `EstornarLancamentoIT` (lancamentos-service)
- `GetLancamentosWebTest` (lancamentos-service)
- `HistoricoJdbcIT` (lancamentos-service)
- `IsolamentoPorComercianteIT` (consolidado-service)
- `IsolamentoPorComercianteIT` (lancamentos-service)
- `LancamentoJdbcRepositoryIT` (lancamentos-service)
- `PostEstornoWebTest` (lancamentos-service)
- `PostLancamentoWebTest` (lancamentos-service)

### RNF-001
- `BacklogDoConsumidorIT` (consolidado-service)
- `ConsolidadoParadoIT` (lancamentos-service)
- `ConsumidorReiniciadoIT` (consolidado-service)
- `KafkaForaIT` (lancamentos-service)
- `PublicadorOutboxIT` (lancamentos-service)

### RNF-003
- `HistogramaDoLagTest` (consolidado-service)
- `MetricasDoConsumidorIT` (consolidado-service)

### RNF-004
- `ArchitectureRulesNegativeTest#controllerReturningDomainViolatesRule` (lancamentos-service)
- `ArchitectureTest#controllersReturnWebRecords` (lancamentos-service)
- `ChaveOrfaEAtomicidadeIT` (lancamentos-service)
- `ConstraintsLancamentoIT` (lancamentos-service)
- `ConsumidorDeLancamentosIT` (consolidado-service)
- `ConsumidorReiniciadoIT` (consolidado-service)
- `DeduplicacaoConcorrenteIT` (consolidado-service)
- `DltIT` (consolidado-service)
- `EstornoConcorrenteIT` (lancamentos-service)
- `EstornoIdempotenteConcorrenteIT` (lancamentos-service)
- `EstornoIntercaladoIT` (lancamentos-service)
- `EstornoPropertiesTest` (lancamentos-service)
- `EstornoTest` (lancamentos-service)
- `FalhaEntreEnvioEMarcacaoIT` (lancamentos-service)
- `IdempotenciaConcorrenteIT` (lancamentos-service)
- `IdempotenciaRollbackConcorrenteIT` (lancamentos-service)
- `IdempotencyKeyFkDiferidaIT` (lancamentos-service)
- `LancamentoJdbcRepositoryIT` (lancamentos-service)
- `LancamentoRegistradoTest` (consolidado-service)
- `LancamentoTest` (lancamentos-service)
- `MigracaoEPermissoesIT` (consolidado-service)
- `MigracaoEPermissoesIT` (lancamentos-service)
- `OutboxJdbcRepositoryIT` (lancamentos-service)
- `ProjecaoJdbcRepositoryIT` (consolidado-service)
- `ProjecaoPostgresPropertiesIT` (consolidado-service)
- `ProjecaoPropertiesTest` (consolidado-service)
- `PublicadorConcorrenteIT` (lancamentos-service)
- `PublicarOutboxTest` (lancamentos-service)

### RNF-005
- `ArchitectureRulesNegativeTest#outboundHttpClientViolatesRule` (consolidado-service)
- `ArchitectureRulesNegativeTest#outboundHttpClientViolatesRule` (lancamentos-service)
- `ArchitectureTest#outboundHttpClientsAreNotUsed` (consolidado-service)
- `ArchitectureTest#outboundHttpClientsAreNotUsed` (lancamentos-service)
- `AuditoriaEstornoWebTest` (lancamentos-service)
- `CabecalhosDeSegurancaWebTest` (consolidado-service)
- `CabecalhosDeSegurancaWebTest` (lancamentos-service)
- `CorsEOptionsWebTest` (consolidado-service)
- `CorsEOptionsWebTest` (lancamentos-service)
- `DependenciasSemCveTest` (consolidado-service)
- `DependenciasSemCveTest` (lancamentos-service)
- `DevTokenIT` (consolidado-service)
- `DevTokenIT` (lancamentos-service)
- `GetLancamentosWebTest` (lancamentos-service)
- `LimitesHttpWebTest` (consolidado-service)
- `LimitesHttpWebTest` (lancamentos-service)
- `LogDeAcessoWebTest` (consolidado-service)
- `LogDeAcessoWebTest` (lancamentos-service)
- `PostEstornoWebTest` (lancamentos-service)
- `SegurancaJwtWebTest` (consolidado-service)
- `SegurancaJwtWebTest` (lancamentos-service)
- `SwaggerUiProfileTest` (consolidado-service)
- `SwaggerUiProfileTest` (lancamentos-service)

### RNF-006
- `GestaoActuatorWebTest` (consolidado-service)
- `GestaoActuatorWebTest` (lancamentos-service)
- `HistogramaDoLagTest` (consolidado-service)
- `MetricasDeNegocioWebTest` (lancamentos-service)
- `MetricasDosPoolsEDoKafkaIT` (consolidado-service)
- `PoolsMetricasIT` (lancamentos-service)
- `RastroDoConsumoIT` (consolidado-service)
- `RastroDoEventoIT` (lancamentos-service)
- `TraceIdNosErrosWebTest` (consolidado-service)
- `TraceIdNosErrosWebTest` (lancamentos-service)

### RNF-007
- `DescricaoDeFalhaTest` (lancamentos-service)
- `DltForaIT` (consolidado-service)
- `FalhaEntreEnvioEMarcacaoIT` (lancamentos-service)
- `KafkaForaIT` (lancamentos-service)
- `LacoDoPublicadorTest` (lancamentos-service)
- `MetricasOutboxIT` (lancamentos-service)
- `OutboxJdbcRepositoryIT` (lancamentos-service)
- `OutboxPropertiesTest` (lancamentos-service)
- `PoliticaDeIntervaloTest` (lancamentos-service)
- `PoolsJdbcIT` (lancamentos-service)
- `PublicadorKafkaTest` (lancamentos-service)
- `PublicarOutboxTest` (lancamentos-service)

### RNF-008
- `ArchitectureRulesNegativeTest` (consolidado-service)
- `ArchitectureRulesNegativeTest` (lancamentos-service)
- `ArchitectureTest` (consolidado-service)
- `ArchitectureTest` (lancamentos-service)

### RNF-009
- `ConsolidadoApplicationTest` (consolidado-service)
- `LancamentosApplicationTest` (lancamentos-service)

### RNF-012
- `AuditoriaEstornoWebTest` (lancamentos-service)
- `GetLancamentosWebTest` (lancamentos-service)
- `LogDeAcessoWebTest` (consolidado-service)
- `LogDeAcessoWebTest` (lancamentos-service)
- `LogsDoConsumidorIT` (consolidado-service)
- `PostEstornoIT` (lancamentos-service)
- `PostEstornoWebTest` (lancamentos-service)
- `PostLancamentoIT` (lancamentos-service)
- `PublicadorOutboxIT` (lancamentos-service)
- `RastroDoConsumoIT` (consolidado-service)
- `RastroDoEventoIT` (lancamentos-service)
- `RequestIdWebTest` (consolidado-service)
- `RequestIdWebTest` (lancamentos-service)
- `TraceIdNosErrosWebTest` (consolidado-service)
- `TraceIdNosErrosWebTest` (lancamentos-service)
- `TratamentoDeErroComCorrelacaoTest` (consolidado-service)

### RNF-013
- `DatasEstritasWebTest` (lancamentos-service)
- `GetConsolidadoWebTest` (consolidado-service)
- `GetLancamentosWebTest` (lancamentos-service)
- `PostEstornoWebTest` (lancamentos-service)
- `ValidacaoEstritaWebTest` (lancamentos-service)

### RNF-014
- `ContratoRespostasWebTest` (consolidado-service)
- `ContratoRespostasWebTest` (lancamentos-service)
- `InventarioDeRotasTest` (consolidado-service)
- `InventarioDeRotasTest` (lancamentos-service)
- `OpenApiExemplosTest` (consolidado-service)
- `OpenApiExemplosTest` (lancamentos-service)
- `SwaggerUiProfileTest` (consolidado-service)
- `SwaggerUiProfileTest` (lancamentos-service)
