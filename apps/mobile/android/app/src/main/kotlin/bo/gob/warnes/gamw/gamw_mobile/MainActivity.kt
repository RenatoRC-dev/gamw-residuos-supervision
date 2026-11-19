package bo.gob.warnes.gamw.gamw_mobile

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class MainActivity : FlutterActivity() {
    private var pendiente: MethodChannel.Result? = null
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger,"gamw/gps").setMethodCallHandler { call,result ->
            when(call.method) {
                "permisos" -> {
                    if(pendiente != null) { result.error("OCUPADO","Solicitud de permisos en curso",null); return@setMethodCallHandler }
                    val permisos=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
                    if(Build.VERSION.SDK_INT>=33) permisos.add(Manifest.permission.POST_NOTIFICATIONS)
                    // H-02/H-04: se devuelve el estado de cada permiso; Flutter decide con evaluarPermisosGps (probado en Dart).
                    if(ubicacionPrecisa() && notificaciones()) result.success(estadoPermisos())
                    else { pendiente=result; requestPermissions(permisos.toTypedArray(),410) }
                }
                "iniciar" -> {
                    // GPS_PROVIDER exige ubicación PRECISA. Las notificaciones NO condicionan el servicio en primer plano (H-02):
                    // su notificación obligatoria se crea igual; si están denegadas puede no mostrarse y se informa en "estado".
                    if(!ubicacionPrecisa()) {
                        ServicioCapturaGps.estado=if(ubicacionAproximada()) SOLO_APROXIMADA else "Permiso denegado"
                        result.success(ServicioCapturaGps.estado); return@setMethodCallHandler
                    }
                    val args=call.arguments as? Map<*,*> ?: emptyMap<Any,Any>()
                    val intent=Intent(this,ServicioCapturaGps::class.java)
                    val solicitud=java.util.UUID.randomUUID().toString()
                    ServicioCapturaGps.solicitudActual=solicitud
                    intent.putExtra("solicitud",solicitud)
                    for(k in listOf("ruta","jornadaId","asignacionJornadaId","usuarioId")) intent.putExtra(k,args[k] as? String)
                    intent.putExtra("intervalo",(args["intervalo"] as? Number)?.toLong() ?: 30000L)
                    try {
                        if(Build.VERSION.SDK_INT>=26) startForegroundService(intent) else startService(intent)
                        result.success("Esperando ubicación")
                    } catch(e:RuntimeException) { result.success("Restricción de segundo plano: reabra la aplicación") }
                }
                "detener" -> {
                    try {
                        if(ServicioCapturaGps.desactivar(this,call.argument<String>("jornadaId"))) stopService(Intent(this,ServicioCapturaGps::class.java))
                        result.success(null)
                    } catch(e:RuntimeException) { result.error("ALMACENAMIENTO","No se pudo confirmar detención GPS",null) }
                }
                "estado" -> {
                    val activo=ServicioCapturaGps.estado=="Capturando GPS" || ServicioCapturaGps.estado=="Esperando ubicación"
                    result.success(if(activo && !notificaciones()) "${ServicioCapturaGps.estado} · $SIN_NOTIFICACIONES" else ServicioCapturaGps.estado)
                }
                else -> result.notImplemented()
            }
        }
    }
    companion object {
        const val SOLO_APROXIMADA="Ubicación precisa requerida: solo se concedió ubicación aproximada; no se captura GPS."
        const val SIN_NOTIFICACIONES="Notificaciones denegadas: la captura continúa, pero su aviso puede no mostrarse."
    }
    private fun concedido(permiso:String)=checkSelfPermission(permiso)==PackageManager.PERMISSION_GRANTED
    private fun ubicacionPrecisa()=concedido(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun ubicacionAproximada()=concedido(Manifest.permission.ACCESS_COARSE_LOCATION)
    private fun notificaciones()=Build.VERSION.SDK_INT<33 || concedido(Manifest.permission.POST_NOTIFICATIONS)
    private fun estadoPermisos():Map<String,Boolean> =
        mapOf("fina" to ubicacionPrecisa(),"aproximada" to ubicacionAproximada(),"notificaciones" to notificaciones())

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==410) {
            if(!ubicacionPrecisa()) ServicioCapturaGps.estado=if(ubicacionAproximada()) SOLO_APROXIMADA else "Permiso denegado"
            pendiente?.success(estadoPermisos()); pendiente=null
        }
    }
}
