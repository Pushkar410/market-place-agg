import { get, post } from '../../common/services/api/request.js';

// Search runs several sources in parallel and may call an LLM (plan + parse + explain), so allow more than 10s.
const SEARCH_TIMEOUT_MS = Number(import.meta.env.VITE_AGGREGATOR_TIMEOUT_MS) || 45000;

export const aggregatorService = {
 /** POST /api/v1/aggregator/search -> AggregatorResponse */
 search: ({ query, limit = 10 }) =>
 post('/v1/aggregator/search', { query, limit }, { timeout: SEARCH_TIMEOUT_MS }),

 /** GET /api/v1/aggregator/sources -> SourceInfo[] */
 sources: () => get('/v1/aggregator/sources', { timeout: 10000 }),

 /** POST /api/v1/aggregator/submit -> { id, message } (share a listing found elsewhere) */
 submit: (payload) => post('/v1/aggregator/submit', payload, { timeout: 15000 }),
};

export default aggregatorService;
