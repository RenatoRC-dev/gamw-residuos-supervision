import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { apiRegistroOperativo as api, type PersonaNatural, type EmpresaOperadora, type PeriodoServicio,
  type VinculoPersonaEmpresa, type FuncionOperativa } from '../api/registroOperativo';
import './operadores.css';
import { SeccionVehiculos } from '../componentes/SeccionVehiculos';

const opcional = (valor: FormDataEntryValue | null) => String(valor ?? '').trim() || null;
const texto = (datos: FormData, clave: string) => String(datos.get(clave) ?? '');

export function PaginaOperadores() {
  const [empresas, setEmpresas] = useState<EmpresaOperadora[]>([]);
  const [personas, setPersonas] = useState<PersonaNatural[]>([]);
  const [funciones, setFunciones] = useState<FuncionOperativa[]>([]);
  const [empresaId, setEmpresaId] = useState('');
  const [empresaEditada, setEmpresaEditada] = useState<EmpresaOperadora | null>(null);
  const [personaEditada, setPersonaEditada] = useState<PersonaNatural | null>(null);
  const [periodos, setPeriodos] = useState<PeriodoServicio[]>([]);
  const [personal, setPersonal] = useState<VinculoPersonaEmpresa[]>([]);
  const [periodoEditado, setPeriodoEditado] = useState<PeriodoServicio | null>(null);
  const [vinculoEditado, setVinculoEditado] = useState<VinculoPersonaEmpresa | null>(null);
  const [personaId, setPersonaId] = useState('');
  const [revision, setRevision] = useState(0);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [clavePersona, setClavePersona] = useState(0);
  const [claveEmpresa, setClaveEmpresa] = useState(0);
  const [clavePeriodo, setClavePeriodo] = useState(0);
  const [claveVinculo, setClaveVinculo] = useState(0);

  useEffect(() => {
    const abort = new AbortController();
    setCargando(true);
    Promise.all([api.empresas(abort.signal), api.personas(abort.signal), api.funciones(abort.signal)])
      .then(([e, p, f]) => { if (!abort.signal.aborted) { setEmpresas(e); setPersonas(p); setFunciones(f); } })
      .catch(e => { if (!abort.signal.aborted) setError(e instanceof Error ? e.message : 'No se pudo cargar el registro.'); })
      .finally(() => { if (!abort.signal.aborted) setCargando(false); });
    return () => abort.abort();
  }, [revision]);

  useEffect(() => {
    const abort = new AbortController();
    setPeriodos([]); setPersonal([]);
    if (empresaId) {
      Promise.all([api.periodos(empresaId, abort.signal), api.personal(empresaId, abort.signal)])
        .then(([p, v]) => { if (!abort.signal.aborted) { setPeriodos(p); setPersonal(v); } })
        .catch(e => { if (!abort.signal.aborted) setError(e instanceof Error ? e.message : 'No se pudo cargar la empresa.'); });
    }
    return () => abort.abort();
  }, [empresaId, revision]);

  useEffect(() => {
    setPeriodoEditado(null); setVinculoEditado(null);
    setClavePeriodo(c => c + 1); setClaveVinculo(c => c + 1);
  }, [empresaId]);

  async function ejecutar(accion: () => Promise<void>) {
    setGuardando(true); setError(''); setMensaje('');
    try { await accion(); setRevision(r => r + 1); setMensaje('Cambios guardados.'); }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo completar la operación.'); }
    finally { setGuardando(false); }
  }
  function guardarEmpresa(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const datos = new FormData(event.currentTarget);
    void ejecutar(async () => {
      const entrada = { nit: opcional(datos.get('nit')), razonSocial: texto(datos, 'razonSocial'), nombreComercial: opcional(datos.get('nombreComercial')) };
      const empresa = empresaEditada ? await api.actualizarEmpresa(empresaEditada.id, entrada) : await api.crearEmpresa(entrada);
      setEmpresaId(empresa.id); setEmpresaEditada(null); setClaveEmpresa(c => c + 1);
    });
  }
  function guardarPersona(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const datos = new FormData(event.currentTarget);
    void ejecutar(async () => {
      const entrada = { ci: texto(datos, 'ci'), nombres: texto(datos, 'nombres'), apellidoPaterno: opcional(datos.get('apellidoPaterno')),
        apellidoMaterno: opcional(datos.get('apellidoMaterno')), telefono: opcional(datos.get('telefono')) };
      const persona = personaEditada ? await api.actualizarPersona(personaEditada.id, entrada) : await api.crearPersona(entrada);
      setPersonaId(persona.id); setPersonaEditada(null); setClavePersona(c => c + 1);
    });
  }
  function guardarPeriodo(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const datos = new FormData(event.currentTarget);
    void ejecutar(async () => {
      const entrada = { fechaInicio: texto(datos, 'fechaInicio'), fechaFin: texto(datos, 'fechaFin') };
      if (periodoEditado) await api.actualizarPeriodo(empresaId, periodoEditado.id, entrada);
      else await api.crearPeriodo(empresaId, entrada);
      setPeriodoEditado(null); setClavePeriodo(c => c + 1);
    });
  }
  function guardarVinculo(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const datos = new FormData(event.currentTarget);
    void ejecutar(async () => {
      const entrada = { fechaInicio: texto(datos, 'fechaInicio'), fechaFin: opcional(datos.get('fechaFin')),
        activo: datos.has('activo') };
      if (vinculoEditado) await api.actualizarVinculo(empresaId, vinculoEditado.id, entrada);
      else {
        const funcionOperativaIds = datos.getAll('funcionOperativaIds').map(String);
        if (!funcionOperativaIds.length) throw new Error('Seleccione al menos una función operativa activa.');
        await api.crearVinculo(empresaId, personaId, { ...entrada, funcionOperativaIds });
      }
      setVinculoEditado(null); setClaveVinculo(c => c + 1);
    });
  }
  const empresaSeleccionada = empresas.find(e => e.id === empresaId);
  const personaSeleccionada = personas.find(p => p.id === personaId);
  const ocupado = cargando || guardando;

  return <main className="operadores">
    <Link to="/">Inicio</Link>
    <h1>Registro de operadores</h1>
    <p>Empresas, periodos y personal operativo. Las funciones operativas no son roles de acceso al sistema.</p>
    {error && <p role="alert">{error}</p>}
    {(cargando || guardando || mensaje) && <p role="status">{guardando ? 'Guardando…' : cargando ? 'Cargando…' : mensaje}</p>}
    <fieldset disabled={ocupado} className="registro-operativo"><legend>Administración del registro operativo</legend>
      <section aria-labelledby="empresas-titulo">
        <h2 id="empresas-titulo">Empresas operadoras</h2>
        <table><thead><tr><th>Razón social</th><th>NIT</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>{empresas.map(e => <tr key={e.id}><td>{e.razonSocial}</td><td>{e.nit ?? 'Sin NIT'}</td><td>{e.activo ? 'Activa' : 'Inactiva'}</td><td>
            <button onClick={() => { setEmpresaId(e.id); setError(''); }}>Seleccionar empresa</button>
            <button onClick={() => { setEmpresaEditada(e); setClaveEmpresa(c => c + 1); }}>Editar empresa</button>
            <button onClick={() => void ejecutar(async () => { await api.estadoEmpresa(e.id, !e.activo); })}>{e.activo ? 'Desactivar empresa' : 'Activar empresa'}</button>
          </td></tr>)}</tbody></table>
        {!empresas.length && <p>No hay empresas registradas.</p>}
        <form key={`empresa-${claveEmpresa}`} onSubmit={guardarEmpresa} aria-label="Datos de empresa">
          <h3>{empresaEditada ? 'Editar empresa' : 'Registrar empresa'}</h3>
          <label>Razón social<input name="razonSocial" required defaultValue={empresaEditada?.razonSocial ?? ''} /></label>
          <label>NIT<input name="nit" defaultValue={empresaEditada?.nit ?? ''} /></label>
          <label>Nombre comercial<input name="nombreComercial" defaultValue={empresaEditada?.nombreComercial ?? ''} /></label>
          <button type="submit">{empresaEditada ? 'Guardar empresa' : 'Crear empresa'}</button>
          {empresaEditada && <button type="button" onClick={() => { setEmpresaEditada(null); setClaveEmpresa(c => c + 1); }}>Cancelar edición de empresa</button>}
        </form>
      </section>
      <section aria-labelledby="personas-titulo">
        <h2 id="personas-titulo">Personas naturales</h2>
        <p>Identidad independiente de la empresa. Cerrar un vínculo no elimina la persona.</p>
        <label>Persona existente<select value={personaId} onChange={e => setPersonaId(e.target.value)}>
          <option value="">Seleccione una persona</option>
          {personas.map(p => <option key={p.id} value={p.id}>{p.ci} · {p.nombres} {p.apellidoPaterno ?? ''} {p.apellidoMaterno ?? ''}{p.activo ? '' : ' · Inactiva'}</option>)}
        </select></label>
        {personaSeleccionada && <>
          <button onClick={() => { setPersonaEditada(personaSeleccionada); setClavePersona(c => c + 1); }}>Editar persona</button>
          <button onClick={() => void ejecutar(async () => { await api.estadoPersona(personaSeleccionada.id, !personaSeleccionada.activo); })}>{personaSeleccionada.activo ? 'Desactivar persona' : 'Activar persona'}</button>
        </>}
        <form key={`persona-${clavePersona}`} onSubmit={guardarPersona} aria-label="Datos de persona">
          <h3>{personaEditada ? 'Editar persona' : 'Registrar persona'}</h3>
          <label>CI<input name="ci" required defaultValue={personaEditada?.ci ?? ''} /></label>
          <label>Nombres<input name="nombres" required defaultValue={personaEditada?.nombres ?? ''} /></label>
          <label>Apellido paterno<input name="apellidoPaterno" defaultValue={personaEditada?.apellidoPaterno ?? ''} /></label>
          <label>Apellido materno<input name="apellidoMaterno" defaultValue={personaEditada?.apellidoMaterno ?? ''} /></label>
          <label>Teléfono<input name="telefono" defaultValue={personaEditada?.telefono ?? ''} /></label>
          <button type="submit">{personaEditada ? 'Guardar persona' : 'Crear persona'}</button>
          {personaEditada && <button type="button" onClick={() => { setPersonaEditada(null); setClavePersona(c => c + 1); }}>Cancelar edición de persona</button>}
        </form>
      </section>
      {empresaSeleccionada ? <section aria-labelledby="empresa-seleccionada-titulo">
        <h2 id="empresa-seleccionada-titulo">Empresa seleccionada: {empresaSeleccionada.razonSocial}</h2>
        <section aria-labelledby="periodos-titulo">
          <h3 id="periodos-titulo">Periodos de servicio</h3>
          <ul>{periodos.map(p => <li key={p.id}>{p.fechaInicio} — {p.fechaFin} · {p.activo ? 'Activo' : 'Inactivo'}
            <button onClick={() => { setPeriodoEditado(p); setClavePeriodo(c => c + 1); }}>Editar periodo</button>
            <button onClick={() => void ejecutar(async () => { await api.estadoPeriodo(empresaId, p.id, !p.activo); })}>{p.activo ? 'Desactivar periodo' : 'Activar periodo'}</button>
          </li>)}</ul>
          <form key={`periodo-${empresaId}-${clavePeriodo}`} onSubmit={guardarPeriodo} aria-label="Datos de periodo">
            <label>Inicio del periodo<input name="fechaInicio" type="date" required defaultValue={periodoEditado?.fechaInicio ?? ''} /></label>
            <label>Fin del periodo<input name="fechaFin" type="date" required defaultValue={periodoEditado?.fechaFin ?? ''} /></label>
            <button type="submit">{periodoEditado ? 'Guardar periodo' : 'Crear periodo'}</button>
            {periodoEditado && <button type="button" onClick={() => { setPeriodoEditado(null); setClavePeriodo(c => c + 1); }}>Cancelar edición de periodo</button>}
          </form>
        </section>
        <section aria-labelledby="personal-titulo">
          <h3 id="personal-titulo">Personal de la empresa</h3>
          <ul>{personal.map(v => <li key={v.id}>{v.persona.ci} · {v.persona.nombres} · {v.fechaInicio} — {v.fechaFin ?? 'Sin cierre'} · {v.activo ? 'Activo' : 'Inactivo'}
            <span> · {v.funcionesOperativas.map(f => f.nombre).join(', ') || 'Sin funciones asignadas'}</span>
            <button onClick={() => { setVinculoEditado(v); setPersonaId(v.personaId); setClaveVinculo(c => c + 1); }}>Editar vínculo</button>
          </li>)}</ul>
          <form key={`vinculo-${empresaId}-${claveVinculo}`} onSubmit={guardarVinculo} aria-label="Datos de vínculo">
            <p>{vinculoEditado ? 'Editar vínculo histórico' : 'Asociar persona a la empresa'}</p>
            <label>Persona del vínculo<select required value={vinculoEditado?.personaId ?? personaId} disabled={!!vinculoEditado} onChange={e => setPersonaId(e.target.value)}>
              <option value="">Seleccione una persona</option>
              {personas.map(p => <option key={p.id} value={p.id}>{p.ci} · {p.nombres}</option>)}
            </select></label>
            <label>Inicio del vínculo<input name="fechaInicio" type="date" required readOnly={!!vinculoEditado} defaultValue={vinculoEditado?.fechaInicio ?? ''} /></label>
            <label>Fin del vínculo<input name="fechaFin" type="date" defaultValue={vinculoEditado?.fechaFin ?? ''} /></label>
            <label><input name="activo" type="checkbox" defaultChecked={vinculoEditado?.activo ?? true} />Vínculo activo</label>
            {vinculoEditado ? <p>Funciones históricas: {vinculoEditado.funcionesOperativas.map(f => f.nombre).join(', ')}.
              Para cambiar funciones, cierre/desactive este vínculo y cree uno nuevo para la misma persona.</p> :
            <fieldset><legend>Funciones operativas (seleccione al menos una; no son roles de seguridad)</legend>
              {funciones.filter(f => f.activo).map(f => <label key={f.id}>
                <input type="checkbox" name="funcionOperativaIds" value={f.id} />{f.nombre}
              </label>)}
            </fieldset>}
            <button type="submit">{vinculoEditado ? 'Guardar vínculo' : 'Asociar persona'}</button>
            {vinculoEditado && <button type="button" onClick={() => { setVinculoEditado(null); setClaveVinculo(c => c + 1); }}>Nuevo vínculo</button>}
          </form>
        </section>
        <SeccionVehiculos key={empresaId} empresaId={empresaId} empresaNombre={empresaSeleccionada.razonSocial} />
      </section> : <p>Seleccione o registre una empresa para gestionar sus periodos, personal y vehículos.</p>}
    </fieldset>
  </main>;
}
