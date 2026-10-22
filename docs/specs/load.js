// TESTE DE CARGA (RNF-002 / RNF-010).
// Cenário misto: leitura do Consolidado a 50 req/s (pico do enunciado) enquanto
// Lançamentos recebe escrita simultânea (prova de isolamento, RNF-001).
//
// Variáveis: READ_RPS (50), WRITE_RPS (10), DURATION (5m).
// Execuções informativas de RNF-010: READ_RPS=100 e READ_RPS=200.
//
// Falha = HTTP fora de 2xx, erro de conexão ou > 2 s. 'dropped_iterations' > 0 indica
// que o gerador de carga não sustentou a taxa (resultado inválido, não "perda" do serviço).
// Latência do lag de projeção (RNF-003) é medida via Prometheus (consolidado_event_lag_seconds).
import { check } from 'k6';
import {
  postLancamento, consolidadoRange, consolidadoDia, totalQtd, waitFor, businessDate,
} from './lib/common.js';

const READ_RPS = Number(__ENV.READ_RPS || 50);
const WRITE_RPS = Number(__ENV.WRITE_RPS || 10);
const DURATION = __ENV.DURATION || '5m';
const SEED_DAYS = 30; // limite de retroatividade (A4)

export const options = {
  setupTimeout: '120s',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    consolidado_read: {
      executor: 'constant-arrival-rate',
      rate: READ_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 50,
      maxVUs: 400,
      exec: 'readConsolidado',
      tags: { flow: 'read' },
    },
    lancamentos_write: {
      executor: 'constant-arrival-rate',
      rate: WRITE_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 20,
      maxVUs: 200,
      exec: 'writeLancamento',
      tags: { flow: 'write' },
    },
  },
  thresholds: {
    // RNF-002: perda < 5 % (meta interna: 1 %) e p95 ≤ 500 ms na leitura.
    'http_req_failed{flow:read}': ['rate<0.05'],
    'http_req_duration{flow:read}': ['p(95)<500'],
    // Isolamento: escrita ≥ 99 % de sucesso com a leitura em pico.
    'http_req_failed{flow:write}': ['rate<0.01'],
    // Meta local (P8): ajustar após a primeira medição documentada.
    'http_req_duration{flow:write}': ['p(95)<1000'],
    'checks{flow:read}': ['rate>0.99'],
    'checks{flow:write}': ['rate>0.99'],
    // Validade do teste: o gerador sustentou a taxa pedida.
    dropped_iterations: ['count<1'],
  },
};

export function setup() {
  const baseline = Math.max(totalQtd(consolidadoRange(SEED_DAYS - 1)), 0);
  let posted = 0;
  for (let d = 0; d < SEED_DAYS; d++) {
    for (const [tipo, valor] of [['CREDITO', '1000.00'], ['DEBITO', '350.00']]) {
      const { res } = postLancamento(tipo, valor, d);
      if (res.status !== 201) {
        throw new Error(`Seed falhou (${res.status}): ${res.body}`);
      }
      posted++;
    }
  }
  const converged = waitFor(() => totalQtd(consolidadoRange(SEED_DAYS - 1)) >= baseline + posted, 60);
  if (!converged) {
    throw new Error('Consolidado não convergiu após o seed em 60 s');
  }
  return { seedDays: SEED_DAYS };
}

export function readConsolidado(data) {
  if (Math.random() < 0.8) {
    const res = consolidadoRange(data.seedDays - 1, { tags: { endpoint: 'range' } });
    check(res, {
      'range 200 com 30 dias': (r) => r.status === 200 && r.json('dias').length === data.seedDays,
    });
  } else {
    const dia = businessDate(Math.floor(Math.random() * data.seedDays));
    const res = consolidadoDia(dia, { tags: { endpoint: 'dia' } });
    check(res, { 'dia 200': (r) => r.status === 200 });
  }
}

export function writeLancamento() {
  const tipo = Math.random() < 0.6 ? 'CREDITO' : 'DEBITO';
  const valor = (1 + Math.floor(Math.random() * 100000) / 100).toFixed(2);
  const daysAgo = Math.random() < 0.9 ? 0 : 1 + Math.floor(Math.random() * 5);
  const { res } = postLancamento(tipo, valor, daysAgo);
  check(res, { 'escrita 201': (r) => r.status === 201 });
}
