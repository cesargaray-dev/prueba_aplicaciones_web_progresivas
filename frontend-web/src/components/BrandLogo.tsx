import React, { useState } from 'react';
import { Zap, Smartphone, Droplets, Tv, CreditCard, HelpCircle } from 'lucide-react';

interface BrandLogoProps {
  serviceId?: string | number;
  serviceName: string;
  logoUrl?: string | null;
  className?: string;
  size?: 'sm' | 'md' | 'lg';
}

export const BrandLogo: React.FC<BrandLogoProps> = ({
  serviceId,
  serviceName,
  logoUrl: propLogoUrl,
  className = '',
  size = 'md',
}) => {
  const [failedLogoUrl, setFailedLogoUrl] = useState<string | null>(null);

  const sanitizedServiceId =
    serviceId !== undefined && serviceId !== null ? String(serviceId).trim() : '';
  const safeExternalUrl =
    propLogoUrl && /^https?:\/\//i.test(propLogoUrl.trim()) ? propLogoUrl.trim() : null;
  const resolvedLogoUrl =
    safeExternalUrl ||
    (sanitizedServiceId
      ? `https://gestopago.portalventas.net/sistema/images/gestopago/servicios/${encodeURIComponent(
          sanitizedServiceId
        )}.png`
      : null);

  const imageError = Boolean(resolvedLogoUrl && failedLogoUrl === resolvedLogoUrl);

  const sizeClasses = {
    sm: 'w-8 h-8 text-xs',
    md: 'w-12 h-12 text-sm',
    lg: 'w-16 h-16 text-lg',
  }[size];

  const getFallbackIcon = () => {
    const s = serviceName.toUpperCase();
    if (
      s.includes('TELCEL') ||
      s.includes('MOVISTAR') ||
      s.includes('AT&T') ||
      s.includes('AT-T') ||
      s.includes('ATT') ||
      s.includes('UNEFON') ||
      s.includes('BAIT') ||
      s.includes('RECARGA')
    ) {
      return <Smartphone className="w-1/2 h-1/2 text-blue-500" />;
    }
    if (s.includes('CFE') || s.includes('LUZ') || s.includes('ELECTRIC')) {
      return <Zap className="w-1/2 h-1/2 text-amber-500" />;
    }
    if (s.includes('AGUA') || s.includes('SAPA') || s.includes('SIAPA') || s.includes('CEA')) {
      return <Droplets className="w-1/2 h-1/2 text-cyan-500" />;
    }
    if (
      s.includes('IZZI') ||
      s.includes('TOTAL') ||
      s.includes('TELMEX') ||
      s.includes('SKY') ||
      s.includes('DISH')
    ) {
      return <Tv className="w-1/2 h-1/2 text-purple-500" />;
    }
    if (s.includes('CREDITO') || s.includes('BANCO') || s.includes('FINAN') || s.includes('AFORE')) {
      return <CreditCard className="w-1/2 h-1/2 text-emerald-500" />;
    }
    return <HelpCircle className="w-1/2 h-1/2 text-slate-400" />;
  };

  const getInitials = () => {
    const parts = serviceName.trim().split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
    }
    return serviceName.trim().slice(0, 2).toUpperCase() || 'GP';
  };

  if (resolvedLogoUrl && !imageError) {
    return (
      <div
        data-testid="brand-logo-tile"
        className={`brand-logo-tile ${sizeClasses} rounded-2xl p-1.5 flex items-center justify-center shadow-sm overflow-hidden flex-shrink-0 ${className}`}
      >
        <img
          src={resolvedLogoUrl}
          alt={serviceName}
          onError={() => setFailedLogoUrl(resolvedLogoUrl)}
          className="max-w-full max-h-full object-contain transition-transform duration-200 hover:scale-105"
          loading="lazy"
        />
      </div>
    );
  }

  return (
    <div
      data-testid="brand-logo-fallback"
      className={`brand-logo-fallback ${sizeClasses} rounded-2xl flex flex-col items-center justify-center font-bold shadow-sm flex-shrink-0 relative overflow-hidden ${className}`}
      title={serviceName}
    >
      {getFallbackIcon()}
      <span className="text-[10px] uppercase tracking-tighter opacity-90 mt-0.5">
        {getInitials()}
      </span>
    </div>
  );
};
