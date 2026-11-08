package bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU-09 / RF-06 / BR-04. No authority/approval workflow is assumed here. */
@Service
@Transactional(readOnly = true)
public class GestionarRutasReferencia {
    private final RepositorioRuta rutas;

    public GestionarRutasReferencia(RepositorioRuta rutas) { this.rutas = rutas; }

    @Transactional
    public RutaCreada crear(String codigo, String nombre, GeometriaRuta geometria, Instant vigenteDesde, String motivoCambio) {
        Instant now = Instant.now();
        var ruta = new Ruta(UUID.randomUUID(), codigo.strip(), nombre.strip(), true, now, now);
        rutas.insertar(ruta);
        var version = new RutaVersion(UUID.randomUUID(), ruta.id(), 1, geometria, vigenteDesde, null, motivoCambio, now);
        rutas.insertar(version);
        return new RutaCreada(ruta, version);
    }

    @Transactional
    public RutaVersion crearVersion(UUID rutaId, GeometriaRuta geometria, Instant vigenteDesde, String motivoCambio) {
        rutas.buscar(rutaId, true).orElseThrow(() -> new RutaNoEncontrada("Ruta no encontrada."));
        var anterior = rutas.ultima(rutaId).orElseThrow(() -> new IllegalStateException("Ruta sin versión inicial."));
        if (!vigenteDesde.isAfter(anterior.vigenteDesde())) {
            throw new ConflictoVersion("La nueva vigencia debe ser posterior a la última versión.");
        }
        Instant now = Instant.now();
        rutas.cerrarVersion(anterior.id(), vigenteDesde);
        var siguiente = new RutaVersion(UUID.randomUUID(), rutaId, Math.addExact(anterior.numeroVersion(), 1),
                geometria, vigenteDesde, null, motivoCambio, now);
        rutas.insertar(siguiente);
        rutas.actualizarFecha(rutaId, now);
        return siguiente;
    }

    public List<Ruta> listar() { return rutas.listar(); }
    public Ruta obtener(UUID id) {
        return rutas.buscar(id, false).orElseThrow(() -> new RutaNoEncontrada("Ruta no encontrada."));
    }
    public List<RutaVersion> versiones(UUID rutaId) { obtener(rutaId); return rutas.versiones(rutaId); }
    public RutaVersion obtenerVersion(UUID rutaId, int numeroVersion) {
        return rutas.obtenerVersion(rutaId, numeroVersion).orElseThrow(() -> new RutaNoEncontrada("Versión no encontrada."));
    }

    public record RutaCreada(Ruta ruta, RutaVersion version) { }
    public static class RutaNoEncontrada extends RuntimeException {
        public RutaNoEncontrada(String message) { super(message); }
    }
    public static class ConflictoVersion extends RuntimeException {
        public ConflictoVersion(String message) { super(message); }
    }
}
