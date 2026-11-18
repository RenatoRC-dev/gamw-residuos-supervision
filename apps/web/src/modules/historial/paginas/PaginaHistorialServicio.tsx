import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useSesionActual } from '../../administracion/identidad/aplicacion/contextoAutenticacion';
import { apiHistorialServicio, validarFiltros, type DetalleHistorico, type FiltrosHistorial, type ResumenJornadaHistorica } from '../api/historialServicio';

const formato = (v: string | null) => (v ? new Date(v).toLocaleString('es-BO', { timeZone: 'America/La_Paz' }) : '—');
const VACIO: FiltrosHistorial = { periodoId: '', empresaId: '', desde: '', hasta: '' };

export function PaginaHistorialServicio() {
  const { sesion } = useSesionActual();
  const [parametros, setParametros] = useSearchParams();
  return <main><Link to="/">Inicio</Link><h1>Historial institucional del servicio</h1>
    <p>Consulta de solo lectura. Muestra los valores registrados en cada jornada (empresa, periodo, versión de ruta y asignaciones), no los vigentes hoy.</p>
    {!sesion ? <p><Link to="/acceso">Autentíquese para consultar.</Link></p>
      : <Historial key={sesion.tokenAcceso} jornadaId={parametros.get('jornada') ?? ''} elegir={id => setParametros(id ? { jornada: id } : {})} />}
  </main>;
}

function Historial({ jornadaId, elegir }: { jornadaId: string; elegir: (id: string) => void }) {
  const [filtros, setFiltros] = useState<FiltrosHistorial>(VACIO);
  const [aplicados, setAplicados] = useState<FiltrosHistorial | null>(null);
  const [desplazamiento, setDesplazamiento] = useState(0);
  const [lista, setLista] = useState<ResumenJornadaHistorica[] | null>(null);
  const [siguiente, setSiguiente] = useState<number | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!aplicados) return undefined;
    const abort = new AbortController();
    setCargando(true); setError('');
    apiHistorialServicio.buscar(aplicados, desplazamiento, abort.signal)
      .then(r => { if (!abort.signal.aborted) { setLista(r.jornadas); setSiguiente(r.siguiente); } })
      .catch(e => { if (!abort.signal.aborted) { setLista(null); setError(mensaje(e)); } })
      .finally(() => { if (!abort.signal.aborted) setCargando(false); });
    return () => abort.abort();
  }, [aplicados, desplazamiento]);

  function buscar(e: FormEvent) {
    e.preventDefault();
    const problema = validarFiltros(filtros);
    if (problema) { setError(problema); setLista(null); return; }
    setError(''); setDesplazamiento(0); setAplicados({ ...filtros });
  }
  if (jornadaId) return <DetalleJornadaHistorica id={jornadaId} volver={() => elegir('')} elegir={elegir} />;
  return <>
    <form onSubmit={buscar} aria-label="Filtros del historial">
      <label>Desde <input type="date" value={filtros.desde} onChange={e => setFiltros({ ...filtros, desde: e.target.value })} /></label>{' '}
      <label>Hasta <input type="date" value={filtros.hasta} onChange={e => setFiltros({ ...filtros, hasta: e.target.value })} /></label>{' '}
      <label>Periodo (ID) <input value={filtros.periodoId} onChange={e => setFiltros({ ...filtros, periodoId: e.target.value })} /></label>{' '}
      <label>Empresa (ID) <input value={filtros.empresaId} onChange={e => setFiltros({ ...filtros, empresaId: e.target.value })} /></label>{' '}
      <button type="submit" disabled={cargando}>Buscar</button>{' '}
      <button type="button" onClick={() => { setFiltros(VACIO); setAplicados(null); setLista(null); setError(''); }}>Limpiar</button>
      <p>Las fechas se interpretan como fecha de servicio en America/La_Paz. Para filtrar por empresa o periodo use «Filtrar» en un resultado.</p>
    </form>
    {error && <p role="alert">{error}</p>}
    {cargando && <p role="status">Consultando historial…</p>}
    {!aplicados && !error && <p>Defina filtros (o ninguno) y pulse Buscar.</p>}
    {lista && lista.length === 0 && <p role="status">La consulta no encontró antecedentes para esos filtros. No se creó ni modificó ningún registro.</p>}
    {lista && lista.length > 0 && <>
      <table><thead><tr><th>Inicio programado</th><th>Empresa operadora</th><th>Periodo</th><th>Ruta / versión</th><th>Estado</th><th>Asignaciones</th><th /></tr></thead>
        <tbody>{lista.map(j => <tr key={j.id}>
          <td>{formato(j.inicioProgramado)}</td>
          <td>{j.empresa.razonSocial} <button type="button" onClick={() => { const f = { ...filtros, empresaId: j.empresa.id }; setFiltros(f); setDesplazamiento(0); setAplicados(f); }}>Filtrar</button></td>
          <td>{j.periodo.fechaInicio} → {j.periodo.fechaFin} <button type="button" onClick={() => { const f = { ...filtros, periodoId: j.periodo.id }; setFiltros(f); setDesplazamiento(0); setAplicados(f); }}>Filtrar</button></td>
          <td>{j.ruta.nombre} · v{j.ruta.numeroVersion}</td>
          <td>{j.vigente ? 'Vigente' : 'Reprogramada (sustituida)'}{j.antecedenteId ? ' · tiene antecedente' : ''}</td>
          <td>{j.cantidadAsignaciones}</td>
          <td><button type="button" onClick={() => elegir(j.id)}>Ver detalle</button></td></tr>)}</tbody></table>
      <p>
        <button type="button" disabled={desplazamiento === 0 || cargando} onClick={() => setDesplazamiento(Math.max(0, desplazamiento - 50))}>Anterior</button>{' '}
        <button type="button" disabled={siguiente === null || cargando} onClick={() => siguiente !== null && setDesplazamiento(siguiente)}>Siguiente</button>
      </p></>}
  </>;
}

