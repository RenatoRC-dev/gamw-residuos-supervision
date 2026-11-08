import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { apiJornadas as api, type DetalleJornada, type AsignacionJornada, type PeriodoOpcion } from '../api/jornadas';
import { apiRutas, type Ruta, type RutaVersion } from '../../rutas/api/rutas';
import { apiRegistroOperativo, type VinculoPersonaEmpresa } from '../../../administracion/registro-operativo/api/registroOperativo';
import { apiVehiculos, type Vehiculo, type VinculoVehiculoEmpresa } from '../../../administracion/registro-operativo/api/vehiculos';
import { cubreFecha, versionAplicable, fechaHoraLocal } from '../modelo/vigencia';
import './jornadas.css';
import { ResumenEjecucion } from '../../../ejecucion/jornadas/componentes/ResumenEjecucion';

export function PaginaJornadas() {
  const [periodos, setPeriodos] = useState<PeriodoOpcion[]>([]);
  const [rutas, setRutas] = useState<Ruta[]>([]);
  const [jornadas, setJornadas] = useState<DetalleJornada[]>([]);
  const [versiones, setVersiones] = useState<RutaVersion[]>([]);
  const [seleccionada, setSeleccionada] = useState<DetalleJornada | null>(null);
  const [historial, setHistorial] = useState<AsignacionJornada[]>([]);
  const [vinculosVehiculos, setVinculosVehiculos] = useState<VinculoVehiculoEmpresa[]>([]);
  const [personal, setPersonal] = useState<VinculoPersonaEmpresa[]>([]);
  const [vehiculos, setVehiculos] = useState<Vehiculo[]>([]);
  const [periodoId, setPeriodoId] = useState('');
  const [rutaId, setRutaId] = useState('');
  const [versionId, setVersionId] = useState('');
  const [inicio, setInicio] = useState('');
  const [fin, setFin] = useState('');
  const [motivoReprogramacion, setMotivoReprogramacion] = useState('');
  const [reprogramando, setReprogramando] = useState<DetalleJornada | null>(null);
  const [reasignando, setReasignando] = useState(false);
  const [vinculoVehiculoId, setVinculoVehiculoId] = useState('');
  const [vinculoPersonaId, setVinculoPersonaId] = useState('');
  const [motivoCambio, setMotivoCambio] = useState('');
  const [ocupado, setOcupado] = useState(true);
  const [cargandoVersiones, setCargandoVersiones] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');

  useEffect(() => {
    const abort = new AbortController();
    Promise.all([api.periodos(abort.signal), apiRutas.listar(abort.signal), api.listar(abort.signal)])
      .then(([p, r, j]) => { if (!abort.signal.aborted) { setPeriodos(p); setRutas(r); setJornadas(j); } })
      .catch(e => { if (!abort.signal.aborted) setError(e instanceof Error ? e.message : 'No se pudo cargar la planificación.'); })
      .finally(() => { if (!abort.signal.aborted) setOcupado(false); });
    return () => abort.abort();
  }, []);

  useEffect(() => {
    const abort = new AbortController();
    setVersiones([]);
    if (!rutaId) { setCargandoVersiones(false); return () => abort.abort(); }
    setCargandoVersiones(true);
    apiRutas.versiones(rutaId).then(v => { if (!abort.signal.aborted) setVersiones(v); })
      .catch(e => { if (!abort.signal.aborted) setError(e instanceof Error ? e.message : 'No se pudieron consultar versiones.'); })
      .finally(() => { if (!abort.signal.aborted) setCargandoVersiones(false); });
    return () => abort.abort();
  }, [rutaId]);

  async function cargarDetalle(id: string) {
    const d = await api.obtener(id);
    const [h, v, p, maestros] = await Promise.all([api.historial(id), apiVehiculos.vinculos(d.empresaOperadora.id),
      apiRegistroOperativo.personal(d.empresaOperadora.id), apiVehiculos.listar()]);
    setSeleccionada(d); setHistorial(h); setVinculosVehiculos(v); setPersonal(p); setVehiculos(maestros);
    setReasignando(false); setVinculoVehiculoId(''); setVinculoPersonaId(''); setMotivoCambio('');
  }
  async function ejecutar(accion: () => Promise<void>) {
    setOcupado(true); setError(''); setMensaje('');
    try { await accion(); setMensaje('Planificación guardada y recuperada del servidor.'); }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo completar la planificación.'); }
    finally { setOcupado(false); }
  }
  function nueva() {
    setReprogramando(null); setPeriodoId(''); setRutaId(''); setVersionId(''); setInicio(''); setFin(''); setMotivoReprogramacion('');
  }
  function iniciarReprogramacion() {
    if (!seleccionada) return;
    setReprogramando(seleccionada); setPeriodoId(seleccionada.jornada.periodoServicioId);
    setRutaId(seleccionada.rutaVersion.rutaId); setVersionId(seleccionada.jornada.rutaVersionId);
    setInicio(fechaHoraLocal(seleccionada.jornada.inicioProgramado));
    setFin(seleccionada.jornada.finProgramado ? fechaHoraLocal(seleccionada.jornada.finProgramado) : '');
    setMotivoReprogramacion(''); setMensaje('');
  }
  function programar(event: FormEvent) {
    event.preventDefault();
    void ejecutar(async () => {
      const datos = { periodoServicioId: periodoId, rutaVersionId: versionId, inicioProgramado: new Date(inicio).toISOString(),
        finProgramado: fin ? new Date(fin).toISOString() : null };
      const creada = reprogramando ? await api.reprogramar(reprogramando.jornada.id, datos, motivoReprogramacion) : await api.crear(datos);
      setJornadas(await api.listar()); await cargarDetalle(creada.jornada.id); nueva();
    });
  }
  function asignar(event: FormEvent) {
    event.preventDefault();
    if (!seleccionada) return;
    void ejecutar(async () => {
      const recursos = { vinculoVehiculoEmpresaId: vinculoVehiculoId, vinculoPersonaEmpresaId: vinculoPersonaId };
      if (reasignando) await api.reasignar(seleccionada.jornada.id, recursos, motivoCambio);
      else await api.asignar(seleccionada.jornada.id, recursos);
      setJornadas(await api.listar()); await cargarDetalle(seleccionada.jornada.id);
    });
  }
  const periodo = periodos.find(p => p.id === periodoId);
  const disponibles = versiones.filter(v => !inicio || versionAplicable(new Date(inicio).toISOString(), v.vigenteDesde, v.vigenteHasta));
  const fecha = seleccionada?.fechaServicio ?? '';
  const vehiculosValidos = vinculosVehiculos.filter(v => v.activo && cubreFecha(fecha, v.fechaInicio, v.fechaFin)
    && vehiculos.some(m => m.id === v.vehiculoId && m.activo));
  const personasValidas = personal.filter(p => p.activo && p.persona.activo && cubreFecha(fecha, p.fechaInicio, p.fechaFin));
  const vigente = seleccionada?.jornada.vigente;
  return <main className="jornadas">
    <Link to="/">Inicio</Link><h1>Planificación de jornadas</h1>
    <p>La vigencia indica programación actual o sustituida; no acredita ejecución ni recolección. Los horarios de asignación son administrativos.</p>
    {error && <p role="alert">{error}</p>}
    {(ocupado || mensaje) && <p role="status">{ocupado ? 'Consultando planificación…' : mensaje}</p>}
    <fieldset disabled={ocupado}><legend>Programación y recursos</legend>
      <form aria-label="Programación de jornada" onSubmit={programar}>
        <h2>{reprogramando ? 'Nueva jornada por reprogramación' : 'Programar jornada'}</h2>
        {reprogramando && <p>Se conservará la jornada del {reprogramando.jornada.inicioProgramado} y se creará otra sin copiar recursos.</p>}
        <label>Periodo de servicio<select aria-label="Periodo de servicio" required value={periodoId} onChange={e => setPeriodoId(e.target.value)}>
          <option value="">Seleccione un periodo</option>{periodos.map(p => <option key={p.id} value={p.id} disabled={!p.activo || !p.empresaActiva}>
            {p.razonSocial} · {p.fechaInicio} — {p.fechaFin}{p.activo && p.empresaActiva ? '' : ' · Inactivo'}
          </option>)}
        </select></label>
        <p>Empresa del periodo: <output aria-label="Empresa del periodo">{periodo?.razonSocial ?? 'Seleccione un periodo'}</output></p>
        <label>Ruta de referencia<select aria-label="Ruta de referencia" required value={rutaId} onChange={e => { setRutaId(e.target.value); setVersionId(''); }}>
          <option value="">Seleccione una ruta</option>{rutas.map(r => <option key={r.id} value={r.id}>{r.codigo} · {r.nombre}</option>)}
        </select></label>
        <label>Inicio programado<input type="datetime-local" step="1" required value={inicio} onChange={e => { setInicio(e.target.value); setVersionId(''); }} /></label>
        <label>Fin programado<input type="datetime-local" step="1" value={fin} onChange={e => setFin(e.target.value)} /></label>
        <label>Versión de ruta<select aria-label="Versión de ruta" required disabled={cargandoVersiones} value={versionId} onChange={e => setVersionId(e.target.value)}>
          <option value="">Seleccione una versión válida</option>{disponibles.map(v => <option key={v.id} value={v.id}>Versión {v.numeroVersion} · desde {v.vigenteDesde}{v.vigenteHasta ? ` hasta ${v.vigenteHasta}` : ' · abierta'}</option>)}
        </select></label>
        {reprogramando && <label>Motivo de reprogramación<input required value={motivoReprogramacion} onChange={e => setMotivoReprogramacion(e.target.value)} /></label>}
        <button type="submit" disabled={cargandoVersiones}>{reprogramando ? 'Confirmar reprogramación' : 'Programar jornada'}</button>
        {reprogramando && <button type="button" onClick={nueva}>Cancelar reprogramación</button>}
      </form>
      <h2>Jornadas registradas</h2>
      <table><thead><tr><th>Programación</th><th>Empresa y periodo</th><th>Ruta y versión</th><th>Recursos actuales</th><th>Vigencia</th><th>Consulta</th></tr></thead>
        <tbody>{jornadas.map(d => <tr key={d.jornada.id}>
          <td>{d.jornada.inicioProgramado}{d.jornada.finProgramado ? ` — ${d.jornada.finProgramado}` : ' · Sin fin programado'}</td>
          <td>{d.empresaOperadora.razonSocial} · {d.periodoServicio.fechaInicio} — {d.periodoServicio.fechaFin}</td>
          <td>{d.rutaVersion.nombreRuta} · Versión {d.rutaVersion.numeroVersion}</td>
          <td>{d.asignacionActual ? `${d.asignacionActual.vehiculo.placa} · ${d.asignacionActual.responsable.nombres}` : 'Sin asignación'}</td>
          <td>{d.jornada.vigente ? 'Vigente' : 'Sustituida por reprogramación'}</td>
          <td><button onClick={() => void ejecutar(() => cargarDetalle(d.jornada.id))}>Consultar jornada</button></td>
        </tr>)}</tbody></table>
      {seleccionada && <section aria-label="Detalle de jornada">
        <h2>Jornada seleccionada</h2><p>Inicio: {seleccionada.jornada.inicioProgramado} · Empresa: {seleccionada.empresaOperadora.razonSocial}</p>
        <p>{vigente ? 'Programación vigente' : 'Programación histórica sustituida'}</p>
        {seleccionada.jornada.motivoReprogramacion && <p>Motivo de reprogramación: {seleccionada.jornada.motivoReprogramacion}</p>}
        {seleccionada.jornada.jornadaAntecedenteId && <button onClick={() => void ejecutar(() => cargarDetalle(seleccionada.jornada.jornadaAntecedenteId!))}>Consultar antecedente</button>}
        {jornadas.filter(d => d.jornada.jornadaAntecedenteId === seleccionada.jornada.id).map(d => <button key={d.jornada.id} onClick={() => void ejecutar(() => cargarDetalle(d.jornada.id))}>Consultar sucesora</button>)}
        {vigente && <button onClick={iniciarReprogramacion}>Reprogramar jornada</button>}
        <h3>Asignación actual</h3>
        <p>{seleccionada.asignacionActual ? `${seleccionada.asignacionActual.vehiculo.placa} · ${seleccionada.asignacionActual.responsable.nombres}` : 'Sin asignación de recursos'}</p>
        {vigente && seleccionada.asignacionActual && !reasignando && <button onClick={() => { setReasignando(true); setMensaje(''); }}>Reasignar recursos</button>}
        {vigente && (!seleccionada.asignacionActual || reasignando) && <form aria-label="Asignación de recursos" onSubmit={asignar}>
          <label>Vínculo de vehículo<select aria-label="Vínculo de vehículo" required value={vinculoVehiculoId} onChange={e => setVinculoVehiculoId(e.target.value)}>
            <option value="">Seleccione un vínculo válido</option>{vehiculosValidos.map(v => <option key={v.id} value={v.id}>{vehiculos.find(m => m.id === v.vehiculoId)?.placa} · desde {v.fechaInicio}</option>)}
          </select></label>
          <label>Vínculo de responsable<select aria-label="Vínculo de responsable" required value={vinculoPersonaId} onChange={e => setVinculoPersonaId(e.target.value)}>
            <option value="">Seleccione un vínculo válido</option>{personasValidas.map(p => <option key={p.id} value={p.id}>{p.persona.nombres} · {p.persona.ci} · {p.funcionesOperativas.map(f => f.nombre).join(', ')}</option>)}
          </select></label>
          {reasignando && <label>Motivo de reasignación<input required value={motivoCambio} onChange={e => setMotivoCambio(e.target.value)} /></label>}
          <button type="submit">{reasignando ? 'Confirmar reasignación' : 'Asignar recursos'}</button>
          {reasignando && <button type="button" onClick={() => setReasignando(false)}>Cancelar reasignación</button>}
        </form>}
        <h3>Historial administrativo de asignaciones</h3>
        <ul>{historial.map(a => <li key={a.id}>{a.vehiculo.placa} · {a.responsable.nombres} · {a.vigenteDesde} — {a.vigenteHasta ?? 'Abierta'}
          {a.motivoCambio ? ` · Motivo: ${a.motivoCambio}` : ' · Asignación inicial'}</li>)}</ul>
        <ResumenEjecucion key={seleccionada.jornada.id} jornadaId={seleccionada.jornada.id} />
      </section>}
    </fieldset>
  </main>;
}
