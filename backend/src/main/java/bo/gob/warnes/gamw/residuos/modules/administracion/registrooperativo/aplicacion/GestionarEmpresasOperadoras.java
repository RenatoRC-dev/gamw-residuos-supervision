package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU-04 / RF-03. Cambiar el estado no altera personas, periodos ni vínculos. */
@Service
@Transactional(readOnly = true)
public class GestionarEmpresasOperadoras {
    private final RepositorioRegistroOperativo repositorio;
    public GestionarEmpresasOperadoras(RepositorioRegistroOperativo repositorio) { this.repositorio = repositorio; }
    public List<EmpresaOperadora> listar() { return repositorio.empresas(); }
    public EmpresaOperadora obtener(UUID id) { return buscar(id,false); }
    public EmpresaOperadora buscar(UUID id, boolean bloquear) {
        return repositorio.empresa(id,bloquear).orElseThrow(() -> new RegistroNoEncontrado("Empresa operadora no encontrada."));
    }
    @Transactional
    public EmpresaOperadora crear(String nit, String razonSocial, String nombreComercial) {
        var ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var empresa = new EmpresaOperadora(UUID.randomUUID(),nit,razonSocial,nombreComercial,true,ahora,ahora);
        repositorio.insertarEmpresa(empresa);
        return empresa;
    }
    @Transactional
    public EmpresaOperadora actualizar(UUID id, String nit, String razonSocial, String nombreComercial) {
        var anterior = buscar(id,true);
        var empresa = new EmpresaOperadora(id,nit,razonSocial,nombreComercial,anterior.activo(),anterior.creadoEn(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        repositorio.actualizarEmpresa(empresa);
        return empresa;
    }
    @Transactional
    public EmpresaOperadora cambiarEstado(UUID id, boolean activo) {
        var e = buscar(id,true);
        var empresa = new EmpresaOperadora(id,e.nit(),e.razonSocial(),e.nombreComercial(),activo,e.creadoEn(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        repositorio.actualizarEmpresa(empresa);
        return empresa;
    }
}
