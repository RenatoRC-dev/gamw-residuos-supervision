package bo.gob.warnes.gamw.residuos.modules.planning.routes.domain;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RouteGeometryTests {
    @Test
    void preservesLongitudeLatitudeAndCopiesPositions() {
        var position = new ArrayList<>(List.of(-10.25, 20.5));
        var geometry = new RouteGeometry(List.of(position, List.of(-10.2, 20.6)));
        position.set(0, 100.0);
        assertThat(geometry.coordinates().getFirst()).containsExactly(-10.25, 20.5);
        assertThatThrownBy(() -> geometry.coordinates().getFirst().set(0, 0.0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingShortDegenerateAndNon2dLines() {
        assertThatThrownBy(() -> new RouteGeometry(null)).isInstanceOf(IllegalArgumentException.class);
        for (var positions : List.of(List.<List<Double>>of(), List.of(List.of(1.0, 2.0)),
                List.of(List.of(1.0, 2.0), List.of(1.0, 2.0)),
                List.of(List.of(1.0, 2.0, 3.0), List.of(2.0, 3.0, 4.0)))) {
            assertThatThrownBy(() -> new RouteGeometry(positions)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsNonFiniteAndOutOfRangeOrNullCoordinates() {
        for (double longitude : new double[]{-181, 181, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> new RouteGeometry(List.of(List.of(longitude, 0.0), List.of(1.0, 2.0))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (double latitude : new double[]{-91, 91, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> new RouteGeometry(List.of(List.of(0.0, latitude), List.of(1.0, 2.0))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new RouteGeometry(java.util.Arrays.asList(null, List.of(1.0, 2.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
