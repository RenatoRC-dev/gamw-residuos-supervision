// QA automatizada en EMULADOR Android (no es QA física): app real, SQLite y KeyStore nativos, HTTP real contra un backend/BD de
// prueba aislados con datos sintéticos. Las posiciones GPS son SINTÉTICAS (coordenadas y marcas de tiempo conocidas) inyectadas en
// el almacén local; NO prueban el proveedor GPS nativo, la batería ni el servicio en segundo plano (siguen PENDING: dispositivo físico).
//
// Orquestado por backend AndroidEmuladorQaIT en 4 fases (un `flutter drive` por fase; la app conserva SQLite y sesión entre fases):
//   1 en línea: sesión A, INICIO local (jornada A) y 3 posiciones sintéticas desordenadas, sin sincronizar.
//   2 SIN RED (el arnés retiró `adb reverse` y comprobó la indisponibilidad): reinicio, pendientes intactos, sincronizar no altera
//     nada, FIN local y INICIO local de la jornada C, todo pendiente.
//   3 en línea como cuenta B: la evidencia de A no se envía ni se revela.
//   4 en línea como cuenta A: sincronización completa e idempotente (dos pasadas).
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:gamw_mobile/modules/ejecucion/gps/aplicacion/sincronizar_posiciones_gps.dart';
import 'package:gamw_mobile/modules/ejecucion/gps/dominio/posicion_gps_local.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/aplicacion/cola_ejecucion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/dominio/evento_ejecucion_local.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/infraestructura/cliente_ejecucion_autenticada.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/persistencia/almacen_eventos_sqlite.dart';
import 'package:gamw_mobile/shared/api/cliente_backend.dart';
import 'package:gamw_mobile/shared/auth/almacen_sesion_seguro.dart';
import 'package:gamw_mobile/shared/auth/sesion.dart';

const _fase = int.fromEnvironment('GAMW_QA_FASE');
const _api = String.fromEnvironment('GAMW_QA_API_URL');
const _usuarioA = String.fromEnvironment('GAMW_QA_USUARIO_A');
const _usuarioB = String.fromEnvironment('GAMW_QA_USUARIO_B');
const _clave = String.fromEnvironment('GAMW_QA_CLAVE');
const _jornadaA = String.fromEnvironment('GAMW_QA_JORNADA_A');
const _asignacionA = String.fromEnvironment('GAMW_QA_ASIGNACION_A');
const _jornadaC = String.fromEnvironment('GAMW_QA_JORNADA_C');
const _asignacionC = String.fromEnvironment('GAMW_QA_ASIGNACION_C');
const _baseIso = String.fromEnvironment('GAMW_QA_BASE_ISO'); // instante UTC conocido, en el pasado
const _p1 = 'aaaaaaaa-0000-4000-8000-000000000001', _p2 = 'aaaaaaaa-0000-4000-8000-000000000002', _p3 = 'aaaaaaaa-0000-4000-8000-000000000003';

