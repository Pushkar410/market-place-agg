import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { AlertTriangle, BookOpen, Check, ChevronDown, Clock, ExternalLink, Layers, MapPin, ShieldCheck } from 'lucide-react';
import TrustBadge from './TrustBadge.jsx';
import TrustBreakdown from './TrustBreakdown.jsx';
import { classifyListingUrl, formatPosted, formatPrice, humanize } from '../utils/aggregatorUtils.js';

const ListingImage = ({ src, alt }) => {
  const [failed, setFailed] = useState(false);
  if (!src || failed) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-slate-100 to-slate-200 text-slate-400">
        <Layers className="h-8 w-8" />
      </div>
    );
  }
  return <img src={src} alt={alt} loading="lazy" onError={() => setFailed(true)} className="h-full w-full object-cover" />;
};
ListingImage.propTypes = { src: PropTypes.string, alt: PropTypes.string };

const ListingResultCard = ({ ranked, simulated }) => {
  const [open, setOpen] = useState(false);
  const { listing, trust, reasons, alsoListedOn } = ranked;
  const link = classifyListingUrl(listing.url, simulated);
  const seller = listing.seller;

  const isMap =
    listing.sourceId === 'verified-places' ||
    listing.category === 'HOUSING' ||
    listing.url?.includes('google.com/maps') ||
    listing.url?.includes('maps.google.com') ||
    listing.url?.includes('openstreetmap.org');

  const isOpenLibrary = listing.sourceId === 'open-library' || listing.url?.includes('openlibrary.org');

  const externalLabel = isMap
    ? 'View on Google Maps'
    : isOpenLibrary
    ? 'View on Open Library'
    : listing.sourceId === 'certified-market' || listing.sourceId === 'live-products'
    ? 'View verified product'
    : 'View original post';

  const cta =
    link.kind === 'internal' ? (
      <Link to={link.to} className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-3.5 py-2 text-xs font-bold text-white hover:bg-slate-800">
        Open listing
      </Link>
    ) : link.kind === 'external' ? (
      <a
        href={link.href}
        target="_blank"
        rel="noopener noreferrer"
        className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-3.5 py-2 text-xs font-bold text-white hover:bg-slate-800 transition-colors shadow-sm"
      >
        {isMap ? (
          <MapPin className="h-3.5 w-3.5 text-emerald-400" />
        ) : isOpenLibrary ? (
          <BookOpen className="h-3.5 w-3.5 text-sky-400" />
        ) : (
          <ExternalLink className="h-3.5 w-3.5" />
        )}
        {externalLabel}
      </a>
    ) : (
      <span className="rounded-xl bg-slate-100 px-3 py-2 text-[11px] font-semibold text-slate-500">{link.label}</span>
    );

  return (
    <article
      className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md sm:p-5"
      data-testid="result-card"
    >
      <div className="flex flex-col gap-4 sm:flex-row">
        <div className="relative h-32 w-full shrink-0 overflow-hidden rounded-xl sm:h-24 sm:w-32">
          <ListingImage src={listing.imageUrls?.[0]} alt={listing.title} />
          <span className="absolute left-2 top-2 flex h-7 w-7 items-center justify-center rounded-full bg-slate-900 text-xs font-extrabold text-white shadow">
            {ranked.rank}
          </span>
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5 text-[11px] font-semibold">
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">{listing.sourceName}</span>
            {!simulated && (
              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-emerald-700 ring-1 ring-emerald-600/20 font-bold">
                <ShieldCheck className="h-3 w-3 text-emerald-600" /> Real-World Verified
              </span>
            )}
            {listing.condition && <span className="rounded-full bg-sky-50 px-2 py-0.5 text-sky-700">{humanize(listing.condition)}</span>}
            {alsoListedOn.length > 0 && (
              <span className="inline-flex items-center gap-1 rounded-full bg-violet-50 px-2 py-0.5 text-violet-700" title={alsoListedOn.join(', ')}>
                <Layers className="h-3 w-3" /> Also on {alsoListedOn.length} more
              </span>
            )}
          </div>

          <h3 className="mt-2 text-base font-bold leading-snug text-slate-900 sm:text-lg">{listing.title}</h3>

          <div className="mt-1 flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <span className="text-xl font-extrabold text-slate-900">{formatPrice(listing.price, listing.currency)}</span>
            {listing.originalPrice && <span className="text-xs text-slate-400">listed as {listing.originalPrice}</span>}
          </div>

          <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
            {listing.city && (
              <span className="inline-flex items-center gap-1"><MapPin className="h-3.5 w-3.5" />{listing.city}</span>
            )}
            <span className="inline-flex items-center gap-1"><Clock className="h-3.5 w-3.5" />{formatPosted(listing.postedDaysAgo)}</span>
            {seller && (
              <span className="inline-flex items-center gap-1">
                {seller.verified && <ShieldCheck className="h-3.5 w-3.5 text-emerald-600" />}
                {seller.name}
                {seller.rating ? ` · ${Number(seller.rating).toFixed(1)}★ (${seller.reviewCount})` : ' · no reviews yet'}
              </span>
            )}
          </div>
        </div>

        <div className="flex shrink-0 items-center justify-between gap-4 sm:flex-col sm:justify-start">
          <TrustBadge score={trust.score} level={trust.level} />
        </div>
      </div>

      {(reasons.length > 0 || trust.riskFlags.length > 0) && (
        <div className={`mt-4 grid gap-3 ${reasons.length > 0 && trust.riskFlags.length > 0 ? 'md:grid-cols-2' : ''}`}>
          {reasons.length > 0 && (
            <ul className="space-y-1.5 rounded-2xl bg-emerald-50/60 p-3 text-xs text-slate-700">
              {reasons.slice(0, 2).map((r) => (
                <li key={r} className="flex gap-2"><Check className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-600" />{r}</li>
              ))}
            </ul>
          )}
          {trust.riskFlags.length > 0 && (
            <ul className="space-y-1.5 rounded-2xl bg-rose-50/70 p-3 text-xs text-slate-700" aria-label="Risk flags">
              {trust.riskFlags.map((f) => (
                <li key={f} className="flex gap-2"><AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-rose-600" />{f}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          className="inline-flex items-center gap-1 text-xs font-bold text-slate-600 hover:text-slate-900"
        >
          Why this score? <ChevronDown className={`h-4 w-4 transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
        {cta}
      </div>

      {open && <TrustBreakdown ranked={ranked} />}
    </article>
  );
};

ListingResultCard.propTypes = {
  ranked: PropTypes.object.isRequired,
  simulated: PropTypes.bool,
};

export default ListingResultCard;
