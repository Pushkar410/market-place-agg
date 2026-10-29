export const EXAMPLE_QUERIES = [
  'laptop under 40k in Bengaluru, trusted seller',
  'PG near SIT Tumkur under 8000',
  'cheapest used bicycle in Tumkur',
  'VTU engineering textbooks in Tumakuru',
  'MacBook Air M1 in Bengaluru',
];

export const SORT_OPTIONS = [
  { value: 'best', label: 'Best match' },
  { value: 'trust', label: 'Highest trust' },
  { value: 'price', label: 'Lowest price' },
];

export const TRUST_LEVEL_STYLES = {
  HIGH: { label: 'High trust', ring: 'text-emerald-600', bar: 'bg-emerald-500', chip: 'bg-emerald-50 text-emerald-700 ring-emerald-200' },
  MEDIUM: { label: 'Medium trust', ring: 'text-amber-600', bar: 'bg-amber-500', chip: 'bg-amber-50 text-amber-700 ring-amber-200' },
  LOW: { label: 'High risk', ring: 'text-rose-600', bar: 'bg-rose-500', chip: 'bg-rose-50 text-rose-700 ring-rose-200' },
};

export const trustStyle = (level) => TRUST_LEVEL_STYLES[level] || TRUST_LEVEL_STYLES.MEDIUM;

export const formatPrice = (price, currency = 'INR') => {
  if (price === null || price === undefined) return 'Price not stated';
  try {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(price);
  } catch {
    return `${currency} ${Math.round(price)}`;
  }
};

export const humanize = (value) =>
  value ? String(value).toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase()) : '';

export const formatPosted = (days) => {
  if (days === null || days === undefined) return '';
  if (days === 0) return 'Today';
  if (days === 1) return '1 day ago';
  return `${days} days ago`;
};

/**
 * internal -> router link; http(s) -> external link (only these two schemes are ever linked);
 * anything else -> no link, with a label that depends on whether the source is simulated.
 */
export const classifyListingUrl = (url, simulated = false) => {
  if (url && url.startsWith('/')) return { kind: 'internal', to: url };
  if (url && /^https?:\/\//i.test(url) && !/\.invalid(\/|$)/.test(url)) return { kind: 'external', href: url };
  return { kind: 'none', label: simulated ? 'Simulated source · no live link' : 'Verified place / store' };
};

/** Client-side view of the ranked results: optional hiding of high-risk listings, real-only filter and re-sorting. */
export const arrangeResults = (results, { sort = 'best', hideRisky = false, realOnly = false, simulatedIds = new Set() } = {}) => {
  let list = results;
  if (hideRisky) list = list.filter((r) => r.trust.level !== 'LOW');
  if (realOnly) list = list.filter((r) => !simulatedIds.has(r.listing.sourceId));
  if (sort === 'trust') list = [...list].sort((a, b) => b.trust.score - a.trust.score || a.rank - b.rank);
  else if (sort === 'price') list = [...list].sort((a, b) => (a.listing.price ?? Infinity) - (b.listing.price ?? Infinity) || a.rank - b.rank);
  else list = [...list].sort((a, b) => a.rank - b.rank);
  return list;
};

const RECENT_KEY = 'aggregator.recentSearches';

export const loadRecentSearches = () => {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(RECENT_KEY) || '[]');
    return Array.isArray(parsed) ? parsed.filter((q) => typeof q === 'string').slice(0, 5) : [];
  } catch {
    return [];
  }
};

export const saveRecentSearch = (query) => {
  const q = query.trim();
  if (!q) return loadRecentSearches();
  const existing = loadRecentSearches().filter((x) => x.toLowerCase() !== q.toLowerCase());
  const updated = [q, ...existing].slice(0, 5);
  try {
    window.localStorage.setItem(RECENT_KEY, JSON.stringify(updated));
  } catch {
    /* quota */
  }
  return updated;
};
