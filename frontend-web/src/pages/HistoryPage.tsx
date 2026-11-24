import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useAuth } from '../context/useAuth';
import { historyApi } from '../api/historyApi';
import type { ItemHistorialPago } from '../types/history';
import { BrandLogo } from '../components/BrandLogo';
import { TransactionDetailModal } from '../components/TransactionDetailModal';
import { esItemHistorialRecarga } from '../utils/validation';
import {
  Search,
  RefreshCw,
  Loader2,
  FileText,
  AlertCircle,
  Receipt,
  ArrowRight,
  TrendingUp,
  CheckCircle2,
  Clock,
  XCircle,
} from 'lucide-react';

interface HistoryPageProps {
  onGoToCatalog: () => void;
}

type HistoryTypeFilter = 'ALL' | 'RECARGAS' | 'SERVICIOS';

const isRecargaItem = (p: ItemHistorialPago): boolean => esItemHistorialRecarga(p);

const getDestinoLabel = (p: ItemHistorialPago): string => {
  if (isRecargaItem(p)) {
    return p.telefono || p.referencia || '—';
  }
  return p.referencia || (p.telefono && p.telefono !== '1111111111' ? p.telefono : '—');
};

const getStatusStyle = (estado?: string) => {
  const norm = (estado || 'EXITOSA').toUpperCase();
  if (norm === 'EXITOSA' || norm === 'COMPLETADA') {
    return {
      label: 'EXITOSA',
      cls: 'bg-emerald-50 text-emerald-700 border-emerald-200',
      kpiBg: 'bg-emerald-50 text-emerald-600',
      icon: <CheckCircle2 className="w-3 h-3" />,
      kpiIcon: <CheckCircle2 className="w-5 h-5 text-emerald-600" />,
    };
  }
  if (norm === 'PENDIENTE' || norm === 'EN_PROCESO') {
    return {
      label: 'PENDIENTE',
      cls: 'bg-amber-50 text-amber-700 border-amber-200',
      kpiBg: 'bg-amber-50 text-amber-600',
      icon: <Clock className="w-3 h-3" />,
      kpiIcon: <Clock className="w-5 h-5 text-amber-600" />,
    };
  }
  return {
    label: norm,
    cls: 'bg-red-50 text-red-700 border-red-200',
    kpiBg: 'bg-red-50 text-red-600',
    icon: <XCircle className="w-3 h-3" />,
    kpiIcon: <XCircle className="w-5 h-5 text-red-600" />,
  };
};

