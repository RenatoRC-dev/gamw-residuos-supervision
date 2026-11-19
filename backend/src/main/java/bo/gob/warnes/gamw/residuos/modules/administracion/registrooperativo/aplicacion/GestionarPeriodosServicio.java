package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU-05 / RF-03. No se impone exclusión de periodos solapados. */
@Service
@Transactional(readOnly = true)
public class GestionarPeriodosServicio {
    private final RepositorioRegistroOperativo repositorio;
    private final GestionarEmpresasOperadoras empresas;
    public GestionarPeriodosServicio(RepositorioRegistroOperativo repositorio, GestionarEmpresasOperadoras empresas) {
        this.repositorio = repositorio; this.empresas = empresas;
    }
    public List<PeriodoServicio> listar(UUID empresaId) { empresas.obtener(empresaId); return repositorio.periodos(empresaId); }
    private PeriodoServicio obtener(UUID empresaId, UUID id) {
        return repositorio.periodo(empresaId,id).orElseThrow(() -> new RegistroNoEncontrado("Periodo de la empresa no encontrado."));
    }
    @Transactional
    public PeriodoServicio crear(UUID empresaId, LocalDate fechaInicio, LocalDate fechaFin) {
        empresas.buscar(empresaId,true);
        var ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var periodo = new PeriodoServicio(UUID.randomUUID(),empresaId,fechaInicio,fechaFin,true,ahora,ahora);
        repositorio.insertarPeriodo(periodo);
        return periodo;
    }
    @Transactional
    public PeriodoServicio actualizar(UUID empresaId, UUID id, LocalDate fechaInicio, LocalDate fechaFin) {
        empresas.buscar(empresaId,true);
        var anterior = obtener(empresaId,id);
        var periodo = new PeriodoServicio(id,empresaId,fechaInicio,fechaFin,anterior.activo(),anterior.creadoEn(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        // CU-05 E3 / CA-CU-05-06: una corrección de fechas no puede dejar fuera del periodo jornadas ya registradas en él
        // (desnaturalizaría su referencia histórica). No es una política de solapamiento: solo protege lo ya programado.
        long fuera = repositorio.jornadasFueraDeFechas(id,periodo.fechaInicio(),periodo.fechaFin());
        if (fuera > 0) throw new ModificacionHistoricaIncompatible("La corrección dejaría " + fuera
                + " jornada(s) ya registrada(s) fuera del periodo; no se modifica el historial.");
        repositorio.actualizarPeriodo(periodo);
        return periodo;
    }
    @Transactional
    public PeriodoServicio cambiarEstado(UUID empresaId, UUID id, boolean activo) {
        empresas.buscar(empresaId,true);
        var p = obtener(empresaId,id);
        var periodo = new PeriodoServicio(id,empresaId,p.fechaInicio(),p.fechaFin(),activo,p.creadoEn(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        repositorio.actualizarPeriodo(periodo);
        return periodo;
    }
}
