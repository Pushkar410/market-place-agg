import { useEffect, useMemo, useRef, useState } from 'react';
import { Search, ShieldCheck } from 'lucide-react';
import { useAggregatorSearch, useAggregatorSources } from '../hooks/useAggregator.js';
import { EXAMPLE_QUERIES, SORT_OPTIONS, arrangeResults, loadRecentSearches, saveRecentSearch } from '../utils/aggregatorUtils.js';
import ListingResultCard from '../components/ListingResultCard.jsx';
import { AgentTrace, ClarificationCard, Searching, SourceLine, SummaryPanel } from '../components/AgentPanels.jsx';

const errorMessage = (error) => {
  if (!error) return null;
  if (error.response?.status === 429) return 'Too many searches in a short time. Please wait a moment and try again.';
  if (error.code === 'ECONNABORTED') return 'The search took too long. Please try again.';
  if (!error.response) return 'Cannot reach the backend. Make sure it is running on http://localhost:8080 (see README).';
  return error.response?.data?.message || error.message || 'Search failed.';
};

const AggregatorPage = () => {
  const [query, setQuery] = useState('');
  const [lastQuery, setLastQuery] = useState('');
  const [sort, setSort] = useState('best');
  const [hideRisky, setHideRisky] = useState(false);
  const [realOnly, setRealOnly] = useState(false);
  const [recent, setRecent] = useState(() => loadRecentSearches());
  const resultsRef = useRef(null);
  const search = useAggregatorSearch();
  const sources = useAggregatorSources();
  const data = search.data;

  useEffect(() => {
    document.title = 'Trust Aggregator';
  }, []);

  useEffect(() => {
    if (data && resultsRef.current) resultsRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [data]);

  const simulatedIds = useMemo(() => new Set((sources.data || []).filter((s) => s.simulated).map((s) => s.id)), [sources.data]);
  const shown = useMemo(() => (data ? arrangeResults(data.results, { sort, hideRisky, realOnly, simulatedIds }) : []), [data, sort, hideRisky, realOnly, simulatedIds]);
  const riskyCount = data ? data.results.filter((r) => r.trust.level === 'LOW').length : 0;
  const realCount = data ? data.results.filter((r) => !simulatedIds.has(r.listing.sourceId)).length : 0;

  const run = (q) => {
    const text = q.trim();
    if (!text || search.isPending) return;
    setQuery(text);
    setLastQuery(text);
    setSort('best');
    setHideRisky(false);
    setRecent(saveRecentSearch(text));
    search.mutate({ query: text, limit: 10 });
  };

  const onSubmit = (e) => {
    e.preventDefault();
    run(query);
  };

  const suggestions = recent.length > 0 ? recent : EXAMPLE_QUERIES;

  return (
    <div className="pb-16">
      <section className="border-b border-slate-200 bg-white">
        <div className="mx-auto max-w-3xl px-5 py-10 text-center sm:py-14">
          <h1 className="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl">
            Find the right listing &mdash; <span className="text-slate-500">and know who to trust.</span>
          </h1>
          <p className="mx-auto mt-3 max-w-xl text-sm text-slate-600">
            Describe what you need. The agent searches verified real-world places, certified platforms & marketplaces, merges duplicates, and scores every listing for risk.
          </p>

          <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-3 sm:flex-row" role="search">
            <label htmlFor="aggregator-query" className="sr-only">Describe what you are looking for</label>
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
              <input
                id="aggregator-query"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                maxLength={300}
                placeholder="e.g. laptop under 40k in Bengaluru, trusted seller"
                className="h-12 w-full rounded-xl border border-slate-300 bg-white pl-11 pr-4 text-base text-slate-900 outline-none transition focus:border-slate-900 focus:ring-4 focus:ring-slate-900/10"
                autoComplete="off"
              />
            </div>
            <button
              type="submit"
              disabled={!query.trim() || search.isPending}
              className="h-12 rounded-xl bg-slate-900 px-7 text-base font-bold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {search.isPending ? 'Searching…' : 'Search'}
            </button>
          </form>

          <div className="mt-3 flex flex-wrap items-center justify-center gap-2">
            <span className="text-xs font-semibold text-slate-500">{recent.length > 0 ? 'Recent:' : 'Try:'}</span>
            {suggestions.map((q) => (
              <button key={q} type="button" onClick={() => run(q)} className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-medium text-slate-700 hover:border-slate-400">
                {q}
              </button>
            ))}
          </div>
        </div>
      </section>

      <main className="mx-auto mt-6 max-w-3xl space-y-5 px-5" ref={resultsRef}>
        {search.isPending && <Searching />}

        {search.isError && (
          <div role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{errorMessage(search.error)}</div>
        )}

        {!search.isPending && data?.clarification && (
          <ClarificationCard question={data.clarification} examples={EXAMPLE_QUERIES} onPick={run} />
        )}

        {!search.isPending && data && !data.clarification && (
          <>
            <SummaryPanel data={data} />

            <div className="flex flex-wrap items-center justify-between gap-3">
              <SourceLine sources={data.sources} plan={data.plan} />
              {data.results.length > 1 && (
                <div className="flex flex-wrap items-center gap-3 text-xs text-slate-600">
                  <label className="flex items-center gap-1.5 font-medium text-emerald-800 bg-emerald-50/80 px-2.5 py-1 rounded-lg border border-emerald-200/80 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={realOnly}
                      onChange={(e) => setRealOnly(e.target.checked)}
                      className="rounded text-emerald-600 focus:ring-emerald-500"
                    />
                    <ShieldCheck className="h-3.5 w-3.5 text-emerald-600" />
                    Real-World only ({realCount})
                  </label>

                  <label className="flex items-center gap-1.5">
                    Sort
                    <select value={sort} onChange={(e) => setSort(e.target.value)} className="rounded-lg border border-slate-300 bg-white px-2 py-1 text-xs">
                      {SORT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                    </select>
                  </label>

                  {riskyCount > 0 && (
                    <label className="flex items-center gap-1.5">
                      <input type="checkbox" checked={hideRisky} onChange={(e) => setHideRisky(e.target.checked)} />
                      Hide high-risk ({riskyCount})
                    </label>
                  )}
                </div>
              )}
            </div>

            <section aria-label="Ranked results" className="space-y-3">
              {shown.length === 0 ? (
                <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500">
                  {data.results.length === 0
                    ? 'No listings matched. Try fewer words, a higher budget, or a different city.'
                    : 'Everything was hidden by your active filter. Uncheck filters to view more.'}
                </div>
              ) : (
                shown.map((r) => <ListingResultCard key={r.listing.id} ranked={r} simulated={simulatedIds.has(r.listing.sourceId)} />)
              )}
            </section>

            <AgentTrace trace={data.agentTrace} />
            <p className="text-center text-xs text-slate-400">Showing results for &ldquo;{lastQuery}&rdquo;</p>
          </>
        )}
      </main>
    </div>
  );
};

export default AggregatorPage;
