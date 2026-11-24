import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { catalogApi } from '../api/catalogApi';
import type { ProductDTO } from '../types/catalog';
import type { ValidarReferenciaResponse, PagoServicioResponse } from '../types/payment';
import {
  esConsultaSaldo,
  esRecargaTelefonica,
  getProductCategoryName,
  normalizarBusquedaCatalogo,
} from '../utils/validation';
import { ServiceCard } from '../components/ServiceCard';
import { CategoryChips } from '../components/CategoryChips';
import type { CategoryFilterType } from '../components/CategoryChips';
import { ReferenceModal } from '../components/ReferenceModal';
import { Payment2FAModal } from '../components/Payment2FAModal';
import { ReceiptModal } from '../components/ReceiptModal';
import { ProfileModal } from '../components/ProfileModal';
import { useAuth } from '../context/useAuth';
import {
  Search,
  AlertCircle,
  RefreshCw,
  Sparkles,
  ChevronDown,
  Flame,
  Smartphone,
  ReceiptText,
  Layers,
  Landmark,
  Wifi,
  PlusCircle,
  ShieldCheck,
  Copy,
  Check,
} from 'lucide-react';

const PAGE_SIZE = 24;
const POPULAR_PROVIDERS = ['Telcel', 'AT&T', 'Movistar', 'CFE', 'IZZI', 'Telmex', 'PASE', 'Amazon'];

export type FrontTypeFilter = 'ALL' | '1' | '2';

interface CatalogPageProps {
  onGoToHistory?: () => void;
}

const matchesCategory = (p: ProductDTO, cat: CategoryFilterType): boolean => {
  if (cat === 'ALL') return true;

  const s = (p.service || '').toUpperCase();
  const name = (p.name || '').toUpperCase();
  const catId = p.categoryId;

  switch (cat) {
    case 'RECARGAS':
      return esRecargaTelefonica(p);
    case 'CFE':
      return catId === '5' || s.includes('CFE') || s.includes('LUZ') || name.includes('CFE');
    case 'AGUA':
      return (
        catId === '15' ||
        s.includes('AGUA') ||
        s.includes('SAPA') ||
        s.includes('SIAPA') ||
        s.includes('CEA') ||
        s.includes('JAPAY') ||
        s.includes('COMAPA')
      );
    case 'INTERNET':
      return (
        catId === '4' ||
        s.includes('IZZI') ||
        s.includes('TOTALPLAY') ||
        s.includes('TELMEX') ||
        s.includes('SKY') ||
        s.includes('DISH') ||
        s.includes('MEGACABLE')
      );
    case 'TAG':
      return (
        catId === '14' ||
        catId === '18' ||
        s.includes('TAG') ||
        s.includes('PASE') ||
        s.includes('TELEVIA') ||
        s.includes('EASYTRIP')
      );
    case 'FINANZAS':
      return (
        catId === '8' ||
        s.includes('CREDITO') ||
        s.includes('BANCO') ||
        s.includes('FINAN') ||
        s.includes('AFORE') ||
        s.includes('INFONAVIT')
      );
    case 'ENTRETENIMIENTO':
      return (
        catId === '10' ||
        catId === '11' ||
        catId === '16' ||
        s.includes('AMAZON') ||
        s.includes('SPOTIFY') ||
        s.includes('NETFLIX') ||
        s.includes('PLAYSTATION') ||
        s.includes('XBOX') ||
        s.includes('YOUTUBE') ||
        s.includes('KATAPLUM') ||
        s.includes('ACUARIO') ||
        s.includes('KINGSBET')
      );
    default:
      return true;
  }
};

