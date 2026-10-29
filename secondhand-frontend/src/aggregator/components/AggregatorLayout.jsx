import { Link, Outlet } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext.jsx';
import { ROUTES } from '../../common/constants/routes.js';

/** Deliberately minimal chrome: brand, one action, sign-in. The marketplace keeps its own full header. */
const AggregatorLayout = () => {
 const { authState } = useAuth();
 const signedIn = Boolean(authState?.isAuthenticated);
 return (
 <div className="flex min-h-screen flex-col bg-slate-50">
 <header className="border-b border-slate-200 bg-white">
 <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-5 sm:px-8">
 <Link to={ROUTES.HOME} className="flex items-center gap-2 font-extrabold tracking-tight text-slate-900">
 <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-900 text-white">
 <ShieldCheck className="h-4 w-4" />
 </span>
 Trust Aggregator
 </Link>
 <nav className="flex items-center gap-1 text-sm font-semibold" aria-label="Primary">
 <Link to={ROUTES.SHARE_LISTING} className="rounded-lg px-3 py-1.5 text-slate-700 hover:bg-slate-100">Share a listing</Link>
 {signedIn ? (
 <Link to={ROUTES.PROFILE} className="rounded-lg px-3 py-1.5 text-slate-700 hover:bg-slate-100">Account</Link>
 ) : (
 <Link to={ROUTES.LOGIN} className="rounded-lg px-3 py-1.5 text-slate-700 hover:bg-slate-100">Sign in</Link>
 )}
 </nav>
 </div>
 </header>
 <main className="flex-1">
 <Outlet />
 </main>
 <footer className="border-t border-slate-200 bg-white py-4 text-center text-xs text-slate-400">
 Trust scores are guidance, not a guarantee. Never pay in advance to someone you have not met.
 </footer>
 </div>
 );
};

export default AggregatorLayout;
