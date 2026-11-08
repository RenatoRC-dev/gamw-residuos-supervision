package bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.api;

import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ContratosRuta {
    private ContratosRuta() { }

    public record GeometriaGeoJson(String type, List<List<Double>> coordinates) {
        GeometriaRuta aDominio() {
            if (!"LineString".equals(type)) throw new IllegalArgumentException("La geometría debe ser LineString.");
            return new GeometriaRuta(coordinates);
        }
        static GeometriaGeoJson desde(GeometriaRuta geometria) { return new GeometriaGeoJson("LineString", geometria.coordenadas()); }
    }
    public record CrearRutaSolicitud(@NotBlank @Size(max = 64) String codigo,
            @NotBlank @Size(max = 160) String nombre, @NotNull @Valid GeometriaGeoJson geometria,
            @NotNull Instant vigenteDesde, @Size(max = 500) String motivoCambio) { }
    public record CrearVersionSolicitud(@NotNull @Valid GeometriaGeoJson geometria,
            @NotNull Instant vigenteDesde, @Size(max = 500) String motivoCambio) { }
    public record RutaRespuesta(UUID id, String codigo, String nombre, boolean activo, Instant creadoEn, Instant actualizadoEn) {
        static RutaRespuesta desde(Ruta ruta) {
            return new RutaRespuesta(ruta.id(), ruta.codigo(), ruta.nombre(), ruta.activo(), ruta.creadoEn(), ruta.actualizadoEn());
        }
    }
    public record RutaVersionRespuesta(UUID id, UUID rutaId, int numeroVersion, GeometriaGeoJson geometria,
            Instant vigenteDesde, Instant vigenteHasta, String motivoCambio, Instant creadoEn) {
        static RutaVersionRespuesta desde(RutaVersion version) {
            return new RutaVersionRespuesta(version.id(), version.rutaId(), version.numeroVersion(), GeometriaGeoJson.desde(version.geometria()),
                    version.vigenteDesde(), version.vigenteHasta(), version.motivoCambio(), version.creadoEn());
        }
    }
    public record RutaCreadaRespuesta(RutaRespuesta ruta, RutaVersionRespuesta version) { }
}
