import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { catalogApi } from '../api/catalogApi';
import type { ProductDTO } from '../types/catalog';
import type { ValidarReferenciaResponse, PagoServicioResponse } from '../types/payment';
import { esConsultaSaldo, esRecargaTelefonica, getProductCategoryName } from '../utils/validation';
import { ServiceCard } from '../components/ServiceCard';
import { CategoryChips } from '../components/CategoryChips';
import type { CategoryFilterType } from '../components/CategoryChips';
import { ReferenceModal } from '../components/ReferenceModal';
import { Payment2FAModal } from '../components/Payment2FAModal';
import { ReceiptModal } from '../components/ReceiptModal';
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
    return frontTypeFilteredProducts.filter((p) => {
      if (!matchesCategory(p, selectedCategory)) return false;
      if (!q) return true;
      const categoryLabel = getProductCategoryName(p).toLowerCase();
      return (
        (p.name && p.name.toLowerCase().includes(q)) ||
        (p.service && p.service.toLowerCase().includes(q)) ||
        (p.legend && p.legend.toLowerCase().includes(q)) ||
        categoryLabel.includes(q) ||
        (p.serviceId && p.serviceId.includes(q)) ||
        (p.id && p.id.includes(q))
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

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Top Hero Banner */}
      <div className="bg-gradient-to-br from-slate-900 via-blue-950 to-indigo-900 rounded-3xl p-6 sm:p-8 text-white shadow-xl border border-white/10 space-y-5">
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
          <div className="w-full md:w-80">
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
    </div>
  );
};
