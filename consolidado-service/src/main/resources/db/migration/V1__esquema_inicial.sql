-- Esquema do banco consolidado (03 §3.2, T-021). Migrado como consolidado_owner (D-15).

-- Projeção: movimento diário aditivo por comerciante e dia (D-06, ADR-005).
CREATE TABLE consolidado_diario (
  comerciante_id UUID NOT NULL,
  data_referencia DATE NOT NULL,             -- a do evento; nunca recalculada (03 §4.1)
  total_creditos NUMERIC(18,2) NOT NULL DEFAULT 0,
  total_debitos NUMERIC(18,2) NOT NULL DEFAULT 0,
  total_estornos_credito NUMERIC(18,2) NOT NULL DEFAULT 0,
  total_estornos_debito NUMERIC(18,2) NOT NULL DEFAULT 0,
  qtd_lancamentos INTEGER NOT NULL DEFAULT 0,
  atualizado_em TIMESTAMPTZ NOT NULL,        -- maior instante aplicado, pelo Clock (Q-038)
  PRIMARY KEY (comerciante_id, data_referencia),
  CONSTRAINT ck_totais_nao_negativos CHECK ( -- Q-039: mesma invariante de MovimentoDiario
    total_creditos >= 0 AND total_debitos >= 0 AND total_estornos_credito >= 0
    AND total_estornos_debito >= 0 AND qtd_lancamentos >= 0)
);

-- Deduplicação por eventId, e só por ele (ADR-005). Sem limpeza no MVP (Q-041).
CREATE TABLE evento_processado (
  event_id UUID PRIMARY KEY,
  processado_em TIMESTAMPTZ NOT NULL DEFAULT now()  -- gravado com o instante do Clock (Q-038)
);

-- Grants mínimos do role da aplicação (T-021).
-- consolidado_diario: o upsert aditivo; sem DELETE/TRUNCATE (rebuild é evolução, ADR-006).
GRANT SELECT, INSERT, UPDATE ON consolidado_diario TO consolidado_app;
-- evento_processado: a deduplicação não pode ser desfeita pela aplicação.
GRANT SELECT, INSERT ON evento_processado TO consolidado_app;
