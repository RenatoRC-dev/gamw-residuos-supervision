package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PlanificacionTests {
    private final UUID id = UUID.randomUUID(), periodo = UUID.randomUUID(), ruta = UUID.randomUUID();
    private final Instant inicio = Instant.parse("2026-05-10T23:00:00Z");
    private Jornada jornada(Instant desde,Instant hasta,UUID antecedente,String motivo) {
        return new Jornada(id,periodo,ruta,desde,hasta,antecedente,motivo,true,inicio,inicio);
    }
    @Test void programacionOriginalYFinOpcional() {
        var j = jornada(inicio,null,null,null);
        assertThat(j.jornadaAntecedenteId()).isNull(); assertThat(j.motivoReprogramacion()).isNull();
        assertThat(jornada(inicio,inicio.plusSeconds(7200),null,null).finProgramado()).isAfter(inicio);
    }
    @Test void rechazaInicioAusenteEIntervalosInvalidos() {
        assertThatThrownBy(() -> jornada(null,null,null,null)).isInstanceOf(IllegalArgumentException.class);
        for (var fin : new Instant[]{inicio,inicio.minusSeconds(1)}) assertThatThrownBy(() -> jornada(inicio,fin,null,null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reprogramacionConservaAntecedenteYExigeMotivo() {
        var antecedente = UUID.randomUUID();
        var j = jornada(inicio,null,antecedente,"  Cambio sintético  ");
        assertThat(j.jornadaAntecedenteId()).isEqualTo(antecedente);
        assertThat(j.motivoReprogramacion()).isEqualTo("Cambio sintético");
        assertThatThrownBy(() -> jornada(inicio,null,id,"Motivo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jornada(inicio,null,antecedente," ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jornada(inicio,null,antecedente,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jornada(inicio,null,null,"No corresponde")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void asignacionInicialSinMotivoYReasignacionConMotivo() {
        var inicial = AsignacionJornada.inicial(id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),inicio);
        assertThat(inicial.motivoCambio()).isNull();
        assertThat(inicial.vigenteHasta()).isNull();
        var siguiente = AsignacionJornada.reasignacion(UUID.randomUUID(),inicial.jornadaId(),inicial.vinculoVehiculoEmpresaId(),inicial.vinculoPersonaEmpresaId(),inicio,"  Cambio  ");
        assertThat(siguiente.motivoCambio()).isEqualTo("Cambio");
        assertThatThrownBy(() -> AsignacionJornada.reasignacion(id,inicial.jornadaId(),inicial.vinculoVehiculoEmpresaId(),inicial.vinculoPersonaEmpresaId(),inicio," ")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void cierreAdministrativoNoAnteriorAlInicio() {
        new AsignacionJornada(id,id,id,id,inicio,inicio,null,inicio);
        assertThatThrownBy(() -> new AsignacionJornada(id,id,id,id,inicio,inicio.minusSeconds(1),null,inicio)).isInstanceOf(IllegalArgumentException.class);
    }
}
