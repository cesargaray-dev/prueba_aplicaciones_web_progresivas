import React, { useState, useEffect } from 'react';
import {
  Landmark,
  LogOut,
  History,
  LayoutGrid,
  RefreshCw,
  Clock,
  Wallet,
  UserCog,
  ShieldCheck,
  ShieldAlert,
  X,
  Moon,
  Sun,
} from 'lucide-react';
import { useAuth } from '../context/useAuth';
import { ProfileModal } from './ProfileModal';
import { esCorreoPlaceholder } from '../utils/validation';

interface NavbarProps {
  activeTab: 'catalog' | 'history' | 'admin';
  setActiveTab: (tab: 'catalog' | 'history' | 'admin') => void;
}

export const Navbar: React.FC<NavbarProps> = ({ activeTab, setActiveTab }) => {
  const { user, secondsRemaining, logout, refreshBalance, sessionNotice, clearSessionNotice } =
    useAuth();
  const [refreshing, setRefreshing] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [isDark, setIsDark] = useState<boolean>(() => {
    const saved = window.localStorage.getItem('theme_mode');
    return saved ? saved === 'dark' : document.documentElement.classList.contains('dark');
  });

  useEffect(() => {
    document.documentElement.classList.toggle('dark', isDark);
    window.localStorage.setItem('theme_mode', isDark ? 'dark' : 'light');
  }, [isDark]);

  const handleRefresh = async () => {
    setRefreshing(true);
    await refreshBalance();
    setTimeout(() => setRefreshing(false), 500);
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('es-MX', {
      style: 'currency',
      currency: 'MXN',
    }).format(amount);
  };

  const formatTimer = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const needsRealEmail = esCorreoPlaceholder(user?.email || '');
  const isAdmin = Boolean(user?.esAdmin || user?.usuario?.toLowerCase() === 'admin');

  return (
    <>
      <header className="sticky top-0 z-40 w-full bg-white/90 backdrop-blur-md border-b border-slate-200/80 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16 sm:h-20 gap-3">
            {/* Brand Logo & Title */}
            <button
              type="button"
              onClick={() => setActiveTab('catalog')}
              className="flex items-center gap-3 shrink-0 text-left group focus:outline-none"
              title="Ir al Catálogo de Servicios"
            >
              <div className="w-10 h-10 bg-gradient-to-br from-blue-900 to-blue-700 rounded-xl flex items-center justify-center shadow-md shadow-blue-900/20 text-white group-hover:scale-105 transition-transform">
                <Landmark className="w-6 h-6" />
              </div>
              <div className="hidden sm:block">
                <span className="text-lg font-extrabold text-slate-900 tracking-tight block leading-none group-hover:text-blue-900 transition-colors">
                  Banco Digital
                </span>
                <span className="text-[11px] font-semibold text-blue-700 uppercase tracking-wider">
                  Pagos GestoPago
                </span>
              </div>
            </button>

            {/* Center Navigation Tabs (Desktop) */}
            {isAdmin ? (
              <div className="hidden md:flex items-center gap-2 px-4 py-2 rounded-2xl bg-gradient-to-r from-amber-400/20 via-amber-500/15 to-amber-400/20 border border-amber-400/50 text-amber-900">
                <ShieldCheck className="w-4 h-4 text-amber-500" />
                <span className="text-xs font-black tracking-wide uppercase">
                  Consola de Administración de Usuarios
                </span>
              </div>
            ) : (
              <nav className="hidden md:flex items-center gap-1.5 bg-slate-100 p-1 rounded-2xl border border-slate-200/70">
                <button
                  type="button"
                  onClick={() => setActiveTab('catalog')}
                  className={`px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-2 transition-all ${
                    activeTab === 'catalog'
                      ? 'bg-white text-blue-950 shadow-sm'
                      : 'text-slate-600 hover:text-slate-900 hover:bg-slate-200/50'
                  }`}
                >
                  <LayoutGrid className="w-4 h-4" />
                  <span>Catálogo y Pagos</span>
                </button>

                <button
                  type="button"
                  onClick={() => setActiveTab('history')}
                  className={`px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-2 transition-all ${
                    activeTab === 'history'
                      ? 'bg-white text-blue-950 shadow-sm'
                      : 'text-slate-600 hover:text-slate-900 hover:bg-slate-200/50'
                  }`}
                >
                  <History className="w-4 h-4" />
                  <span>Historial de Pagos</span>
                </button>
              </nav>
            )}

            {/* Right Section: Account, Balance, Profile, Logout */}
            <div className="flex items-center gap-2 sm:gap-3">
              {/* Available Balance Pill (Only for normal customer accounts) */}
              {user && !isAdmin && (
                <div className="hidden sm:flex items-center gap-2.5 bg-gradient-to-r from-slate-950 via-blue-950 to-indigo-950 text-white px-4 py-2 rounded-2xl shadow-[0_8px_20px_-4px_rgba(15,23,42,0.55),0_0_15px_-3px_rgba(37,99,235,0.35)] border border-amber-300/30 ring-1 ring-white/10">
                  <div className="flex items-center gap-2">
                    <div className="p-1.5 rounded-xl bg-gradient-to-br from-amber-300/25 to-amber-500/10 border border-amber-300/40">
                      <Wallet className="w-3.5 h-3.5 text-amber-300" />
                    </div>
                    <div className="text-right">
                      <span className="text-[10px] text-amber-200/90 block leading-none font-bold tracking-wide">
                        Saldo Cuenta {user.numeroCuenta ? `••${user.numeroCuenta.slice(-4)}` : ''}
                      </span>
                      <span className="text-sm font-black tracking-tight text-white drop-shadow-sm">
                        {formatCurrency(user.saldo)}
                      </span>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={handleRefresh}
                    title="Actualizar saldo"
                    className="p-1.5 hover:bg-white/15 rounded-full transition-colors text-blue-200 hover:text-white"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin' : ''}`} />
                  </button>
                </div>
              )}

              {/* Inactivity Timer Badge (visible across mobile, tablet, desktop) */}
              <div
                className={`flex items-center gap-1 sm:gap-1.5 px-2 py-1 sm:px-2.5 sm:py-1.5 rounded-xl border text-[11px] sm:text-xs font-semibold shrink-0 ${
                  secondsRemaining < 60
                    ? 'bg-amber-50 border-amber-200 text-amber-700 animate-pulse'
                    : 'bg-slate-50 border-slate-200 text-slate-500'
                }`}
                title="Tiempo restante de sesión por inactividad"
              >
                <Clock className="w-3 h-3 sm:w-3.5 sm:h-3.5" />
                <span className="font-mono">{formatTimer(secondsRemaining)}</span>
              </div>

              {/* Profile / Fund Account Button */}
              {user && (
                <button
                  type="button"
                  id="nav-profile-btn"
                  onClick={() => setIsProfileOpen(true)}
                  className="relative flex items-center gap-2 pl-2.5 pr-3 py-1.5 rounded-2xl bg-slate-100 hover:bg-blue-50 border border-slate-200 hover:border-blue-300 transition-colors text-left"
                  title="Mi Perfil, Correo 2FA y Abonos"
                >
                  <UserCog className="w-4 h-4 text-blue-900 shrink-0" />
                  <div className="hidden xl:block">
                    <p className="text-xs font-bold text-slate-900 leading-tight max-w-[130px] truncate">
                      {user.nombreCompleto}
                    </p>
                    <p className="text-[10px] text-slate-500 leading-tight">
                      Perfil / Abonar
                    </p>
                  </div>
                  <span className="xl:hidden text-xs font-bold text-slate-800">Perfil</span>
                  {needsRealEmail && (
                    <span
                      className="w-2.5 h-2.5 rounded-full bg-amber-500 border-2 border-white shrink-0"
                      title="Actualiza tu correo real para recibir códigos 2FA y comprobantes"
                    />
                  )}
                </button>
              )}

              {/* Theme Mode Toggle (Light / Dark) */}
              <button
                type="button"
                onClick={() => setIsDark((prev) => !prev)}
                title={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
                aria-label="Cambiar tema"
                className="p-2 rounded-xl bg-slate-100 hover:bg-blue-50 border border-slate-200 text-slate-700 transition-colors"
              >
                {isDark ? (
                  <Sun className="w-4 h-4 text-amber-400" />
                ) : (
                  <Moon className="w-4 h-4 text-blue-900" />
                )}
              </button>

              {/* Logout Button */}
              <button
                type="button"
                onClick={() => logout()}
                className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-red-50 hover:bg-red-100 text-red-700 text-xs font-bold border border-red-200/80 transition-colors"
                title="Cerrar sesión"
              >
                <LogOut className="w-4 h-4 text-red-600" />
                <span className="hidden sm:inline">Salir</span>
              </button>
            </div>
          </div>

          {/* Mobile Bottom Navigation (Only for normal customer accounts) */}
          {!isAdmin && (
            <div className="flex md:hidden border-t border-slate-100 py-2 items-center justify-between gap-2">
              <div className="flex items-center gap-1 flex-1">
                <button
                  type="button"
                  onClick={() => setActiveTab('catalog')}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-bold flex items-center justify-center gap-1.5 ${
                    activeTab === 'catalog'
                      ? 'bg-blue-900 text-white'
                      : 'text-slate-600 bg-slate-100'
                  }`}
                >
                  <LayoutGrid className="w-3.5 h-3.5" />
                  <span>Catálogo</span>
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab('history')}
                  className={`flex-1 py-1.5 rounded-lg text-xs font-bold flex items-center justify-center gap-1.5 ${
                    activeTab === 'history'
                      ? 'bg-blue-900 text-white'
                      : 'text-slate-600 bg-slate-100'
                  }`}
                >
                  <History className="w-3.5 h-3.5" />
                  <span>Historial</span>
                </button>
              </div>
              {user && (
                <button
                  type="button"
                  onClick={() => setIsProfileOpen(true)}
                  title="Ver perfil y abonar saldo"
                  className="px-2.5 py-1.5 bg-blue-950 hover:bg-blue-900 text-white rounded-lg text-xs font-bold flex items-center gap-1.5 transition-colors"
                >
                  <Wallet className="w-3.5 h-3.5 text-blue-300" />
                  <span>{formatCurrency(user.saldo)}</span>
                </button>
              )}
            </div>
          )}
        </div>
      </header>

      {sessionNotice && (
        <div
          role="status"
          className="bg-amber-50 border-b border-amber-200 text-amber-900 px-4 py-2.5 text-xs font-semibold"
        >
          <div className="max-w-7xl mx-auto flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <ShieldAlert className="w-4 h-4 text-amber-600 shrink-0" />
              <span>{sessionNotice}</span>
            </div>
            <button
              type="button"
              onClick={clearSessionNotice}
              aria-label="Cerrar aviso de seguridad"
              className="p-1 rounded-lg hover:bg-amber-100 text-amber-700 transition-colors shrink-0"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      )}

      <ProfileModal isOpen={isProfileOpen} onClose={() => setIsProfileOpen(false)} />
    </>
  );
};