PosicionGpsLocal _posicion(String id, int segundos, double lat, double lon, double precision, String usuario, DateTime base) => PosicionGpsLocal(
  capturaId: id, jornadaId: _jornadaA, asignacionJornadaId: _asignacionA, usuarioIdCaptura: usuario,
  capturadoEn: base.add(Duration(seconds: segundos)), latitud: lat, longitud: lon, precisionMetros: precision);

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('QA emulador CU-13 fase $_fase', (tester) async {
    expect(_fase, inInclusiveRange(1, 4), reason: 'Falta GAMW_QA_FASE');
    expect(_api, startsWith('http://127.0.0.1:'), reason: 'El cliente solo admite HTTP por loopback; el arnés usa adb reverse y lo retira en la fase sin red.');
    final base = DateTime.parse(_baseIso).toUtc();
    final api = ClienteBackend(Uri.parse(_api));
    final sesiones = GestorSesion(api, AlmacenSesionSeguro());
    final almacen = await AlmacenEventosSqlite.abrir();
    var reloj = base;
    final cliente = ClienteEjecucionAutenticada(sesiones);
    final cola = ColaEjecucion(almacen, cliente, ahora: () => reloj,
      sincronizarGps: SincronizarPosicionesGps(almacen, sesiones).sincronizar, confirmarInicioServidor: cliente.confirmarInicio);

    Future<void> servidorAlcanzable() async { await api.solicitar('GET', 'technical/health'); }
    Future<void> servidorInalcanzable() async {
      await expectLater(api.solicitar('GET', 'technical/health'), throwsA(isA<SocketException>()),
        reason: 'La fase sin red exige que la app NO alcance el backend (adb reverse retirado).');
    }
    Future<List<EventoEjecucionLocal>> eventos(String jornada) async => (await almacen.listar()).where((e) => e.jornadaId == jornada).toList();

    try {
      switch (_fase) {
        case 1: {
          await servidorAlcanzable();
          await sesiones.iniciar(_usuarioA, _clave);
          final a = sesiones.actual!.usuarioId;
          reloj = base;
          await cola.registrarLocal(_jornadaA, _asignacionA, TipoEventoEjecucion.inicio, usuarioIdCaptura: a);
          // Capturas desordenadas: se registran 3, 1, 2; el orden lo define capturado_en, no el de llegada.
          await almacen.registrarPosicion(_posicion(_p3, 3, 20.30, -10.90, 9.5, a, base));
          await almacen.registrarPosicion(_posicion(_p1, 1, 20.10, -10.70, 5.5, a, base));
          await almacen.registrarPosicion(_posicion(_p2, 2, 20.20, -10.80, 7.5, a, base));
          expect((await eventos(_jornadaA)).single.estadoSincronizacion, EstadoSincronizacion.pendiente);
          expect((await almacen.listarPosiciones(a)).map((p) => p.capturaId), [_p1, _p2, _p3]); // orden cronológico local
        }
        case 2: {
          await servidorInalcanzable();
          await sesiones.restaurar(); // reinicio sin red: la identidad persistida se conserva, sin validar contra el servidor
          final a = sesiones.actual!.usuarioId;
          expect(sesiones.actual!.nombreUsuario, _usuarioA);
          final inicio = (await eventos(_jornadaA)).single;
          expect(inicio.tipoEvento, TipoEventoEjecucion.inicio);
          expect(inicio.capturadoEn.toUtc(), base);
          expect(inicio.usuarioIdCaptura, a);
          expect(inicio.estadoSincronizacion, EstadoSincronizacion.pendiente);
          final antes = await almacen.listarPosiciones(a);
          expect(antes.map((p) => [p.capturaId, p.latitud, p.longitud, p.precisionMetros, p.capturadoEn.toUtc()]), [
            [_p1, 20.10, -10.70, 5.5, base.add(const Duration(seconds: 1))],
            [_p2, 20.20, -10.80, 7.5, base.add(const Duration(seconds: 2))],
            [_p3, 20.30, -10.90, 9.5, base.add(const Duration(seconds: 3))]]);
          expect(antes.every((p) => p.estado == EstadoSincronizacion.pendiente), isTrue);
          await cola.sincronizar(); // sin red: no lanza y no altera evidencia ni IDs
          expect((await eventos(_jornadaA)).single.eventoClienteId, inicio.eventoClienteId);
          expect((await eventos(_jornadaA)).single.estadoSincronizacion, EstadoSincronizacion.pendiente);
          expect((await almacen.listarPosiciones(a)).every((p) => p.estado == EstadoSincronizacion.pendiente && p.error == null), isTrue);
          reloj = base.add(const Duration(seconds: 10));
          await cola.registrarLocal(_jornadaA, _asignacionA, TipoEventoEjecucion.fin, usuarioIdCaptura: a);
          reloj = base.add(const Duration(seconds: 11));
          await cola.registrarLocal(_jornadaC, _asignacionC, TipoEventoEjecucion.inicio, usuarioIdCaptura: a);
          expect((await almacen.listar()).length, 3);
          expect((await almacen.listar()).every((e) => e.estadoSincronizacion == EstadoSincronizacion.pendiente && e.usuarioIdCaptura == a), isTrue);
          expect(await almacen.hayGpsNoAceptado(_jornadaA), isTrue);
        }
        case 3: {
          await servidorAlcanzable();
          await sesiones.iniciar(_usuarioB, _clave); // otra cuenta en el mismo dispositivo
          final b = sesiones.actual!.usuarioId;
          expect(sesiones.actual!.nombreUsuario, _usuarioB);
          expect(await almacen.listarPosiciones(b), isEmpty);
          await cola.sincronizar(); // la cola de A no se transmite ni se atribuye a B
          await cola.sincronizar();
          final todos = await almacen.listar();
          expect(todos.length, 3);
          expect(todos.every((e) => e.estadoSincronizacion == EstadoSincronizacion.pendiente && e.usuarioIdCaptura != b), isTrue);
          expect(await almacen.listarPosiciones(b), isEmpty);
        }
        case 4: {
          await servidorAlcanzable();
          await sesiones.iniciar(_usuarioA, _clave);
          final a = sesiones.actual!.usuarioId;
          await cola.sincronizar();
          await cola.sincronizar(); // segunda pasada: idempotente
          final todos = await almacen.listar();
          expect(todos.length, 3);
          expect(todos.every((e) => e.estadoSincronizacion == EstadoSincronizacion.sincronizado && e.usuarioIdCaptura == a), isTrue,
            reason: 'Estados: ${todos.map((e) => '${e.tipoEvento.codigo}:${e.estadoSincronizacion.codigo}').toList()}');
          final posiciones = await almacen.listarPosiciones(a);
          expect(posiciones.length, 3);
          expect(posiciones.every((p) => p.estado == EstadoSincronizacion.sincronizado), isTrue,
            reason: 'GPS: ${posiciones.map((p) => '${p.capturaId}:${p.estado.codigo}:${p.error}').toList()}');
          // Evidencia local original preservada tras sincronizar (tiempos y coordenadas intactos).
          expect(posiciones.map((p) => p.capturadoEn.toUtc()), [1, 2, 3].map((s) => base.add(Duration(seconds: s))));
        }
      }
    } finally {
      await almacen.cerrar();
      sesiones.dispose();
      api.cerrar();
    }
  });
}
