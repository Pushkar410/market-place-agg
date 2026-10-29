import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import { useShareListing } from '../hooks/useAggregator.js';
import { ROUTES } from '../../common/constants/routes.js';

const CATEGORIES = ['', 'ELECTRONICS', 'BOOKS', 'FURNITURE', 'VEHICLES', 'HOUSING', 'SPORTS', 'CLOTHING', 'SERVICES', 'OTHER'];
const EMPTY = { title: '', description: '', price: '', city: '', category: '', originName: '', originUrl: '', contactName: '', contactProvided: true };

const submitError = (error) => {
 if (error?.response?.status === 429) return error.response.data?.message || 'Too many listings were shared recently. Please try again later.';
 if (!error?.response) return 'Cannot reach the backend. Make sure it is running on http://localhost:8080.';
 const detail = error.response?.data?.message || error.response?.data?.detail;
 return detail || 'Please check the form: a title of at least 5 characters is required, and the link must start with http:// or https://.';
};

const field = 'mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-900/10';

const ShareListingPage = () => {
 const [form, setForm] = useState(EMPTY);
 const share = useShareListing();
 const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));

 const onSubmit = (e) => {
 e.preventDefault();
 const price = form.price === '' ? null : Number(form.price);
 share.mutate({
 title: form.title.trim(),
 description: form.description.trim() || null,
 price: Number.isFinite(price) && price >= 0 ? price : null,
 city: form.city.trim() || null,
 category: form.category || null,
 originName: form.originName.trim() || null,
 originUrl: form.originUrl.trim(),
 contactName: form.contactName.trim() || null,
 contactProvided: form.contactProvided,
 });
 };

 if (share.isSuccess) {
 return (
 <div className="mx-auto max-w-xl px-5 py-14 text-center">
 <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-600" />
 <h1 className="mt-4 text-2xl font-extrabold text-slate-900">Listing shared</h1>
 <p className="mt-2 text-sm text-slate-600">{share.data?.message || 'It is now searchable.'}</p>
 <div className="mt-6 flex justify-center gap-3">
 <Link to={ROUTES.HOME} className="rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-bold text-white hover:bg-slate-800">Back to search</Link>
 <button type="button" onClick={() => { share.reset(); setForm(EMPTY); }} className="rounded-xl border border-slate-300 px-5 py-2.5 text-sm font-bold text-slate-700 hover:bg-slate-50">
 Share another
 </button>
 </div>
 </div>
 );
 }

 return (
 <div className="mx-auto max-w-xl px-5 py-10">
 <h1 className="text-2xl font-extrabold text-slate-900">Share a listing you found elsewhere</h1>
 <p className="mt-2 text-sm text-slate-600">
 Saw something on OLX, a WhatsApp group or a notice board? Add it here and the agent will score it for trust and rank it
 next to everything else. Please share only listings you are allowed to share, and never put phone numbers in the text.
 </p>

 <form onSubmit={onSubmit} className="mt-6 space-y-4">
 <label className="block text-sm font-semibold text-slate-700">Title *
 <input className={field} value={form.title} onChange={set('title')} required minLength={5} maxLength={120} placeholder="e.g. Samsung 24 inch monitor, 1 year old" />
 </label>
 <label className="block text-sm font-semibold text-slate-700">Description
 <textarea className={field} rows={4} value={form.description} onChange={set('description')} maxLength={1000} placeholder="Condition, what is included, how the seller can be met..." />
 </label>
 <div className="grid gap-4 sm:grid-cols-3">
 <label className="block text-sm font-semibold text-slate-700">Price (₹)
 <input className={field} type="number" min="0" step="1" value={form.price} onChange={set('price')} />
 </label>
 <label className="block text-sm font-semibold text-slate-700">City
 <input className={field} value={form.city} onChange={set('city')} maxLength={60} placeholder="Tumkur" />
 </label>
 <label className="block text-sm font-semibold text-slate-700">Category
 <select className={field} value={form.category} onChange={set('category')}>
 {CATEGORIES.map((c) => <option key={c} value={c}>{c ? c.charAt(0) + c.slice(1).toLowerCase() : 'Detect automatically'}</option>)}
 </select>
 </label>
 </div>
 <div className="grid gap-4 sm:grid-cols-2">
 <label className="block text-sm font-semibold text-slate-700">Where did you find it?
 <input className={field} value={form.originName} onChange={set('originName')} maxLength={60} placeholder="OLX, college WhatsApp group..." />
 </label>
 <label className="block text-sm font-semibold text-slate-700">Link to the original post
 <input className={field} type="url" value={form.originUrl} onChange={set('originUrl')} maxLength={300} placeholder="https://..." />
 </label>
 </div>
 <div className="grid gap-4 sm:grid-cols-2">
 <label className="block text-sm font-semibold text-slate-700">Seller name
 <input className={field} value={form.contactName} onChange={set('contactName')} maxLength={60} />
 </label>
 <label className="flex items-end gap-2 pb-2 text-sm font-semibold text-slate-700">
 <input type="checkbox" checked={form.contactProvided} onChange={set('contactProvided')} /> Seller can be contacted
 </label>
 </div>

 {share.isError && <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{submitError(share.error)}</div>}

 <button type="submit" disabled={share.isPending || form.title.trim().length < 5}
 className="rounded-xl bg-slate-900 px-6 py-2.5 text-sm font-bold text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50">
 {share.isPending ? 'Sharing…' : 'Share listing'}
 </button>
 </form>
 </div>
 );
};

export default ShareListingPage;
