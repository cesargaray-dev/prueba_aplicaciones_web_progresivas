import React, { useState } from 'react';
import { AuthProvider } from './context/AuthContext';
import { useAuth } from './context/useAuth';
import { Navbar } from './components/Navbar';
import { LoginPage } from './pages/LoginPage';
import { CatalogPage } from './pages/CatalogPage';
import { HistoryPage } from './pages/HistoryPage';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { Landmark, Loader2, ShieldCheck } from 'lucide-react';

const CURRENT_YEAR = new Date().getFullYear();

const MainLayout: React.FC = () => {
  const { isAuthenticated, isInitializing, user } = useAuth();
  const [activeTab, setActiveTab] = useState<'catalog' | 'history' | 'admin'>('catalog');
  const isAdmin = Boolean(user?.esAdmin || user?.usuario?.toLowerCase() === 'admin');

  if (isInitializing) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 flex flex-col items-center justify-center p-6 text-white">
        <div className="p-4 bg-white/10 backdrop-blur-md rounded-2xl border border-white/20 shadow-xl mb-4">
          <Landmark className="w-9 h-9 text-blue-300" />
        </div>
        <div className="flex items-center gap-2.5 text-sm font-semibold text-blue-100">
          <Loader2 className="w-4 h-4 animate-spin text-blue-300" />
          <span>Verificando sesión segura...</span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <LoginPage />;
  }

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col justify-between">
      <div>
        <Navbar activeTab={isAdmin ? 'admin' : activeTab} setActiveTab={setActiveTab} />
        <main>
          {isAdmin ? (
            <AdminUsersPage />
          ) : activeTab === 'catalog' ? (
            <CatalogPage onGoToHistory={() => setActiveTab('history')} />
          ) : (
            <HistoryPage onGoToCatalog={() => setActiveTab('catalog')} />
          )}
        </main>
      </div>

      {/* Footer */}
      <footer className="mt-16 bg-white border-t border-slate-200 py-6 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-slate-700 font-semibold">
            <ShieldCheck className="w-4 h-4 text-emerald-600" />
            <span>Banco Digital S.A. &bull; Pagos y Recargas Certificados GestoPago</span>
          </div>
          <p className="text-[11px] text-slate-400">
            &copy; {CURRENT_YEAR} Banco Digital &bull; Verificación 2FA, validación IFT y comprobantes oficiales inmediatos
          </p>
        </div>
      </footer>
    </div>
  );
};

export function App() {
  return (
    <AuthProvider>
      <MainLayout />
    </AuthProvider>
  );
}

export default App;
