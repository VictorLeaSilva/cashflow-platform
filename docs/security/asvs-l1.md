# OWASP ASVS 4.0.3, nível 1: lista de verificação de baseline

Referência complementar do `04 §2.6`. Para cada requisito de nível 1 dos capítulos que se aplicam a uma API REST sem sessão nem interface própria, a coluna **Situação** diz **atende**, **não se aplica** (com o porquê) ou **evolução** (não atende hoje, é da borda ou do alvo). Evidência = controle e `classe#método` de [`owasp-mapping.md`](owasp-mapping.md) ou documento de decisão.

**Procedência dos IDs:** os números e o sentido de cada requisito foram lidos do repositório oficial `OWASP/ASVS`, tag `v4.0.3` (`4.0/en/`), em 2026-10-25; a leitura cobriu os capítulos V4, V5, V6, V7, V8, V9, V13 e V14 (as seções V14.2 a V14.5). Os capítulos V2 e V3 (autenticação e sessão), V10, V11 e V12 aparecem só por capítulo, porque o arquivo da fonte não pôde ser lido nesta passada. Conferir linha a linha com o PDF oficial é um *follow-up* (T-040 em diante).

| ID | Requisito (resumo) | Situação | Evidência |
|---|---|---|---|
| **V2/V3** | Autenticação por senha, recuperação, sessões e *cookies* | Não se aplica | Não há login, senha, sessão nem *cookie*: Resource Server JWT sem estado (ADR-007). A validação do token está em V13/API2 |
| **V4.1.1** | Controle de acesso aplicado numa camada de serviço confiável | Atende | Autorização por *scope* e por comerciante no servidor (`SegurancaJwtWebTest#tokenSemScopeDeEscritaResponde403`) |
| **V4.1.2** | Atributos de autorização não manipuláveis pelo usuário | Atende | O comerciante só vem do token; `comercianteId` na entrada é 400 (`PostLancamentoWebTest#comercianteIdNoCorpoResponde400`) |
| **V4.1.3** | Privilégio mínimo | Atende | *Scopes* separados; escrita não inclui leitura (`GetLancamentosWebTest#semTokenResponde401SoComEscritaResponde403EComLeitura200`) |
| **V4.1.5** | Controles de acesso falham de forma segura | Atende | Sem token ou token inválido → 401; falha inesperada → 500 genérico (`PostLancamentoWebTest#erroInesperadoResponde500Generico`) |
| **V4.2.1** | Proteção contra IDOR em dados e APIs sensíveis | Atende | Cross-tenant → 404 (`IsolamentoPorComercianteIT#aEBNaoSeEnxergamEmNenhumSentido`, nos dois serviços) |
| **V4.2.2** | Proteção contra CSRF em ações autenticadas | Não se aplica | Sem *cookie* nem sessão; a credencial é o cabeçalho `Authorization`, que o navegador não envia sozinho. CSRF desligado de propósito na cadeia de segurança |
| **V4.3.1** | Console administrativo com múltiplos fatores | Não se aplica | Não há console administrativo; o Actuator fica em porta de gestão que nunca é publicada (ADR-010) |
| **V4.3.2** | Sem listagem de diretório nem metadados expostos | Atende | Sem recursos estáticos fora do contrato (`spring.web.resources.add-mappings: false`); Swagger UI só no profile `local` (`SwaggerUiProfileTest.SemProfile#rotasNaoExistemMesmoComToken`) |
| **V5.1.1** | Proteção contra poluição de parâmetros HTTP | Atende | Parâmetro repetido é rejeitado (`GetConsolidadoWebTest#parametroRepetidoEhRejeitado`) |
| **V5.1.2** | Proteção contra *mass assignment* | Atende | DTOs explícitos e propriedade desconhecida → 400 (`PostEstornoWebTest#propriedadeNoCorpoResponde400`) |
| **V5.1.3** | Validação positiva (lista de permitidos) | Atende | Valores, enums, datas `AAAA-MM-DD`, chave `^[A-Za-z0-9._-]{1,64}$` (`DatasEstritasWebTest`, `ValidacaoEstritaWebTest#textoLivreInvalidoResponde400`) |
| **V5.1.4** | Dados estruturados fortemente tipados e com padrão | Atende | Valor como texto decimal com 2 casas, UUID canônico, datas estritas (`PostLancamentoWebTest#valorInvalidoResponde400SemGravar`) |
| **V5.1.5** | Redirecionamentos só para destinos aprovados | Não se aplica | A API não redireciona |
| **V5.2.1 a V5.2.5, V5.2.7, V5.2.8** | Sanitização de HTML, e-mail, *eval*, *templates*, SVG, Markdown | Não se aplica | A API só troca JSON; não renderiza HTML, não envia e-mail e não executa código vindo da entrada |
| **V5.2.2** | Texto livre limitado a caracteres e tamanho permitidos | Atende | Controle e espaços nas pontas rejeitados, tamanho máximo, NFC (`ValidacaoEstritaWebTest#textoLivreInvalidoResponde400`) |
| **V5.2.6** | Proteção contra SSRF | Atende | Nenhum cliente HTTP de saída (`ArchitectureTest#outboundHttpClientsAreNotUsed`) |
| **V5.3.1 a V5.3.3, V5.3.5** | Codificação de saída por contexto, anti-XSS | Não se aplica | A resposta é `application/json`/`problem+json` com `nosniff` e CSP `default-src 'none'`; a codificação para exibição é de quem renderiza (`03 §5.2`) |
| **V5.3.4** | Consultas parametrizadas contra injeção de SQL | Atende | Só `JdbcClient` com parâmetros nomeados (ADR-002); sem SQL montado com entrada |
| **V5.3.6** | Proteção contra injeção de JSON | Atende | Serialização por biblioteca; nenhum JSON montado por concatenação com entrada |
| **V5.3.7 a V5.3.10** | Injeção de LDAP, comando de SO, inclusão de arquivo, XPath/XML | Não se aplica | Sem LDAP, sem processo externo, sem inclusão de arquivo e sem XML na API |
| **V5.5.1 a V5.5.4** | Desserialização segura, XML, JSON | Atende / não se aplica | Só JSON por Jackson estrito, sem tipos polimórficos nem serialização Java; o evento do Kafka passa pelo JSON Schema antes do efeito (`LeitorDeEventoTest#violacaoDeSchemaListaCampoEPalavraChave`); sem XML |
| **V6.2.1** | Módulos criptográficos falham de forma segura | Atende | Só validação RS256 por biblioteca padrão; falha de assinatura → 401, sem detalhe (`SegurancaJwtWebTest#tokenInvalidoResponde401`) |
| **V7.1.1** | Sem credenciais nem dados de pagamento no log | Atende | Logs sem token, segredo nem payload financeiro completo (`LogDeAcessoWebTest`, constituição) |
| **V7.1.2** | Sem dados pessoais protegidos por lei no log | Atende | O log de acesso não grava corpo, token nem `comercianteId` em claro (`03 §5.2`, `LogDeAcessoWebTest`) |
| **V7.4.1** | Erro genérico com identificador para investigação | Atende | `problem+json` com `requestId` e `traceId` (`CorsEOptionsWebTest#optionsSemTokenResponde405ComRequestIdETraceId`, `TraceIdNosErrosWebTest`) |
| **V8.2.1** | Cabeçalhos contra cache de dado sensível | Atende | `Cache-Control: no-store` em toda resposta (`CabecalhosDeSegurancaWebTest#todaRespostaTrazOsCabecalhosSeguros`) |
| **V8.2.2, V8.2.3** | Armazenamento no navegador e limpeza de dados do cliente | Não se aplica | Sem cliente web |
| **V8.3.1** | Dado privado no corpo, não na URL | Atende | Identificadores de negócio na URL são UUIDs e datas; nada sensível em query (`ameacas.md`) |
| **V8.3.2, V8.3.3** | Exportação/remoção e consentimento de dados pessoais | Não se aplica | O sistema guarda lançamentos financeiros de comerciantes, sem dado pessoal de titulares; política de retenção é da operação |
| **V8.3.4** | Inventário de dados sensíveis e política de tratamento | Atende | `ameacas.md` e `00-constitution` (o que não vai a log); catálogo de dados em `03 §3` |
| **V9.1.1 a V9.1.3** | TLS em toda a comunicação e cifras fortes | Evolução | Localmente HTTP; TLS termina na borda (ALB/gateway, ADR-010). HSTS idem (V14.4.5) |
| **V13.1.1** | Mesmas codificações e *parsers* em todos os componentes | Atende | JSON UTF-8 nos dois serviços; `Content-Type` e `Accept` conferidos (415 e 406: `ValidacaoEstritaWebTest#contentTypeDiferenteDeJsonResponde415`) |
| **V13.1.3** | URLs sem chaves nem tokens | Atende | Credencial só no cabeçalho `Authorization`; a chave de idempotência é cabeçalho |
| **V13.2.1** | Métodos HTTP restritos aos adequados | Atende | Métodos fora do contrato → 405; `OPTIONS` → 405 (`CorsEOptionsWebTest#optionsEmRotaDeRecursoEEmRotaInexistenteResponde405`) |
| **V13.2.2** | Validação do JSON contra *schema* antes de usar | Atende | OpenAPI validado nos testes de contrato; o evento por JSON Schema (`ContratoRespostasWebTest`, `LeitorDeEventoTest`) |
| **V13.2.3, V13.3.1** | REST com *cookie* sem anti-CSRF; validação de XSD | Não se aplica | Sem *cookie* nem SOAP/XML |
| **V14.2.1** | Componentes atualizados com verificação automática | Atende | Dependabot semanal (Maven, Actions, compose) e OWASP Dependency-Check (CVSS ≥ 7 falha) em `security.yml` |
| **V14.2.2** | Sem recursos, exemplos nem configurações não usados | Atende | Swagger UI e exemplos só no profile `local`; Actuator limitado a `health` e `prometheus` (`GestaoActuatorWebTest#soHealthEPrometheusEstaoExpostos`) |
| **V14.2.3** | Integridade de recursos hospedados fora | Não se aplica | A Swagger UI sai dos *webjars* do próprio jar, sem CDN |
| **V14.3.2** | Modo de depuração desligado em produção | Atende | Sem `debug`; erros sem *stack trace* (`LimitesHttpWebTest#limitesDoServidorEstaoNaConfiguracao`, `#nenhumErroDevolveStackTraceOuNomeDeClasse`) |
| **V14.3.3** | Sem detalhes de versão de componentes nas respostas | Atende | O cabeçalho `Server` não traz versão e não há `X-Powered-By` (`LimitesHttpWebTest#respostasNaoRevelamAVersaoDoServidor`) |
| **V14.4.1** | Todo resposta com `Content-Type` correto | Atende | `application/json` ou `application/problem+json` (`ContratoRespostasWebTest`) |
| **V14.4.2** | Respostas de API com `Content-Disposition: attachment` | Não se aplica / evolução | A API devolve JSON para clientes programáticos e a CSP `default-src 'none'` com `nosniff` impede execução no navegador; o requisito pede `attachment` e não é aplicado |
| **V14.4.3** | CSP contra injeção | Atende | `default-src 'none'; frame-ancestors 'none'`; a Swagger UI local tem CSP própria (`SwaggerUiProfileTest.ComProfileLocal#swaggerUiTemCspPropriaEAsDemaisRotasMantemACspFechada`) |
| **V14.4.4** | `X-Content-Type-Options: nosniff` em toda resposta | Atende | `CabecalhosDeSegurancaWebTest#todaRespostaTrazOsCabecalhosSeguros` |
| **V14.4.5** | HSTS em todas as respostas | Evolução | É da borda (TLS termina no gateway, ADR-010); localmente não é enviado, de propósito (`CabecalhosDeSegurancaWebTest`) |
| **V14.4.6** | Sem vazar URL sensível a terceiros | Atende | `Referrer-Policy: no-referrer` (`CabecalhosDeSegurancaWebTest`) |
| **V14.4.7** | Sem enquadramento por terceiros | Atende | `X-Frame-Options: DENY` e `frame-ancestors 'none'` (`CabecalhosDeSegurancaWebTest`) |
| **V14.5.1** | Apenas os métodos HTTP usados pela aplicação | Atende | 405 fora do contrato, `OPTIONS` incluído (`CorsEOptionsWebTest`) |
| **V14.5.2** | O cabeçalho `Origin` nunca decide acesso | Atende | A autorização usa só o token; `Origin` é ignorado (`CorsEOptionsWebTest#requisicaoComOriginNaoRecebeAllowOrigin`) |
| **V14.5.3** | CORS sem lista que inclua a origem `null` | Atende | CORS fechado: nenhuma origem é permitida e nenhuma resposta traz `Access-Control-Allow-Origin` (`CorsEOptionsWebTest#preflightDeQualquerOrigemEhRecusadoSemCabecalhosCors`) |
| **V10, V11, V12** | Código malicioso, lógica de negócio, arquivos | Atende / não se aplica | V10: dependências verificadas (Dependency-Check, Dependabot) e sem código dinâmico; V11: idempotência, estorno único e limites de valor e data (ver API6 em `owasp-mapping.md`); V12: a API não recebe nem serve arquivos |

## Resumo
- **Atende:** autorização, validação de entrada, injeção, erros e log, cabeçalhos HTTP, CORS, métodos, dependências.
- **Não se aplica:** autenticação por senha, sessão, CSRF por *cookie*, HTML, arquivos, XML.
- **Evolução (borda e alvo):** TLS e HSTS (V9.1, V14.4.5) e `Content-Disposition` (V14.4.2), além de *rate limit* e WAF (`owasp-mapping.md`, API4).
