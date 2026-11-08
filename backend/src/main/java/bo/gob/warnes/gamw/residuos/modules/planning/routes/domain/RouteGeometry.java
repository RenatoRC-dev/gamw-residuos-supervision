package bo.gob.warnes.gamw.residuos.modules.planning.routes.domain;

import java.util.List;

/** CU-09 / RF-06: WGS84 2D positions, always longitude then latitude. */
public record RouteGeometry(List<List<Double>> coordinates) {
    public RouteGeometry {
        if (coordinates == null || coordinates.size() < 2 || coordinates.size() > 10000) {
            throw new IllegalArgumentException("La línea debe tener entre 2 y 10000 posiciones.");
        }
        for (var position : coordinates) {
            if (position == null || position.size() != 2 || position.get(0) == null || position.get(1) == null
                    || !Double.isFinite(position.get(0)) || !Double.isFinite(position.get(1))
                    || position.get(0) < -180 || position.get(0) > 180
                    || position.get(1) < -90 || position.get(1) > 90) {
                throw new IllegalArgumentException("Se requieren posiciones [longitud, latitud] válidas y finitas.");
            }
        }
        if (coordinates.stream().allMatch(coordinates.getFirst()::equals)) {
            throw new IllegalArgumentException("La línea necesita al menos dos posiciones distintas.");
        }
        coordinates = coordinates.stream().map(List::copyOf).toList();
    }
}
