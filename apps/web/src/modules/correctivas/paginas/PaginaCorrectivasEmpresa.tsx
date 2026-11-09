import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useSesionActual } from '../../administracion/identidad/aplicacion/contextoAutenticacion';
import { correctivasEmpresaApi, type ResumenEmpresa, type DetalleEmpresa, type SoporteEmpresa } from '../api/correctivasEmpresa';
import { ErrorActuacion } from '../api/actuaciones';

export function PaginaCorrectivasEmpresa() {
  const { sesion, cerrar } = useSesionActual();
  return <main><Link to="/">Inicio</Link><h1>Atención empresarial de acciones correctivas</h1>
    <p>Acceso concedido por periodo de servicio, sin herencia automática de empresas ni de periodos históricos.</p>
    {sesion ? <Seleccion key={sesion.tokenAcceso} token={sesion.tokenAcceso} vencer={cerrar} /> : <Link to="/acceso">Inicie sesión.</Link>}
  </main>;
}
function Seleccion({ token, vencer }: { token: string; vencer: (m?: string) => void }) {
  const [acciones, setAcciones] = useState<ResumenEmpresa[]>([]), [desde, setDesde] = useState(0), [siguiente, setSiguiente] = useState<number | null>(null);
  const [seleccion, setSeleccion] = useState(''), [error, setError] = useState(''), [cargando, setCargando] = useState(true), [revision, setRevision] = useState(0);
  useEffect(() => {
    const a = new AbortController(); setCargando(true); setError(''); setAcciones([]); setSeleccion('');
    correctivasEmpresaApi.listar(token, desde, a.signal).then(r => { if (!a.signal.aborted) { setAcciones(r.acciones); setSiguiente(r.siguiente); } })
      .catch(e => { if (!a.signal.aborted) { setError(e instanceof Error ? e.message : 'Consulta no disponible.'); if (e instanceof ErrorActuacion && e.estado === 401) vencer('Sesión vencida.'); } })
      .finally(() => { if (!a.signal.aborted) setCargando(false); });
    return () => a.abort();
  }, [token, desde, revision, vencer]);
  return <><p role="alert">{error}</p>{cargando && <p role="status">Consultando acciones autorizadas…</p>}
    {!cargando && !error && !acciones.length && <p>No hay acciones disponibles en los periodos expresamente concedidos.</p>}
    <button disabled={cargando} onClick={() => setRevision(n => n + 1)}>Actualizar acceso y acciones</button>
    <label>Acción correctiva<select value={seleccion} onChange={e => setSeleccion(e.target.value)}><option value="">Seleccione</option>
      {acciones.map(a => <option key={a.id} value={a.id}>{a.razonSocial} · {a.inicioProgramado} · {a.descripcion.slice(0, 100)} · periodo {a.periodoServicioId}</option>)}</select></label>
    <button disabled={cargando || desde === 0} onClick={() => setDesde(Math.max(0, desde - 50))}>Anterior</button>
    <button disabled={cargando || siguiente === null} onClick={() => siguiente !== null && setDesde(siguiente)}>Siguiente</button>
    {seleccion && <Detalle key={`${token}:${seleccion}`} token={token} id={seleccion} vencer={vencer} />}</>;
}
function Detalle({ token, id, vencer }: { token: string; id: string; vencer: (m?: string) => void }) {
  const [datos, setDatos] = useState<DetalleEmpresa | null>(null), [revision, setRevision] = useState(0), [error, setError] = useState('');
  const [ocupado, setOcupado] = useState(false), [aviso, setAviso] = useState('');
  const peticiones = useRef(new Set<AbortController>());
  useEffect(() => () => { peticiones.current.forEach(a => a.abort()); peticiones.current.clear(); }, []);
  function fallo(e: unknown) {
    setError(e instanceof Error ? e.message : 'Operación no confirmada.');
    if (e instanceof ErrorActuacion && (e.estado === 401 || e.estado === 403)) setDatos(null);
    if (e instanceof ErrorActuacion && e.estado === 401) vencer('Sesión vencida.');
  }
  useEffect(() => {
    const a = new AbortController();
    correctivasEmpresaApi.detalle(token, id, a.signal).then(d => { if (!a.signal.aborted) setDatos(d); })
      .catch(e => { if (!a.signal.aborted) fallo(e); });
    return () => a.abort();
  // Instancia aislada por token/acción; sin identidad obtenida de parámetros.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, id, revision]);
  async function responder(cuerpo: unknown): Promise<boolean> {
    if (ocupado) return false;
    const a = new AbortController(); peticiones.current.add(a); setOcupado(true); setError(''); setAviso('');
    try {
      await correctivasEmpresaApi.responder(token, id, cuerpo, a.signal);
      if (a.signal.aborted) return false;
      setAviso('Respuesta registrada. No equivale a verificación municipal.'); setRevision(n => n + 1); return true;
    } catch (e) {
      if (!a.signal.aborted) { fallo(e); if (e instanceof ErrorActuacion && e.estado === 400) throw e; }
      return false;
    } finally { peticiones.current.delete(a); if (!a.signal.aborted) setOcupado(false); }
  }
  async function descargar(e: SoporteEmpresa) {
    const a = new AbortController(); peticiones.current.add(a);
    try { await correctivasEmpresaApi.descargar(token, id, e, a.signal); }
    catch (errorDescarga) { if (!a.signal.aborted) fallo(errorDescarga); }
    finally { peticiones.current.delete(a); }
  }
  return <section><p role="alert">{error}</p><p role="status">{aviso}</p>
    <button disabled={ocupado} onClick={() => { setError(''); setRevision(n => n + 1); }}>Actualizar historial</button>
    {!datos ? <p>Detalle no disponible o cargando.</p> : <>
      <h2>Solicitud municipal</h2><p>{datos.solicitud.descripcion}</p>
      <p>Solicitud {datos.solicitud.id} · solicitante {datos.solicitud.usuarioId} · {datos.solicitud.creadaEn}</p>
      <p>Empresa {datos.contexto.empresa.razonSocial} ({datos.contexto.empresa.id}) · periodo {datos.contexto.periodo.id}: {datos.contexto.periodo.fechaInicio} — {datos.contexto.periodo.fechaFin}</p>
      <p>Jornada {datos.contexto.jornada.id} · {datos.contexto.jornada.inicioProgramado} — {datos.contexto.jornada.finProgramado ?? 'Sin fin programado'}</p>
      <p>Ruta {datos.contexto.ruta.codigo} · {datos.contexto.ruta.nombre} · versión histórica {datos.contexto.rutaVersion.numeroVersion} ({datos.contexto.rutaVersion.id})</p>
      <h3>Antecedente compartido</h3><p>{datos.antecedente.tipo} {datos.antecedente.id} · {datos.antecedente.fecha}</p><p>{datos.antecedente.descripcion}</p>
      <h3>Soporte histórico disponible</h3>{!datos.evidenciasDisponibles.length && <p>Sin archivos compartidos por el antecedente.</p>}
      {datos.evidenciasDisponibles.map(e => <article key={e.id}><p>{e.nombreOriginal} · {e.descripcion} · procedencia: {e.origen}</p>
        <p>Captura declarada {e.capturadaEn ?? 'No disponible'} · incorporación {e.incorporadaEn} · SHA-256 {e.sha256}</p>
        <button onClick={() => descargar(e)}>Descargar evidencia {e.id}</button></article>)}
      <h3>Atenciones anteriores</h3>{!datos.respuestas.length && <p>Sin respuesta empresarial registrada.</p>}
      {datos.respuestas.map(r => <article key={r.id}><h4>Respuesta {r.id}</h4><p>{r.descripcion}</p><p>{r.evidenciaDeclarada}</p>
        <p>Autor {r.usuarioId} · comunicada {r.comunicadaEn} · recibida {r.registradaEn}</p>
        <p>Evidencias referenciadas: {r.evidenciasReferenciadas.join(', ') || 'Ninguna'}</p></article>)}
      <h3>Verificaciones municipales registradas</h3>{!datos.verificaciones.length && <p>Sin verificación; la atención no se declara validada automáticamente.</p>}
      {datos.verificaciones.map(v => <article key={v.id}><p>{v.resultado} · {v.observaciones}</p>
        <p>Respuesta {v.respuestaId} · fiscalizador {v.usuarioId} · {v.registradaEn} · política {v.referenciaAprobacion}</p></article>)}
      {datos.puedeResponder ? <FormularioRespuesta datos={datos} ocupado={ocupado} enviar={responder} /> : <p>Consulta únicamente: no hay concesión vigente de respuesta para esta jornada.</p>}
    </>}
  </section>;
}
function FormularioRespuesta({ datos, ocupado, enviar }: { datos: DetalleEmpresa; ocupado: boolean; enviar: (c: unknown) => Promise<boolean> }) {
  const [pendiente, setPendiente] = useState<object | null>(null);
  async function registrar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget, f = new FormData(form);
    const solicitud = pendiente ?? { solicitudId: crypto.randomUUID(), comunicadaEn: new Date(`${String(f.get('fecha'))}-04:00`).toISOString(),
      observaciones: f.get('observaciones'), evidenciaDeclarada: f.get('soporte'), evidenciasReferenciadas: f.getAll('evidencia') };
    setPendiente(solicitud);
    try { if (await enviar(solicitud)) { setPendiente(null); form.reset(); } }
    catch { setPendiente(null); } // Solo rechazo 400 confirmado: permite corregir el contrato.
  }
  return <form onSubmit={registrar}><h3>Comunicar atención empresarial</h3>
    <fieldset disabled={ocupado || !!pendiente}>
      <label>Fecha/hora de comunicación (Bolivia UTC−04:00)<input required name="fecha" type="datetime-local" step="1" /></label>
      <label>Observaciones y atención realizada<textarea required name="observaciones" maxLength={20000} /></label>
      <label>Soporte y procedencia declarados (opcional, sin archivos nuevos)<textarea name="soporte" maxLength={10000} /></label>
      <p>Referencias históricas opcionales; no modifican los archivos originales:</p>
      {datos.evidenciasDisponibles.map(e => <label key={e.id}><input type="checkbox" name="evidencia" value={e.id} />{e.nombreOriginal} · {e.id}</label>)}
      <label><input type="checkbox" required />Confirmo la respuesta empresarial, sin sustituir la solicitud ni emitir una verificación municipal.</label>
    </fieldset><button disabled={ocupado}>{pendiente ? 'Reintentar misma respuesta' : 'Registrar atención'}</button>
    {pendiente && <p>Solicitud fijada para reintento idempotente. Ante una respuesta incierta, consulte el historial antes de generar otra actuación.</p>}
  </form>;
}
