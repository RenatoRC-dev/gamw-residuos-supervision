import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useSesionActual } from '../../../administracion/identidad/aplicacion/contextoAutenticacion';
import { ErrorRevisionGps, revisarGps, type ParametrosRevision, type RevisionGps, type TipoRevision } from '../api/revisionGps';
import { MapaRevisionGps } from '../componentes/MapaRevisionGps';
import { actuacionesApi, ErrorActuacion, type Incidencia } from '../../../correctivas/api/actuaciones';

export function PaginaRevisionGps() {
  const { sesion, cerrar } = useSesionActual(); const [parametros] = useSearchParams();
  return <main><Link to="/">Inicio</Link><h1>Revisión técnica de evidencia GPS</h1>
    <p>Señales para revisión humana; no son sanciones, fraude comprobado ni cumplimiento de recolección.</p>
    {sesion ? <PanelRevisionGps key={`${sesion.tokenAcceso}:${parametros.get('jornada') ?? ''}`} token={sesion.tokenAcceso} inicial={parametros.get('jornada') ?? ''} vencer={cerrar} /> : <Link to="/acceso">Autenticarse</Link>}
  </main>;
}
const iniciales: ParametrosRevision = { huecoSegundos: 120, precisionMetros: 50, velocidadKmh: 120,
  desvioMetros: 100, radioDetencionMetros: 25, detencionSegundos: 180, antiguedadSegundos: 120 };
const etiquetas: Record<keyof ParametrosRevision, string> = { huecoSegundos: 'Separación temporal (s)', precisionMetros: 'Precisión reportada máxima (m)',
  velocidadKmh: 'Velocidad candidata máxima (km/h)', desvioMetros: 'Separación de ruta (m)', radioDetencionMetros: 'Radio técnico de agrupación (m)',
  detencionSegundos: 'Duración candidata de detención (s)', antiguedadSegundos: 'Antigüedad de captura (s)' };
/**
 * H-08 · CU-19: contexto de SOLO LECTURA con las incidencias ya registradas de la misma jornada, mediante la consulta existente
 * /api/correctivas/jornadas/{id} y su permiso existente CORRECTIVA_CONSULTAR (no se crea permiso ni asociación). No se cruzan
 * automáticamente con las señales: una incidencia no explica, justifica ni excusa una separación o detención candidata.
 */
function ContextoIncidencias({ token, jornada }: { token: string; jornada: string }) {
  const [estado, setEstado] = useState<{ incidencias: Incidencia[] } | { error: string } | null>(null);
  useEffect(() => {
    const abort = new AbortController();
    actuacionesApi.expediente(token, jornada, abort.signal)
      .then(e => { if (!abort.signal.aborted) setEstado({ incidencias: [...e.incidencias].sort((a, b) => a.ocurridaEn.localeCompare(b.ocurridaEn)) }); })
      .catch(e => { if (!abort.signal.aborted) setEstado({ error: e instanceof ErrorActuacion && e.estado === 403
        ? 'Sin permiso de consulta correctiva (CORRECTIVA_CONSULTAR): no se muestran incidencias de la jornada.'
        : 'No se pudo consultar las incidencias de la jornada; la revisión técnica no cambia.' }); });
    return () => abort.abort();
  }, [token, jornada]);
  return <section aria-label="Incidencias registradas de la jornada"><h3>Incidencias registradas de la jornada (contexto)</h3>
    <p>Solo contexto declarado para la revisión humana: no se asocian automáticamente a las señales ni explican, justifican o excusan una separación o detención.</p>
    {!estado ? <p role="status">Consultando incidencias autorizadas…</p>
      : 'error' in estado ? <p>{estado.error}</p>
        : !estado.incidencias.length ? <p>Sin incidencias registradas para esta jornada; no se infiere ninguna causa.</p>
          : <ul>{estado.incidencias.map(i => <li key={i.id}>Hecho: {i.ocurridaEn} · Registro: {i.registradaEn} · {i.descripcion}{i.lugarDescripcion ? ` · Lugar declarado: ${i.lugarDescripcion}` : ''}</li>)}</ul>}
  </section>;
}

