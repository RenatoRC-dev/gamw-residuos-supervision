package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia del registro operativo: CU-04, CU-05 y CU-07 parcial; sin borrado físico de negocio. */
public interface RepositorioRegistroOperativo {
    void insertarPersona(PersonaNatural persona);
    void actualizarPersona(PersonaNatural persona);
    Optional<PersonaNatural> persona(UUID id, boolean bloquear);
    List<PersonaNatural> personas();
    void insertarEmpresa(EmpresaOperadora empresa);
    void actualizarEmpresa(EmpresaOperadora empresa);
    Optional<EmpresaOperadora> empresa(UUID id, boolean bloquear);
    List<EmpresaOperadora> empresas();
    void insertarPeriodo(PeriodoServicio periodo);
    void actualizarPeriodo(PeriodoServicio periodo);
    Optional<PeriodoServicio> periodo(UUID empresaId, UUID id);
    List<PeriodoServicio> periodos(UUID empresaId);
    Optional<PeriodoServicio> periodoPorId(UUID id);
    List<PeriodoServicio> todosPeriodos();
    void insertarVinculo(VinculoPersonaEmpresa vinculo);
    void actualizarVinculo(VinculoPersonaEmpresa vinculo);
    Optional<VinculoPersonaEmpresa> vinculo(UUID empresaId, UUID id);
    Optional<VinculoPersonaEmpresa> vinculoPorId(UUID id);
    List<VinculoPersonaEmpresa> personal(UUID empresaId);
    List<FuncionOperativa> funciones();
    List<FuncionOperativa> funcionesDelVinculo(UUID vinculoId);
    void insertarFunciones(List<VinculoPersonaFuncion> funciones);
}
