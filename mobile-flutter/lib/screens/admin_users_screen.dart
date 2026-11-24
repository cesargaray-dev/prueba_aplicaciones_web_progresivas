import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../services/api_client.dart';
import '../theme/app_theme.dart';

class AdminUsersScreen extends StatefulWidget {
  final ApiClient? apiClient;

  const AdminUsersScreen({super.key, this.apiClient});

  @override
  State<AdminUsersScreen> createState() => _AdminUsersScreenState();
}

class _AdminUsersScreenState extends State<AdminUsersScreen> {
  late final ApiClient _api = widget.apiClient ?? ApiClient();
  final TextEditingController _searchController = TextEditingController();
  final NumberFormat _currency = NumberFormat.currency(locale: 'es_MX', symbol: '\$');

  List<Map<String, dynamic>> _users = [];
  bool _isLoading = true;
  bool _isDeletingAll = false;
  String? _error;
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    _loadUsers();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadUsers() async {
    if (!mounted) return;
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final data = await _api.get('/api/v1/admin/usuarios');
      if (data is List && mounted) {
        setState(() {
          _users = data.map((e) => Map<String, dynamic>.from(e as Map)).toList();
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e is ApiException ? e.message : 'No fue posible cargar los usuarios.';
          _isLoading = false;
        });
      }
    }
  }

  int get _nonAdminCount => _users.where((u) => u['esAdmin'] != true).length;

  List<Map<String, dynamic>> get _filteredUsers {
    final q = _searchQuery.trim().toLowerCase();
    if (q.isEmpty) return _users;
    return _users.where((u) {
      final name = (u['nombreCompleto'] ?? '').toString().toLowerCase();
      final usr = (u['usuario'] ?? '').toString().toLowerCase();
      final email = (u['correo'] ?? '').toString().toLowerCase();
      final tel = (u['telefonoMovil'] ?? '').toString();
      final cta = (u['numeroCuenta'] ?? '').toString();
      final curp = (u['curp'] ?? '').toString().toLowerCase();
      final rfc = (u['rfc'] ?? '').toString().toLowerCase();
      return name.contains(q) ||
          usr.contains(q) ||
          email.contains(q) ||
          tel.contains(q) ||
          cta.contains(q) ||
          curp.contains(q) ||
          rfc.contains(q);
    }).toList();
  }

  Future<void> _toggleStatus(Map<String, dynamic> user) async {
    final id = user['clienteId'];
    final isLocked = user['bloqueado'] == true;
    final isActive = user['activo'] == true;
    final nextActive = isLocked ? true : !isActive;

    try {
      await _api.put('/api/v1/admin/usuarios/$id', body: {
        'activo': nextActive,
        'bloqueado': false,
      });
      await _loadUsers();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: const Color(0xFF10B981),
            content: Text('Estado de @${user['usuario']} actualizado correctamente.'),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: AppTheme.errorRed,
            content: Text(e is ApiException ? e.message : 'Error al actualizar estado'),
          ),
        );
      }
    }
  }

  Future<void> _confirmDelete(Map<String, dynamic> user) async {
    final id = user['clienteId'];
    final usr = (user['usuario'] ?? '').toString();
    final nombre = (user['nombreCompleto'] ?? '').toString();

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Row(
          children: [
            Icon(Icons.delete_forever_rounded, color: AppTheme.errorRed),
            SizedBox(width: 8),
            Expanded(child: Text('¿Borrar usuario?')),
          ],
        ),
        content: Text(
          'Se eliminará permanentemente a @$usr ($nombre), su cuenta bancaria y todos sus registros.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancelar'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.errorRed),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Sí, Borrar'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    try {
      await _api.delete('/api/v1/admin/usuarios/$id');
      await _loadUsers();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: const Color(0xFF10B981),
            content: Text('Usuario @$usr eliminado permanentemente.'),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: AppTheme.errorRed,
            content: Text(e is ApiException ? e.message : 'No se pudo eliminar el usuario'),
          ),
        );
      }
    }
  }

  Future<void> _confirmDeleteAll() async {
    final count = _nonAdminCount;
    if (count == 0) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Row(
          children: [
            Icon(Icons.delete_sweep_rounded, color: AppTheme.errorRed),
            SizedBox(width: 8),
            Expanded(child: Text('¿Eliminar todos los usuarios?')),
          ],
        ),
        content: Text(
          'Se eliminarán permanentemente todos los usuarios clientes ($count cuentas), junto con sus saldos, biometría e historial de pagos.\n\nLa cuenta principal @admin se conservará activa.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancelar'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.errorRed),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Sí, Eliminar Todos'),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    setState(() => _isDeletingAll = true);
    try {
      final resp = await _api.delete('/api/v1/admin/usuarios');
      final msg = (resp is Map && resp['mensaje'] != null)
          ? resp['mensaje'].toString()
          : 'Se eliminaron todos los usuarios clientes.';
      await _loadUsers();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: const Color(0xFF10B981),
            content: Text(msg),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: AppTheme.errorRed,
            content: Text(
              e is ApiException ? e.message : 'No se pudieron eliminar los usuarios',
            ),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isDeletingAll = false);
      }
    }
  }

  Future<void> _openEditDialog(Map<String, dynamic> user) async {
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.cardBg(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (_) => _AdminEditUserSheet(api: _api, user: user),
    );

    if (saved == true && mounted) {
      await _loadUsers();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            backgroundColor: Color(0xFF10B981),
            content: Text('Usuario actualizado exitosamente.'),
          ),
        );
      }
    }
  }

  Future<void> _openCreateDialog() async {
    final created = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.cardBg(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (_) => _AdminCreateUserSheet(api: _api),
    );

    if (created == true && mounted) {
      await _loadUsers();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            backgroundColor: Color(0xFF10B981),
            content: Text('Usuario creado exitosamente.'),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final list = _filteredUsers;

    return RefreshIndicator(
      onRefresh: _loadUsers,
      child: Column(
        children: [
          Container(
            width: double.infinity,
            margin: const EdgeInsets.fromLTRB(16, 14, 16, 8),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFF040914), Color(0xFF0C1E4A), Color(0xFF1D4ED8)],
              ),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Flexible(
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.admin_panel_settings_rounded,
                            color: Color(0xFFFDE68A),
                            size: 20,
                          ),
                          SizedBox(width: 8),
                          Flexible(
                            child: Text(
                              'GESTIÓN TOTAL DE USUARIOS',
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                color: Color(0xFFFDE68A),
                                fontWeight: FontWeight.w900,
                                fontSize: 12,
                                letterSpacing: 1.0,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      visualDensity: VisualDensity.compact,
                      onPressed: _loadUsers,
                      icon: const Icon(Icons.refresh_rounded, color: Colors.white, size: 18),
                      tooltip: 'Actualizar lista',
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  children: [
                    FilledButton.tonalIcon(
                      onPressed: _openCreateDialog,
                      style: FilledButton.styleFrom(
                        backgroundColor: const Color(0xFFF59E0B),
                        foregroundColor: const Color(0xFF040914),
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                        minimumSize: Size.zero,
                      ),
                      icon: const Icon(Icons.person_add_alt_1_rounded, size: 15),
                      label: const Text(
                        'Nuevo',
                        style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w900),
                      ),
                    ),
                    FilledButton.tonalIcon(
                      onPressed: (_isLoading || _isDeletingAll || _nonAdminCount == 0)
                          ? null
                          : _confirmDeleteAll,
                      style: FilledButton.styleFrom(
                        backgroundColor: AppTheme.errorRed,
                        disabledBackgroundColor: AppTheme.errorRed.withValues(alpha: 0.35),
                        foregroundColor: Colors.white,
                        disabledForegroundColor: Colors.white70,
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                        minimumSize: Size.zero,
                      ),
                      icon: _isDeletingAll
                          ? const SizedBox(
                              width: 13,
                              height: 13,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                            )
                          : const Icon(Icons.delete_sweep_rounded, size: 15),
                      label: const Text(
                        'Eliminar Todos',
                        style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w900),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _searchController,
                  onChanged: (v) => setState(() => _searchQuery = v),
                  style: TextStyle(fontSize: 13, color: AppTheme.textPrimary(context)),
                  decoration: InputDecoration(
                    hintText: 'Buscar por nombre, @usuario, correo o cuenta...',
                    prefixIcon: const Icon(Icons.search_rounded, size: 18),
                    filled: true,
                    fillColor: AppTheme.cardBg(context),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                    ? Center(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.error_outline, color: AppTheme.errorRed, size: 42),
                              const SizedBox(height: 8),
                              Text(_error!, textAlign: TextAlign.center),
                              const SizedBox(height: 12),
                              ElevatedButton(
                                onPressed: _loadUsers,
                                child: const Text('Reintentar'),
                              ),
                            ],
                          ),
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
                        itemCount: list.length,
                        itemBuilder: (context, index) {
                          final u = list[index];
                          final isAdmin = u['esAdmin'] == true;
                          final isActive = u['activo'] == true;
                          final isLocked = u['bloqueado'] == true;
                          final saldo = (u['saldo'] is num)
                              ? (u['saldo'] as num).toDouble()
                              : double.tryParse(u['saldo']?.toString() ?? '0') ?? 0.0;

                          return Card(
                            margin: const EdgeInsets.only(bottom: 10),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(18),
                              side: BorderSide(
                                color: isAdmin
                                    ? const Color(0xFFF59E0B).withValues(alpha: 0.5)
                                    : AppTheme.borderCol(context),
                              ),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.all(14),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Expanded(
                                        child: Text(
                                          (u['nombreCompleto'] ?? '').toString(),
                                          style: TextStyle(
                                            fontWeight: FontWeight.w800,
                                            fontSize: 14,
                                            color: AppTheme.textPrimary(context),
                                          ),
                                        ),
                                      ),
                                      Container(
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 8,
                                          vertical: 3,
                                        ),
                                        decoration: BoxDecoration(
                                          color: isLocked
                                              ? AppTheme.errorRed.withValues(alpha: 0.12)
                                              : isActive
                                                  ? const Color(0xFF10B981).withValues(alpha: 0.12)
                                                  : AppTheme.elevatedBg(context),
                                          borderRadius: BorderRadius.circular(12),
                                        ),
                                        child: Text(
                                          isLocked
                                              ? 'BLOQUEADO'
                                              : isActive
                                                  ? 'ACTIVO'
                                                  : 'INACTIVO',
                                          style: TextStyle(
                                            fontSize: 10,
                                            fontWeight: FontWeight.w800,
                                            color: isLocked
                                                ? AppTheme.errorRed
                                                : isActive
                                                    ? const Color(0xFF10B981)
                                                    : AppTheme.textSecondary(context),
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '@${u['usuario']} • ${u['correo']} • ${u['telefonoMovil']}',
                                    style: TextStyle(
                                      fontSize: 11.5,
                                      color: AppTheme.textSecondary(context),
                                    ),
                                  ),
                                  if ((u['curp'] ?? '').toString().isNotEmpty ||
                                      (u['rfc'] ?? '').toString().isNotEmpty) ...[
                                    const SizedBox(height: 3),
                                    Text(
                                      'CURP: ${u['curp'] ?? '—'} · RFC: ${u['rfc'] ?? '—'}',
                                      style: TextStyle(
                                        fontSize: 10.5,
                                        fontFamily: 'monospace',
                                        fontWeight: FontWeight.w600,
                                        color: AppTheme.brandAccent(context),
                                      ),
                                    ),
                                  ],
                                  const SizedBox(height: 8),
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text(
                                        'Cuenta: ${u['numeroCuenta'] ?? 'N/A'}',
                                        style: TextStyle(
                                          fontFamily: 'monospace',
                                          fontSize: 11.5,
                                          fontWeight: FontWeight.w600,
                                          color: AppTheme.textPrimary(context),
                                        ),
                                      ),
                                      Text(
                                        '${_currency.format(saldo)} MXN',
                                        style: TextStyle(
                                          fontWeight: FontWeight.w900,
                                          fontSize: 14,
                                          color: AppTheme.brandAccent(context),
                                        ),
                                      ),
                                    ],
                                  ),
                                  const Divider(height: 18),
                                  Wrap(
                                    alignment: WrapAlignment.end,
                                    spacing: 4,
                                    children: [
                                      TextButton.icon(
                                        onPressed: () => _openEditDialog(u),
                                        icon: const Icon(Icons.edit_rounded, size: 16),
                                        label: const Text('Editar'),
                                      ),
                                      if (!isAdmin) ...[
                                        TextButton.icon(
                                          onPressed: () => _toggleStatus(u),
                                          icon: Icon(
                                            isLocked || !isActive
                                                ? Icons.lock_open_rounded
                                                : Icons.block_rounded,
                                            size: 16,
                                          ),
                                          label: Text(
                                            isLocked
                                                ? 'Desbloquear'
                                                : isActive
                                                    ? 'Desactivar'
                                                    : 'Activar',
                                          ),
                                        ),
                                        TextButton.icon(
                                          style: TextButton.styleFrom(
                                            foregroundColor: AppTheme.errorRed,
                                          ),
                                          onPressed: () => _confirmDelete(u),
                                          icon: const Icon(Icons.delete_outline_rounded, size: 16),
                                          label: const Text('Borrar'),
                                        ),
                                      ],
                                    ],
                                  ),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }
}

class _AdminEditUserSheet extends StatefulWidget {
  final ApiClient api;
  final Map<String, dynamic> user;

  const _AdminEditUserSheet({required this.api, required this.user});

  @override
  State<_AdminEditUserSheet> createState() => _AdminEditUserSheetState();
}

class _AdminEditUserSheetState extends State<_AdminEditUserSheet> {
  late final TextEditingController _nombreCtrl;
  late final TextEditingController _segundoCtrl;
  late final TextEditingController _paternoCtrl;
  late final TextEditingController _maternoCtrl;
  late final TextEditingController _curpCtrl;
  late final TextEditingController _rfcCtrl;
  late final TextEditingController _correoCtrl;
  late final TextEditingController _telCtrl;
  late final TextEditingController _usrCtrl;
  late final TextEditingController _saldoCtrl;
  late final TextEditingController _passCtrl;

  late bool _activo;
  late bool _bloqueado;
  bool _isSaving = false;
  bool _didClose = false;
  String? _modalError;

  @override
  void initState() {
    super.initState();
    final u = widget.user;
    _nombreCtrl = TextEditingController(text: (u['nombre'] ?? '').toString());
    _segundoCtrl = TextEditingController(text: (u['segundoNombre'] ?? '').toString());
    _paternoCtrl = TextEditingController(text: (u['apellidoPaterno'] ?? '').toString());
    _maternoCtrl = TextEditingController(text: (u['apellidoMaterno'] ?? '').toString());
    _curpCtrl = TextEditingController(text: (u['curp'] ?? '').toString());
    _rfcCtrl = TextEditingController(text: (u['rfc'] ?? '').toString());
    _correoCtrl = TextEditingController(text: (u['correo'] ?? '').toString());
    _telCtrl = TextEditingController(text: (u['telefonoMovil'] ?? '').toString());
    _usrCtrl = TextEditingController(text: (u['usuario'] ?? '').toString());
    _saldoCtrl = TextEditingController(text: (u['saldo'] ?? '0').toString());
    _passCtrl = TextEditingController();
    _activo = u['activo'] == true;
    _bloqueado = u['bloqueado'] == true;
  }

  @override
  void dispose() {
    _nombreCtrl.dispose();
    _segundoCtrl.dispose();
    _paternoCtrl.dispose();
    _maternoCtrl.dispose();
    _curpCtrl.dispose();
    _rfcCtrl.dispose();
    _correoCtrl.dispose();
    _telCtrl.dispose();
    _usrCtrl.dispose();
    _saldoCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  void _closeSheet(bool result) {
    if (!mounted || _didClose) return;
    final route = ModalRoute.of(context);
    if (route != null && route.isActive && route.isCurrent) {
      _didClose = true;
      Navigator.of(context).pop(result);
    }
  }

  Future<void> _saveChanges() async {
    if (_isSaving) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _isSaving = true;
      _modalError = null;
    });

    final id = widget.user['clienteId'];
    final body = <String, dynamic>{
      'nombre': _nombreCtrl.text.trim(),
      'segundoNombre': _segundoCtrl.text.trim(),
      'apellidoPaterno': _paternoCtrl.text.trim(),
      'apellidoMaterno': _maternoCtrl.text.trim(),
      if (_curpCtrl.text.trim().isNotEmpty) 'curp': _curpCtrl.text.trim().toUpperCase(),
      if (_rfcCtrl.text.trim().isNotEmpty) 'rfc': _rfcCtrl.text.trim().toUpperCase(),
      'correo': _correoCtrl.text.trim(),
      'telefonoMovil': _telCtrl.text.trim(),
      'usuario': _usrCtrl.text.trim(),
      'saldo': double.tryParse(_saldoCtrl.text.trim()) ?? 0.0,
      'activo': _activo,
      'bloqueado': _bloqueado,
    };
    if (_passCtrl.text.trim().isNotEmpty) {
      body['nuevaContrasena'] = _passCtrl.text.trim();
    }

    try {
      await widget.api.put('/api/v1/admin/usuarios/$id', body: body);
      _closeSheet(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _isSaving = false;
          _modalError = e is ApiException ? e.message : 'Error al guardar cambios';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isAdmin = widget.user['esAdmin'] == true;

    return PopScope(
      canPop: !_isSaving,
      child: Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 20,
          bottom: MediaQuery.of(context).viewInsets.bottom + 24,
        ),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Text(
                      'Editar @${widget.user['usuario']}',
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w900,
                        color: AppTheme.textPrimary(context),
                      ),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close_rounded),
                    onPressed: _isSaving ? null : () => _closeSheet(false),
                  ),
                ],
              ),
              if (_modalError != null) ...[
                const SizedBox(height: 8),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppTheme.errorRed.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppTheme.errorRed.withValues(alpha: 0.4)),
                  ),
                  child: Text(
                    _modalError!,
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: AppTheme.errorRed,
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _nombreCtrl,
                      decoration: const InputDecoration(labelText: 'Nombre *'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: _segundoCtrl,
                      decoration: const InputDecoration(labelText: 'Segundo Nombre'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _paternoCtrl,
                      decoration: const InputDecoration(labelText: 'Apellido Paterno *'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: _maternoCtrl,
                      decoration: const InputDecoration(labelText: 'Apellido Materno (opcional)'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _curpCtrl,
                      maxLength: 18,
                      textCapitalization: TextCapitalization.characters,
                      decoration: const InputDecoration(
                        labelText: 'CURP (18 car.)',
                        counterText: '',
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: _rfcCtrl,
                      maxLength: 13,
                      textCapitalization: TextCapitalization.characters,
                      decoration: const InputDecoration(
                        labelText: 'RFC (13 car.)',
                        counterText: '',
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _correoCtrl,
                keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(labelText: 'Correo Electrónico *'),
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _telCtrl,
                      maxLength: 10,
                      keyboardType: TextInputType.phone,
                      decoration: const InputDecoration(
                        labelText: 'Teléfono (10 díg) *',
                        counterText: '',
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: _usrCtrl,
                      enabled: !isAdmin,
                      decoration: const InputDecoration(labelText: 'Usuario *'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _saldoCtrl,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.textPrimary(context),
                      ),
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(
                        labelText: 'Saldo Disponible (MXN) *',
                        prefixText: '\$ ',
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: _passCtrl,
                      style: TextStyle(
                        fontSize: 14,
                        color: AppTheme.textPrimary(context),
                      ),
                      decoration: const InputDecoration(
                        labelText: 'Nueva Contraseña (opcional)',
                      ),
                    ),
                  ),
                ],
              ),
              if (!isAdmin) ...[
                const SizedBox(height: 10),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  activeThumbColor: AppTheme.accentEmerald,
                  title: Text(
                    'Cuenta Activa',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.textPrimary(context),
                    ),
                  ),
                  value: _activo,
                  onChanged: _isSaving ? null : (v) => setState(() => _activo = v),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  activeThumbColor: AppTheme.errorRed,
                  title: Text(
                    'Acceso Bloqueado',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.textPrimary(context),
                    ),
                  ),
                  value: _bloqueado,
                  onChanged: _isSaving ? null : (v) => setState(() => _bloqueado = v),
                ),
              ],
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.brandAccent(context),
                    foregroundColor: Colors.white,
                    disabledBackgroundColor: AppTheme.elevatedBg(context),
                    disabledForegroundColor: AppTheme.textSecondary(context),
                  ),
                  icon: _isSaving
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Icon(Icons.save_rounded),
                  label: Text(_isSaving ? 'Guardando...' : 'Guardar Cambios'),
                  onPressed: _isSaving ? null : _saveChanges,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AdminCreateUserSheet extends StatefulWidget {
  final ApiClient api;

  const _AdminCreateUserSheet({required this.api});

  @override
  State<_AdminCreateUserSheet> createState() => _AdminCreateUserSheetState();
}

class _AdminCreateUserSheetState extends State<_AdminCreateUserSheet> {
  final _nombreCtrl = TextEditingController();
  final _paternoCtrl = TextEditingController();
  final _curpCtrl = TextEditingController();
  final _rfcCtrl = TextEditingController();
  final _correoCtrl = TextEditingController();
  final _telCtrl = TextEditingController();
  final _usrCtrl = TextEditingController();
  final _passCtrl = TextEditingController();

  bool _isSaving = false;
  bool _didClose = false;
  String? _modalError;

  @override
  void dispose() {
    _nombreCtrl.dispose();
    _paternoCtrl.dispose();
    _curpCtrl.dispose();
    _rfcCtrl.dispose();
    _correoCtrl.dispose();
    _telCtrl.dispose();
    _usrCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  void _closeSheet(bool result) {
    if (!mounted || _didClose) return;
    final route = ModalRoute.of(context);
    if (route != null && route.isActive && route.isCurrent) {
      _didClose = true;
      Navigator.of(context).pop(result);
    }
  }

  Future<void> _createUser() async {
    if (_isSaving) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _isSaving = true;
      _modalError = null;
    });

    try {
      await widget.api.post('/api/v1/admin/usuarios', body: {
        'nombre': _nombreCtrl.text.trim(),
        'apellidoPaterno': _paternoCtrl.text.trim(),
        if (_curpCtrl.text.trim().isNotEmpty) 'curp': _curpCtrl.text.trim().toUpperCase(),
        if (_rfcCtrl.text.trim().isNotEmpty) 'rfc': _rfcCtrl.text.trim().toUpperCase(),
        'correo': _correoCtrl.text.trim(),
        'telefonoMovil': _telCtrl.text.trim(),
        'usuario': _usrCtrl.text.trim(),
        'contrasena': _passCtrl.text,
      });
      _closeSheet(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _isSaving = false;
          _modalError = e is ApiException ? e.message : 'Error al crear usuario';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !_isSaving,
      child: Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 20,
          bottom: MediaQuery.of(context).viewInsets.bottom + 24,
        ),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Crear Nuevo Usuario',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w900,
                      color: AppTheme.textPrimary(context),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close_rounded),
                    onPressed: _isSaving ? null : () => _closeSheet(false),
                  ),
                ],
              ),
              if (_modalError != null) ...[
                const SizedBox(height: 8),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppTheme.errorRed.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppTheme.errorRed.withValues(alpha: 0.4)),
                  ),
                  child: Text(
                    _modalError!,
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: AppTheme.errorRed,
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _nombreCtrl,
                      decoration: const InputDecoration(labelText: 'Nombre *'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: _paternoCtrl,
                      decoration: const InputDecoration(labelText: 'Apellido(s) *'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _telCtrl,
                maxLength: 10,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(
                  labelText: 'Teléfono (10 díg) *',
                  counterText: '',
                ),
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _curpCtrl,
                      maxLength: 18,
                      textCapitalization: TextCapitalization.characters,
                      decoration: const InputDecoration(
                        labelText: 'CURP (18 car.)',
                        counterText: '',
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: _rfcCtrl,
                      maxLength: 13,
                      textCapitalization: TextCapitalization.characters,
                      decoration: const InputDecoration(
                        labelText: 'RFC (13 car.)',
                        counterText: '',
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _correoCtrl,
                keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(labelText: 'Correo Electrónico Real *'),
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _usrCtrl,
                      decoration: const InputDecoration(labelText: 'Usuario *'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: _passCtrl,
                      decoration: const InputDecoration(labelText: 'Contraseña (ej. Venta2026#) *'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  icon: _isSaving
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Icon(Icons.person_add_alt_1_rounded),
                  label: Text(_isSaving ? 'Creando...' : 'Crear Usuario'),
                  onPressed: _isSaving ? null : _createUser,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