/** Identidad inmutable de una consulta: un resultado solo se muestra si coincide con el contexto visible del formulario. */
const claveConsulta = (token: string, jornada: string, tipo: TipoRevision, p: ParametrosRevision) => JSON.stringify([token, jornada.trim(), tipo, p]);
export function PanelRevisionGps({ token, inicial, vencer }: { token: string; inicial: string; vencer?: (mensaje?: string) => void }) {
  const [jornada, setJornada] = useState(inicial), [tipo, setTipo] = useState<TipoRevision>('integridad');
  const [parametros, setParametros] = useState<ParametrosRevision>(iniciales);
  const [resultado, setResultado] = useState<{ clave: string; datos: RevisionGps } | null>(null);
  const [error, setError] = useState(''), [avisoMapa, setAvisoMapa] = useState(''), [cargando, setCargando] = useState(false);
  const peticion = useRef<AbortController | null>(null);
  // H-01: cualquier cambio de jornada, tipo, umbral o sesión cancela la consulta pendiente y retira el resultado o error anterior.
  const invalidar = () => { peticion.current?.abort(); peticion.current = null; setResultado(null); setError(''); setAvisoMapa(''); setCargando(false); };
  useEffect(() => () => { peticion.current?.abort(); peticion.current = null; }, [token]);
  const vigente = claveConsulta(token, jornada, tipo, parametros);
  const datos = resultado && resultado.clave === vigente ? resultado.datos : null;
  const consultar = async (e: FormEvent) => {
    e.preventDefault(); peticion.current?.abort(); const abort = new AbortController(); peticion.current = abort;
    const clave = vigente, solicitada = { jornada: jornada.trim(), tipo, parametros };
    setResultado(null); setError(''); setAvisoMapa(''); setCargando(true);
    const actual = () => peticion.current === abort && !abort.signal.aborted;
    try {
      const r = await revisarGps(token, solicitada.jornada, solicitada.tipo, solicitada.parametros, abort.signal);
      if (actual()) setResultado({ clave, datos: r });
    } catch (err) {
      if (!actual()) return;
      setError((err as Error).message);
      if (err instanceof ErrorRevisionGps && err.estado === 401) vencer?.('Sesión vencida. Vuelva a autenticarse.');
    } finally { if (actual()) { setCargando(false); peticion.current = null; } }
  };
  return <>
    <form onSubmit={consultar}>
      <label>Jornada UUID<input required value={jornada} onChange={e => { invalidar(); setJornada(e.target.value); }} /></label>
      <label>Revisión<select value={tipo} onChange={e => { invalidar(); setTipo(e.target.value as TipoRevision); }}>
        <option value="integridad">CU-17 · Continuidad e integridad</option><option value="coincidencias">CU-18 · Coincidencias espaciales</option>
        <option value="desvios">CU-19 · Separaciones y detenciones candidatas</option></select></label>
      <fieldset><legend>Umbrales técnicos PROVISIONALES, no contractuales</legend>
        {(Object.keys(etiquetas) as (keyof ParametrosRevision)[]).map(k => <label key={k}>{etiquetas[k]}
          <input type="number" required min="0.001" max="1000000" step="any" value={parametros[k]} onChange={e => { invalidar(); setParametros(p => ({ ...p, [k]: Number(e.target.value) })); }} />
        </label>)}
      </fieldset>
      <button disabled={cargando}>Revisar muestras recibidas</button>
    </form>
    {cargando && <p role="status">Calculando revisión autorizada…</p>}{error && <p role="alert">{error}</p>}
    {!datos && !cargando && !error && <p>Seleccione una jornada desde monitoreo/trayectoria o indique su UUID. Cada revisión exige su permiso específico.</p>}
    {datos && <section aria-label="Resultado técnico de revisión">
      <h2>{datos.estado}</h2><p>{datos.politica}</p>
      <p>Resultado de la consulta vigente: revisión {tipo} · jornada {datos.jornadaId}.</p>
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
      {tipo === 'desvios'
        ? <ContextoIncidencias key={`${token}:${datos.jornadaId}`} token={token} jornada={datos.jornadaId} />
        : <p>Incidencias operativas y verificación de causas no están implementadas en esta revisión; no se inventa una explicación.</p>}
    </section>}
  </>;
}
