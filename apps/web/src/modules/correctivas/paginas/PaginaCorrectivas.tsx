import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useSesionActual } from '../../administracion/identidad/aplicacion/contextoAutenticacion';
import { actuacionesApi, ErrorActuacion, type ExpedienteCorrectivo, type HistoriaCorrectiva } from '../api/actuaciones';
import type { JornadaTrayectoria } from '../../monitoreo/trayectorias/api/trayectorias';
import type { ContextoFiscal } from '../../fiscalizacion/api/fiscalizacion';
import { Contexto as ContextoFiscalizacion } from '../../fiscalizacion/paginas/PaginaFiscalizacion';

export function PaginaCorrectivas() {
  const { sesion, cerrar } = useSesionActual(); const [busqueda] = useSearchParams();
  return <main><Link to="/">Inicio</Link><h1>Incidencias y acciones correctivas</h1>
    <p>La atención empresarial y la verificación municipal son actuaciones distintas. No se determinan sanciones ni cumplimiento automáticamente.</p>
    {sesion ? <Seleccion key={sesion.tokenAcceso} token={sesion.tokenAcceso} inicial={busqueda.get('jornada') ?? ''} vencer={cerrar} />
      : <Link to="/acceso">Autentíquese para continuar.</Link>}
  </main>;
}
function Seleccion({ token, inicial, vencer }: { token: string; inicial: string; vencer: (s?: string) => void }) {
  const [jornadas, setJornadas] = useState<JornadaTrayectoria[]>([]), [desde, setDesde] = useState(0), [siguiente, setSiguiente] = useState<number | null>(null);
  const [id, setId] = useState(inicial), [error, setError] = useState(''), [cargando, setCargando] = useState(false);
  useEffect(() => {
    const abort = new AbortController(); setCargando(true); setError('');
    actuacionesApi.jornadas(token, desde, abort.signal).then(r => { if (!abort.signal.aborted) { setJornadas(r.jornadas); setSiguiente(r.siguiente); } })
      .catch(e => { if (!abort.signal.aborted) { setJornadas([]); setId(''); setError((e as Error).message); if (e instanceof ErrorActuacion && e.estado === 401) vencer('Sesión vencida.'); } })
      .finally(() => { if (!abort.signal.aborted) setCargando(false); });
    return () => abort.abort();
  }, [token, desde, vencer]);
  return <><p role="alert">{error}</p>{cargando && <p role="status">Cargando jornadas autorizadas…</p>}
    {!cargando && !jornadas.length && <p>Sin jornadas disponibles en esta página.</p>}
    <label>Jornada<select value={id} onChange={e => setId(e.target.value)}><option value="">Seleccione</option>
      {jornadas.map(j => <option value={j.jornadaId} key={j.jornadaId}>{j.inicioProgramado} · {j.empresaOperadora} · {j.codigoRuta} v{j.numeroVersion}</option>)}</select></label>
    <button disabled={desde === 0 || cargando} onClick={() => setDesde(Math.max(0, desde - 50))}>Anterior</button>
    <button disabled={siguiente === null || cargando} onClick={() => siguiente !== null && setDesde(siguiente)}>Siguiente</button>
    {id && <Expediente key={`${token}:${id}`} token={token} jornada={id} vencer={vencer} />}</>;
}
function Expediente({ token, jornada, vencer }: { token: string; jornada: string; vencer: (s?: string) => void }) {
  const [datos, setDatos] = useState<ExpedienteCorrectivo | null>(null), [revision, setRevision] = useState(0), [error, setError] = useState('');
  const [ocupado, setOcupado] = useState(false), [aviso, setAviso] = useState('');
  const vida = useRef<AbortController | null>(null);
  useEffect(() => { const a = new AbortController(); vida.current = a; return () => a.abort(); }, []);
  function fallo(e: unknown) {
    setError(e instanceof Error ? e.message : 'Operación no confirmada.');
    if (e instanceof ErrorActuacion && e.estado === 401) vencer('Sesión vencida.');
    if (e instanceof ErrorActuacion && e.estado === 403) setDatos(null);
  }
  useEffect(() => {
    const a = new AbortController();
    actuacionesApi.expediente(token, jornada, a.signal).then(d => { if (!a.signal.aborted) setDatos(d); })
      .catch(e => { if (!a.signal.aborted) fallo(e); });
    return () => a.abort();
  // La instancia está aislada por jornada/token; revisar no cambia identidad.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revision, token, jornada]);
  async function guardar(accion: string | null, cuerpo: unknown): Promise<boolean> {
    if (ocupado || !vida.current || vida.current.signal.aborted) return false;
    setOcupado(true); setError(''); setAviso('');
    try {
      if (accion === null) await actuacionesApi.crear(token, jornada, cuerpo, vida.current.signal);
      else await actuacionesApi.verificar(token, accion, cuerpo, vida.current.signal);
      if (vida.current.signal.aborted) return false;
      setAviso('Registro confirmado por el servidor; historial anterior conservado.'); setRevision(n => n + 1); return true;
    } catch (e) {
      if (!vida.current.signal.aborted) {
        fallo(e);
        if (e instanceof ErrorActuacion && e.estado === 400) throw e;
      }
      return false;
    }
    finally { if (!vida.current.signal.aborted) setOcupado(false); }
  }
  return <section><p role="alert">{error}</p><p role="status">{aviso}</p>
    <button disabled={ocupado} onClick={() => { setError(''); setRevision(n => n + 1); }}>Actualizar expediente</button>
    {!datos ? <p>Expediente no disponible o cargando.</p> : <>
      <ContextoFiscalizacion datos={datos.contexto} /><p>{datos.limitaciones}</p>
      <p><Link to={`/fiscalizacion?jornada=${jornada}`}>Inspecciones y fiscalización</Link> · <Link to={`/trayectorias?jornada=${jornada}`}>Trayectoria GPS (permiso independiente)</Link></p>
      <h2>Incidencias operativas</h2>{!datos.incidencias.length && <p>Sin incidencias registradas; no se infiere cumplimiento.</p>}
      {datos.incidencias.map(i => <article key={i.id}><h3>Incidencia {i.id}</h3><p>{i.descripcion}</p>
        <p>Hecho declarado: {i.ocurridaEn}; recepción: {i.registradaEn}; autor: {i.usuarioId}; asignación: {i.asignacionJornadaId}.</p>
        <p>Lugar declarado: {i.lugarDescripcion || 'No informado'}. Soporte declarado: {i.evidenciaDeclarada || 'No informado'}.</p></article>)}
      {datos.puedeSolicitar && <Solicitud key={jornada} datos={datos} ocupado={ocupado} guardar={c => guardar(null, c)} />}
      <h2>Historial correctivo</h2>{!datos.acciones.length && <p>Sin acciones; una incidencia no genera una acción automáticamente.</p>}
      {datos.acciones.map(h => <article key={h.accion.id}><h3>Acción {h.accion.id}</h3><p>{h.accion.descripcion}</p>
        <p>Solicitante {h.accion.usuarioId} · {h.accion.creadaEn}. Origen: {h.accion.incidenciaId ?? h.accion.inspeccionId ?? h.accion.fiscalizacionId}.</p>
        <details><summary>Contexto conservado al solicitar</summary><ContextoHistorico json={h.accion.antecedentes} /></details>
        {!h.respuestas.length && <p>Sin atención empresarial disponible. Se requiere una respuesta autorizada antes de verificar.</p>}
        {h.respuestas.map(r => <section key={r.id}><h4>Atención {r.id}</h4><p>{r.descripcion}</p><p>{r.evidenciaDeclarada}</p>
          <p>Autor {r.usuarioId} · comunicación {r.comunicadaEn} · recepción {r.registradaEn}</p>
          <p>Evidencias históricas referenciadas: {r.evidenciasReferenciadas.join(', ') || 'Ninguna'}</p></section>)}
        {h.verificaciones.map(v => <section key={v.id}><h4>Verificación humana: {v.resultado}</h4><p>{v.observaciones}</p>
          <p>Respuesta {v.respuestaId} · autor {v.usuarioId} · {v.registradaEn} · política {v.referenciaAprobacion}</p>
          {v.inspeccionId && <p>Inspección de soporte: {v.inspeccionId}</p>}</section>)}
        {datos.puedeVerificar && <Verificar key={h.accion.id} historia={h} datos={datos} ocupado={ocupado} guardar={c => guardar(h.accion.id, c)} />}
      </article>)}
      <p><Link to="/empresa/correctivas">Atención empresarial</Link>: exige concesiones explícitas por periodo; no se habilita por conocer UUID ni por función operativa.</p>
    </>}
  </section>;
}
function ContextoHistorico({ json }: { json: string }) {
  try { return <ContextoFiscalizacion datos={JSON.parse(json) as ContextoFiscal} />; }
  catch { return <p>Contexto no disponible en esta presentación; no se reemplaza.</p>; }
}
function Solicitud({ datos, ocupado, guardar }: { datos: ExpedienteCorrectivo; ocupado: boolean; guardar: (c: unknown) => Promise<boolean> }) {
  const [pendiente, setPendiente] = useState<object | null>(null);
  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget, f = new FormData(form);
    const [tipo, id] = String(f.get('origen')).split(':');
    const c = pendiente ?? { solicitudId: crypto.randomUUID(), incidenciaId: tipo === 'incidencia' ? id : null,
      inspeccionId: tipo === 'inspeccion' ? id : null, fiscalizacionId: tipo === 'fiscalizacion' ? id : null, descripcion: f.get('descripcion') };
    setPendiente(c);
    try { if (await guardar(c)) { setPendiente(null); form.reset(); } }
    catch { setPendiente(null); } // Rechazo 400 confirmado: permite corregir datos.
  }
  const hayOrigen = datos.incidencias.length + datos.inspecciones.length + (datos.fiscalizacion ? 1 : 0) > 0;
  return <form onSubmit={enviar}><h2>Solicitar corrección municipal</h2><fieldset disabled={ocupado || !!pendiente || !hayOrigen}>
    <label>Antecedente<select required name="origen" defaultValue=""><option value="">Seleccione</option>
      {datos.incidencias.map(i => <option key={i.id} value={`incidencia:${i.id}`}>Incidencia {i.ocurridaEn} · {i.descripcion.slice(0, 80)}</option>)}
      {datos.inspecciones.map(i => <option key={i.id} value={`inspeccion:${i.id}`}>Inspección {i.realizadaEn} · {i.observaciones.slice(0, 80)}</option>)}
      {datos.fiscalizacion && <option value={`fiscalizacion:${datos.fiscalizacion.id}`}>Fiscalización {datos.fiscalizacion.resultado.nombre}</option>}
    </select></label><label>Corrección requerida<textarea name="descripcion" required maxLength={20000} /></label>
  </fieldset><button disabled={ocupado || !hayOrigen}>{pendiente ? 'Reintentar misma solicitud' : 'Registrar solicitud'}</button>
    {pendiente && <p>Datos fijados para reintento idempotente. Actualice el historial ante una respuesta incierta; no genere otra solicitud equivalente.</p>}
    {!hayOrigen && <p>Registre primero un antecedente válido; no se crean actuaciones sin origen.</p>}</form>;
}
function Verificar({ historia, datos, ocupado, guardar }: { historia: HistoriaCorrectiva; datos: ExpedienteCorrectivo; ocupado: boolean; guardar: (c: unknown) => Promise<boolean> }) {
  const [pendiente, setPendiente] = useState<object | null>(null), [resultado, setResultado] = useState('');
  const respuestas = historia.respuestas.filter(r => !historia.verificaciones.some(v => v.respuestaId === r.id));
  const categoria = datos.resultados.find(c => c.id === resultado);
  if (!respuestas.length) return null;
  if (!datos.resultados.length) return <p>Verificación bloqueada: catálogo de resultados aprobado pendiente.</p>;
  async function enviar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget, f = new FormData(form);
    const c = pendiente ?? { solicitudId: crypto.randomUUID(), respuestaId: f.get('respuesta'), resultadoId: f.get('resultado'),
      observaciones: f.get('observaciones'), inspeccionId: f.get('inspeccion') || null };
    setPendiente(c);
    try { if (await guardar(c)) { setPendiente(null); setResultado(''); form.reset(); } }
    catch { setPendiente(null); }
  }
  return <form onSubmit={enviar}><h4>Registrar verificación municipal</h4><fieldset disabled={ocupado || !!pendiente}>
    <label>Atención a evaluar<select required name="respuesta" defaultValue=""><option value="">Seleccione</option>{respuestas.map(r => <option key={r.id} value={r.id}>{r.comunicadaEn} · {r.descripcion.slice(0, 80)}</option>)}</select></label>
    <label>Resultado aprobado<select required name="resultado" value={resultado} onChange={e => setResultado(e.target.value)}><option value="">Seleccione</option>{datos.resultados.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}</select></label>
    {categoria && <p>Referencia de política: {categoria.referenciaAprobacion}</p>}
    <label>Observaciones<textarea name="observaciones" required={categoria?.observacionesObligatorias} maxLength={20000} /></label>
    <label>Inspección de soporte<select name="inspeccion" defaultValue=""><option value="">Sin referencia adicional</option>{datos.inspecciones.map(i => <option key={i.id} value={i.id}>{i.realizadaEn} · {i.id}</option>)}</select></label>
    <label><input type="checkbox" required />Confirmo la evaluación humana; no constituye sanción ni cierre automático.</label>
  </fieldset><button disabled={ocupado}>{pendiente ? 'Reintentar misma verificación' : 'Registrar verificación'}</button></form>;
}
