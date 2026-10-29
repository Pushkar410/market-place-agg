import PropTypes from 'prop-types';
import { trustStyle } from '../utils/aggregatorUtils.js';

const pct = (v) => `${Math.round(v * 100)}%`;

/** "Why this score?" - every signal that went into the Trust Score, plus the ranking components. */
const TrustBreakdown = ({ ranked }) => {
 const { trust, relevance, priceScore, locationScore, preferenceScore, finalScore } = ranked;
 const style = trustStyle(trust.level);
 return (
 <div className="mt-4 grid gap-6 rounded-2xl bg-slate-50 p-4 md:grid-cols-2">
 <div>
 <h4 className="mb-3 text-xs font-bold uppercase tracking-wide text-slate-500">Trust score signals</h4>
 <ul className="space-y-2.5">
 {trust.signals.map((s) => {
 const negative = s.points < 0;
 const width = s.max > 0 ? Math.max(0, Math.min(100, (s.points / s.max) * 100)) : 0;
 return (
 <li key={s.name}>
 <div className="flex items-baseline justify-between text-xs">
 <span className="font-semibold text-slate-700">{s.name}</span>
 <span className={`font-mono ${negative ? 'text-rose-600' : 'text-slate-500'}`}>
 {negative ? s.points : `${s.points}/${s.max}`}
 </span>
 </div>
 {!negative && (
 <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-200">
 <div className={`h-full rounded-full ${style.bar}`} style={{ width: `${width}%` }} />
 </div>
 )}
 <p className="mt-0.5 text-[11px] text-slate-500">{s.detail}</p>
 </li>
 );
 })}
 </ul>
 </div>
 <div>
 <h4 className="mb-3 text-xs font-bold uppercase tracking-wide text-slate-500">Ranking components</h4>
 <dl className="space-y-2 text-xs">
 {[
 ['Relevance to your query', relevance],
 ['Trust (score / 100)', trust.score / 100],
 ['Price attractiveness', priceScore],
 ['Location fit', locationScore],
 ['Preference match', preferenceScore],
 ].map(([label, value]) => (
 <div key={label} className="flex items-center justify-between gap-3">
 <dt className="text-slate-600">{label}</dt>
 <dd className="flex w-32 items-center gap-2">
 <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-200">
 <span className="block h-full rounded-full bg-slate-700" style={{ width: pct(value) }} />
 </span>
 <span className="w-9 text-right font-mono text-slate-500">{pct(value)}</span>
 </dd>
 </div>
 ))}
 </dl>
 <p className="mt-4 rounded-xl bg-white p-3 text-[11px] leading-relaxed text-slate-500 ring-1 ring-slate-200">
 Final score <strong className="text-slate-800">{finalScore.toFixed(3)}</strong> = weighted sum of the five components
 above. Weights shift with your intent (&ldquo;trusted&rdquo;, &ldquo;cheapest&rdquo;, &ldquo;nearby&rdquo;), and listings
 with high-risk flags are down-ranked so a too-good-to-be-true deal never leads the list.
 </p>
 </div>
 </div>
 );
};

TrustBreakdown.propTypes = {
 ranked: PropTypes.object.isRequired,
};

export default TrustBreakdown;
