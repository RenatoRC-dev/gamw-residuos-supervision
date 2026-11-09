package bo.gob.warnes.gamw.gamw_mobile

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.location.*
import android.os.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Solo lecturas nuevas del proveedor Android. Sin lastKnownLocation, relleno ni red. */
class ServicioCapturaGps : Service(), LocationListener {
    companion object {
        @Volatile var estado="GPS detenido"
        @Volatile var solicitudActual:String?=null
        private const val ARCHIVO="gamw_eventos_ejecucion.db"
        fun desactivar(context:Context,jornada:String?=null):Boolean {
            val file=context.getDatabasePath(ARCHIVO)
            if(!file.exists()) { solicitudActual=null; return true }
            SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
                if(jornada!=null) db.rawQuery("SELECT 1 FROM captura_gps_local WHERE jornada_id<>? AND activa=1",arrayOf(jornada)).use {
                    if(it.moveToFirst()) return false
                }
                db.execSQL("UPDATE captura_gps_local SET activa=0")
            }
            solicitudActual=null
            estado="GPS detenido"
            return true
        }
    }
    private var db:SQLiteDatabase?=null
    private var ubicacion:LocationManager?=null
    private var jornada=""
    private var asignacion=""
    private var usuario=""
    private var intervalo=30000L
    private var ultimaLectura=0L
    private var desde=0L

    override fun onBind(intent:Intent?) : IBinder? = null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        try {
            if(intent?.getStringExtra("solicitud")!=solicitudActual || solicitudActual==null) { stopSelf(); return START_NOT_STICKY }
            val ruta=intent?.getStringExtra("ruta") ?: throw IllegalArgumentException()
            require(File(ruta).canonicalPath==getDatabasePath(ARCHIVO).canonicalPath)
            val j=UUID.fromString(intent.getStringExtra("jornadaId")).toString()
            val a=UUID.fromString(intent.getStringExtra("asignacionJornadaId")).toString()
            val u=UUID.fromString(intent.getStringExtra("usuarioId")).toString()
            if(db!=null && jornada==j && asignacion==a && usuario==u) return START_NOT_STICKY
            if(db!=null && jornada!=j) { estado="Otra jornada mantiene captura activa"; return START_NOT_STICKY }
            ubicacion?.removeUpdates(this)
            db?.close()
            db=SQLiteDatabase.openDatabase(ruta,null,SQLiteDatabase.OPEN_READWRITE)
            db!!.execSQL("PRAGMA synchronous=FULL")
            db!!.beginTransaction()
            try {
                db!!.rawQuery("SELECT 1 FROM captura_gps_local WHERE activa=1 AND (jornada_id<>? OR usuario_id_captura<>?)",arrayOf(j,u)).use {
                    require(!it.moveToFirst()) { "Otra jornada mantiene captura activa" }
                }
                require(activaLocal(db!!,j)) { "GPS requiere INICIO local sin FIN" }
                db!!.execSQL("INSERT OR REPLACE INTO captura_gps_local VALUES (1,?,?,?,1)",arrayOf(j,a,u))
                db!!.setTransactionSuccessful()
            } finally { db!!.endTransaction() }
            jornada=j; asignacion=a; usuario=u
            intervalo=intent.getLongExtra("intervalo",30000L).coerceAtLeast(1000L)
            desde=SystemClock.elapsedRealtimeNanos(); ultimaLectura=0
            val manager=getSystemService(NotificationManager::class.java)
            if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel("gamw_gps","Captura GPS de jornada",NotificationManager.IMPORTANCE_LOW))
            val abrir=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val builder=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,"gamw_gps") else Notification.Builder(this)
            val notificacion=builder.setContentTitle("GAMW · GPS de jornada")
                .setContentText("Captura activa. Abra GAMW para registrar FIN o cerrar sesión.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentIntent(abrir).setOngoing(true).build()
            if(Build.VERSION.SDK_INT>=29) startForeground(410,notificacion,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(410,notificacion)
            ubicacion=getSystemService(LocationManager::class.java)
            estado="Esperando ubicación"
            // GPS satelital funciona sin Internet. Si está deshabilitado se informa, no se inventa una lectura.
            // La adquisición debe ser más frecuente que la evidencia persistida.
// GPS_PROVIDER continúa siendo la única fuente de ubicación.
// 'intervalo' sigue controlando la persistencia (30 s actualmente).
ubicacion!!.requestLocationUpdates(
    LocationManager.GPS_PROVIDER,
    5_000L,
    0f,
    this
)
            if(!ubicacion!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) estado="GPS desactivado"
        } catch(e:SecurityException) { estado="Permiso denegado"; stopSelf() }
        catch(e:Exception) { estado="No se pudo iniciar GPS; revise jornada y almacenamiento"; stopSelf() }
        return START_NOT_STICKY // No arrancar ubicación desde background sin acción visible; recuperar al reabrir.
    }
    private fun activaLocal(db:SQLiteDatabase,j:String):Boolean = db.rawQuery("""
        SELECT 1 FROM evento_ejecucion_local i WHERE i.jornada_id=? AND i.tipo_evento='INICIO'
        AND NOT EXISTS(SELECT 1 FROM evento_ejecucion_local f WHERE f.jornada_id=i.jornada_id AND f.tipo_evento='FIN')
        """,arrayOf(j)).use { it.moveToFirst() }

    override fun onLocationChanged(location:Location) {
        // Android reporta el instante original y edad monotónica; nunca reciclar un fix previo.
        if(location.elapsedRealtimeNanos<desde || location.elapsedRealtimeNanos<=ultimaLectura ||
            (ultimaLectura!=0L && location.elapsedRealtimeNanos-ultimaLectura<intervalo*1000000L)) return
        if(location.time<=0 || !location.hasAccuracy() || !location.latitude.isFinite() || !location.longitude.isFinite() ||
            !location.accuracy.isFinite() || location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0 || location.accuracy<0) return
        // El flag no certifica fraude; se rechazan fuentes declaradas mock sin producir evidencia artificial.
        if(if(Build.VERSION.SDK_INT>=31) location.isMock else location.isFromMockProvider) { estado="Ubicación simulada no registrada"; return }
        val conexion=db ?: return
        try {
            conexion.beginTransaction()
            try {
                val autorizada=conexion.rawQuery("SELECT 1 FROM captura_gps_local WHERE activa=1 AND jornada_id=? AND usuario_id_captura=?",
                    arrayOf(jornada,usuario)).use { it.moveToFirst() } && activaLocal(conexion,jornada)
                if(!autorizada) { stopSelf(); return }
                val formato=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.ROOT).apply { timeZone=TimeZone.getTimeZone("UTC") }
                conexion.execSQL("""INSERT INTO posicion_gps_local(captura_id,jornada_id,asignacion_jornada_id,usuario_id_captura,
                    capturado_en,latitud,longitud,precision_metros,estado_sincronizacion) VALUES (?,?,?,?,?,?,?,?,'PENDIENTE')""",
                    arrayOf(UUID.randomUUID().toString(),jornada,asignacion,usuario,formato.format(Date(location.time)),
                        location.latitude,location.longitude,location.accuracy.toDouble()))
                conexion.setTransactionSuccessful()
                ultimaLectura=location.elapsedRealtimeNanos
                estado="Capturando GPS"
            } finally { conexion.endTransaction() }
        } catch(e:Exception) { estado="Error de almacenamiento GPS; captura detenida"; stopSelf() }
    }
    override fun onProviderDisabled(provider:String) { estado="GPS desactivado" }
    override fun onProviderEnabled(provider:String) { estado="Esperando ubicación" }
    @Deprecated("Android antiguo")
    override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) { }
    override fun onDestroy() {
        try { ubicacion?.removeUpdates(this) } catch(e:SecurityException) { estado="Permiso denegado" }
        db?.close(); db=null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