export const CatalogPage: React.FC<CatalogPageProps> = ({ onGoToHistory }) => {
  const { user, refreshBalance } = useAuth();
  const [isRefreshingBalance, setIsRefreshingBalance] = useState(false);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const [copiedCardNumber, setCopiedCardNumber] = useState(false);

  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedFrontType, setSelectedFrontType] = useState<FrontTypeFilter>('ALL');
  const [selectedCategory, setSelectedCategory] = useState<CategoryFilterType>('ALL');
  const [visibleCount, setVisibleCount] = useState<number>(PAGE_SIZE);

  // Payment flow modal states
  const [productForPayment, setProductForPayment] = useState<ProductDTO | null>(null);
  const [isReferenceModalOpen, setIsReferenceModalOpen] = useState(false);

  const [paymentData, setPaymentData] = useState<{
    product: ProductDTO;
    referencia: string;
    monto: number;
    validation: ValidarReferenciaResponse;
  } | null>(null);
  const [is2FAModalOpen, setIs2FAModalOpen] = useState(false);

  const [receiptData, setReceiptData] = useState<{
    receipt: PagoServicioResponse;
    product: ProductDTO;
    referencia: string;
  } | null>(null);
  const [isReceiptModalOpen, setIsReceiptModalOpen] = useState(false);

  const fetchCatalog = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await catalogApi.getProductos();
      setProducts(data.filter((p) => !esConsultaSaldo(p)));
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al consultar catálogo de GestoPago';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Initial load without synchronous setState in effect body
  useEffect(() => {
    let cancelled = false;
    catalogApi
      .getProductos()
      .then((data) => {
        if (!cancelled) {
          setProducts(data.filter((p) => !esConsultaSaldo(p)));
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          const msg =
            err instanceof Error ? err.message : 'Error al consultar catálogo de GestoPago';
          setError(msg);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const frontTypeFilteredProducts = useMemo(() => {
    if (selectedFrontType === 'ALL') return products;
    if (selectedFrontType === '1') {
      return products.filter((p) => esRecargaTelefonica(p));
    }
    return products.filter((p) => !esRecargaTelefonica(p));
  }, [products, selectedFrontType]);

  const filteredProducts = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    const qNorm = normalizarBusquedaCatalogo(searchQuery);
    return frontTypeFilteredProducts.filter((p) => {
      if (!matchesCategory(p, selectedCategory)) return false;
      if (!q) return true;
      const categoryLabel = getProductCategoryName(p).toLowerCase();
      if (
        (p.name && p.name.toLowerCase().includes(q)) ||
        (p.service && p.service.toLowerCase().includes(q)) ||
        (p.legend && p.legend.toLowerCase().includes(q)) ||
        categoryLabel.includes(q) ||
        (p.serviceId && p.serviceId.includes(q)) ||
        (p.id && p.id.includes(q))
      ) {
        return true;
      }
      if (!qNorm) return false;
      return (
        normalizarBusquedaCatalogo(p.service || '').includes(qNorm) ||
        normalizarBusquedaCatalogo(p.name || '').includes(qNorm) ||
        normalizarBusquedaCatalogo(categoryLabel).includes(qNorm)
      );
    });
  }, [frontTypeFilteredProducts, searchQuery, selectedCategory]);

  const visibleProducts = useMemo(
    () => filteredProducts.slice(0, visibleCount),
    [filteredProducts, visibleCount]
  );

  const categoryCounts = useMemo(() => {
    const counts: Partial<Record<CategoryFilterType, number>> = {
      ALL: frontTypeFilteredProducts.length,
      RECARGAS: frontTypeFilteredProducts.filter((p) => matchesCategory(p, 'RECARGAS')).length,
      CFE: frontTypeFilteredProducts.filter((p) => matchesCategory(p, 'CFE')).length,
      AGUA: frontTypeFilteredProducts.filter((p) => matchesCategory(p, 'AGUA')).length,
      INTERNET: frontTypeFilteredProducts.filter((p) => matchesCategory(p, 'INTERNET')).length,
      TAG: frontTypeFilteredProducts.filter((p) => matchesCategory(p, 'TAG')).length,
      FINANZAS: frontTypeFilteredProducts.filter((p) => matchesCategory(p, 'FINANZAS')).length,
      ENTRETENIMIENTO: frontTypeFilteredProducts.filter((p) =>
        matchesCategory(p, 'ENTRETENIMIENTO')
      ).length,
    };
    return counts;
  }, [frontTypeFilteredProducts]);

  const handleSelectFrontType = (nextFront: FrontTypeFilter) => {
    setSelectedFrontType(nextFront);
    setSelectedCategory('ALL');
    setVisibleCount(PAGE_SIZE);
  };

  const handleSelectCategory = (cat: CategoryFilterType) => {
    setSelectedCategory(cat);
    setVisibleCount(PAGE_SIZE);
  };

  const handleSearchChange = (value: string) => {
    setSearchQuery(value);
    setVisibleCount(PAGE_SIZE);
  };

  const handleResetFilters = () => {
    setSearchQuery('');
    setSelectedFrontType('ALL');
    setSelectedCategory('ALL');
    setVisibleCount(PAGE_SIZE);
  };

  const handleSelectProduct = (product: ProductDTO) => {
    setProductForPayment(product);
    setIsReferenceModalOpen(true);
  };

  const handleProceedTo2FA = (data: {
    product: ProductDTO;
    referencia: string;
    monto: number;
    validation: ValidarReferenciaResponse;
  }) => {
    setPaymentData(data);
    setIsReferenceModalOpen(false);
    setIs2FAModalOpen(true);
  };

  const handlePaymentSuccess = (receipt: PagoServicioResponse) => {
    if (paymentData) {
      setReceiptData({
        receipt,
        product: paymentData.product,
        referencia: paymentData.referencia,
      });
    }
    setIs2FAModalOpen(false);
    setIsReceiptModalOpen(true);
  };

  const remainingCount = Math.max(0, filteredProducts.length - visibleProducts.length);
  const hasActiveFilters =
    searchQuery.trim() !== '' || selectedCategory !== 'ALL' || selectedFrontType !== 'ALL';

  const maskedAccount = user?.numeroCuenta
    ? `**** **** **** ${user.numeroCuenta.slice(-4)}`
    : '**** **** **** 0000';

  const formattedBalance = new Intl.NumberFormat('es-MX', {
    style: 'currency',
    currency: 'MXN',
  }).format(user?.saldo ?? 0);

  const handleCardRefreshBalance = async () => {
    setIsRefreshingBalance(true);
    try {
      await refreshBalance();
    } finally {
      setTimeout(() => setIsRefreshingBalance(false), 450);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Top Hero Section: Luxury Banking Card + Catalog Search Banner */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        {/* Super-Premium 3D Digital Banking Card */}
        {user && (
          <div className="lg:col-span-5 relative group">
            {/* Ambient multi-layer 3D outer glow */}
            <div className="absolute -inset-0.5 bg-gradient-to-r from-blue-600/40 via-amber-400/30 to-indigo-600/40 rounded-[28px] blur-xl opacity-75 group-hover:opacity-100 transition duration-500 pointer-events-none" />

            <div className="relative h-full overflow-hidden rounded-[26px] bg-gradient-to-br from-[#040914] via-[#0C1E4A] to-[#0A1128] p-6 text-white shadow-[0_24px_48px_-12px_rgba(4,9,20,0.82),0_10px_24px_-6px_rgba(29,78,216,0.42)] border border-white/25 flex flex-col justify-between transition-transform duration-300 group-hover:-translate-y-0.5">
              {/* Ambient radial light orbs & glass reflection */}
              <div className="pointer-events-none absolute -top-16 -right-12 w-52 h-52 rounded-full bg-blue-500/25 blur-2xl" />
              <div className="pointer-events-none absolute -bottom-20 -left-12 w-48 h-48 rounded-full bg-amber-500/20 blur-2xl" />
              <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_rgba(255,255,255,0.12),_transparent_60%)]" />
              <div className="pointer-events-none absolute -top-24 left-20 w-28 h-80 -rotate-45 bg-gradient-to-r from-transparent via-white/10 to-transparent" />

              {/* Card Header: Brand Emblem + Platinum 2FA + Contactless */}
              <div className="relative z-10 flex items-center justify-between gap-3">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="p-2 rounded-xl bg-gradient-to-br from-white/25 to-white/5 border border-amber-200/45 shadow-md shrink-0">
                    <Landmark className="w-4 h-4 text-amber-200" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs font-black tracking-[0.16em] text-white uppercase truncate">
                      BANCO DIGITAL
                    </p>
                    <div className="flex items-center gap-1.5 mt-0.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-amber-400 shadow-[0_0_8px_#fbbf24]" />
                      <span className="text-[9px] font-extrabold tracking-[0.14em] text-amber-200 uppercase">
                        PLATINUM • 2FA VERIFIED
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-black/35 border border-white/15 text-[10px] font-bold text-emerald-300">
                    <ShieldCheck className="w-3 h-3 text-emerald-400" />
                    Activa
                  </span>
                  <Wifi className="w-5 h-5 text-white/75 rotate-90" />
                </div>
              </div>

              {/* Card Middle: Metallic Gold EMV Chip + Balance & Quick Actions */}
              <div className="relative z-10 my-5 flex items-center justify-between gap-3">
                <div className="flex items-center gap-3.5 min-w-0">
                  {/* Metallic Gold EMV Chip */}
                  <div className="w-11 h-8 rounded-lg bg-gradient-to-br from-yellow-200 via-amber-400 to-amber-700 border border-yellow-100/80 shadow-[0_4px_10px_rgba(0,0,0,0.45)] relative flex items-center justify-center shrink-0">
                    <div className="w-5 h-4 rounded-[3px] border border-amber-950/45" />
                    <div className="absolute left-0 w-2.5 h-[1px] bg-amber-950/45" />
                    <div className="absolute right-0 w-2.5 h-[1px] bg-amber-950/45" />
                  </div>

                  <div className="min-w-0">
                    <div className="flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_8px_#34d399]" />
                      <span className="text-[10px] font-extrabold uppercase tracking-[0.14em] text-blue-100/80">
                        SALDO DISPONIBLE
                      </span>
                    </div>
                    <p className="text-2xl sm:text-[28px] font-black tracking-tight text-white drop-shadow-[0_3px_10px_rgba(0,0,0,0.5)] leading-tight truncate">
                      {formattedBalance} <span className="text-xs font-extrabold text-amber-200/90">MXN</span>
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    type="button"
                    onClick={() => setIsProfileModalOpen(true)}
                    className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-white/15 hover:bg-white/25 border border-amber-200/50 text-white text-xs font-extrabold shadow-md transition-all active:scale-95"
                    title="Abonar fondos a tu cuenta"
                  >
                    <PlusCircle className="w-3.5 h-3.5 text-amber-200" />
                    <span>Abonar</span>
                  </button>
                  <button
                    type="button"
                    onClick={handleCardRefreshBalance}
                    aria-label="Sincronizar saldo de tarjeta"
                    title="Sincronizar saldo"
                    className="p-2 rounded-xl bg-white/10 hover:bg-white/20 border border-white/15 text-blue-100 hover:text-white transition-colors"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${isRefreshingBalance ? 'animate-spin' : ''}`} />
                  </button>
                </div>
              </div>

              {/* Metallic Gradient Divider */}
              <div className="relative z-10 h-px w-full bg-gradient-to-r from-white/5 via-amber-200/40 to-white/5" />

              {/* Card Footer: Cardholder Name, Embossed Account Number & Network Emblem */}
              <div className="relative z-10 pt-3.5 flex items-end justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-[9px] font-extrabold uppercase tracking-[0.16em] text-blue-200/65">
                    TITULAR DE LA CUENTA
                  </p>
                  <p className="text-sm font-extrabold uppercase tracking-wider text-white truncate drop-shadow-sm mt-0.5">
                    {user.nombreCompleto}
                  </p>
                  <div className="flex items-center gap-2 mt-1">
                    <p className="text-xs sm:text-[13px] font-mono font-bold tracking-[0.22em] text-amber-200">
                      {maskedAccount}
                    </p>
                    {user.numeroCuenta && (
                      <button
                        type="button"
                        onClick={() => {
                          void navigator.clipboard?.writeText(user.numeroCuenta).then(() => {
                            setCopiedCardNumber(true);
                            setTimeout(() => setCopiedCardNumber(false), 1800);
                          });
                        }}
                        title="Copiar número de cuenta completo"
                        aria-label="Copiar número de cuenta"
                        className="p-1 rounded-lg bg-white/10 hover:bg-white/20 text-amber-200 transition-colors"
                      >
                        {copiedCardNumber ? (
                          <Check className="w-3 h-3 text-emerald-300" />
                        ) : (
                          <Copy className="w-3 h-3" />
                        )}
                      </button>
                    )}
                  </div>
                  {(user.curp || user.rfc) && (
                    <p className="text-[10px] font-mono text-blue-200/80 mt-0.5 truncate">
                      {user.curp ? `CURP: ${user.curp}` : ''}
                      {user.curp && user.rfc ? ' · ' : ''}
                      {user.rfc ? `RFC: ${user.rfc}` : ''}
                    </p>
                  )}
                </div>

                {/* Dual-Circle Metallic Network Emblem */}
                <div className="flex flex-col items-end shrink-0">
                  <div className="flex items-center -space-x-2.5">
                    <span className="w-6 h-6 rounded-full bg-amber-500/85 border border-white/40 shadow-sm" />
                    <span className="w-6 h-6 rounded-full bg-sky-400/80 border border-white/40 shadow-sm" />
                  </div>
                  <span className="text-[8px] font-extrabold tracking-[0.14em] text-blue-100/70 mt-1">
                    DIGITAL INFINITE
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Catalog Search & Popular Providers Hero Banner */}
        <div
          className={`${
            user ? 'lg:col-span-7' : 'lg:col-span-12'
          } bg-gradient-to-br from-slate-900 via-blue-950 to-indigo-900 rounded-[26px] p-6 sm:p-8 text-white shadow-[0_20px_40px_-15px_rgba(15,23,42,0.65)] border border-white/15 flex flex-col justify-between space-y-5`}
        >
          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-6">
            <div className="space-y-2">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/10 text-xs font-semibold backdrop-blur-sm border border-white/20">
                <Sparkles className="w-3.5 h-3.5 text-amber-300" />
                <span>Catálogo Oficial Directo de GestoPago</span>
              </div>
              <h1 className="text-2xl sm:text-3xl font-black tracking-tight">
                Paga tus Servicios y Recarga Tiempo Aire
              </h1>
              <p className="text-xs sm:text-sm text-blue-100/90 max-w-xl">
                Selecciona tu proveedor, valida tu referencia al instante y autoriza cada pago con código 2FA enviado a tu correo.
              </p>
            </div>

            {/* Search bar inside banner */}
            <div className="w-full md:w-72 shrink-0">
              <div className="bg-white/10 backdrop-blur-md rounded-2xl border border-white/20 p-2.5 flex items-center gap-2 shadow-inner focus-within:bg-white/15 focus-within:border-blue-300/50 transition-all">
                <Search className="w-5 h-5 text-blue-200 pl-1 shrink-0" />
                <input
                  type="text"
                  placeholder="Buscar servicio, categoría o ID..."
                  value={searchQuery}
                  onChange={(e) => handleSearchChange(e.target.value)}
                  className="bg-transparent text-white placeholder-blue-200/70 text-xs sm:text-sm w-full outline-none"
                />
                {searchQuery && (
                  <button
                    type="button"
                    onClick={() => handleSearchChange('')}
                    aria-label="Limpiar búsqueda"
                    className="text-xs text-blue-200 hover:text-white font-bold px-1.5"
                  >
                    ✕
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* Popular Providers Quick Access */}
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-1 text-[11px] font-bold text-blue-200/90 whitespace-nowrap mr-1">
              <Flame className="w-3.5 h-3.5 text-amber-300" />
              Populares:
            </span>
            {POPULAR_PROVIDERS.map((brand) => {
              const active = searchQuery.toLowerCase() === brand.toLowerCase();
              return (
                <button
                  key={brand}
                  type="button"
                  onClick={() => {
                    setSelectedFrontType('ALL');
                    setSelectedCategory('ALL');
                    handleSearchChange(active ? '' : brand);
                  }}
                  className={`px-3 py-1 rounded-full text-[11px] font-bold whitespace-nowrap transition-all border ${
                    active
                      ? 'bg-white text-blue-950 border-white shadow-sm'
                      : 'bg-white/10 text-blue-100 border-white/15 hover:bg-white/20'
                  }`}
                >
                  {brand}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* Operation Type Toggle (tipoFront Parity) & Category Filter Chips */}
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="inline-flex bg-white p-1 rounded-2xl border border-slate-200 shadow-sm">
            {(
              [
                { id: 'ALL', label: 'Todos', icon: Layers },
                { id: '1', label: 'Recargas', icon: Smartphone },
                { id: '2', label: 'Pago de Servicios', icon: ReceiptText },
              ] as const
            ).map((opt) => {
              const Icon = opt.icon;
              const active = selectedFrontType === opt.id;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => handleSelectFrontType(opt.id)}
                  className={`px-3.5 py-2 rounded-xl text-xs font-bold flex items-center gap-1.5 transition-all ${
                    active
                      ? 'bg-blue-900 text-white shadow-sm'
                      : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5" />
                  <span>{opt.label}</span>
                </button>
              );
            })}
          </div>

          <div className="flex items-center gap-2">
            {hasActiveFilters && (
              <button
                type="button"
                onClick={handleResetFilters}
                className="text-[11px] font-bold text-blue-700 hover:text-blue-900 bg-blue-50 hover:bg-blue-100 border border-blue-200 px-2.5 py-1 rounded-full transition-colors"
              >
                Limpiar filtros ✕
              </button>
            )}
            <button
              type="button"
              onClick={fetchCatalog}
              disabled={isLoading}
              aria-label="Actualizar catálogo"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white border border-slate-200 text-slate-600 hover:text-blue-900 hover:bg-slate-50 text-xs font-bold shadow-sm transition-colors disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
              <span className="hidden sm:inline">Actualizar</span>
            </button>
            <span className="text-xs text-slate-500 font-semibold">
              Mostrando {visibleProducts.length} de {filteredProducts.length} servicios
            </span>
          </div>
        </div>

        <div className="space-y-2">
          <h2 className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Filtrar por Categoría
          </h2>
          <CategoryChips
            selectedCategory={selectedCategory}
            onSelectCategory={handleSelectCategory}
            counts={categoryCounts}
          />
        </div>
      </div>

      {/* Main Catalog Grid */}
      {isLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 sm:gap-6">
          {Array.from({ length: 8 }).map((_, idx) => (
            <div
              key={idx}
              className="bg-white rounded-3xl border border-slate-200/80 p-5 space-y-4 animate-pulse"
            >
              <div className="flex items-start justify-between">
                <div className="w-12 h-12 rounded-2xl bg-slate-200" />
                <div className="w-24 h-6 rounded-full bg-slate-100" />
              </div>
              <div className="space-y-2">
                <div className="w-20 h-3 rounded bg-slate-100" />
                <div className="w-3/4 h-4 rounded bg-slate-200" />
              </div>
              <div className="pt-4 border-t border-slate-100 flex items-center justify-between">
                <div className="w-20 h-6 rounded bg-slate-200" />
                <div className="w-24 h-9 rounded-2xl bg-slate-200" />
              </div>
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="p-8 rounded-3xl bg-red-50 border border-red-200 text-center space-y-4">
          <AlertCircle className="w-10 h-10 text-red-600 mx-auto" />
          <h3 className="font-bold text-red-900 text-base">Error al cargar productos</h3>
          <p className="text-xs text-red-700 max-w-md mx-auto">{error}</p>
          <button
            type="button"
            onClick={fetchCatalog}
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-red-600 text-white rounded-2xl text-xs font-bold hover:bg-red-700 transition-colors shadow-md"
          >
            <RefreshCw className="w-4 h-4" />
            <span>Reintentar Carga</span>
          </button>
        </div>
      ) : filteredProducts.length === 0 ? (
        <div className="py-20 bg-white rounded-3xl border border-dashed border-slate-300 text-center space-y-3">
          <div className="w-12 h-12 bg-slate-100 text-slate-400 rounded-full flex items-center justify-center mx-auto">
            <Search className="w-6 h-6" />
          </div>
          <h3 className="text-base font-bold text-slate-800">
            No se encontraron servicios {searchQuery ? `para "${searchQuery}"` : 'en esta categoría'}
          </h3>
          <p className="text-xs text-slate-500 max-w-sm mx-auto">
            Intenta con otro término de búsqueda o selecciona la categoría &quot;Todos los Servicios&quot;.
          </p>
          <button
            type="button"
            onClick={handleResetFilters}
            className="px-4 py-2 bg-blue-900 text-white text-xs font-bold rounded-xl"
          >
            Ver todos los servicios
          </button>
        </div>
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 sm:gap-6">
            {visibleProducts.map((prod) => (
              <ServiceCard
                key={`${prod.serviceId}-${prod.id}`}
                product={prod}
                onSelect={handleSelectProduct}
              />
            ))}
          </div>

          {remainingCount > 0 && (
            <div className="flex justify-center pt-2">
              <button
                type="button"
                onClick={() => setVisibleCount((prev) => prev + PAGE_SIZE)}
                className="inline-flex items-center gap-2 px-6 py-3 rounded-2xl bg-white hover:bg-blue-50 border border-slate-200 hover:border-blue-300 text-slate-800 hover:text-blue-900 text-xs font-bold shadow-sm transition-all"
              >
                <ChevronDown className="w-4 h-4" />
                <span>
                  Mostrar {Math.min(PAGE_SIZE, remainingCount)} más ({remainingCount} restantes)
                </span>
              </button>
            </div>
          )}
        </div>
      )}

      {/* Step 1: Reference Modal */}
      <ReferenceModal
        product={productForPayment}
        isOpen={isReferenceModalOpen}
        onClose={() => setIsReferenceModalOpen(false)}
        onProceedTo2FA={handleProceedTo2FA}
      />

      {/* Step 2: 2FA Authorization Modal */}
      {paymentData && (
        <Payment2FAModal
          isOpen={is2FAModalOpen}
          onClose={() => setIs2FAModalOpen(false)}
          product={paymentData.product}
          referencia={paymentData.referencia}
          monto={paymentData.monto}
          validation={paymentData.validation}
          onPaymentSuccess={handlePaymentSuccess}
        />
      )}

      {/* Step 3: Official Receipt / 3-State Result Modal */}
      <ReceiptModal
        isOpen={isReceiptModalOpen}
        onClose={() => setIsReceiptModalOpen(false)}
        receipt={receiptData?.receipt || null}
        product={receiptData?.product || null}
        referencia={receiptData?.referencia || ''}
        onGoToHistory={onGoToHistory}
      />

      {/* Quick Profile / Fund Account Modal from Banking Card */}
      <ProfileModal
        isOpen={isProfileModalOpen}
        onClose={() => setIsProfileModalOpen(false)}
      />
    </div>
  );
};
