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
    /** H1: lectura con bloqueo compartido (FOR SHARE) para validar programación sin carrera con la corrección del periodo. */
    Optional<PeriodoServicio> periodoPorIdCompartido(UUID id);
    /** H1: lectura con bloqueo compartido (FOR SHARE) del estado de la empresa al programar o asignar. */
    Optional<EmpresaOperadora> empresaCompartida(UUID id);
    List<PeriodoServicio> todosPeriodos();
    /** CU-05 E3: jornadas ya registradas del periodo cuya fecha de servicio (America/La_Paz) quedaría fuera de [inicio, fin]. */
    long jornadasFueraDeFechas(UUID periodoId, java.time.LocalDate inicio, java.time.LocalDate fin);
    void insertarVinculo(VinculoPersonaEmpresa vinculo);
    void actualizarVinculo(VinculoPersonaEmpresa vinculo);
    /** CU-07 E5 / CA-CU-07-08: asignaciones (vigentes o históricas) hechas con el vínculo cuya fecha de servicio quedaría después de {@code fin}. */
    long asignacionesPosterioresAlCierre(UUID vinculoId, java.time.LocalDate fin);
    Optional<VinculoPersonaEmpresa> vinculo(UUID empresaId, UUID id);
    Optional<VinculoPersonaEmpresa> vinculoPorId(UUID id);
    List<VinculoPersonaEmpresa> personal(UUID empresaId);
    List<FuncionOperativa> funciones();
    List<FuncionOperativa> funcionesDelVinculo(UUID vinculoId);
    void insertarFunciones(List<VinculoPersonaFuncion> funciones);
}
