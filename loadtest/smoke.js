// TESTE NORMAL (smoke): valida o contrato ponta a ponta com carga mínima.
// Uso: loadtest/run.sh smoke
// Requisitos exercitados: RF-001, RF-002, RF-004, RF-005, RF-007, RNF-003 (convergência) e RNF-005 (401/403).
import http from 'k6/http';
import { check, group } from 'k6';
import {
  LANC, CONS, TOKEN_READONLY, params, uuid, postLancamento, consolidadoRange, totalQtd, waitFor, businessDate,
} from './lib/common.js';

if (!TOKEN_READONLY) {
  throw new Error('TOKEN_READONLY ausente. Use loadtest/run.sh.');
}

export const options = {
  vus: 1,
  iterations: 5,
  thresholds: {
    checks: ['rate==1'], // AC-T033.1: todos os checks em 100 % (sem limiar de latência, Q-073)
  },
};

export default function () {
  const before = totalQtd(consolidadoRange(1));
  check(before, { 'consolidado inicial legível': (n) => n >= 0 });

  let creditoId;
  const creditoKey = uuid();

  group('registrar', () => {
    const c = postLancamento('CREDITO', '100.00', 0, {}, creditoKey);
    check(c.res, {
      'crédito 201': (r) => r.status === 201,
      'crédito com id e dataReferencia': (r) => !!r.json('id') && !!r.json('dataReferencia'),
      'valor preservado': (r) => r.json('valor') === '100.00',
    });
    creditoId = c.res.status === 201 ? c.res.json('id') : null;

    const d = postLancamento('DEBITO', '40.00');
    check(d.res, { 'débito 201': (r) => r.status === 201 });
  });

  group('idempotência', () => {
    const replay = postLancamento('CREDITO', '100.00', 0, {}, creditoKey);
    check(replay.res, {
      'replay devolve 201': (r) => r.status === 201,
      'replay mesmo id': (r) => r.json('id') === creditoId,
      'replay sinalizado': (r) => r.headers['Idempotent-Replayed'] === 'true',
    });
    const reuse = postLancamento('CREDITO', '999.00', 0, { responseCallback: http.expectedStatuses(422) }, creditoKey);
    check(reuse.res, { 'mesma chave, payload diferente → 422': (r) => r.status === 422 });
  });

  group('validação e segurança', () => {
    const neg = postLancamento('CREDITO', '-1.00', 0, { responseCallback: http.expectedStatuses(400) });
    check(neg.res, { 'valor negativo → 400': (r) => r.status === 400 });

    const casas = postLancamento('CREDITO', '10.001', 0, { responseCallback: http.expectedStatuses(400) });
    check(casas.res, { 'mais de 2 casas → 400': (r) => r.status === 400 });

    const semToken = http.get(`${CONS}/api/v1/consolidado/${businessDate(0)}`, {
      timeout: '2s',
      responseCallback: http.expectedStatuses(401),
    });
    check(semToken, { 'sem token → 401': (r) => r.status === 401 });

    // Token válido, porém só com scope de leitura: escrita deve ser negada.
    const semScope = http.post(
      `${LANC}/api/v1/lancamentos`,
      JSON.stringify({ tipo: 'CREDITO', valor: '1.00' }),
      {
        headers: {
          Authorization: `Bearer ${TOKEN_READONLY}`,
          'Content-Type': 'application/json',
          'Idempotency-Key': uuid(),
        },
        timeout: '2s',
        responseCallback: http.expectedStatuses(403),
      },
    );
    check(semScope, { 'sem scope de escrita → 403': (r) => r.status === 403 });
  });

  group('consulta de lançamentos', () => {
    const det = http.get(`${LANC}/api/v1/lancamentos/${creditoId}`, params());
    check(det, { 'detalhe 200': (r) => r.status === 200 });
    const lista = http.get(`${LANC}/api/v1/lancamentos?size=10`, params());
    check(lista, { 'histórico 200': (r) => r.status === 200 });
  });

  group('consolidado (consistência eventual)', () => {
    const converged = waitFor(() => totalQtd(consolidadoRange(1)) >= before + 2, 20);
    check(converged, { 'projeção convergiu em ≤ 20 s': (ok) => ok });

    const res = consolidadoRange(1);
    check(res, {
      'consolidado 200': (r) => r.status === 200,
      'um item por dia (2 dias)': (r) => r.json('dias').length === 2,
    });
  });
}
