import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useSesionActual } from '../../../administracion/identidad/aplicacion/contextoAutenticacion';
import { revisarGps, type ParametrosRevision, type RevisionGps, type TipoRevision } from '../api/revisionGps';
import { MapaRevisionGps } from '../componentes/MapaRevisionGps';

export function PaginaRevisionGps() {
  const { sesion } = useSesionActual(); const [parametros] = useSearchParams();
  return <main><Link to="/">Inicio</Link><h1>Revisión técnica de evidencia GPS</h1>
    <p>Señales para revisión humana; no son sanciones, fraude comprobado ni cumplimiento de recolección.</p>
    {sesion ? <PanelRevisionGps key={`${sesion.tokenAcceso}:${parametros.get('jornada') ?? ''}`} token={sesion.tokenAcceso} inicial={parametros.get('jornada') ?? ''} /> : <Link to="/acceso">Autenticarse</Link>}
  </main>;
}
const iniciales: ParametrosRevision = { huecoSegundos: 120, precisionMetros: 50, velocidadKmh: 120,
  desvioMetros: 100, radioDetencionMetros: 25, detencionSegundos: 180, antiguedadSegundos: 120 };
const etiquetas: Record<keyof ParametrosRevision, string> = { huecoSegundos: 'Separación temporal (s)', precisionMetros: 'Precisión reportada máxima (m)',
  velocidadKmh: 'Velocidad candidata máxima (km/h)', desvioMetros: 'Separación de ruta (m)', radioDetencionMetros: 'Radio técnico de agrupación (m)',
  detencionSegundos: 'Duración candidata de detención (s)', antiguedadSegundos: 'Antigüedad de captura (s)' };
export function PanelRevisionGps({ token, inicial }: { token: string; inicial: string }) {
  const [jornada, setJornada] = useState(inicial), [tipo, setTipo] = useState<TipoRevision>('integridad');
  const [parametros, setParametros] = useState<ParametrosRevision>(iniciales), [datos, setDatos] = useState<RevisionGps | null>(null);
  const [error, setError] = useState(''), [avisoMapa, setAvisoMapa] = useState(''), [cargando, setCargando] = useState(false);
  const peticion = useRef<AbortController | null>(null);
  useEffect(() => () => peticion.current?.abort(), []);
  const consultar = async (e: FormEvent) => {
    e.preventDefault(); peticion.current?.abort(); const abort = new AbortController(); peticion.current = abort;
    setDatos(null); setError(''); setAvisoMapa(''); setCargando(true);
    try { const r = await revisarGps(token, jornada, tipo, parametros, abort.signal); if (!abort.signal.aborted) setDatos(r); }
    catch (e) { if (!abort.signal.aborted) setError((e as Error).message); }
    finally { if (!abort.signal.aborted) setCargando(false); }
  };
  return <>
    <form onSubmit={consultar}>
      <label>Jornada UUID<input required value={jornada} onChange={e => { setJornada(e.target.value); setDatos(null); }} /></label>
      <label>Revisión<select value={tipo} onChange={e => { setTipo(e.target.value as TipoRevision); setDatos(null); }}>
        <option value="integridad">CU-17 · Continuidad e integridad</option><option value="coincidencias">CU-18 · Coincidencias espaciales</option>
        <option value="desvios">CU-19 · Separaciones y detenciones candidatas</option></select></label>
      <fieldset><legend>Umbrales técnicos PROVISIONALES, no contractuales</legend>
        {(Object.keys(etiquetas) as (keyof ParametrosRevision)[]).map(k => <label key={k}>{etiquetas[k]}
          <input type="number" required min="0.001" max="1000000" step="any" value={parametros[k]} onChange={e => setParametros(p => ({ ...p, [k]: Number(e.target.value) }))} />
        </label>)}
      </fieldset>
      <button disabled={cargando}>Revisar muestras recibidas</button>
    </form>
    {cargando && <p role="status">Calculando revisión autorizada…</p>}{error && <p role="alert">{error}</p>}
    {!datos && !cargando && !error && <p>Seleccione una jornada desde monitoreo/trayectoria o indique su UUID. Cada revisión exige su permiso específico.</p>}
    {datos && <section aria-label="Resultado técnico de revisión">
      <h2>{datos.estado}</h2><p>{datos.politica}</p>
      <p>Jornada {datos.jornadaId} · RutaVersion {datos.rutaVersionId} · Cálculo {datos.calculadoEn}</p>
      <p>Muestras recibidas {datos.muestrasDisponibles}; analizadas {datos.muestrasAnalizadas}. {datos.parcial ? 'RESULTADO PARCIAL: límite de cálculo alcanzado.' : 'Solo se evalúa la evidencia recibida; pueden faltar capturas offline.'}</p>
      <p>Última captura: {datos.ultimaCaptura ?? 'No disponible'} · Última recepción: {datos.ultimaRecepcion ?? 'No disponible'}</p>
      <p>{datos.metadatosUbicacion}</p>
      <p>Parámetros efectivamente usados: {JSON.stringify(datos.parametros)}. Se puede recalcular; los datos originales no se alteran.</p>
      {avisoMapa && <p role="status">{avisoMapa}</p>}
      <p>Azul: ruta histórica. Naranja: muestras recibidas. Violeta: referencias espaciales, sin certificación de recolección. No se unen huecos GPS.</p>
      <MapaRevisionGps datos={datos} aviso={setAvisoMapa} />
      {!datos.hallazgos.length && <p>Sin señales listadas bajo estos parámetros; no certifica continuidad, autenticidad ni ausencia de detenciones.</p>}
      <div style={{ overflowX: 'auto' }}><table><thead><tr><th>Señal</th><th>Estado</th><th>Desde / hasta</th><th>Medición</th><th>Evidencia</th><th>Limitación</th></tr></thead>
        <tbody>{datos.hallazgos.map((h, i) => <tr key={i}><td>{h.tipo}</td><td>{h.estado}</td><td>{h.desde ?? 'No disponible'}<br />{h.hasta ?? 'No disponible'}</td>
          <td>{h.valor} {h.unidad}</td><td>{h.capturas.join(', ')}<br />{h.coordenadas.join(', ')}</td><td>{h.detalle}</td></tr>)}</tbody></table></div>
      <h3>Coincidencias observadas y limitaciones</h3>
      {tipo === 'coincidencias' && !datos.referencias.length && <p>Sin referencias aplicables disponibles; no equivale a no haber pasado.</p>}
      <ul>{datos.referencias.map(r => <li key={r.versionId}>{r.nombre} v{r.numeroVersion} · {r.estado} · {r.observadas} coincidencias entre {r.muestras} muestras temporalmente aplicables.
        <br />Primera: {r.primera ?? 'No observada'} · Última: {r.ultima ?? 'No observada'} · Distancia mínima: {r.distanciaMinimaMetros ?? 'No disponible'} m.
        <br />{r.limitacion}</li>)}</ul>
      <p><Link to={`/trayectorias?jornada=${encodeURIComponent(datos.jornadaId)}`}>Consultar trayectoria (permiso independiente)</Link></p>
      <p>Incidencias operativas y verificación de causas no están implementadas en esta revisión; no se inventa una explicación.</p>
    </section>}
  </>;
}
