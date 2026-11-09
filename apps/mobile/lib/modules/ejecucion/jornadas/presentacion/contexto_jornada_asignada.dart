import 'package:flutter/material.dart';

/// Solo presentación del contexto autenticado o de su copia por cuenta.
/// No concede acceso ni cambia la captura o sincronización de evidencias.
class ContextoJornadaAsignada extends StatelessWidget {
  const ContextoJornadaAsignada({super.key, required this.jornada});
  final Map<String, dynamic> jornada;

  static Map<String, dynamic> _mapa(Object? valor) =>
      valor is Map<String, dynamic> ? valor : const {};
  static String _texto(Object? valor) =>
      valor == null || valor.toString().trim().isEmpty
          ? 'No disponible'
          : valor.toString();

  @override
  Widget build(BuildContext context) {
    final datos = _mapa(jornada['contextoOperativo']);
    final periodo = _mapa(datos['periodo']);
    final ruta = _mapa(datos['ruta']);
    final vehiculo = _mapa(datos['vehiculo']);
    return ExpansionTile(
      title: const Text('Contexto operativo de la jornada'),
      subtitle: Text('Vehículo: ${_texto(vehiculo['placa'])} · '
          'Ruta: ${_texto(ruta['codigo'])} · Versión: ${_texto(ruta['numeroVersion'])}'),
      childrenPadding: const EdgeInsets.only(bottom: 12),
      children: [
        if (datos.isEmpty)
          const Text('Contexto operativo no disponible en esta copia. '
              'Actualice mis jornadas cuando tenga conexión.'),
        Text('Jornada: ${_texto(jornada['jornadaId'])}'),
        Text('Inicio programado: ${_texto(jornada['inicioProgramado'])}'),
        Text('Fin programado: ${_texto(jornada['finProgramado'])}'),
        const Text('Los instantes del servidor se presentan en formato ISO 8601; Z indica UTC.'),
        Text('Asignación: ${_texto(jornada['asignacionJornadaId'])}'),
        Text('Desde: ${_texto(jornada['vigenteDesde'])}'),
        Text('Hasta: ${_texto(jornada['vigenteHasta'])}'),
        if (jornada['vigenteHasta'] != null) const Text('Asignación histórica cerrada'),
        Text('Empresa del periodo: ${_texto(periodo['razonSocial'])}'),
        Text('Periodo: ${_texto(periodo['id'])}'),
        Text('Fechas del periodo: ${_texto(periodo['fechaInicio'])} — ${_texto(periodo['fechaFin'])}'),
        Text('Ruta de referencia: ${_texto(ruta['nombre'])}'),
        Text('Ruta ID: ${_texto(ruta['rutaId'])}'),
        Text('Versión de ruta asignada: ${_texto(ruta['rutaVersionId'])}'),
        Text('Vehículo ID: ${_texto(vehiculo['id'])}'),
        Text('Año del vehículo: ${_texto(vehiculo['anio'])}'),
        Text('Vínculo de responsable: ${_texto(datos['vinculoPersonaEmpresaId'])}'),
        Text('Vínculo de vehículo: ${_texto(datos['vinculoVehiculoEmpresaId'])}'),
      ],
    );
  }
}
