import PropTypes from 'prop-types';
import { trustStyle } from '../utils/aggregatorUtils.js';

/** Compact circular 0-100 gauge, colour-coded by trust level (label text too, never colour alone). */
const TrustBadge = ({ score, level, size = 56 }) => {
 const style = trustStyle(level);
 const stroke = 5;
 const r = (size - stroke) / 2;
 const c = 2 * Math.PI * r;
 const pct = Math.max(0, Math.min(100, score));
 return (
 <div className="flex flex-col items-center gap-1" role="img" aria-label={`Trust score ${score} out of 100, ${style.label}`}>
 <div className="relative" style={{ width: size, height: size }}>
 <svg width={size} height={size} className="-rotate-90">
 <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} className="stroke-slate-100" />
 <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} strokeLinecap="round"
 strokeDasharray={c} strokeDashoffset={c - (pct / 100) * c} className={`stroke-current ${style.ring}`} />
 </svg>
 <span className={`absolute inset-0 flex items-center justify-center text-base font-extrabold ${style.ring}`}>{score}</span>
 </div>
 <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ring-1 ${style.chip}`}>{style.label}</span>
 </div>
 );
};

TrustBadge.propTypes = { score: PropTypes.number.isRequired, level: PropTypes.string.isRequired, size: PropTypes.number };

export default TrustBadge;
