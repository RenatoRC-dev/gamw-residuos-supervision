package bo.gob.warnes.gamw.residuos.modules.monitoreo.revision.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion.AutorizacionMunicipal;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.revision.dominio.RepositorioRevisionGps;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.revision.dominio.RepositorioRevisionGps.Muestra;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio.RepositorioContextoTrayectoria;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.PosicionGps;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.RepositorioEventosEjecucion;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.TipoEventoEjecucion;
import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.RepositorioRuta;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.aplicacion.ConsultarTrayectoriasAutorizadas.LineaReferencia;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;

@Service
public class RevisarEvidenciaGps {
    private final AutorizacionMunicipal permisos;
    private final RepositorioRevisionGps repo;
    private final RepositorioContextoTrayectoria contextos;
    private final ObjectMapper json;
    private final RepositorioRuta rutas;
    private final RepositorioEventosEjecucion eventos;
    public RevisarEvidenciaGps(AutorizacionMunicipal permisos,RepositorioRevisionGps repo,RepositorioContextoTrayectoria contextos,ObjectMapper json,RepositorioRuta rutas,RepositorioEventosEjecucion eventos) {
        this.permisos=permisos; this.repo=repo; this.contextos=contextos; this.json=json; this.rutas=rutas; this.eventos=eventos;
    }
    public record Parametros(double huecoSegundos,double precisionMetros,double velocidadKmh,double desvioMetros,
            double radioDetencionMetros,double detencionSegundos,double antiguedadSegundos) {
        void validar() {
            for(double n:new double[]{huecoSegundos,precisionMetros,velocidadKmh,desvioMetros,radioDetencionMetros,detencionSegundos,antiguedadSegundos})
                if(!Double.isFinite(n)||n<=0||n>1000000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Parámetros técnicos positivos, finitos y acotados requeridos.");
        }
    }
    public record Hallazgo(String tipo,String estado,Instant desde,Instant hasta,List<UUID> capturas,
            double valor,String unidad,String detalle,List<Double> coordenadas) { }
    public record Referencia(UUID versionId,String nombre,int numeroVersion,String estado,long muestras,long observadas,
            Instant primera,Instant ultima,Double distanciaMinimaMetros,String limitacion,JsonNode geometria) { }
    public record Resultado(UUID jornadaId,UUID rutaVersionId,Instant calculadoEn,String politica,Parametros parametros,
            long muestrasDisponibles,int muestrasAnalizadas,boolean parcial,Instant ultimaCaptura,Instant ultimaRecepcion,
            String estado,String metadatosUbicacion,List<Hallazgo> hallazgos,List<Referencia> referencias,
            LineaReferencia rutaReferencia,List<PosicionGps> muestras) { }
    private double segundos(Instant a,Instant b) { var d=Duration.between(a,b); return d.getSeconds()+d.getNano()/1e9; }
    private Hallazgo senal(String tipo,PosicionGps p,PosicionGps anterior,double valor,String unidad,String detalle) {
        return new Hallazgo(tipo,"POR_REVISAR",anterior==null?p.capturadoEn():anterior.capturadoEn(),p.capturadoEn(),
            anterior==null?List.of(p.capturaId()):List.of(anterior.capturaId(),p.capturaId()),valor,unidad,detalle,List.of(p.longitud(),p.latitud()));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Resultado revisar(UsuarioAutenticado actor,UUID jornada,String tipo,Parametros p) {
        permisos.exigirRevision(actor,tipo); p.validar();
        var contexto=contextos.obtener(jornada).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Jornada no disponible."));
        var version=rutas.versionPorId(contexto.rutaVersionId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Ruta histórica no disponible."));
        Instant corte=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var filas=repo.muestras(jornada,corte,5001);
        long total=filas.isEmpty()?0:filas.getFirst().total();
        var seleccion=filas.stream().limit(5000).toList();
        var hallazgos=new ArrayList<Hallazgo>(); var referencias=new ArrayList<Referencia>();
        Instant ultima=filas.isEmpty()?null:filas.getFirst().ultimaCaptura(),recepcion=filas.isEmpty()?null:filas.getFirst().ultimaRecepcion();
        if(tipo.equals("integridad")) {
            var historia=eventos.porJornada(jornada);
            var inicio=historia.stream().filter(e->e.tipoEvento()==TipoEventoEjecucion.INICIO).findFirst();
            var fin=historia.stream().filter(e->e.tipoEvento()==TipoEventoEjecucion.FIN).findFirst();
            if(fin.isEmpty() && ultima!=null && segundos(ultima,corte)>p.antiguedadSegundos()) hallazgos.add(new Hallazgo("ULTIMA_CAPTURA_ANTIGUA","POR_REVISAR",ultima,corte,List.of(),segundos(ultima,corte),"s","No equivale a GPS desactivado, detención o falta de recolección.",List.of()));
            Instant primera=seleccion.isEmpty()?null:seleccion.getFirst().posicion().capturadoEn();
            Instant extremo=fin.map(e->e.capturadoEn()).orElse(corte);
            if(inicio.isPresent() && segundos(inicio.get().capturadoEn(),primera==null?extremo:primera)>p.huecoSegundos())
                hallazgos.add(new Hallazgo("INICIO_SIN_MUESTRAS","POR_REVISAR",inicio.get().capturadoEn(),primera==null?extremo:primera,List.of(),
                    segundos(inicio.get().capturadoEn(),primera==null?extremo:primera),"s","Intervalo sin posiciones recibidas; causa desconocida, puede haber datos offline.",List.of()));
            if(fin.isPresent() && ultima!=null && segundos(ultima,fin.get().capturadoEn())>p.huecoSegundos())
                hallazgos.add(new Hallazgo("FIN_SIN_MUESTRAS_RECIENTES","POR_REVISAR",ultima,fin.get().capturadoEn(),List.of(),segundos(ultima,fin.get().capturadoEn()),"s","No se rellena el intervalo ni se presupone GPS detenido.",List.of()));
            for(int i=0;i<seleccion.size();i++) {
                var m=seleccion.get(i); var s=m.posicion(); var ant=i==0?null:seleccion.get(i-1).posicion();
                if(s.precisionMetros()>p.precisionMetros()) hallazgos.add(senal("PRECISION_LIMITADA",s,null,s.precisionMetros(),"m","Precisión reportada; no garantía de error máximo."));
                double demora=segundos(s.capturadoEn(),s.recibidoEn());
                if(demora<0) hallazgos.add(senal("RECEPCION_ANTERIOR_CAPTURA",s,null,demora,"s","Revisar relojes; no prueba manipulación."));
                else if(demora>p.huecoSegundos()) hallazgos.add(senal("RECEPCION_DIFERIDA",s,null,demora,"s","Demora de recepción; no es ausencia de captura GPS."));
                if(ant==null) continue;
                double dt=segundos(ant.capturadoEn(),s.capturadoEn());
                if(dt>p.huecoSegundos()) hallazgos.add(senal("INTERVALO_SIN_MUESTRAS",s,ant,dt,"s","No se conoce la causa ni se interpola el intervalo."));
                if(dt==0 && m.distanciaAnteriorMetros()!=null && m.distanciaAnteriorMetros()>0) hallazgos.add(senal("POSICIONES_DISTINTAS_MISMO_INSTANTE",s,ant,m.distanciaAnteriorMetros(),"m","No calcular velocidad con intervalo cero."));
                if(dt>0 && dt<=p.huecoSegundos() && s.precisionMetros()<=p.precisionMetros() && ant.precisionMetros()<=p.precisionMetros() && m.distanciaAnteriorMetros()!=null) {
                    double velocidad=Math.max(0,m.distanciaAnteriorMetros()-s.precisionMetros()-ant.precisionMetros())/dt*3.6;
                    if(velocidad>p.velocidadKmh()) hallazgos.add(senal("SALTO_POR_REVISAR",s,ant,velocidad,"km/h","Candidato técnico con precisión reportada; no fraude comprobado."));
                }
            }
        } else if(tipo.equals("desvios")) {
            for(var m:seleccion) {
                var s=m.posicion();
                if(m.distanciaRutaMetros()>p.desvioMetros()) hallazgos.add(senal(
                    s.precisionMetros()<=p.precisionMetros() && m.distanciaRutaMetros()-s.precisionMetros()>p.desvioMetros()?"DESVIO_CANDIDATO":"SEPARACION_INDETERMINADA_POR_PRECISION",
                    s,null,m.distanciaRutaMetros(),"m","Distancia geográfica a RutaVersion histórica; no tramo recorrido ni incumplimiento."));
            }
            List<PosicionGps> grupo=new ArrayList<>();
            for(var m:seleccion) {
                var s=m.posicion();
                boolean precisa=s.precisionMetros()<=Math.min(p.precisionMetros(),p.radioDetencionMetros());
                boolean compatible=grupo.isEmpty() || (segundos(grupo.getLast().capturadoEn(),s.capturadoEn())>0
                    && segundos(grupo.getLast().capturadoEn(),s.capturadoEn())<=p.huecoSegundos()
                    && distanciaAproximada(grupo.getFirst(),s)<=p.radioDetencionMetros());
                if(!precisa||!compatible) { agregarDetencion(grupo,p,hallazgos); grupo.clear(); }
                if(precisa) grupo.add(s);
            }
            agregarDetencion(grupo,p,hallazgos);
        } else {
            var coincidencias=repo.coincidencias(jornada,corte,p.precisionMetros());
            for(var c:coincidencias.stream().limit(100).toList()) {
                String estado=c.observadas()>0?"COINCIDENCIA_OBSERVADA":c.muestras()==0||c.precisas()==0||c.cercanasInciertas()>0?"EVIDENCIA_INSUFICIENTE":"SIN_COINCIDENCIA_OBSERVADA";
                referencias.add(new Referencia(c.referencia().id(),c.referencia().nombre(),c.referencia().numeroVersion(),estado,c.muestras(),c.observadas(),
                    c.primera(),c.ultima(),c.distanciaMinimaMetros(),"Solo muestras y vigencia coincidentes con RutaVersion; precisión y huecos limitan interpretación. "+
                    (c.referencia().radioMetros()==null?"Geocerca poligonal sin radio.":"Radio explícito: "+c.referencia().radioMetros()+" m."),json.readTree(c.referencia().geojson())));
            }
            if(coincidencias.size()>100) hallazgos.add(new Hallazgo("LIMITE_REFERENCIAS","PARCIAL",null,null,List.of(),100,"referencias","Se muestran las primeras 100 versiones; no afirmar revisión total.",List.of()));
        }
        boolean parcial=total>5000 || hallazgos.stream().anyMatch(h->h.tipo().equals("LIMITE_REFERENCIAS"));
        String estado=seleccion.size()<2?"EVIDENCIA_INSUFICIENTE":parcial?"REVISION_PARCIAL":"REVISION_TECNICA";
        if(tipo.equals("coincidencias") && referencias.stream().noneMatch(r->r.muestras()>0)) estado="EVIDENCIA_INSUFICIENTE_SIN_REFERENCIAS_APLICABLES";
        if(tipo.equals("desvios") && seleccion.stream().filter(m->m.posicion().precisionMetros()<=Math.min(p.precisionMetros(),p.radioDetencionMetros())).count()<3)
            estado="REVISION_PARCIAL_DETENCIONES_INDETERMINADAS";
        return new Resultado(jornada,contexto.rutaVersionId(),corte,
            "TECNICA_PROVISIONAL_RECALCULABLE: muestras recibidas, sin interpolación. Ausencia de candidatos no acredita ausencia de detenciones; no certifica integridad, presencia del camión ni cumplimiento.",p,total,seleccion.size(),parcial,
            ultima,recepcion,estado,"Ubicación simulada, permisos GPS y eventos del dispositivo: NO DISPONIBLES; no se inventan.",List.copyOf(hallazgos),List.copyOf(referencias),
            new LineaReferencia("LineString",version.geometria().coordenadas()),seleccion.stream().map(Muestra::posicion).toList());
    }
    private void agregarDetencion(List<PosicionGps> grupo,Parametros p,List<Hallazgo> salida) {
        if(grupo.size()<3) return;
        var a=grupo.getFirst(); var b=grupo.getLast(); double duracion=segundos(a.capturadoEn(),b.capturadoEn());
        if(duracion>=p.detencionSegundos()) salida.add(new Hallazgo("DETENCION_CANDIDATA","POR_REVISAR",a.capturadoEn(),b.capturadoEn(),
            grupo.stream().map(PosicionGps::capturaId).toList(),duracion,"s","Mínimo 3 muestras cercanas a un ancla; sin puentear huecos. Distancia de agrupación esférica aproximada; no prueba camión detenido.",List.of(a.longitud(),a.latitud())));
    }
    private double distanciaAproximada(PosicionGps a,PosicionGps b) {
        double dlat=Math.toRadians(b.latitud()-a.latitud()),dlon=Math.toRadians(b.longitud()-a.longitud());
        double h=Math.pow(Math.sin(dlat/2),2)+Math.cos(Math.toRadians(a.latitud()))*Math.cos(Math.toRadians(b.latitud()))*Math.pow(Math.sin(dlon/2),2);
        return 6371008.8*2*Math.asin(Math.sqrt(Math.min(1,h)));
    }
}
