-- Esquema inicial do banco lancamentos (03 §3.1). Executada pelo Flyway como lancamentos_owner;
-- a aplicação opera como lancamentos_app só com os grants do final deste arquivo (D-15).

CREATE TABLE lancamento (
  id UUID PRIMARY KEY,
  comerciante_id UUID NOT NULL,
  tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('CREDITO','DEBITO','ESTORNO')),
  valor NUMERIC(15,2) NOT NULL CHECK (valor > 0),
  descricao VARCHAR(255),
  data_ocorrencia TIMESTAMPTZ NOT NULL,
  data_referencia DATE NOT NULL,
  referencia_lancamento_id UUID REFERENCES lancamento(id),
  tipo_original VARCHAR(10) CHECK (tipo_original IN ('CREDITO','DEBITO')),
  identificador_externo VARCHAR(100),
  criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_estorno CHECK (
    (tipo = 'ESTORNO' AND referencia_lancamento_id IS NOT NULL AND tipo_original IS NOT NULL)
    OR (tipo <> 'ESTORNO' AND referencia_lancamento_id IS NULL AND tipo_original IS NULL))
);
CREATE UNIQUE INDEX ux_estorno_unico ON lancamento (referencia_lancamento_id) WHERE tipo = 'ESTORNO';
CREATE UNIQUE INDEX ux_identificador_externo ON lancamento (comerciante_id, identificador_externo) WHERE identificador_externo IS NOT NULL;
CREATE INDEX ix_lancamento_historico ON lancamento (comerciante_id, data_referencia DESC, id);

CREATE TABLE outbox_event (
  event_id UUID PRIMARY KEY,
  aggregate_id UUID NOT NULL,
  topic VARCHAR(100) NOT NULL,
  message_key VARCHAR(64) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  schema_version INTEGER NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  request_id VARCHAR(64)                   -- X-Request-Id da requisição de origem; vira o cabeçalho Kafka x-request-id (03 §5.2)
);
CREATE INDEX ix_outbox_pendentes ON outbox_event (created_at) WHERE published_at IS NULL;

CREATE TABLE idempotency_key (
  comerciante_id UUID NOT NULL,
  chave VARCHAR(64) NOT NULL,
  request_hash CHAR(64) NOT NULL,          -- SHA-256 de método + caminho + corpo canônico
  lancamento_id UUID NOT NULL,             -- gerado antes do INSERT; a resposta é reconstruída a partir dele
  criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (comerciante_id, chave),
  CONSTRAINT fk_idem_lancamento FOREIGN KEY (lancamento_id) REFERENCES lancamento (id)
    DEFERRABLE INITIALLY DEFERRED          -- no COMMIT, a chave só existe se o lançamento existir
);

-- Grants mínimos do role da aplicação (T-011).
-- lancamento: sem UPDATE/DELETE, imutabilidade garantida pelo banco (A8, P4).
GRANT SELECT, INSERT ON lancamento TO lancamentos_app;
REVOKE UPDATE, DELETE, TRUNCATE ON lancamento FROM lancamentos_app;
GRANT SELECT, INSERT ON idempotency_key TO lancamentos_app;
-- outbox_event: UPDATE marca a publicação e DELETE faz o purge (T-016).
GRANT SELECT, INSERT, UPDATE, DELETE ON outbox_event TO lancamentos_app;
