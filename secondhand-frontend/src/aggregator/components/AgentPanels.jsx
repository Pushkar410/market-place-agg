import PropTypes from 'prop-types';
import { Bot, HelpCircle, Loader2, Sparkles } from 'lucide-react';
import { humanize } from '../utils/aggregatorUtils.js';

/** Plain progress indicator while the request is in flight. */
export const Searching = () => (
 <div role="status" aria-live="polite" className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-5 text-sm font-semibold text-slate-700">
 <Loader2 className="h-5 w-5 animate-spin" /> The agent is planning, searching and scoring&hellip;
 </div>
);

/** The agent asks a question instead of guessing when a request is too vague. */
export const ClarificationCard = ({ question, examples, onPick }) => (
 <section className="rounded-2xl border border-indigo-200 bg-indigo-50 p-5" aria-label="The agent needs more detail">
 <div className="flex items-start gap-3">
 <HelpCircle className="mt-0.5 h-5 w-5 shrink-0 text-indigo-600" />
 <div>
 <p className="text-sm font-semibold text-indigo-900" data-testid="clarification">{question}</p>
 <div className="mt-3 flex flex-wrap gap-2">
 {examples.map((q) => (
 <button key={q} type="button" onClick={() => onPick(q)} className="rounded-full border border-indigo-200 bg-white px-3 py-1 text-xs font-medium text-indigo-800 hover:bg-indigo-100">
 {q}
 </button>
 ))}
 </div>
 </div>
 </div>
 </section>
);
ClarificationCard.propTypes = { question: PropTypes.string.isRequired, examples: PropTypes.array.isRequired, onPick: PropTypes.func.isRequired };

/** Agent's recommendation: the LLM comparison when available, otherwise the built-in summary; plus what it understood. */
export const SummaryPanel = ({ data }) => {
 const { intent } = data;
 const chips = [
 intent.keywords?.length ? intent.keywords.join(' ') : null,
 intent.category ? humanize(intent.category) : null,
 intent.maxPrice != null ? `up to ₹${Math.round(intent.maxPrice).toLocaleString('en-IN')}` : null,
 intent.minPrice != null ? `from ₹${Math.round(intent.minPrice).toLocaleString('en-IN')}` : null,
 intent.city || null,
 intent.condition ? humanize(intent.condition) : null,
 intent.preference && intent.preference !== 'BALANCED' ? `prefers ${humanize(intent.preference).toLowerCase()}` : null,
 ].filter(Boolean);
 const ai = Boolean(data.aiExplanation);
 return (
 <section className="rounded-2xl border border-slate-200 bg-white p-5" aria-label="Agent recommendation">
 <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wide text-slate-500">
 <Sparkles className="h-4 w-4" /> Recommendation
 {ai && <span className="rounded bg-violet-100 px-1.5 py-0.5 text-[10px] text-violet-700">AI-written · based on computed scores</span>}
 </div>
 <p className="mt-2 text-sm leading-relaxed text-slate-800" data-testid="agent-summary">{ai ? data.aiExplanation : data.summary}</p>
 {chips.length > 0 && (
 <p className="mt-3 flex flex-wrap items-center gap-1.5 text-xs text-slate-500" aria-label="How the agent understood your query">
 Understood as:
 {chips.map((c) => (
 <span key={c} className="rounded-full bg-slate-100 px-2.5 py-0.5 font-medium text-slate-700">{c}</span>
 ))}
 </p>
 )}
 </section>
 );
};
SummaryPanel.propTypes = { data: PropTypes.object.isRequired };

/** One quiet line instead of a row of source chips; details live in the trace. */
export const SourceLine = ({ sources, plan }) => {
 const skipped = sources.filter((s) => s.status === 'SKIPPED');
 const queried = sources.filter((s) => s.status !== 'SKIPPED');
 const failed = queried.filter((s) => s.status !== 'OK');
 return (
 <p className="text-xs text-slate-500" data-testid="source-line">
 {queried.length - failed.length} of {queried.length} sources responded
 {skipped.length > 0 && <> · {skipped.length} skipped by the planner{plan?.plannedBy === 'gemini' ? ' (AI)' : ''}</>}
 {failed.length > 0 && <span className="text-amber-700"> · {failed.map((f) => `${f.name} ${f.status.toLowerCase()}`).join(', ')}</span>}
 </p>
 );
};
SourceLine.propTypes = { sources: PropTypes.array.isRequired, plan: PropTypes.object };

/** The agent's step-by-step reasoning (collapsed): plan, search, reflect... */
export const AgentTrace = ({ trace }) => (
 <details className="rounded-2xl border border-slate-200 bg-white p-4">
 <summary className="cursor-pointer list-none text-sm font-bold text-slate-700">
 <span className="inline-flex items-center gap-2"><Bot className="h-4 w-4" /> How the agent decided ({trace.length} steps)</span>
 </summary>
 <ol className="mt-3 space-y-2 border-l-2 border-slate-200 pl-4">
 {trace.map((line, i) => {
 const [stage, ...rest] = line.split(':');
 const accent = stage === 'Reflect' ? 'bg-amber-100 text-amber-800' : stage === 'Plan' || stage === 'Clarify' || stage === 'Explain' ? 'bg-indigo-100 text-indigo-800' : 'bg-slate-100 text-slate-700';
 return (
 <li key={`${i}-${line}`} className="text-xs text-slate-600">
 <span className={`mr-1.5 rounded px-1.5 py-0.5 font-bold ${accent}`}>{stage}</span>
 {rest.join(':').trim()}
 </li>
 );
 })}
 </ol>
 </details>
);
AgentTrace.propTypes = { trace: PropTypes.array.isRequired };
