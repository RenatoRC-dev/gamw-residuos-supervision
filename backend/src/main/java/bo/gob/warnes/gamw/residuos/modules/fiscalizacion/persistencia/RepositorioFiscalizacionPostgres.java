package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.persistencia;

import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.dominio.RepositorioFiscalizacion;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioFiscalizacionPostgres implements RepositorioFiscalizacion {
    private final JdbcTemplate jdbc;
    public RepositorioFiscalizacionPostgres(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean bloquearJornada(UUID id) { return !jdbc.queryForList("SELECT id FROM jornada WHERE id=? FOR UPDATE",UUID.class,id).isEmpty(); }
    public Optional<String> antecedentes(UUID jornada,Instant corte,boolean idsGps) {
        String ids=idsGps?"COALESCE(jsonb_agg(p.id ORDER BY p.capturado_en,p.captura_id,p.id),'[]'::jsonb)":"'null'::jsonb";
        return jdbc.queryForList("""
          SELECT jsonb_build_object('jornada',jsonb_build_object('id',j.id,'periodoServicioId',j.periodo_servicio_id,'rutaVersionId',j.ruta_version_id,
              'inicioProgramado',j.inicio_programado,'finProgramado',j.fin_programado,'vigente',j.vigente,'jornadaAntecedenteId',j.jornada_antecedente_id),
            'periodo',jsonb_build_object('id',ps.id,'empresaOperadoraId',ps.empresa_operadora_id,'fechaInicio',ps.fecha_inicio,'fechaFin',ps.fecha_fin),
            'empresa',jsonb_build_object('id',e.id,'razonSocial',e.razon_social),
            'ruta',jsonb_build_object('id',r.id,'codigo',r.codigo,'nombre',r.nombre),
            'rutaVersion',jsonb_build_object('id',rv.id,'rutaId',rv.ruta_id,'numeroVersion',rv.numero_version,'vigenteDesde',rv.vigente_desde,'vigenteHasta',rv.vigente_hasta),
            'asignaciones',COALESCE((SELECT jsonb_agg(jsonb_build_object('asignacion',jsonb_build_object('id',a.id,'vigenteDesde',a.vigente_desde,'vigenteHasta',a.vigente_hasta,'motivoCambio',a.motivo_cambio),
              'vinculoVehiculoEmpresaId',vv.id,'vehiculoId',v.id,'placa',v.placa,'vinculoPersonaEmpresaId',vp.id,
              'personaId',pn.id,'nombres',pn.nombres,'apellidoPaterno',pn.apellido_paterno,'apellidoMaterno',pn.apellido_materno)
              ORDER BY a.vigente_desde,a.id) FROM asignacion_jornada a
              JOIN vinculo_vehiculo_empresa vv ON vv.id=a.vinculo_vehiculo_empresa_id JOIN vehiculo v ON v.id=vv.vehiculo_id
              JOIN vinculo_persona_empresa vp ON vp.id=a.vinculo_persona_empresa_id JOIN persona_natural pn ON pn.id=vp.persona_id
              WHERE a.jornada_id=j.id),'[]'::jsonb),
            'eventos',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',ev.id,'eventoClienteId',ev.evento_cliente_id,'tipoEvento',ev.tipo_evento,
              'asignacionJornadaId',ev.asignacion_jornada_id,'usuarioId',ev.usuario_id,'capturadoEn',ev.capturado_en,'recibidoEn',ev.recibido_en) ORDER BY ev.capturado_en,ev.id)
              FROM evento_ejecucion_jornada ev WHERE ev.jornada_id=j.id AND ev.recibido_en<=?),'[]'::jsonb),
            'gps',(SELECT jsonb_build_object('cantidad',count(*),'ultimaCaptura',max(p.capturado_en),
              'ultimaRecepcion',max(p.recibido_en),'ids',
          """+ids+"""
              ) FROM posicion_gps p WHERE p.jornada_id=j.id AND p.recibido_en<=?),
            'corteEvidenciaEn',?::timestamptz)::text
          FROM jornada j JOIN periodo_servicio ps ON ps.id=j.periodo_servicio_id JOIN empresa_operadora e ON e.id=ps.empresa_operadora_id
          JOIN ruta_version rv ON rv.id=j.ruta_version_id JOIN ruta r ON r.id=rv.ruta_id WHERE j.id=?
          """,String.class,Timestamp.from(corte),Timestamp.from(corte),Timestamp.from(corte),jornada).stream().findFirst();
    }
    private static final String INSPECCION="""
        SELECT jsonb_build_object('id',i.id,'solicitudId',i.solicitud_id,'jornadaId',i.jornada_id,'inspectorUsuarioId',i.inspector_usuario_id,
          'realizadaEn',i.realizada_en,'registradaEn',i.registrada_en,'lugarDescripcion',i.lugar_descripcion,'latitud',ST_Y(i.geometria),'longitud',ST_X(i.geometria),
          'observaciones',i.observaciones,'evidencias',COALESCE((
          SELECT jsonb_agg(jsonb_build_object('id',e.id,'autorUsuarioId',e.autor_usuario_id,'incorporadaEn',e.incorporada_en,'capturadaEn',e.capturada_en,
            'nombreOriginal',e.nombre_original,'tipoMime',e.tipo_mime,'origen',e.origen,'descripcion',e.descripcion,'referenciaPolitica',e.referencia_politica,
            'sha256',e.sha256,'tamanoBytes',octet_length(e.contenido)) ORDER BY e.incorporada_en,e.id)
          FROM evidencia_inspeccion e WHERE e.inspeccion_id=i.id),'[]'::jsonb)) AS datos
        FROM inspeccion i
        """;
    public List<String> inspecciones(UUID jornada) { return jdbc.query(INSPECCION+" WHERE i.jornada_id=? ORDER BY i.realizada_en,i.id",(r,n)->r.getString("datos"),jornada); }
    public Optional<String> inspeccion(UUID id) { return jdbc.query(INSPECCION+" WHERE i.id=?",(r,n)->r.getString("datos"),id).stream().findFirst(); }
    public Optional<String> fiscalizacion(UUID jornada) {
        return jdbc.queryForList("""
          SELECT jsonb_build_object('id',f.id,'solicitudId',f.solicitud_id,'jornadaId',f.jornada_id,'fiscalizadorUsuarioId',f.fiscalizador_usuario_id,
            'observaciones',f.observaciones,'registradaEn',f.registrada_en,'corteEvidenciaEn',f.corte_evidencia_en,'antecedentes',f.antecedentes,
            'resultado',jsonb_build_object('id',c.id,'codigo',c.codigo,'nombre',c.nombre,'referenciaAprobacion',c.referencia_aprobacion),
            'posicionesNoReferenciadas',(SELECT count(*) FROM posicion_gps p WHERE p.jornada_id=f.jornada_id AND NOT jsonb_exists(f.antecedentes->'gps'->'ids',p.id::text)),
            'inspeccionesPosteriores',(SELECT count(*) FROM inspeccion i WHERE i.jornada_id=f.jornada_id AND i.registrada_en>f.registrada_en),
            'inspeccionesReferenciadas',COALESCE((SELECT jsonb_agg(fi.inspeccion_id) FROM fiscalizacion_inspeccion fi WHERE fi.fiscalizacion_id=f.id),'[]'::jsonb))::text
          FROM fiscalizacion f JOIN catalogo_resultado_fiscalizacion c ON c.id=f.resultado_id WHERE f.jornada_id=?
          """,String.class,jornada).stream().findFirst();
    }
    private Optional<Registro> solicitud(String tabla,UUID id) {
        String autor=tabla.equals("inspeccion")?"inspector_usuario_id":"fiscalizador_usuario_id";
        return jdbc.query("SELECT id,"+autor+",huella_solicitud FROM "+tabla+" WHERE solicitud_id=?",
          (r,i)->new Registro(r.getObject("id",UUID.class),r.getObject(autor,UUID.class),r.getString("huella_solicitud"),null),id).stream().findFirst();
    }
    public Optional<Registro> inspeccionPorSolicitud(UUID id) { return solicitud("inspeccion",id); }
    public Optional<Registro> fiscalizacionPorSolicitud(UUID id) { return solicitud("fiscalizacion",id); }
    private Categoria categoria(ResultSet r,int n) throws SQLException {
        return new Categoria(r.getObject("id",UUID.class),r.getString("codigo"),r.getString("nombre"),r.getString("referencia_aprobacion"),r.getBoolean("observaciones_obligatorias"));
    }
    public List<Categoria> categorias() { return jdbc.query("SELECT * FROM catalogo_resultado_fiscalizacion WHERE habilitado ORDER BY codigo",this::categoria); }
    public Optional<Categoria> categoriaBloqueada(UUID id) { return jdbc.query("SELECT * FROM catalogo_resultado_fiscalizacion WHERE id=? AND habilitado FOR SHARE",this::categoria,id).stream().findFirst(); }
    public void insertarInspeccion(UUID id,UUID solicitud,UUID jornada,UUID actor,Instant realizada,String lugar,Double lat,Double lon,String obs,String huella) {
        jdbc.update("INSERT INTO inspeccion(id,solicitud_id,huella_solicitud,jornada_id,inspector_usuario_id,realizada_en,lugar_descripcion,geometria,observaciones) VALUES (?,?,?,?,?,?,?,CASE WHEN ?::double precision IS NULL THEN NULL ELSE ST_SetSRID(ST_MakePoint(?,?),4326) END,?)",
          id,solicitud,huella,jornada,actor,Timestamp.from(realizada),lugar,lat,lon,lat,obs);
    }
    public void insertarArchivo(Archivo a) { jdbc.update("INSERT INTO evidencia_inspeccion(id,inspeccion_id,autor_usuario_id,capturada_en,nombre_original,tipo_mime,origen,descripcion,referencia_politica,sha256,contenido) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        a.id(),a.inspeccionId(),a.autorId(),a.capturadaEn()==null?null:Timestamp.from(a.capturadaEn()),a.nombre(),a.mime(),a.origen(),a.descripcion(),a.politica(),a.sha256(),a.contenido()); }
    public Optional<Archivo> archivo(UUID jornada,UUID id) {
        return jdbc.query("SELECT e.* FROM evidencia_inspeccion e JOIN inspeccion i ON i.id=e.inspeccion_id WHERE i.jornada_id=? AND e.id=?",(r,n)->{
          var t=r.getTimestamp("capturada_en");
          return new Archivo(id,r.getObject("inspeccion_id",UUID.class),r.getObject("autor_usuario_id",UUID.class),t==null?null:t.toInstant(),
            r.getString("nombre_original"),r.getString("tipo_mime"),r.getString("origen"),r.getString("descripcion"),r.getString("referencia_politica"),r.getString("sha256"),r.getBytes("contenido"));
        },jornada,id).stream().findFirst();
    }
    public void insertarFiscalizacion(UUID id,UUID solicitud,UUID jornada,UUID actor,UUID categoria,String obs,Instant corte,String antecedentes,String huella) {
        jdbc.update("INSERT INTO fiscalizacion(id,solicitud_id,huella_solicitud,jornada_id,fiscalizador_usuario_id,resultado_id,observaciones,corte_evidencia_en,antecedentes) VALUES (?,?,?,?,?,?,?,?,?::jsonb)",
          id,solicitud,huella,jornada,actor,categoria,obs,Timestamp.from(corte),antecedentes);
    }
    public boolean inspeccionPertenece(UUID inspeccion,UUID jornada) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM inspeccion WHERE id=? AND jornada_id=?)",Boolean.class,inspeccion,jornada)); }
    public void vincularInspeccion(UUID f,UUID j,UUID i) { jdbc.update("INSERT INTO fiscalizacion_inspeccion VALUES (?,?,?)",f,j,i); }
}