export const HistoryPage: React.FC<HistoryPageProps> = ({ onGoToCatalog }) => {
  const { user } = useAuth();
  const numeroCuenta = user?.numeroCuenta;
  const [pagos, setPagos] = useState<ItemHistorialPago[]>([]);
  const [isLoading, setIsLoading] = useState(Boolean(numeroCuenta));
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<HistoryTypeFilter>('ALL');
  const [selectedItem, setSelectedItem] = useState<ItemHistorialPago | null>(null);

  const fetchHistory = useCallback(async () => {
    if (!numeroCuenta) return;
    setIsLoading(true);
    setError(null);
    try {
      const response = await historyApi.getHistorial(numeroCuenta);
      setPagos(response.pagos || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al cargar el historial de pagos';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  }, [numeroCuenta]);

  // Initial load without synchronous setState in effect body
  useEffect(() => {
    if (!numeroCuenta) return;
    let cancelled = false;
    historyApi
      .getHistorial(numeroCuenta)
      .then((response) => {
        if (!cancelled) {
          setPagos(response.pagos || []);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          const msg =
            err instanceof Error ? err.message : 'Error al cargar el historial de pagos';
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
  }, [numeroCuenta]);

  const filteredPagos = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    return pagos.filter((p) => {
      if (typeFilter === 'RECARGAS' && !isRecargaItem(p)) return false;
      if (typeFilter === 'SERVICIOS' && isRecargaItem(p)) return false;
      if (!q) return true;
      return (
        (p.servicio && p.servicio.toLowerCase().includes(q)) ||
        (p.producto && p.producto.toLowerCase().includes(q)) ||
        (p.folioTicket && p.folioTicket.toLowerCase().includes(q)) ||
        (p.referencia && p.referencia.toLowerCase().includes(q)) ||
        (p.telefono && p.telefono.toLowerCase().includes(q)) ||
        (p.numeroAutorizacion && p.numeroAutorizacion.toLowerCase().includes(q))
      );
    });
  }, [pagos, searchQuery, typeFilter]);

  const totalPagado = useMemo(
    () => filteredPagos.reduce((acc, item) => acc + (item.monto || 0), 0),
    [filteredPagos]
  );

  const formatCurrency = (val?: number) => {
    return new Intl.NumberFormat('es-MX', {
      style: 'currency',
      currency: 'MXN',
    }).format(val || 0);
  };

  const lastStatus = getStatusStyle(pagos[0]?.estado);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-black tracking-tight text-slate-900">
              Historial de Pagos y Recargas
            </h1>
            <span className="text-xs font-bold px-2.5 py-0.5 rounded-full bg-blue-100 text-blue-900">
              {pagos.length} operaciones
            </span>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Consulta y reenvía los comprobantes de las operaciones realizadas con tu cuenta bancaria.
          </p>
        </div>

        <button
          type="button"
          onClick={fetchHistory}
          disabled={isLoading}
          className="self-start sm:self-auto flex items-center gap-2 px-4 py-2.5 bg-white border border-slate-200 text-slate-700 hover:bg-slate-50 text-xs font-bold rounded-2xl shadow-sm transition-colors"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          <span>Actualizar</span>
        </button>
      </div>

      {/* KPI Summary Cards */}
      {!isLoading && !error && pagos.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="bg-white rounded-2xl border border-slate-200 p-4 flex items-center justify-between">
            <div>
              <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
                Movimientos Filtrados
              </p>
              <p className="text-xl font-black text-slate-900 mt-0.5">{filteredPagos.length}</p>
            </div>
            <div className="p-2.5 rounded-xl bg-blue-50 text-blue-700">
              <Receipt className="w-5 h-5" />
            </div>
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 p-4 flex items-center justify-between">
            <div>
              <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
                Importe Acumulado
              </p>
              <p className="text-xl font-black text-slate-900 mt-0.5">{formatCurrency(totalPagado)}</p>
            </div>
            <div className="p-2.5 rounded-xl bg-emerald-50 text-emerald-700">
              <TrendingUp className="w-5 h-5" />
            </div>
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 p-4 flex items-center justify-between gap-2">
            <div className="min-w-0">
              <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
                Última Operación ({lastStatus.label})
              </p>
              <p className="text-sm font-extrabold text-slate-900 mt-1 truncate">
                {pagos[0]?.servicio} · {formatCurrency(pagos[0]?.monto)}
              </p>
            </div>
            <div className={`p-2.5 rounded-xl shrink-0 ${lastStatus.kpiBg}`}>
              {lastStatus.kpiIcon}
            </div>
          </div>
        </div>
      )}

      {/* Search Bar & Type Filter */}
      <div className="flex flex-col md:flex-row gap-3">
        <div className="flex-1 bg-white p-3 rounded-2xl border border-slate-200 shadow-sm flex items-center gap-3">
          <Search className="w-5 h-5 text-slate-400 pl-1" />
          <input
            type="text"
            placeholder="Buscar por servicio, producto, folio o referencia..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="flex-1 text-xs sm:text-sm text-slate-800 placeholder-slate-400 outline-none"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery('')}
              className="text-xs text-slate-400 hover:text-slate-600 font-bold px-2 py-1"
            >
              Limpiar
            </button>
          )}
        </div>

        <div className="inline-flex bg-white p-1 rounded-2xl border border-slate-200 shadow-sm self-start">
          {(
            [
              { id: 'ALL', label: 'Todos' },
              { id: 'RECARGAS', label: 'Recargas' },
              { id: 'SERVICIOS', label: 'Servicios' },
            ] as const
          ).map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setTypeFilter(tab.id)}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                typeFilter === tab.id
                  ? 'bg-blue-900 text-white shadow-sm'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Content Area */}
      {isLoading ? (
        <div className="py-20 flex flex-col items-center justify-center gap-3 text-slate-400">
          <Loader2 className="w-8 h-8 animate-spin text-blue-600" />
          <p className="text-xs font-semibold">Consultando historial en el servidor...</p>
        </div>
      ) : error ? (
        <div className="p-6 rounded-3xl bg-red-50 border border-red-200 text-center space-y-3">
          <AlertCircle className="w-8 h-8 text-red-600 mx-auto" />
          <h3 className="font-bold text-red-900 text-sm">No se pudo cargar el historial</h3>
          <p className="text-xs text-red-700 max-w-md mx-auto">{error}</p>
          <button
            type="button"
            onClick={fetchHistory}
            className="px-4 py-2 bg-red-600 text-white rounded-xl text-xs font-bold hover:bg-red-700 transition-colors"
          >
            Reintentar
          </button>
        </div>
      ) : filteredPagos.length === 0 ? (
        <div className="py-20 bg-white rounded-3xl border border-dashed border-slate-300 text-center space-y-4">
          <div className="w-16 h-16 bg-blue-50 text-blue-700 rounded-full flex items-center justify-center mx-auto">
            <Receipt className="w-8 h-8" />
          </div>
          <div>
            <h3 className="text-base font-bold text-slate-900">
              {searchQuery || typeFilter !== 'ALL'
                ? 'Sin coincidencias para los filtros actuales'
                : 'Aún no registras operaciones'}
            </h3>
            <p className="text-xs text-slate-500 max-w-sm mx-auto mt-1">
              {searchQuery || typeFilter !== 'ALL'
                ? 'Prueba limpiando la búsqueda o seleccionando "Todos".'
                : 'Realiza tu primera recarga de tiempo aire o pago de servicios desde el catálogo.'}
            </p>
          </div>
          {searchQuery || typeFilter !== 'ALL' ? (
            <button
              type="button"
              onClick={() => {
                setSearchQuery('');
                setTypeFilter('ALL');
              }}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-blue-900 text-white rounded-2xl text-xs font-bold hover:bg-blue-800 transition-colors shadow-md shadow-blue-900/20"
            >
              <span>Limpiar filtros</span>
            </button>
          ) : (
            <button
              type="button"
              onClick={onGoToCatalog}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-blue-900 text-white rounded-2xl text-xs font-bold hover:bg-blue-800 transition-colors shadow-md shadow-blue-900/20"
            >
              <span>Explorar Catálogo</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          )}
        </div>
      ) : (
        <>
          {/* Mobile Card Layout */}
          <div className="grid grid-cols-1 gap-3 md:hidden">
            {filteredPagos.map((pago) => {
              const st = getStatusStyle(pago.estado);
              return (
                <button
                  key={pago.idPago || pago.folioTicket}
                  type="button"
                  onClick={() => setSelectedItem(pago)}
                  className="w-full text-left bg-white rounded-2xl border border-slate-200 p-4 shadow-sm hover:border-blue-300 transition-all space-y-3"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <BrandLogo
                        serviceId={pago.idServicio}
                        serviceName={pago.servicio}
                        logoUrl={pago.logoUrl}
                        size="sm"
                      />
                      <div className="min-w-0">
                        <p className="text-sm font-extrabold text-slate-900 truncate">{pago.servicio}</p>
                        <p className="text-[11px] text-slate-500 truncate">{pago.producto}</p>
                      </div>
                    </div>
                    <p className="text-sm font-black text-slate-900 shrink-0">
                      {formatCurrency(pago.monto)}
                    </p>
                  </div>
                  <div className="flex items-center justify-between text-[11px] pt-2 border-t border-slate-100">
                    <span className="font-mono font-semibold text-slate-700">
                      {getDestinoLabel(pago)}
                    </span>
                    <span
                      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full font-extrabold border ${st.cls}`}
                    >
                      {st.icon}
                      {st.label}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-[10px] text-slate-400 font-mono">
                    <span>Folio: {pago.folioTicket}</span>
                    <span>{pago.fechaOperacion}</span>
                  </div>
                </button>
              );
            })}
          </div>

          {/* Desktop Table Layout */}
          <div className="hidden md:block bg-white rounded-3xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-50/80 border-b border-slate-200 text-[11px] font-extrabold uppercase tracking-wider text-slate-500">
                    <th className="py-4 px-6">Servicio / Proveedor</th>
                    <th className="py-4 px-4">Referencia / Celular</th>
                    <th className="py-4 px-4">Folio de Ticket</th>
                    <th className="py-4 px-4">Fecha y Hora</th>
                    <th className="py-4 px-4 text-right">Importe</th>
                    <th className="py-4 px-4 text-center">Estado</th>
                    <th className="py-4 px-6 text-right">Detalle</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-xs">
                  {filteredPagos.map((pago) => {
                    const st = getStatusStyle(pago.estado);
                    return (
                      <tr
                        key={pago.idPago || pago.folioTicket}
                        className="hover:bg-slate-50/70 transition-colors group cursor-pointer"
                        onClick={() => setSelectedItem(pago)}
                      >
                        <td className="py-4 px-6">
                          <div className="flex items-center gap-3">
                            <BrandLogo
                              serviceId={pago.idServicio}
                              serviceName={pago.servicio}
                              logoUrl={pago.logoUrl}
                              size="sm"
                            />
                            <div>
                              <p className="font-extrabold text-slate-900 group-hover:text-blue-900">
                                {pago.servicio}
                              </p>
                              <p className="text-[11px] text-slate-500">{pago.producto}</p>
                            </div>
                          </div>
                        </td>

                        <td className="py-4 px-4 font-mono font-bold text-slate-800">
                          {getDestinoLabel(pago)}
                        </td>

                        <td className="py-4 px-4 font-mono text-slate-600">{pago.folioTicket}</td>

                        <td className="py-4 px-4 text-slate-500 font-medium whitespace-nowrap">
                          {pago.fechaOperacion}
                        </td>

                        <td className="py-4 px-4 text-right font-black text-slate-900">
                          {formatCurrency(pago.monto)}
                        </td>

                        <td className="py-4 px-4 text-center">
                          <span
                            className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-extrabold border ${st.cls}`}
                          >
                            {st.icon}
                            <span>{st.label}</span>
                          </span>
                        </td>

                        <td className="py-4 px-6 text-right">
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              setSelectedItem(pago);
                            }}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-200 hover:border-blue-400 bg-white hover:bg-blue-50 text-slate-700 hover:text-blue-900 font-bold text-[11px] transition-colors"
                          >
                            <FileText className="w-3.5 h-3.5" />
                            <span>Ver</span>
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {/* Transaction Detail Modal */}
      <TransactionDetailModal
        item={selectedItem}
        isOpen={!!selectedItem}
        onClose={() => setSelectedItem(null)}
      />
    </div>
  );
};
