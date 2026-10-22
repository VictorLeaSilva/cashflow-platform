// Utilitários compartilhados dos testes k6 (RNF-002).
// Definição de "falha" (alinhada ao RNF-002): HTTP fora de 2xx, erro de conexão
// ou resposta acima de 2 s (timeout da requisição).
import http from 'k6/http';
import { sleep } from 'k6';

export const LANC = __ENV.LANC_URL || 'http://localhost:8081';
export const CONS = __ENV.CONS_URL || 'http://localhost:8082';
const TOKEN = __ENV.TOKEN;
export const TOKEN_READONLY = __ENV.TOKEN_READONLY || ''; // sem scope de escrita (prova de 403)
if (!TOKEN) {
  throw new Error('TOKEN ausente. Use loadtest/run.sh, que gera o token com scripts/DevToken.java.');
}

// Por padrão, só 2xx é sucesso. Testes negativos sobrescrevem por requisição.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }));

export function params(extraHeaders = {}, extra = {}) {
  return Object.assign(
    {
      headers: Object.assign(
        { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
        extraHeaders,
      ),
      timeout: '2s',
    },
    extra,
  );
}

export function uuid() {
  const hex = '0123456789abcdef';
  let s = '';
  for (let i = 0; i < 36; i++) {
    if (i === 8 || i === 13 || i === 18 || i === 23) s += '-';
    else if (i === 14) s += '4';
    else if (i === 19) s += hex[8 + Math.floor(Math.random() * 4)];
    else s += hex[Math.floor(Math.random() * 16)];
  }
  return s;
}

// Fuso de negócio America/Sao_Paulo = UTC-3 (sem horário de verão desde 2019).
const SP_OFFSET_MS = 3 * 3600 * 1000;
const DAY_MS = 86400000;

export function businessDate(daysAgo = 0) {
  return new Date(Date.now() - SP_OFFSET_MS - daysAgo * DAY_MS).toISOString().slice(0, 10);
}

export function instantDaysAgo(daysAgo) {
  return new Date(Date.now() - daysAgo * DAY_MS).toISOString();
}

export function postLancamento(tipo, valor, daysAgo = 0, extra = {}, key = uuid()) {
  const body = { tipo, valor, descricao: 'k6' };
  if (daysAgo > 0) body.dataOcorrencia = instantDaysAgo(daysAgo);
  const res = http.post(
    `${LANC}/api/v1/lancamentos`,
    JSON.stringify(body),
    params({ 'Idempotency-Key': key }, extra),
  );
  return { res, key, body };
}

export function consolidadoRange(daysBack, extra = {}) {
  const url = `${CONS}/api/v1/consolidado?dataInicio=${businessDate(daysBack)}&dataFim=${businessDate(0)}`;
  return http.get(url, params({}, extra));
}

export function consolidadoDia(date, extra = {}) {
  return http.get(`${CONS}/api/v1/consolidado/${date}`, params({}, extra));
}

// Soma de lançamentos refletidos no consolidado; -1 se a resposta for inválida.
export function totalQtd(res) {
  if (res.status !== 200) return -1;
  try {
    return res.json('dias').reduce((acc, d) => acc + d.qtdLancamentos, 0);
  } catch (e) {
    return -1;
  }
}

export function waitFor(fn, timeoutSeconds, stepSeconds = 0.5) {
  const end = Date.now() + timeoutSeconds * 1000;
  while (Date.now() < end) {
    if (fn()) return true;
    sleep(stepSeconds);
  }
  return false;
}
