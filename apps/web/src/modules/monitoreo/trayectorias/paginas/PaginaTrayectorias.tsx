import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useSesionActual } from '../../../administracion/identidad/aplicacion/contextoAutenticacion';
import { ErrorTrayectoria, trayectoriasApi, type JornadaTrayectoria, type DetalleTrayectoria } from '../api/trayectorias';
import { MapaTrayectoria } from '../componentes/MapaTrayectoria';

export function PaginaTrayectorias() {
  const { sesion, cerrar } = useSesionActual();
  const [parametros] = useSearchParams();
  return <main><Link to="/">Inicio</Link><h1>Trayectoria GPS de jornada</h1>
    <p>GPS es evidencia, no prueba automática de recolección ni de cumplimiento.</p>
    {!sesion ? <p><Link to="/acceso">Autentíquese para consultar.</Link></p>
      : <Consulta key={`${sesion.tokenAcceso}:${parametros.get('jornada') ?? ''}`} token={sesion.tokenAcceso} vencer={cerrar} jornadaInicial={parametros.get('jornada') ?? ''} />}
  </main>;
}

function Consulta({ token, vencer, jornadaInicial }: { token: string; vencer: (mensaje?: string) => void; jornadaInicial: string }) {
  const [jornadas, setJornadas] = useState<JornadaTrayectoria[]>([]);
  const [inicioLista, setInicioLista] = useState(0);
  const [siguienteLista, setSiguienteLista] = useState<number | null>(null);
  const [seleccion, setSeleccion] = useState(jornadaInicial);
  const [pagina, setPagina] = useState({ desplazamiento: 0, corte: null as string | null });
  const [datos, setDatos] = useState<DetalleTrayectoria | null>(null);
  const [error, setError] = useState('');
  const [avisoMapa, setAvisoMapa] = useState('');
  const [cargandoLista, setCargandoLista] = useState(false);
  const [cargando, setCargando] = useState(false);
  const [unir, setUnir] = useState(false);
  const [revision, setRevision] = useState(0);
  const fallo = useCallback((e: unknown) => {
    setDatos(null);
    if (e instanceof ErrorTrayectoria && e.estado === 401) vencer('Sesión vencida. Vuelva a autenticarse.');
    if (e instanceof ErrorTrayectoria && e.estado === 403) { setJornadas([]); setSeleccion(''); }
    setError(e instanceof ErrorTrayectoria ? e.message : 'No se pudo consultar. Revise la conexión y reintente.');
  }, [vencer]);
  useEffect(() => {
    const abort = new AbortController();
    setCargandoLista(true); setError(''); setSeleccion(inicioLista === 0 ? jornadaInicial : ''); setDatos(null); setJornadas([]);
    trayectoriasApi.jornadas(token, inicioLista, abort.signal).then(r => {
      if (!abort.signal.aborted) { setJornadas(r.jornadas); setSiguienteLista(r.siguiente); }
    }).catch(e => { if (!abort.signal.aborted) fallo(e); })
      .finally(() => { if (!abort.signal.aborted) setCargandoLista(false); });
    return () => abort.abort();
  }, [token, inicioLista, revision, fallo, jornadaInicial]);
  useEffect(() => {
    if (!seleccion) return;
    const abort = new AbortController();
    setCargando(true); setError(''); setAvisoMapa(''); setDatos(null);
    trayectoriasApi.obtener(token, seleccion, pagina.desplazamiento, pagina.corte, abort.signal).then(r => {
      if (!abort.signal.aborted) setDatos(r);
    }).catch(e => { if (!abort.signal.aborted) fallo(e); })
      .finally(() => { if (!abort.signal.aborted) setCargando(false); });
    return () => abort.abort();
  }, [token, seleccion, pagina, fallo]);
  const puntos = datos?.seguimiento.trayectoria.features ?? [];
  return <>
    <button onClick={() => setRevision(v => v + 1)} disabled={cargandoLista || cargando}>Actualizar listado</button>
    {error && <p role="alert">{error}</p>}
    {(cargandoLista || cargando) && <p role="status">Consultando evidencia autorizada…</p>}
    {!cargandoLista && !error && !jornadas.length && <p>Sin jornadas disponibles en el alcance autorizado.</p>}
    <label>Jornada autorizada<select value={seleccion} disabled={cargandoLista} onChange={e => {
      setDatos(null); setSeleccion(e.target.value); setPagina({ desplazamiento: 0, corte: null }); setUnir(false);
    }}><option value="">Seleccione una jornada</option>
      {datos && !jornadas.some(j => j.jornadaId === datos.contexto.jornadaId) && <option value={datos.contexto.jornadaId}>{datos.contexto.codigoRuta} · {datos.contexto.jornadaId}</option>}
      {jornadas.map(j => <option key={j.jornadaId} value={j.jornadaId}>{j.inicioProgramado} · {j.empresaOperadora} · {j.codigoRuta} v{j.numeroVersion} · {j.jornadaId}</option>)}
    </select></label>
    <button disabled={!inicioLista || cargandoLista} onClick={() => setInicioLista(Math.max(0, inicioLista - 50))}>Jornadas anteriores</button>
    <button disabled={siguienteLista === null || cargandoLista} onClick={() => setInicioLista(siguienteLista!)}>Más jornadas</button>
    {datos && <section aria-label="Evidencia de la jornada">
      <h2>{datos.contexto.nombreRuta} · versión {datos.contexto.numeroVersion}</h2>
      <p>Jornada: {datos.contexto.jornadaId} · Periodo: {datos.contexto.periodoServicioId} · {datos.contexto.empresaOperadora}</p>
      <p>Programación: {datos.contexto.inicioProgramado} — {datos.contexto.finProgramado ?? 'Fin no informado'}</p>
      <p>Versión histórica: {datos.contexto.rutaVersionId}. Corte de recepción: {datos.seguimiento.corteRecepcion}.</p>
      <p><Link to={`/fiscalizacion?jornada=${encodeURIComponent(datos.contexto.jornadaId)}`}>Consultar expediente de inspección y fiscalización</Link></p>
      <p><Link to={`/revision-gps?jornada=${encodeURIComponent(datos.contexto.jornadaId)}`}>Revisar continuidad, coincidencias y separaciones (permisos independientes)</Link></p>
      <p>Azul: ruta de referencia. Naranja: muestras GPS. Los intervalos sin muestras no acreditan recorrido.</p>
      <label><input type="checkbox" checked={unir} onChange={e => setUnir(e.target.checked)} />Unir muestras de esta página con trazos inferidos</label>
      {unir && <p>Los segmentos discontinuos son conexiones visuales inferidas, no recorrido físico confirmado.</p>}
      {avisoMapa && <p role="status">{avisoMapa}</p>}
      <MapaTrayectoria datos={datos} unir={unir} aviso={setAvisoMapa} />
      {!puntos.length && <p>No hay posiciones en esta consulta. No se fabrica una ubicación ni se concluye incumplimiento.</p>}
      {puntos.length === 1 && <p>Una muestra aislada no representa un recorrido continuo.</p>}
      <p>Muestras {datos.seguimiento.desplazamiento + (puntos.length ? 1 : 0)}–{datos.seguimiento.desplazamiento + puntos.length}.
        {datos.seguimiento.siguiente !== null || datos.seguimiento.desplazamiento > 0 ? ' Vista paginada; no representa toda la trayectoria en una sola página.' : ' Todas las muestras recibidas dentro del corte consultado.'}</p>
      <button disabled={!datos.seguimiento.desplazamiento || cargando} onClick={() => setPagina({ desplazamiento: Math.max(0, pagina.desplazamiento - 500), corte: datos.seguimiento.corteRecepcion })}>Muestras anteriores</button>
      <button disabled={datos.seguimiento.siguiente === null || cargando} onClick={() => setPagina({ desplazamiento: datos.seguimiento.siguiente!, corte: datos.seguimiento.corteRecepcion })}>Muestras siguientes</button>
      <button onClick={() => setPagina({ desplazamiento: 0, corte: null })}>Consultar nuevas recepciones</button>
      <div style={{ overflowX: 'auto' }}><table><thead><tr><th>Captura UUID</th><th>Capturado en</th><th>Recibido en</th><th>Longitud, latitud</th><th>Precisión (m)</th><th>Asignación</th></tr></thead>
        <tbody>{puntos.map(p => <tr key={p.id}><td>{p.properties.capturaId}</td><td>{p.properties.capturadoEn}</td><td>{p.properties.recibidoEn}</td>
          <td>{p.geometry.coordinates.join(', ')}</td><td>{p.properties.precisionMetros}</td><td>{p.properties.asignacionJornadaId}</td></tr>)}</tbody></table></div>
    </section>}
  </>;
}
