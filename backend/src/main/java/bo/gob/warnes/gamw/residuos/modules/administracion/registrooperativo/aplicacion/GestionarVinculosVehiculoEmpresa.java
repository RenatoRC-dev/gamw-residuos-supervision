package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU-06 / RF-04 / BR-03. Cerrar y crear otro segmento conserva la historia; no se excluyen solapamientos. */
@Service
@Transactional(readOnly = true)
public class GestionarVinculosVehiculoEmpresa {
    private final RepositorioVehiculos repositorio;
    private final GestionarVehiculos vehiculos;
    private final GestionarEmpresasOperadoras empresas;
    public GestionarVinculosVehiculoEmpresa(RepositorioVehiculos repositorio,GestionarVehiculos vehiculos,GestionarEmpresasOperadoras empresas) {
        this.repositorio = repositorio; this.vehiculos = vehiculos; this.empresas = empresas;
    }
    public List<VinculoVehiculoEmpresa> listar(UUID empresaId) {
        empresas.obtener(empresaId); return repositorio.vinculos(empresaId);
    }
    @Transactional
    public VinculoVehiculoEmpresa crear(UUID empresaId,UUID vehiculoId,LocalDate fechaInicio,LocalDate fechaFin,boolean activo) {
        empresas.buscar(empresaId,true);
        vehiculos.obtener(vehiculoId);
        var ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var vinculo = new VinculoVehiculoEmpresa(UUID.randomUUID(),vehiculoId,empresaId,fechaInicio,fechaFin,activo,ahora,ahora);
        repositorio.insertarVinculo(vinculo); return vinculo;
    }
    @Transactional
    public VinculoVehiculoEmpresa cerrar(UUID empresaId,UUID vinculoId,LocalDate fechaFin,boolean activo) {
        empresas.buscar(empresaId,true);
        var anterior = repositorio.vinculo(empresaId,vinculoId).orElseThrow(() -> new RegistroNoEncontrado("Vínculo vehicular de la empresa no encontrado."));
        var vinculo = new VinculoVehiculoEmpresa(anterior.id(),anterior.vehiculoId(),anterior.empresaOperadoraId(),anterior.fechaInicio(),
                fechaFin,activo,anterior.creadoEn(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        repositorio.cerrarVinculo(vinculo); return vinculo;
    }
}
