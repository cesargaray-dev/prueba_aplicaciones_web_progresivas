import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { adminApi } from '../api/adminApi';
import type { AdminUserItem, AdminUpdateUserRequest, RegisterRequest } from '../types/auth';
import {
  Users,
  Search,
  RefreshCw,
  UserPlus,
  Edit3,
  Trash2,
  ShieldCheck,
  Lock,
  Unlock,
  CheckCircle2,
  XCircle,
  AlertCircle,
  X,
  Save,
  Loader2,
  Wallet,
  KeyRound,
  Fingerprint,
  PlusCircle,
} from 'lucide-react';

const extractMsg = (err: unknown): string =>
  err instanceof Error ? err.message : 'Error en la operación administrativa.';

export const AdminUsersPage: React.FC = () => {
  const [users, setUsers] = useState<AdminUserItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; msg: string } | null>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');

  // Edit Modal State
  const [editingUser, setEditingUser] = useState<AdminUserItem | null>(null);
  const [editForm, setEditForm] = useState<AdminUpdateUserRequest>({});
  const [isSavingEdit, setIsSavingEdit] = useState(false);

  // Create Modal State
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState<RegisterRequest>({
    nombre: '',
    apellidoPaterno: '',
    correo: '',
    telefonoMovil: '',
    usuario: '',
    contrasena: '',
  });
  const [isCreating, setIsCreating] = useState(false);

  // Delete confirmation State
  const [deletingUser, setDeletingUser] = useState<AdminUserItem | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // Bulk delete all non-admin users State
  const [isDeleteAllOpen, setIsDeleteAllOpen] = useState(false);
  const [isDeletingAll, setIsDeletingAll] = useState(false);

  // Admin Deposit (Ingresar Dinero) Modal State
  const [depositingUser, setDepositingUser] = useState<AdminUserItem | null>(null);
  const [depositMonto, setDepositMonto] = useState('200');
  const [depositConcepto, setDepositConcepto] = useState('Ingreso de saldo por administrador');
  const [isDepositing, setIsDepositing] = useState(false);

  const handleAdminDeposit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!depositingUser) return;
    const montoNum = parseFloat(depositMonto);
    if (Number.isNaN(montoNum) || montoNum <= 0 || montoNum > 1000000) {
      setFeedback({ type: 'error', msg: 'Ingresa un monto válido mayor a $0.00 MXN.' });
      return;
    }
    setIsDepositing(true);
    setFeedback(null);
    try {
      const updated = await adminApi.abonarSaldoUsuario(depositingUser.clienteId, {
        monto: montoNum,
        concepto: depositConcepto.trim() || 'Ingreso de saldo por administrador',
      });
      setUsers((prev) =>
        prev.map((item) => (item.clienteId === updated.clienteId ? updated : item))
      );
      setFeedback({
        type: 'success',
        msg: `Se ingresaron ${formatCurrency(montoNum)} a la cuenta de @${updated.usuario}. Nuevo saldo: ${formatCurrency(updated.saldo)}.`,
      });
      setDepositingUser(null);
    } catch (err: unknown) {
      setFeedback({ type: 'error', msg: extractMsg(err) });
    } finally {
      setIsDepositing(false);
    }
  };

  const loadUsers = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const list = await adminApi.listarUsuarios();
      setUsers(list);
    } catch (err: unknown) {
      setError(extractMsg(err));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    adminApi
      .listarUsuarios()
      .then((list) => {
        if (!cancelled) {
          setUsers(list);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(extractMsg(err));
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

  const filteredUsers = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return users.filter((u) => {
      if (statusFilter === 'ACTIVE' && (!u.activo || u.bloqueado)) return false;
      if (statusFilter === 'INACTIVE' && u.activo && !u.bloqueado) return false;
      if (!q) return true;
      const haystack = [
        u.nombreCompleto,
        u.usuario,
        u.correo,
        u.telefonoMovil,
        u.numeroCuenta,
        u.curp,
        u.rfc,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return haystack.includes(q);
    });
  }, [users, searchQuery, statusFilter]);

  const stats = useMemo(() => {
    const total = users.length;
    const activos = users.filter((u) => u.activo && !u.bloqueado).length;
    const inactivosOBloqueados = total - activos;
    const saldoTotal = users.reduce((acc, u) => acc + Number(u.saldo ?? 0), 0);
    return { total, activos, inactivosOBloqueados, saldoTotal };
  }, [users]);

  const formatCurrency = (val: number) =>
    new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(val);

  const openEditModal = (u: AdminUserItem) => {
    setFeedback(null);
    setEditingUser(u);
    setEditForm({
      nombre: u.nombre ?? '',
      segundoNombre: u.segundoNombre ?? '',
      apellidoPaterno: u.apellidoPaterno ?? '',
      apellidoMaterno: u.apellidoMaterno ?? '',
      curp: u.curp ?? '',
      rfc: u.rfc ?? '',
      correo: u.correo ?? '',
      telefonoMovil: u.telefonoMovil ?? '',
      usuario: u.usuario ?? '',
      saldo: u.saldo ?? 0,
      activo: u.activo,
      bloqueado: u.bloqueado,
      nuevaContrasena: '',
    });
  };

  const handleSaveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingUser) return;
    setIsSavingEdit(true);
    setFeedback(null);
    try {
      const payload: AdminUpdateUserRequest = {
        nombre: editForm.nombre?.trim(),
        segundoNombre: editForm.segundoNombre?.trim() ?? '',
        apellidoPaterno: editForm.apellidoPaterno?.trim(),
        apellidoMaterno: editForm.apellidoMaterno?.trim(),
        curp: editForm.curp?.trim() ? editForm.curp.trim().toUpperCase() : undefined,
        rfc: editForm.rfc?.trim() ? editForm.rfc.trim().toUpperCase() : undefined,
        correo: editForm.correo?.trim(),
        telefonoMovil: editForm.telefonoMovil?.trim(),
        usuario: editForm.usuario?.trim(),
        saldo: Number(editForm.saldo ?? 0),
        activo: editForm.activo,
        bloqueado: editForm.bloqueado,
      };
      if (editForm.nuevaContrasena && editForm.nuevaContrasena.trim().length > 0) {
        payload.nuevaContrasena = editForm.nuevaContrasena.trim();
      }
      const updated = await adminApi.actualizarUsuario(editingUser.clienteId, payload);
      setUsers((prev) =>
        prev.map((item) => (item.clienteId === updated.clienteId ? updated : item))
      );
      setEditingUser(null);
      setFeedback({
        type: 'success',
        msg: `Usuario @${updated.usuario} actualizado correctamente.`,
      });
    } catch (err: unknown) {
      setFeedback({ type: 'error', msg: extractMsg(err) });
    } finally {
      setIsSavingEdit(false);
    }
  };

  const handleQuickToggleStatus = async (u: AdminUserItem) => {
    setFeedback(null);
    try {
      const nextActivo = u.bloqueado ? true : !u.activo;
      const updated = await adminApi.actualizarUsuario(u.clienteId, {
        activo: nextActivo,
        bloqueado: false,
      });
      setUsers((prev) =>
        prev.map((item) => (item.clienteId === updated.clienteId ? updated : item))
      );
      setFeedback({
        type: 'success',
        msg: `Estado de @${updated.usuario} actualizado.`,
      });
    } catch (err: unknown) {
      setFeedback({ type: 'error', msg: extractMsg(err) });
    }
  };

  const handleConfirmDelete = async () => {
    if (!deletingUser) return;
    setIsDeleting(true);
    setFeedback(null);
    try {
      const res = await adminApi.eliminarUsuario(deletingUser.clienteId);
      setUsers((prev) => prev.filter((item) => item.clienteId !== deletingUser.clienteId));
      setDeletingUser(null);
      setFeedback({ type: 'success', msg: res.mensaje });
    } catch (err: unknown) {
      setFeedback({ type: 'error', msg: extractMsg(err) });
    } finally {
      setIsDeleting(false);
    }
  };

  const handleConfirmDeleteAll = async () => {
    setIsDeletingAll(true);
    setFeedback(null);
    try {
      const res = await adminApi.eliminarTodosUsuarios();
      setUsers((prev) => prev.filter((item) => item.esAdmin));
      setIsDeleteAllOpen(false);
      setFeedback({ type: 'success', msg: res.mensaje });
    } catch (err: unknown) {
      setFeedback({ type: 'error', msg: extractMsg(err) });
    } finally {
      setIsDeletingAll(false);
    }
  };

  const nonAdminCount = useMemo(() => users.filter((u) => !u.esAdmin).length, [users]);

  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsCreating(true);
    setFeedback(null);
    try {
      const created = await adminApi.crearUsuario(createForm);
      setUsers((prev) => [created, ...prev]);
      setIsCreateOpen(false);
      setCreateForm({
        nombre: '',
        apellidoPaterno: '',
        correo: '',
        telefonoMovil: '',
        usuario: '',
        contrasena: '',
      });
      setFeedback({
        type: 'success',
        msg: `Usuario @${created.usuario} creado exitosamente con cuenta ${created.numeroCuenta}.`,
      });
    } catch (err: unknown) {
      setFeedback({ type: 'error', msg: extractMsg(err) });
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Header Banner */}
      <div className="bg-gradient-to-br from-slate-950 via-blue-950 to-indigo-950 rounded-3xl p-6 sm:p-8 text-white shadow-2xl border border-amber-300/25 flex flex-col md:flex-row md:items-center md:justify-between gap-6">
        <div className="space-y-2">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-amber-400/15 border border-amber-300/40 text-amber-200 text-xs font-extrabold">
            <ShieldCheck className="w-3.5 h-3.5 text-amber-300" />
            <span>CONSOLA EJECUTIVA • ROL ADMINISTRADOR</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-black tracking-tight">
            Gestión Total de Usuarios y Cuentas
          </h1>
          <p className="text-xs sm:text-sm text-blue-100/85 max-w-2xl">
            Edita datos personales, ajusta saldos bancarios, restablece contraseñas, desbloquea accesos o elimina usuarios de forma permanente.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3 shrink-0">
          <button
            type="button"
            onClick={() => setIsCreateOpen(true)}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-gradient-to-r from-amber-400 to-amber-500 hover:from-amber-300 hover:to-amber-400 text-slate-950 text-xs font-black shadow-lg transition-all"
          >
            <UserPlus className="w-4 h-4" />
            <span>Nuevo Usuario</span>
          </button>
          <button
            type="button"
            onClick={() => setIsDeleteAllOpen(true)}
            disabled={isLoading || nonAdminCount === 0}
            className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-2xl bg-red-600/90 hover:bg-red-500 disabled:opacity-45 border border-red-400/50 text-white text-xs font-black shadow-lg transition-all"
            title="Eliminar todos los usuarios clientes (conserva la cuenta admin)"
          >
            <Trash2 className="w-4 h-4" />
            <span>Eliminar Todos</span>
          </button>
          <button
            type="button"
            onClick={loadUsers}
            disabled={isLoading}
            className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-2xl bg-white/10 hover:bg-white/20 border border-white/20 text-white text-xs font-bold transition-colors"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            <span>Actualizar</span>
          </button>
        </div>
      </div>

      {/* KPI Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white rounded-2xl p-5 border border-slate-200 shadow-sm">
          <span className="text-[11px] font-bold uppercase text-slate-400">Total Usuarios</span>
          <p className="text-2xl font-black text-slate-900 mt-1">{stats.total}</p>
        </div>
        <div className="bg-white rounded-2xl p-5 border border-slate-200 shadow-sm">
          <span className="text-[11px] font-bold uppercase text-emerald-600">Cuentas Activas</span>
          <p className="text-2xl font-black text-emerald-700 mt-1">{stats.activos}</p>
        </div>
        <div className="bg-white rounded-2xl p-5 border border-slate-200 shadow-sm">
          <span className="text-[11px] font-bold uppercase text-amber-600">
            Inactivos / Bloqueados
          </span>
          <p className="text-2xl font-black text-amber-700 mt-1">{stats.inactivosOBloqueados}</p>
        </div>
        <div className="bg-white rounded-2xl p-5 border border-slate-200 shadow-sm">
          <span className="text-[11px] font-bold uppercase text-blue-600">
            Saldo Total en Custodia
          </span>
          <p className="text-2xl font-black text-blue-950 mt-1">
            {formatCurrency(stats.saldoTotal)}
          </p>
        </div>
      </div>

      {/* Feedback Banner */}
      {feedback && (
        <div
          className={`p-4 rounded-2xl border flex items-center justify-between gap-3 text-xs font-bold ${
            feedback.type === 'success'
              ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
              : 'bg-red-50 border-red-200 text-red-800'
          }`}
        >
          <div className="flex items-center gap-2">
            {feedback.type === 'success' ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
            ) : (
              <AlertCircle className="w-4 h-4 text-red-600 shrink-0" />
            )}
            <span>{feedback.msg}</span>
          </div>
          <button
            type="button"
            onClick={() => setFeedback(null)}
            className="p-1 hover:bg-black/5 rounded-lg"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Search & Filter Bar */}
      <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Buscar por nombre, @usuario, correo, teléfono o cuenta de 16 dígitos..."
            className="w-full pl-10 pr-4 py-2 rounded-xl border border-slate-200 text-xs sm:text-sm outline-none focus:border-blue-600"
          />
        </div>
        <div className="inline-flex bg-slate-100 p-1 rounded-xl border border-slate-200">
          {(
            [
              { id: 'ALL', label: 'Todos' },
              { id: 'ACTIVE', label: 'Activos' },
              { id: 'INACTIVE', label: 'Inactivos / Bloqueados' },
            ] as const
          ).map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setStatusFilter(tab.id)}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                statusFilter === tab.id
                  ? 'bg-blue-950 text-white shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Users Table */}
      {isLoading ? (
        <div className="bg-white rounded-3xl p-12 border border-slate-200 text-center space-y-3">
          <Loader2 className="w-8 h-8 animate-spin text-blue-900 mx-auto" />
          <p className="text-xs font-bold text-slate-500">Cargando padrón de usuarios...</p>
        </div>
      ) : error ? (
        <div className="bg-red-50 rounded-3xl p-8 border border-red-200 text-center space-y-3">
          <AlertCircle className="w-8 h-8 text-red-600 mx-auto" />
          <p className="text-sm font-bold text-red-900">{error}</p>
        </div>
      ) : (
        <div className="bg-white rounded-3xl border border-slate-200 shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-[11px] font-extrabold uppercase text-slate-500">
                  <th className="py-3.5 px-4">Usuario / Titular</th>
                  <th className="py-3.5 px-4">Contacto</th>
                  <th className="py-3.5 px-4">Cuenta &amp; Saldo</th>
                  <th className="py-3.5 px-4">Estado</th>
                  <th className="py-3.5 px-4 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {filteredUsers.map((u) => (
                  <tr key={u.clienteId} className="hover:bg-slate-50/80 transition-colors">
                    <td className="py-3.5 px-4">
                      <div className="flex items-center gap-2.5">
                        <div
                          className={`w-9 h-9 rounded-xl flex items-center justify-center font-black text-xs shrink-0 ${
                            u.esAdmin
                              ? 'bg-amber-100 text-amber-900 border border-amber-300'
                              : 'bg-blue-50 text-blue-900 border border-blue-200'
                          }`}
                        >
                          {(u.nombre || 'U').charAt(0)}
                        </div>
                        <div>
                          <div className="flex items-center gap-1.5">
                            <span className="font-extrabold text-slate-900">
                              {u.nombreCompleto}
                            </span>
                            {u.esAdmin && (
                              <span className="px-2 py-0.5 rounded-full bg-amber-400/20 border border-amber-400 text-amber-900 text-[10px] font-black">
                                ADMIN
                              </span>
                            )}
                          </div>
                          <span className="text-slate-500 font-mono block">
                            @{u.usuario || 'sin_usuario'} &bull; ID #{u.clienteId}
                          </span>
                          {(u.curp || u.rfc) && (
                            <span className="text-[10px] font-mono text-blue-800 bg-blue-50 border border-blue-100 px-1.5 py-0.5 rounded mt-0.5 inline-block">
                              {u.curp || '—'} {u.rfc ? `· ${u.rfc}` : ''}
                            </span>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="py-3.5 px-4">
                      <p className="font-semibold text-slate-800">{u.correo}</p>
                      <p className="text-slate-500 font-mono">{u.telefonoMovil}</p>
                    </td>
                    <td className="py-3.5 px-4">
                      <p className="font-black text-blue-950 text-sm">{formatCurrency(u.saldo)}</p>
                      <p className="text-slate-500 font-mono text-[11px]">
                        {u.numeroCuenta || 'Sin cuenta'}
                      </p>
                    </td>
                    <td className="py-3.5 px-4">
                      <div className="flex flex-wrap items-center gap-1.5">
                        {u.bloqueado ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-red-100 text-red-800 font-bold text-[11px]">
                            <Lock className="w-3 h-3" /> Bloqueado
                          </span>
                        ) : u.activo ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-bold text-[11px]">
                            <CheckCircle2 className="w-3 h-3" /> Activo
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-slate-200 text-slate-700 font-bold text-[11px]">
                            <XCircle className="w-3 h-3" /> Inactivo
                          </span>
                        )}
                        {u.biometriaActiva && (
                          <span
                            className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200 font-bold text-[10px]"
                            title="Huella digital activa"
                          >
                            <Fingerprint className="w-3 h-3" /> Huella
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <div className="inline-flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => {
                            setFeedback(null);
                            setDepositMonto('200');
                            setDepositConcepto('Ingreso de saldo por administrador');
                            setDepositingUser(u);
                          }}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-emerald-50 hover:bg-emerald-100 text-emerald-800 border border-emerald-200 font-bold transition-colors"
                          title="Ingresar dinero a esta cuenta"
                        >
                          <PlusCircle className="w-3.5 h-3.5 text-emerald-700" />
                          <span>Ingresar Saldo</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => openEditModal(u)}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-blue-50 hover:bg-blue-100 text-blue-900 font-bold transition-colors"
                          title="Editar usuario, saldo y contraseña"
                        >
                          <Edit3 className="w-3.5 h-3.5" />
                          <span>Editar</span>
                        </button>
                        {!u.esAdmin && (
                          <>
                            <button
                              type="button"
                              onClick={() => handleQuickToggleStatus(u)}
                              className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold transition-colors"
                              title={
                                u.bloqueado
                                  ? 'Desbloquear usuario'
                                  : u.activo
                                  ? 'Desactivar usuario'
                                  : 'Reactivar usuario'
                              }
                            >
                              {u.bloqueado || !u.activo ? (
                                <Unlock className="w-3.5 h-3.5 text-emerald-600" />
                              ) : (
                                <Lock className="w-3.5 h-3.5 text-amber-600" />
                              )}
                              <span>
                                {u.bloqueado ? 'Desbloquear' : u.activo ? 'Desactivar' : 'Activar'}
                              </span>
                            </button>
                            <button
                              type="button"
                              onClick={() => setDeletingUser(u)}
                              className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-red-50 hover:bg-red-100 text-red-700 font-bold transition-colors"
                              title="Borrar usuario permanentemente"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                              <span>Borrar</span>
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                {filteredUsers.length === 0 && (
                  <tr>
                    <td colSpan={5} className="py-12 text-center text-slate-500 font-semibold">
                      No se encontraron usuarios con ese criterio.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Edit User Modal */}
      {editingUser && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-label="Editar usuario"
        >
          <div className="bg-white rounded-3xl shadow-2xl border border-slate-200 w-full max-w-lg max-h-[90vh] overflow-y-auto">
            <div className="bg-gradient-to-r from-slate-900 via-blue-950 to-indigo-950 px-6 py-4 text-white flex items-center justify-between">
              <div>
                <h3 className="text-base font-extrabold">
                  Editar Usuario @{editingUser.usuario}
                </h3>
                <p className="text-xs text-blue-200 font-mono">
                  ID #{editingUser.clienteId} &bull; Cuenta {editingUser.numeroCuenta}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setEditingUser(null)}
                className="p-1.5 rounded-full hover:bg-white/15"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveEdit} className="p-6 space-y-4 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Nombre *</label>
                  <input
                    type="text"
                    required
                    value={editForm.nombre}
                    onChange={(e) => setEditForm({ ...editForm, nombre: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300"
                  />
                </div>
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Segundo Nombre</label>
                  <input
                    type="text"
                    value={editForm.segundoNombre}
                    onChange={(e) => setEditForm({ ...editForm, segundoNombre: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300"
                  />
                </div>
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Apellido Paterno *</label>
                  <input
                    type="text"
                    required
                    value={editForm.apellidoPaterno}
                    onChange={(e) => setEditForm({ ...editForm, apellidoPaterno: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300"
                  />
                </div>
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Apellido Materno *</label>
                  <input
                    type="text"
                    value={editForm.apellidoMaterno}
                    onChange={(e) => setEditForm({ ...editForm, apellidoMaterno: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300"
                  />
                </div>
                <div>
                  <label className="font-bold text-slate-700 block mb-1">CURP (18 car.)</label>
                  <input
                    type="text"
                    maxLength={18}
                    value={editForm.curp || ''}
                    onChange={(e) =>
                      setEditForm({ ...editForm, curp: e.target.value.toUpperCase() })
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 font-mono"
                  />
                </div>
                <div>
                  <label className="font-bold text-slate-700 block mb-1">RFC (13 car.)</label>
                  <input
                    type="text"
                    maxLength={13}
                    value={editForm.rfc || ''}
                    onChange={(e) =>
                      setEditForm({ ...editForm, rfc: e.target.value.toUpperCase() })
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 font-mono"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Correo Electrónico *</label>
                  <input
                    type="email"
                    required
                    value={editForm.correo}
                    onChange={(e) => setEditForm({ ...editForm, correo: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300"
                  />
                </div>
                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Teléfono Móvil (10 dígitos) *
                  </label>
                  <input
                    type="text"
                    required
                    maxLength={10}
                    value={editForm.telefonoMovil}
                    onChange={(e) =>
                      setEditForm({
                        ...editForm,
                        telefonoMovil: e.target.value.replace(/\D/g, ''),
                      })
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 font-mono"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 border-t border-slate-100">
                <div>
                  <label className="font-bold text-slate-700 flex items-center gap-1 mb-1">
                    <Users className="w-3.5 h-3.5 text-blue-800" /> Usuario *
                  </label>
                  <input
                    type="text"
                    required
                    disabled={editingUser.esAdmin}
                    value={editForm.usuario}
                    onChange={(e) => setEditForm({ ...editForm, usuario: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 font-mono disabled:bg-slate-100"
                  />
                </div>
                <div>
                  <label className="font-bold text-slate-700 flex items-center gap-1 mb-1">
                    <Wallet className="w-3.5 h-3.5 text-emerald-700" /> Saldo Disponible (MXN) *
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    required
                    value={editForm.saldo}
                    onChange={(e) =>
                      setEditForm({ ...editForm, saldo: Number(e.target.value) })
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 font-bold text-blue-950"
                  />
                </div>
              </div>

              <div>
                <label className="font-bold text-slate-700 flex items-center gap-1 mb-1">
                  <KeyRound className="w-3.5 h-3.5 text-amber-600" /> Restablecer Contraseña
                  (opcional)
                </label>
                <input
                  type="text"
                  placeholder="Dejar en blanco para mantener la contraseña actual"
                  value={editForm.nuevaContrasena}
                  onChange={(e) => setEditForm({ ...editForm, nuevaContrasena: e.target.value })}
                  className="w-full px-3 py-2 rounded-xl border border-slate-300"
                />
              </div>

              {!editingUser.esAdmin && (
                <div className="flex items-center justify-between gap-4 p-3 rounded-2xl bg-slate-50 border border-slate-200">
                  <label className="flex items-center gap-2 font-bold text-slate-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={Boolean(editForm.activo)}
                      onChange={(e) => setEditForm({ ...editForm, activo: e.target.checked })}
                      className="rounded"
                    />
                    <span>Cuenta Activa</span>
                  </label>
                  <label className="flex items-center gap-2 font-bold text-red-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={Boolean(editForm.bloqueado)}
                      onChange={(e) => setEditForm({ ...editForm, bloqueado: e.target.checked })}
                      className="rounded"
                    />
                    <span>Acceso Bloqueado</span>
                  </label>
                </div>
              )}

              <div className="flex justify-end gap-2 pt-3 border-t border-slate-200">
                <button
                  type="button"
                  onClick={() => setEditingUser(null)}
                  className="px-4 py-2 rounded-xl border border-slate-300 font-bold text-slate-700"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSavingEdit}
                  className="inline-flex items-center gap-1.5 px-5 py-2 rounded-xl bg-blue-950 hover:bg-blue-900 text-white font-bold shadow-md disabled:opacity-50"
                >
                  <Save className="w-4 h-4" />
                  <span>{isSavingEdit ? 'Guardando...' : 'Guardar Cambios'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Create User Modal */}
      {isCreateOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-label="Crear nuevo usuario"
        >
          <div className="bg-white rounded-3xl shadow-2xl border border-slate-200 w-full max-w-lg max-h-[90vh] overflow-y-auto">
            <div className="bg-gradient-to-r from-slate-900 via-blue-950 to-indigo-950 px-6 py-4 text-white flex items-center justify-between">
              <h3 className="text-base font-extrabold">Registrar Nuevo Usuario</h3>
              <button
                type="button"
                onClick={() => setIsCreateOpen(false)}
                className="p-1.5 rounded-full hover:bg-white/15"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateUser} className="p-6 space-y-3 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Nombre *</label>
                  <input
                    type="text"
                    required
                    value={createForm.nombre}
                    onChange={(e) => setCreateForm({ ...createForm, nombre: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300"
                  />
                </div>
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Apellido(s) / Paterno *</label>
                  <input
                    type="text"
                    required
                    value={createForm.apellidoPaterno}
                    onChange={(e) =>
                      setCreateForm({ ...createForm, apellidoPaterno: e.target.value })
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300"
                  />
                </div>
              </div>

              <div>
                <label className="font-bold text-slate-700 block mb-1">Correo Real *</label>
                <input
                  type="email"
                  required
                  value={createForm.correo}
                  onChange={(e) => setCreateForm({ ...createForm, correo: e.target.value })}
                  className="w-full px-3 py-2 rounded-xl border border-slate-300"
                />
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Teléfono (10 díg) *</label>
                  <input
                    type="text"
                    required
                    maxLength={10}
                    value={createForm.telefonoMovil}
                    onChange={(e) =>
                      setCreateForm({
                        ...createForm,
                        telefonoMovil: e.target.value.replace(/\D/g, ''),
                      })
                    }
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 font-mono"
                  />
                </div>
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Usuario *</label>
                  <input
                    type="text"
                    required
                    value={createForm.usuario}
                    onChange={(e) => setCreateForm({ ...createForm, usuario: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300 font-mono"
                  />
                </div>
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Contraseña *</label>
                  <input
                    type="text"
                    required
                    placeholder="Ej: Venta2026#"
                    value={createForm.contrasena}
                    onChange={(e) => setCreateForm({ ...createForm, contrasena: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl border border-slate-300"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-slate-200">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  className="px-4 py-2 rounded-xl border border-slate-300 font-bold text-slate-700"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isCreating}
                  className="inline-flex items-center gap-1.5 px-5 py-2 rounded-xl bg-blue-950 hover:bg-blue-900 text-white font-bold shadow-md disabled:opacity-50"
                >
                  <UserPlus className="w-4 h-4" />
                  <span>{isCreating ? 'Creando...' : 'Crear Usuario'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deletingUser && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-label="Confirmar eliminación"
        >
          <div className="bg-white rounded-3xl shadow-2xl border border-red-200 w-full max-w-md p-6 space-y-4">
            <div className="flex items-center gap-3 text-red-700">
              <div className="p-3 rounded-2xl bg-red-100">
                <Trash2 className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-black">¿Eliminar usuario permanentemente?</h3>
                <p className="text-xs text-slate-500">Esta acción no se puede deshacer.</p>
              </div>
            </div>
            <p className="text-xs text-slate-700 leading-relaxed">
              Se eliminará al usuario <strong>@{deletingUser.usuario}</strong> (
              {deletingUser.nombreCompleto}), su cuenta bancaria{' '}
              <span className="font-mono font-bold">{deletingUser.numeroCuenta}</span> y todos sus
              registros asociados.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setDeletingUser(null)}
                className="px-4 py-2 rounded-xl border border-slate-300 text-xs font-bold text-slate-700"
              >
                Cancelar
              </button>
              <button
                type="button"
                disabled={isDeleting}
                onClick={handleConfirmDelete}
                className="px-5 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white text-xs font-bold shadow-md disabled:opacity-50"
              >
                {isDeleting ? 'Eliminando...' : 'Sí, Eliminar Usuario'}
              </button>
            </div>
          </div>
        </div>
      )}
      {/* Ingresar Dinero (Admin Deposit) Modal */}
      {depositingUser && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-label="Ingresar saldo a cuenta"
        >
          <div className="bg-white rounded-3xl shadow-2xl border border-emerald-200 w-full max-w-md overflow-hidden">
            <div className="bg-gradient-to-r from-emerald-800 via-emerald-700 to-teal-700 px-6 py-4 text-white flex items-center justify-between">
              <div>
                <h3 className="text-base font-extrabold flex items-center gap-2">
                  <Wallet className="w-4 h-4" />
                  <span>Ingresar Dinero a @{depositingUser.usuario}</span>
                </h3>
                <p className="text-xs text-emerald-100 font-mono">
                  Cuenta {depositingUser.numeroCuenta} &bull; Actual: {formatCurrency(depositingUser.saldo)}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setDepositingUser(null)}
                className="p-1.5 rounded-full hover:bg-white/15"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleAdminDeposit} className="p-6 space-y-4 text-xs">
              <div className="grid grid-cols-4 gap-2">
                {['100', '200', '500', '1000'].map((amt) => (
                  <button
                    key={amt}
                    type="button"
                    onClick={() => setDepositMonto(amt)}
                    className={`py-2 rounded-xl font-bold border transition-colors ${
                      depositMonto === amt
                        ? 'bg-emerald-700 text-white border-emerald-700'
                        : 'bg-emerald-50 text-emerald-900 border-emerald-200 hover:bg-emerald-100'
                    }`}
                  >
                    +${amt}
                  </button>
                ))}
              </div>

              <div>
                <label className="font-bold text-slate-700 block mb-1">Monto a ingresar (MXN) *</label>
                <input
                  type="number"
                  min={1}
                  max={1000000}
                  step="0.01"
                  required
                  value={depositMonto}
                  onChange={(e) => setDepositMonto(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-xl border border-slate-300 font-mono font-bold text-sm"
                />
              </div>

              <div>
                <label className="font-bold text-slate-700 block mb-1">Concepto del depósito</label>
                <input
                  type="text"
                  maxLength={120}
                  value={depositConcepto}
                  onChange={(e) => setDepositConcepto(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl border border-slate-300"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setDepositingUser(null)}
                  className="px-4 py-2 rounded-xl border border-slate-300 font-bold text-slate-700"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isDepositing}
                  className="px-5 py-2 rounded-xl bg-emerald-700 hover:bg-emerald-600 text-white font-bold shadow-md inline-flex items-center gap-1.5 disabled:opacity-50"
                >
                  {isDepositing ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                  <span>Confirmar Ingreso</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete All Non-Admin Users Modal */}
      {isDeleteAllOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-label="Confirmar eliminación masiva"
        >
          <div className="bg-white rounded-3xl shadow-2xl border border-red-200 w-full max-w-md p-6 space-y-4">
            <div className="flex items-center gap-3 text-red-700">
              <div className="p-3 rounded-2xl bg-red-100">
                <Trash2 className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-black">¿Eliminar todos los usuarios?</h3>
                <p className="text-xs text-slate-500">Esta acción eliminará {nonAdminCount} cuentas cliente.</p>
              </div>
            </div>
            <p className="text-xs text-slate-700 leading-relaxed">
              Se eliminarán permanentemente <strong>todos los usuarios clientes ({nonAdminCount})</strong>, sus cuentas bancarias, saldos, biometría e historial de pagos. La cuenta principal <strong>@admin</strong> se conservará intacta para mantener el acceso administrativo.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                disabled={isDeletingAll}
                onClick={() => setIsDeleteAllOpen(false)}
                className="px-4 py-2 rounded-xl border border-slate-300 text-xs font-bold text-slate-700"
              >
                Cancelar
              </button>
              <button
                type="button"
                disabled={isDeletingAll}
                onClick={handleConfirmDeleteAll}
                className="px-5 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white text-xs font-bold shadow-md disabled:opacity-50"
              >
                {isDeletingAll ? 'Eliminando...' : 'Sí, Eliminar Todos'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
