import React from 'react';
import { ArrowRight, Smartphone, ReceiptText, Gift } from 'lucide-react';
import type { ProductDTO } from '../types/catalog';
import { BrandLogo } from './BrandLogo';
import { esRecargaTelefonica, getProductCategoryName } from '../utils/validation';

interface ServiceCardProps {
  product: ProductDTO;
  onSelect: (product: ProductDTO) => void;
}

export const ServiceCard: React.FC<ServiceCardProps> = ({ product, onSelect }) => {
  const isRecarga = esRecargaTelefonica(product);
  const isFront1Otro = product.frontType === '1' && !isRecarga;
  const priceNum = parseFloat(product.price || '0');
  const hasFixedPrice = !isNaN(priceNum) && priceNum > 0;
  const categoryName = getProductCategoryName(product);

  const badgeLabel = isRecarga
    ? 'Recarga Móvil'
    : isFront1Otro
    ? 'Tarjeta / Pase'
    : 'Pago de Servicio';

  const badgeClass = isRecarga
    ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
    : isFront1Otro
    ? 'bg-indigo-50 text-indigo-700 border-indigo-200'
    : 'bg-blue-50 text-blue-700 border-blue-200';

  return (
    <div className="group bg-white rounded-3xl border border-slate-200/80 shadow-sm hover:shadow-xl hover:shadow-blue-900/5 hover:border-blue-300 transition-all duration-300 flex flex-col justify-between p-5 relative overflow-hidden">
      <div className="space-y-4">
        {/* Top: Logo & Type Badge */}
        <div className="flex items-start justify-between gap-2">
          <BrandLogo
            serviceId={product.serviceId}
            serviceName={product.service || product.name}
            logoUrl={product.logoUrl}
            size="md"
          />

          <div className="flex flex-col items-end gap-1">
            <span
              className={`px-2.5 py-1 rounded-full text-[11px] font-bold border inline-flex items-center gap-1 ${badgeClass}`}
            >
              {isRecarga ? (
                <Smartphone className="w-3 h-3" />
              ) : isFront1Otro ? (
                <Gift className="w-3 h-3" />
              ) : (
                <ReceiptText className="w-3 h-3" />
              )}
              <span>{badgeLabel}</span>
            </span>
            <span className="text-[10px] font-semibold text-slate-400">{categoryName}</span>
          </div>
        </div>

        {/* Service Provider, Product Title & Optional Legend */}
        <div>
          <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
            {product.service || 'Proveedor GestoPago'}
          </p>
          <h3 className="text-sm font-extrabold text-slate-900 line-clamp-2 mt-0.5 min-h-[2.5rem] group-hover:text-blue-900 transition-colors">
            {product.name}
          </h3>
          {product.legend && product.legend.trim() && (
            <p className="text-[11px] text-slate-500 line-clamp-2 mt-1 leading-snug">
              {product.legend.trim()}
            </p>
          )}
        </div>
      </div>

      {/* Price / Info & CTA */}
      <div className="pt-4 mt-4 border-t border-slate-100 flex items-center justify-between gap-2">
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
            {hasFixedPrice ? 'Importe fijo' : 'Importe'}
          </p>
          <p className="text-base font-black text-slate-900">
            {hasFixedPrice ? `$${priceNum.toFixed(2)}` : 'Monto Abierto'}
          </p>
        </div>

        <button
          type="button"
          onClick={() => onSelect(product)}
          className="px-4 py-2 rounded-2xl bg-slate-900 group-hover:bg-blue-900 text-white text-xs font-bold flex items-center gap-1.5 transition-all active:scale-95 shadow-sm"
        >
          <span>{isRecarga ? 'Recargar' : 'Pagar'}</span>
          <ArrowRight className="w-3.5 h-3.5 transition-transform group-hover:translate-x-0.5" />
        </button>
      </div>
    </div>
  );
};
