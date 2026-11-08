package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio;

import java.time.Instant;
import java.util.UUID;

/** Vigencia administrativa; no representa inicio o fin de ejecución de la recolección. */
public record AsignacionJornada(UUID id,UUID jornadaId,UUID vinculoVehiculoEmpresaId,UUID vinculoPersonaEmpresaId,
        Instant vigenteDesde,Instant vigenteHasta,String motivoCambio,Instant creadoEn) {
    public AsignacionJornada {
        if (id == null || jornadaId == null || vinculoVehiculoEmpresaId == null || vinculoPersonaEmpresaId == null || vigenteDesde == null) {
            throw new IllegalArgumentException("Jornada, vínculos históricos e inicio de vigencia son obligatorios.");
        }
        if (vigenteHasta != null && vigenteHasta.isBefore(vigenteDesde)) throw new IllegalArgumentException("El cierre administrativo no puede ser anterior a la asignación.");
        if (motivoCambio != null) motivoCambio = Jornada.motivoObligatorio(motivoCambio);
    }
    public static AsignacionJornada inicial(UUID id,UUID jornada,UUID vehiculo,UUID persona,Instant ahora) {
        return new AsignacionJornada(id,jornada,vehiculo,persona,ahora,null,null,ahora);
    }
    public static AsignacionJornada reasignacion(UUID id,UUID jornada,UUID vehiculo,UUID persona,Instant ahora,String motivo) {
        return new AsignacionJornada(id,jornada,vehiculo,persona,ahora,null,Jornada.motivoObligatorio(motivo),ahora);
    }
}
