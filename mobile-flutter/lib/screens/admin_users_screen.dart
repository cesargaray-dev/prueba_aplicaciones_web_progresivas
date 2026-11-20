import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../services/api_client.dart';
import '../theme/app_theme.dart';

class AdminUsersScreen extends StatefulWidget {
  const AdminUsersScreen({super.key});

  @override
  State<AdminUsersScreen> createState() => _AdminUsersScreenState();
}

class _AdminUsersScreenState extends State<AdminUsersScreen> {
  final ApiClient _api = ApiClient();
  final TextEditingController _searchController = TextEditingController();
  final NumberFormat _currency = NumberFormat.currency(locale: 'es_MX', symbol: '\$');

  List<Map<String, dynamic>> _users = [];
  bool _isLoading = true;
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

  List<Map<String, dynamic>> get _filteredUsers {
    final q = _searchQuery.trim().toLowerCase();
    if (q.isEmpty) return _users;
    return _users.where((u) {
      final name = (u['nombreCompleto'] ?? '').toString().toLowerCase();
      final usr = (u['usuario'] ?? '').toString().toLowerCase();
      final email = (u['correo'] ?? '').toString().toLowerCase();
      final tel = (u['telefonoMovil'] ?? '').toString();
      final cta = (u['numeroCuenta'] ?? '').toString();
      return name.contains(q) ||
          usr.contains(q) ||
          email.contains(q) ||
          tel.contains(q) ||
          cta.contains(q);
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
            content: Text(
              'Estado de @${user['usuario']} actualizado correctamente.',
            ),
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

  Future<void> _openEditDialog(Map<String, dynamic> user) async {
    final id = user['clienteId'];
    final isAdmin = user['esAdmin'] == true;
    final nombreCtrl = TextEditingController(text: (user['nombre'] ?? '').toString());
    final segundoCtrl = TextEditingController(text: (user['segundoNombre'] ?? '').toString());
    final paternoCtrl = TextEditingController(text: (user['apellidoPaterno'] ?? '').toString());
    final maternoCtrl = TextEditingController(text: (user['apellidoMaterno'] ?? '').toString());
    final correoCtrl = TextEditingController(text: (user['correo'] ?? '').toString());
    final telCtrl = TextEditingController(text: (user['telefonoMovil'] ?? '').toString());
    final usrCtrl = TextEditingController(text: (user['usuario'] ?? '').toString());
    final saldoCtrl = TextEditingController(text: (user['saldo'] ?? '0').toString());
    final passCtrl = TextEditingController();
    bool activo = user['activo'] == true;
    bool bloqueado = user['bloqueado'] == true;

    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.cardBg(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            return Padding(
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
                          'Editar @${user['usuario']}',
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w900,
                            color: AppTheme.primaryNavy,
                          ),
                        ),
                        IconButton(
                          icon: const Icon(Icons.close_rounded),
                          onPressed: () => Navigator.of(ctx).pop(),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: nombreCtrl,
                            decoration: const InputDecoration(labelText: 'Nombre *'),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: TextField(
                            controller: segundoCtrl,
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
                            controller: paternoCtrl,
                            decoration: const InputDecoration(labelText: 'Apellido Paterno *'),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: TextField(
                            controller: maternoCtrl,
                            decoration: const InputDecoration(labelText: 'Apellido Materno *'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      controller: correoCtrl,
                      keyboardType: TextInputType.emailAddress,
                      decoration: const InputDecoration(labelText: 'Correo Electrónico *'),
                    ),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: telCtrl,
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
                            controller: usrCtrl,
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
                            controller: saldoCtrl,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            decoration: const InputDecoration(labelText: 'Saldo Disponible (MXN) *'),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: TextField(
                            controller: passCtrl,
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
                        title: const Text(
                          'Cuenta Activa',
                          style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
                        ),
                        value: activo,
                        onChanged: (v) => setModalState(() => activo = v),
                      ),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text(
                          'Acceso Bloqueado',
                          style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
                        ),
                        value: bloqueado,
                        onChanged: (v) => setModalState(() => bloqueado = v),
                      ),
                    ],
                    const SizedBox(height: 16),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        icon: const Icon(Icons.save_rounded),
                        label: const Text('Guardar Cambios'),
                        onPressed: () async {
                          final navigator = Navigator.of(ctx);
                          final messenger = ScaffoldMessenger.of(this.context);
                          try {
                            final body = <String, dynamic>{
                              'nombre': nombreCtrl.text.trim(),
                              'segundoNombre': segundoCtrl.text.trim(),
                              'apellidoPaterno': paternoCtrl.text.trim(),
                              'apellidoMaterno': maternoCtrl.text.trim(),
                              'correo': correoCtrl.text.trim(),
                              'telefonoMovil': telCtrl.text.trim(),
                              'usuario': usrCtrl.text.trim(),
                              'saldo': double.tryParse(saldoCtrl.text.trim()) ?? 0.0,
                              'activo': activo,
                              'bloqueado': bloqueado,
                            };
                            if (passCtrl.text.trim().isNotEmpty) {
                              body['nuevaContrasena'] = passCtrl.text.trim();
                            }
                            await _api.put('/api/v1/admin/usuarios/$id', body: body);
                            navigator.pop();
                            await _loadUsers();
                            messenger.showSnackBar(
                              const SnackBar(
                                backgroundColor: Color(0xFF10B981),
                                content: Text('Usuario actualizado exitosamente.'),
                              ),
                            );
                          } catch (e) {
                            messenger.showSnackBar(
                              SnackBar(
                                backgroundColor: AppTheme.errorRed,
                                content: Text(
                                  e is ApiException ? e.message : 'Error al guardar cambios',
                                ),
                              ),
                            );
                          }
                        },
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
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
                    const Row(
                      children: [
                        Icon(
                          Icons.admin_panel_settings_rounded,
                          color: Color(0xFFFDE68A),
                          size: 20,
                        ),
                        SizedBox(width: 8),
                        Text(
                          'CONSOLA ADMIN • USUARIOS',
                          style: TextStyle(
                            color: Color(0xFFFDE68A),
                            fontWeight: FontWeight.w900,
                            fontSize: 12,
                            letterSpacing: 1.1,
                          ),
                        ),
                      ],
                    ),
                    Text(
                      '${_users.length} registrados',
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _searchController,
                  onChanged: (v) => setState(() => _searchQuery = v),
                  style: const TextStyle(fontSize: 13),
                  decoration: InputDecoration(
                    hintText: 'Buscar por nombre, @usuario, correo o cuenta...',
                    prefixIcon: const Icon(Icons.search_rounded, size: 18),
                    filled: true,
                    fillColor: Colors.white,
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
                                    : Colors.grey.shade200,
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
                                                  : Colors.grey.shade200,
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
                                                    : Colors.grey.shade700,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '@${u['usuario']} • ${u['correo']} • ${u['telefonoMovil']}',
                                    style: TextStyle(fontSize: 11.5, color: Colors.grey.shade700),
                                  ),
                                  const SizedBox(height: 8),
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text(
                                        'Cuenta: ${u['numeroCuenta'] ?? 'N/A'}',
                                        style: const TextStyle(
                                          fontFamily: 'monospace',
                                          fontSize: 11.5,
                                          fontWeight: FontWeight.w600,
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
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.end,
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
