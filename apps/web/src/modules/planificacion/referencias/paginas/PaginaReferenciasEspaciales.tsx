import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import type { FeatureCollection, Geometry } from 'geojson';
import { useSesionActual } from '../../../administracion/identidad/aplicacion/contextoAutenticacion';
import { crearMapaReferencias } from '../../../../shared/map/adaptadorReferenciasEspaciales';
import { referenciasApi, type ReferenciaEspacial, type VersionRutaControl } from '../api/referenciasEspaciales';
import 'maplibre-gl/dist/maplibre-gl.css';

export function PaginaReferenciasEspaciales() {
  const { sesion } = useSesionActual();
  return <main><Link to="/">Inicio</Link><h1>Puntos de control y geocercas</h1>
    <p>Referencias de revisión; no prueban recolección. La gestión exige concesión explícita, no autoridad administrativa implícita.</p>
    {sesion ? <Gestion key={sesion.tokenAcceso} token={sesion.tokenAcceso} /> : <Link to="/acceso">Autenticarse</Link>}
  </main>;
}
function Gestion({ token }: { token: string }) {
  const [filas, setFilas] = useState<ReferenciaEspacial[]>([]), [rutas, setRutas] = useState<VersionRutaControl[]>([]);
  const [desde, setDesde] = useState(0), [siguiente, setSiguiente] = useState<number | null>(null), [paginaRutas, setPaginaRutas] = useState(0);
  const [revision, setRevision] = useState(0), [versionando, setVersionando] = useState<ReferenciaEspacial | null>(null);
  const [nombre, setNombre] = useState(''), [ruta, setRuta] = useState(''), [motivo, setMotivo] = useState(''), [radio, setRadio] = useState('');
  const [geometria, setGeometria] = useState<Geometry | null>(null), [error, setError] = useState(''), [aviso, setAviso] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const nodo = useRef<HTMLDivElement>(null), mapa = useRef<ReturnType<typeof crearMapaReferencias> | null>(null);
  const peticion = useRef<AbortController | null>(null);
  const cambio = useCallback((g: Geometry | null) => setGeometria(g), []);
  useEffect(() => {
    if (!nodo.current) return;
    try { mapa.current = crearMapaReferencias(nodo.current, cambio, setError); }
    catch { setError('No se pudo iniciar el editor cartográfico.'); }
    return () => { mapa.current?.destruir(); peticion.current?.abort(); };
  }, [cambio]);
  useEffect(() => {
    const abort = new AbortController(); setFilas([]);
    referenciasApi.listar(token, desde, abort.signal).then(r => { if (!abort.signal.aborted) { setFilas(r.referencias); setSiguiente(r.siguiente); } })
      .catch(e => { if (!abort.signal.aborted) setError((e as Error).message); });
    return () => abort.abort();
  }, [token, desde, revision]);
  useEffect(() => {
    const abort = new AbortController(); setRutas([]);
    referenciasApi.rutas(token, paginaRutas, abort.signal).then(r => { if (!abort.signal.aborted) setRutas(r); })
      .catch(e => { if (!abort.signal.aborted) setError((e as Error).message); });
    return () => abort.abort();
  }, [token, paginaRutas]);
  useEffect(() => {
    const coleccion: FeatureCollection = { type: 'FeatureCollection', features: filas.map(v => ({ type: 'Feature', id: v.id,
      geometry: v.geometria, properties: { nombre: v.nombre, vigenteHasta: v.vigenteHasta } })) };
    mapa.current?.mostrar(coleccion);
  }, [filas]);
  const guardar = async (event: FormEvent) => {
    event.preventDefault();
    if (!geometria || !ruta) { setError('Dibuje o seleccione una geometría y su versión de ruta.'); return; }
    peticion.current?.abort(); const abort = new AbortController(); peticion.current = abort; setOcupado(true); setError('');
    try {
      const v = await referenciasApi.guardar(token, versionando?.referenciaId ?? null, { nombre, rutaVersionId: ruta, motivo,
        geometria, tipo: geometria.type === 'Point' ? 'PUNTO' : 'GEOCERCA', radioMetros: geometria.type === 'Point' ? Number(radio) : null }, abort.signal);
      if (!abort.signal.aborted) { setAviso(`Versión ${v.numeroVersion} conservada desde ${v.vigenteDesde}.`); setVersionando(null); setRevision(n => n + 1); }
    } catch (e) { if (!abort.signal.aborted) setError((e as Error).message); }
    finally { if (!abort.signal.aborted) setOcupado(false); }
  };
  const cerrar = async (v: ReferenciaEspacial) => {
    if (!motivo.trim()) { setError('Indique un motivo de cierre en el formulario.'); return; }
    peticion.current?.abort(); const abort = new AbortController(); peticion.current = abort; setOcupado(true);
    try { await referenciasApi.cerrar(token, v.referenciaId, motivo, abort.signal); if (!abort.signal.aborted) setRevision(n => n + 1); }
    catch (e) { if (!abort.signal.aborted) setError((e as Error).message); }
    finally { if (!abort.signal.aborted) setOcupado(false); }
  };
  return <>
    {error && <p role="alert">{error}</p>}{aviso && <p role="status">{aviso}</p>}
    <button onClick={() => mapa.current?.dibujar('point')}>Dibujar punto</button>
    <button onClick={() => mapa.current?.dibujar('polygon')}>Dibujar geocerca</button>
    <div ref={nodo} aria-label="Editor de referencias espaciales" style={{ height: 420 }} />
    <form onSubmit={guardar}>
      <h2>{versionando ? `Nueva versión de ${versionando.nombre}` : 'Registrar referencia'}</h2>
      <label>Nombre<input required maxLength={1000} value={nombre} onChange={e => setNombre(e.target.value)} /></label>
      <label>Versión de ruta<select required value={ruta} onChange={e => setRuta(e.target.value)}><option value="">Seleccione</option>
        {ruta && !rutas.some(r => r.rutaVersionId === ruta) && <option value={ruta}>{ruta}</option>}
        {rutas.map(r => <option key={r.rutaVersionId} value={r.rutaVersionId}>{r.codigo} · {r.nombre} v{r.numeroVersion}</option>)}</select></label>
      <button type="button" disabled={!paginaRutas} onClick={() => setPaginaRutas(Math.max(0, paginaRutas - 100))}>Versiones anteriores</button>
      <button type="button" disabled={rutas.length < 100} onClick={() => setPaginaRutas(paginaRutas + 100)}>Más versiones de ruta</button>
      <label>Radio explícito del punto (m)<input type="number" min="0.01" max="10000" step="any" required={geometria?.type === 'Point'} disabled={geometria?.type !== 'Point'} value={radio} onChange={e => setRadio(e.target.value)} /></label>
      <label>Motivo del registro, cambio o cierre<input required maxLength={1000} value={motivo} onChange={e => setMotivo(e.target.value)} /></label>
      <p>Vigencia desde el guardado; no se retrofecha. Los cambios crean versiones, sin sobrescribir geometrías. El radio es técnico explícito, no contractual.</p>
      <p>Geometría: {geometria?.type ?? 'No dibujada'}. MultiPolygon se conserva; para reemplazarlo puede dibujar un Polygon nuevo.</p>
      <button disabled={ocupado}>Guardar {versionando ? 'nueva versión' : 'referencia'}</button>
      <button type="button" onClick={() => { setVersionando(null); setGeometria(null); setNombre(''); setRadio(''); }}>Nuevo registro</button>
    </form>
    <p>Mapa y listado muestran esta página de versiones, incluidas las cerradas; no son ubicaciones oficiales precargadas.</p>
    <ul>{filas.map(v => <li key={v.id}>{v.nombre} · {v.tipo} v{v.numeroVersion} · RutaVersion {v.rutaVersionId} · {v.vigenteDesde} — {v.vigenteHasta ?? 'Abierta'} · Radio {v.radioMetros ?? 'No aplica'} m
      {!v.vigenteHasta && <><button disabled={ocupado} onClick={() => { setVersionando(v); setNombre(v.nombre); setRuta(v.rutaVersionId); setGeometria(v.geometria); setRadio(v.radioMetros?.toString() ?? ''); setMotivo(''); }}>Preparar nueva versión</button>
        <button disabled={ocupado} onClick={() => void cerrar(v)}>Cerrar vigencia conservando historia</button></>}
    </li>)}</ul>
    <button disabled={!desde} onClick={() => setDesde(Math.max(0, desde - 100))}>Página anterior</button>
    <button disabled={siguiente === null} onClick={() => setDesde(siguiente!)}>Página siguiente</button>
  </>;
}
