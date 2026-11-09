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
                    if(permisosConcedidos()) result.success(true)
                    else { pendiente=result; requestPermissions(permisos.toTypedArray(),410) }
                }
                "iniciar" -> {
                    if(!permisosConcedidos()) { ServicioCapturaGps.estado="Permiso denegado"; result.success("Permiso denegado"); return@setMethodCallHandler }
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
                "estado" -> result.success(ServicioCapturaGps.estado)
                else -> result.notImplemented()
            }
        }
    }
    private fun permisosConcedidos(): Boolean =
        (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) &&
        (Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==410) {
            if(!permisosConcedidos()) ServicioCapturaGps.estado="Permiso denegado"
            pendiente?.success(permisosConcedidos()); pendiente=null
        }
    }
}
