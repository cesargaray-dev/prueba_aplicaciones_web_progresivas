import React from 'react';
import {
  Layers,
  Smartphone,
  Zap,
  Droplets,
  Tv,
  CreditCard,
  Car,
  Film,
} from 'lucide-react';

export type CategoryFilterType =
  | 'ALL'
  | 'RECARGAS'
  | 'CFE'
  | 'AGUA'
  | 'INTERNET'
  | 'TAG'
  | 'FINANZAS'
  | 'ENTRETENIMIENTO';

interface CategoryOption {
  id: CategoryFilterType;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

const CATEGORIES: CategoryOption[] = [
  { id: 'ALL', label: 'Todos los Servicios', icon: Layers },
  { id: 'RECARGAS', label: 'Telefonía y Recargas', icon: Smartphone },
  { id: 'CFE', label: 'Luz y Energía (CFE)', icon: Zap },
  { id: 'AGUA', label: 'Agua Potable', icon: Droplets },
  { id: 'INTERNET', label: 'Internet y Televisión', icon: Tv },
  { id: 'TAG', label: 'TAG y Casetas', icon: Car },
  { id: 'FINANZAS', label: 'Financiero y Créditos', icon: CreditCard },
  { id: 'ENTRETENIMIENTO', label: 'Streaming y Pines', icon: Film },
];

interface CategoryChipsProps {
  selectedCategory: CategoryFilterType;
  onSelectCategory: (cat: CategoryFilterType) => void;
  counts?: Partial<Record<CategoryFilterType, number>>;
}

export const CategoryChips: React.FC<CategoryChipsProps> = ({
  selectedCategory,
  onSelectCategory,
  counts = {},
}) => {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {CATEGORIES.map((cat) => {
        const Icon = cat.icon;
        const isSelected = selectedCategory === cat.id;
        const count = counts[cat.id];

        return (
          <button
            key={cat.id}
            onClick={() => onSelectCategory(cat.id)}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold whitespace-nowrap transition-all duration-150 border ${
              isSelected
                ? 'bg-blue-900 text-white border-blue-900 shadow-sm shadow-blue-900/20'
                : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50 hover:border-slate-300'
            }`}
          >
            <Icon className={`w-3.5 h-3.5 ${isSelected ? 'text-blue-300' : 'text-slate-500'}`} />
            <span>{cat.label}</span>
            {count !== undefined && count > 0 && (
              <span
                className={`text-[10px] px-1.5 py-0.2 rounded-full font-bold ${
                  isSelected ? 'bg-blue-800 text-blue-100' : 'bg-slate-100 text-slate-600'
                }`}
              >
                {count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
};