function mensaje(e: unknown) {
  const texto = e instanceof Error ? e.message : '';
  if (texto.includes('(403)') || /autoriz|permiso|Forbidden/i.test(texto)) return 'No tiene autorización municipal para consultar el historial de jornadas.';
  return texto || 'No se pudo leer el historial. Los registros no fueron modificados; reintente.';
}

function DetalleJornadaHistorica({ id, volver, elegir }: { id: string; volver: () => void; elegir: (id: string) => void }) {
  const [datos, setDatos] = useState<DetalleHistorico | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    const abort = new AbortController();
    setDatos(null); setError('');
    apiHistorialServicio.detalle(id, abort.signal)
      .then(d => { if (!abort.signal.aborted) setDatos(d); })
      .catch(e => { if (!abort.signal.aborted) setError(mensaje(e)); });
    return () => abort.abort();
  }, [id]);
  return <section aria-label="Detalle histórico de jornada">
    <p><button type="button" onClick={volver}>← Volver a resultados</button></p>
    {error && <p role="alert">{error}</p>}
    {!datos && !error && <p role="status">Cargando detalle…</p>}
    {datos && <>
      <h2>Jornada {formato(datos.jornada.inicioProgramado)}</h2>
      <dl>
        <dt>Empresa operadora (del periodo original)</dt><dd>{datos.jornada.empresa.razonSocial}</dd>
        <dt>Periodo de servicio</dt><dd>{datos.jornada.periodo.fechaInicio} → {datos.jornada.periodo.fechaFin}</dd>
        <dt>Ruta y versión originales</dt><dd>{datos.jornada.ruta.nombre} · versión {datos.jornada.ruta.numeroVersion} (vigencia {formato(datos.jornada.ruta.vigenteDesde)} → {formato(datos.jornada.ruta.vigenteHasta)})</dd>
        <dt>Estado de la programación</dt><dd>{datos.jornada.vigente ? 'Vigente' : 'Reprogramada: sustituida por una sucesora'}{datos.jornada.motivoReprogramacion ? ` · motivo: ${datos.jornada.motivoReprogramacion}` : ''}</dd>
      </dl>
      {datos.inconsistencias.length > 0 && <div role="note"><h3>Observaciones sobre los registros</h3><ul>{datos.inconsistencias.map(i => <li key={i}>{i}</li>)}</ul></div>}
      <h3>Cadena de reprogramación</h3>
      <ol>{datos.cadena.map(c => <li key={c.id}>{c.id === id ? <strong>{formato(c.inicioProgramado)} (esta jornada)</strong> : <button type="button" onClick={() => elegir(c.id)}>{formato(c.inicioProgramado)}</button>}
        {' '}— {c.empresa.razonSocial}, v{c.ruta.numeroVersion}, {c.vigente ? 'vigente' : 'sustituida'}{c.motivoReprogramacion ? `, motivo: ${c.motivoReprogramacion}` : ''}</li>)}</ol>
      <h3>Historial de asignaciones de recursos</h3>
      {datos.asignaciones.length === 0 ? <p>No hay asignaciones registradas para esta jornada.</p>
        : <table><thead><tr><th>Desde</th><th>Hasta</th><th>Vehículo</th><th>Responsable</th><th>Motivo</th></tr></thead>
          <tbody>{datos.asignaciones.map(a => <tr key={a.id}><td>{formato(a.vigenteDesde)}</td><td>{a.vigenteHasta ? formato(a.vigenteHasta) : 'Abierta'}</td><td>{a.placa}</td><td>{a.responsable}</td><td>{a.motivoCambio ?? '—'}</td></tr>)}</tbody></table>}
      <h3>Evidencias (vistas existentes)</h3>
      <p>Esta página no confirma si existen registros: cada vista aplica su propio permiso y distingue «sin registros» de «sin acceso».</p>
      <ul>{datos.enlaces.map(l => <li key={l.ruta}><Link to={l.ruta}>{l.modulo}</Link> — {l.nota}</li>)}</ul>
    </>}
  </section>;
}
