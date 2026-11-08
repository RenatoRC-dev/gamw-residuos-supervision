package bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio;

import java.util.List;

/** CU-09 / RF-06: WGS84 2D positions, always longitude then latitude. */
public record GeometriaRuta(List<List<Double>> coordenadas) {
    public GeometriaRuta {
        if (coordenadas == null || coordenadas.size() < 2 || coordenadas.size() > 10000) {
            throw new IllegalArgumentException("La línea debe tener entre 2 y 10000 posiciones.");
        }
        for (var position : coordenadas) {
            if (position == null || position.size() != 2 || position.get(0) == null || position.get(1) == null
                    || !Double.isFinite(position.get(0)) || !Double.isFinite(position.get(1))
                    || position.get(0) < -180 || position.get(0) > 180
                    || position.get(1) < -90 || position.get(1) > 90) {
                throw new IllegalArgumentException("Se requieren posiciones [longitud, latitud] válidas y finitas.");
            }
        }
        if (coordenadas.stream().allMatch(coordenadas.getFirst()::equals)) {
            throw new IllegalArgumentException("La línea necesita al menos dos posiciones distintas.");
        }
        coordenadas = coordenadas.stream().map(List::copyOf).toList();
    }
}
