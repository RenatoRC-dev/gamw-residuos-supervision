import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { FeatureCollection, Point } from 'geojson';
import { useSesionActual } from '../../administracion/identidad/aplicacion/contextoAutenticacion';
import { solicitarReclamos, descargarAnexo, ErrorReclamo, type OpcionesReclamos, type Reclamo, type Punto, type Pagina, type DetalleReclamo, type DetallePunto, type Anexo } from '../api/reclamos';
import { MapaReclamos } from '../componentes/MapaReclamos';

const R = '/reclamos-institucionales', P = '/puntos-criticos';
const fecha = (f: FormData, campo: string) => new Date(`${String(f.get(campo))}-04:00`).toISOString();
const opcional = (f: FormData, campo: string) => String(f.get(campo) ?? '').trim() || null;
const numero = (f: FormData, campo: string) => opcional(f, campo) === null ? null : Number(f.get(campo));
function ubicaciones(datos: { id: string; longitud: number | null; latitud: number | null; validadoMunicipalmente: boolean }[]): FeatureCollection<Point> {
  return { type: 'FeatureCollection', features: datos.filter(x => x.longitud !== null && x.latitud !== null).map(x => ({ type: 'Feature', id: x.id,
    properties: { validado: x.validadoMunicipalmente }, geometry: { type: 'Point', coordinates: [x.longitud!, x.latitud!] } })) };
}
export function PaginaReclamosPuntos() {
  const { sesion, cerrar } = useSesionActual();
  return <main><Link to="/">Inicio</Link><h1>Reclamos institucionales y puntos críticos</h1>
    <p>Recibir un reclamo, validar su pertinencia e identificar un punto crítico son actuaciones diferentes. No determinan incumplimiento ni sanción automáticamente.</p>
    {sesion ? <Contenido key={sesion.tokenAcceso} token={sesion.tokenAcceso} vencer={cerrar} /> : <Link to="/acceso">Autentíquese para continuar.</Link>}
  </main>;
}
function Contenido({ token, vencer }: { token: string; vencer: (s?: string) => void }) {
  const [opciones, setOpciones] = useState<OpcionesReclamos | null>(null), [error, setError] = useState(''), [tab, setTab] = useState('reclamos');
  useEffect(() => { const a = new AbortController(); solicitarReclamos<OpcionesReclamos>(token, `${R}/opciones`, a.signal).then(o => {
    if (!a.signal.aborted) { setOpciones(o); if (!o.puedeConsultarReclamos) setTab('puntos'); }
  }).catch(e => { if (!a.signal.aborted) { setError((e as Error).message); if (e instanceof ErrorReclamo && e.estado === 401) vencer('Sesión vencida.'); } });return () => a.abort(); }, [token, vencer]);
  return <><p role="alert">{error}</p>{!opciones ? <p>Sin acceso disponible o cargando permisos.</p> : <><p>{opciones.pendientes}</p>
    <nav>{opciones.puedeConsultarReclamos && <button onClick={() => setTab('reclamos')}>Reclamos recibidos</button>}{opciones.puedeConsultarPuntos && <button onClick={() => setTab('puntos')}>Puntos y observaciones</button>}</nav>
    {tab === 'reclamos' && opciones.puedeConsultarReclamos && <Reclamos token={token} opciones={opciones} vencer={vencer} />}
    {tab === 'puntos' && opciones.puedeConsultarPuntos && <Puntos token={token} opciones={opciones} vencer={vencer} />}</>}
  </>;
}
function useOperaciones(token: string, vencer: (s?: string) => void) {
  const vida = useRef<AbortController | null>(null), [ocupado, setOcupado] = useState(false), [mensaje, setMensaje] = useState(''), [denegado, setDenegado] = useState(false);
  useEffect(() => { const a = new AbortController(); vida.current = a; return () => a.abort(); }, []);
  const fallo = useCallback((e: unknown) => {
    setMensaje(e instanceof Error ? e.message : 'No se confirmó la operación.');
    if (e instanceof ErrorReclamo && (e.estado === 401 || e.estado === 403)) setDenegado(true);
    if (e instanceof ErrorReclamo && e.estado === 401) vencer('Sesión vencida.');
  }, [vencer]);
  async function guardar<T>(ruta: string, datos: unknown, recibir: (d: T) => void): Promise<boolean> {
    if (ocupado || !vida.current || vida.current.signal.aborted) return false;
    setOcupado(true); setMensaje('');
    try { const d = await solicitarReclamos<T>(token, ruta, vida.current.signal, datos); if (vida.current.signal.aborted) return false;
      recibir(d); setMensaje('Registro confirmado; origen e historia conservados.'); return true;
    } catch (e) { if (!vida.current.signal.aborted) { fallo(e); if (e instanceof ErrorReclamo && e.estado === 400) throw e; } return false; }
    finally { if (!vida.current.signal.aborted) setOcupado(false); }
  }
  async function descargar(id: string, anexo: Anexo) {
    if (!vida.current) return;
    try { await descargarAnexo(token, id, anexo, vida.current.signal); } catch (e) { if (!vida.current.signal.aborted) fallo(e); }
  }
  return { ocupado, mensaje, denegado, fallo, guardar, descargar };
}
function Formulario({ titulo, construir, enviar, ocupado, children }: { titulo: string; construir: (f: FormData) => unknown; enviar: (d: unknown) => Promise<boolean>; ocupado: boolean; children: ReactNode }) {
  const [pendiente, setPendiente] = useState<unknown>(null), [error, setError] = useState('');
  async function registrar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget; setError('');
    try { const datos = pendiente ?? construir(new FormData(form)); setPendiente(datos); if (await enviar(datos)) { setPendiente(null); form.reset(); } }
    catch { setPendiente(null); setError('Revise los campos. La solicitud inválida no se confirma.'); }
  }
  return <form onSubmit={registrar}><h3>{titulo}</h3><p role="alert">{error}</p><fieldset disabled={ocupado || pendiente !== null}>{children}</fieldset>
    <button disabled={ocupado}>{pendiente !== null ? 'Reintentar misma solicitud' : 'Registrar'}</button>
    {pendiente !== null && <p>Datos fijados para reintento; consulte el historial ante una respuesta incierta.</p>}</form>;
}
function CamposUbicacion({ obligatoria = false }: { obligatoria?: boolean }) {
  const [lon, setLon] = useState(''), [lat, setLat] = useState('');
  const datos = useMemo<FeatureCollection<Point>>(() => ({ type: 'FeatureCollection', features: [] }), []);
  return <><MapaReclamos datos={datos} seleccionar={(x, y) => { setLon(String(x)); setLat(String(y)); }} />
    <label>Longitud (EPSG:4326)<input name="longitud" type="number" step="any" min={-180} max={180} required={obligatoria || lat !== ''} value={lon} onChange={e => setLon(e.target.value)} /></label>
    <label>Latitud (EPSG:4326)<input name="latitud" type="number" step="any" min={-90} max={90} required={obligatoria || lon !== ''} value={lat} onChange={e => setLat(e.target.value)} /></label>
    <button type="button" onClick={() => { setLon(''); setLat(''); }}>Quitar ubicación seleccionada</button></>;
}
function Reclamos({ token, opciones, vencer }: { token: string; opciones: OpcionesReclamos; vencer: (s?: string) => void }) {
  const [pagina, setPagina] = useState<Pagina<Reclamo>>({ registros: [], siguiente: null }), [desde, setDesde] = useState(0), [revision, setRevision] = useState(0), [id, setId] = useState('');
  const op = useOperaciones(token, vencer), mapa = useMemo(() => ubicaciones(pagina.registros), [pagina.registros]);
  useEffect(() => { const a = new AbortController(); solicitarReclamos<Pagina<Reclamo>>(token, `${R}?desplazamiento=${desde}`, a.signal).then(d => { if (!a.signal.aborted) setPagina(d); }).catch(e => { if (!a.signal.aborted) op.fallo(e); });return () => a.abort(); }, [token, desde, revision, op.fallo]);
  return <section><p role="status">{op.mensaje}</p>{op.denegado ? <p>Acceso denegado. Reautentíquese o consulte sus concesiones.</p> : <>
    <h2>Recepción institucional</h2><MapaReclamos datos={mapa} tipo="reclamo" /><button onClick={() => setRevision(n => n + 1)}>Actualizar entradas</button>
    {!pagina.registros.length && <p>Sin reclamos en esta página; no se presume ausencia de problemas.</p>}
    <label>Reclamo<select value={id} onChange={e => setId(e.target.value)}><option value="">Seleccione</option>{id && !pagina.registros.some(r => r.id === id) && <option value={id}>{id}</option>}
      {pagina.registros.map(r => <option key={r.id} value={r.id}>{r.recibidoEn} · {r.canalDeclarado} · {r.validadoMunicipalmente ? 'Validación municipal registrada' : 'Sin validación municipal'} · {r.descripcion.slice(0, 90)}</option>)}</select></label>
    <button disabled={desde === 0} onClick={() => setDesde(Math.max(0, desde - 50))}>Anterior</button><button disabled={pagina.siguiente === null} onClick={() => pagina.siguiente !== null && setDesde(pagina.siguiente)}>Siguiente</button>
    {opciones.puedeRegistrarReclamos && <Formulario titulo="Registrar reclamo recibido" ocupado={op.ocupado} construir={f => ({ solicitudId: crypto.randomUUID(), canalDeclarado: f.get('canal'), recibidoEn: fecha(f, 'fecha'),
      descripcion: f.get('descripcion'), lugarDescripcion: f.get('lugar'), referenciasOrigen: f.get('origen'), longitud: numero(f, 'longitud'), latitud: numero(f, 'latitud'), jornadaId: opcional(f, 'jornada') })}
      enviar={d => op.guardar<DetalleReclamo>(R, d, r => { setId(r.reclamo.id); setRevision(n => n + 1); })}>
      <label>Canal declarado (no catálogo oficial)<input required name="canal" maxLength={200} placeholder="Teléfono, WhatsApp, atención presencial…" /></label>
      <label>Recepción original (Bolivia UTC−04:00)<input required name="fecha" type="datetime-local" step="1" /></label>
      <label>Descripción recibida<textarea required name="descripcion" maxLength={20000} /></label><label>Lugar descrito (opcional)<input name="lugar" maxLength={2000} /></label>
      <label>Referencias de origen y sustento de la relación, si existe<textarea name="origen" maxLength={10000} /></label>
      <label>Jornada comprobable (UUID opcional)<input name="jornada" /></label><CamposUbicacion />
      <p>No transcriba datos personales innecesarios ni credenciales de mensajería. Las referencias se conservan como texto, sin acceder a servicios externos.</p>
    </Formulario>}
    {id && <RevisionReclamo key={`${token}:${id}`} token={token} id={id} opciones={opciones} vencer={vencer} actualizar={() => setRevision(n => n + 1)} />}
  </>}</section>;
}
function RevisionReclamo({ token, id, opciones, vencer, actualizar }: { token: string; id: string; opciones: OpcionesReclamos; vencer: (s?: string) => void; actualizar: () => void }) {
  const [datos, setDatos] = useState<DetalleReclamo | null>(null), [revision, setRevision] = useState(0); const op = useOperaciones(token, vencer);
  useEffect(() => { const a = new AbortController(); solicitarReclamos<DetalleReclamo>(token, `${R}/${id}`, a.signal).then(d => { if (!a.signal.aborted) setDatos(d); }).catch(e => { if (!a.signal.aborted) op.fallo(e); });return () => a.abort(); }, [token, id, revision, op.fallo]);
  const recibir = (d: DetalleReclamo) => { setDatos(d); actualizar(); };
  return <section><p role="status">{op.mensaje}</p><button onClick={() => setRevision(n => n + 1)}>Actualizar historial</button>
    {op.denegado || !datos ? <p>Detalle no disponible o cargando.</p> : <><h2>Reclamo {id}</h2><p>{datos.reclamo.descripcion}</p>
      <p>Canal {datos.reclamo.canalDeclarado} · recepción {datos.reclamo.recibidoEn} · registro {datos.reclamo.registradoEn} · registrante {datos.reclamo.usuarioId}</p>
      <p>{datos.reclamo.lugarDescripcion} · {datos.reclamo.referenciasOrigen} · jornada original {datos.reclamo.jornadaId ?? 'No identificada'}</p>
      <p>{datos.reclamo.validadoMunicipalmente ? 'Pertinencia validada municipalmente; no prueba incumplimiento.' : 'Sin validación municipal registrada; consulte las valoraciones disponibles.'}</p>
      <h3>Actuaciones de revisión</h3>{datos.revisiones.map(r => <article key={r.id}><p>{r.resultado ?? 'Observación sin decisión de catálogo'} · {r.observaciones}</p><p>Autor {r.usuarioId} · {r.registradaEn} · política {r.referenciaAprobacion ?? 'Sin decisión final'}</p></article>)}
      <h3>Antecedentes relacionados</h3>{datos.relaciones.map(r => <p key={r.id}>{r.tipo} · {r.referenciaId} · {r.fundamento} · actor {r.usuarioId} · {r.registradaEn}</p>)}
      <p><Link to="/correctivas">Consultar correctivas (permiso independiente)</Link> · <Link to="/fiscalizacion">Consultar inspecciones (permiso independiente)</Link></p>
      {opciones.puedeRevisarReclamos && <><Formulario titulo="Agregar revisión humana" ocupado={op.ocupado} construir={f => ({ solicitudId: crypto.randomUUID(), resultadoId: opcional(f, 'resultado'), observaciones: f.get('observaciones') })} enviar={d => op.guardar(`${R}/${id}/revisiones`, d, recibir)}>
        <label>Resultado habilitado<select name="resultado" defaultValue=""><option value="">Solo observación, sin decisión final</option>{!datos.revisiones.some(r => r.resultadoId) && opciones.resultados.map(r => <option key={r.id} value={r.id}>{r.nombre} · {r.referenciaAprobacion}</option>)}</select></label>
        {!opciones.resultados.length && <p>Catálogo aprobado pendiente: puede documentar revisión, no inventar resultados.</p>}<label>Observaciones<textarea name="observaciones" required maxLength={20000} /></label>
      </Formulario><Formulario titulo="Vincular antecedente comprobable" ocupado={op.ocupado} construir={f => ({ tipo: f.get('tipo'), referenciaId: f.get('referencia'), fundamento: f.get('fundamento') })} enviar={d => op.guardar(`${R}/${id}/relaciones`, d, recibir)}>
        <label>Tipo<select name="tipo"><option>JORNADA</option><option>INCIDENCIA</option><option>ACCION_CORRECTIVA</option><option>INSPECCION</option></select></label><label>UUID existente<input required name="referencia" /></label><label>Fundamento de la relación<textarea required name="fundamento" maxLength={10000} /></label>
      </Formulario></>}
      <h3>Anexos conservados</h3>{datos.anexos.map(a => <article key={a.id}><p>{a.nombreOriginal} · {a.descripcion} · origen {a.origen}</p><p>Captura declarada {a.capturadoEn ?? 'No disponible'} · registro {a.registradoEn} · autor {a.usuarioId} · SHA-256 {a.sha256} · política {a.referenciaPolitica}</p><button onClick={() => op.descargar(id, a)}>Descargar anexo autorizado</button></article>)}
      {opciones.puedeRegistrarReclamos && (opciones.anexosHabilitados ? <Formulario titulo="Incorporar fotografía de respaldo" ocupado={op.ocupado} construir={f => {
        const fechaCaptura = opcional(f, 'capturadoEn'); if (fechaCaptura) f.set('capturadoEn', new Date(`${fechaCaptura}-04:00`).toISOString()); else f.delete('capturadoEn');return f;
      }} enviar={d => op.guardar(`${R}/${id}/anexos`, d, recibir)}>
        <p>{opciones.referenciaPoliticaAnexos}. Solo JPEG/PNG originales, hasta 5 MiB. El anexo no valida el reclamo.</p>
        <input required name="archivo" type="file" accept="image/png,image/jpeg" /><label>Procedencia<input required name="origen" maxLength={2000} /></label><label>Descripción<input required name="descripcion" maxLength={4000} /></label><label>Captura declarada opcional (Bolivia)<input name="capturadoEn" type="datetime-local" step="1" /></label>
      </Formulario> : <p>Anexos deshabilitados hasta contar con política específica de datos personales y evidencia; el reclamo principal se conserva.</p>)}
    </>}
  </section>;
}
function Puntos({ token, opciones, vencer }: { token: string; opciones: OpcionesReclamos; vencer: (s?: string) => void }) {
  const [pagina, setPagina] = useState<Pagina<Punto>>({ registros: [], siguiente: null }), [desde, setDesde] = useState(0), [revision, setRevision] = useState(0), [id, setId] = useState(''); const op = useOperaciones(token, vencer);
  const mapa = useMemo(() => ubicaciones(pagina.registros), [pagina.registros]);
  useEffect(() => { const a = new AbortController(); solicitarReclamos<Pagina<Punto>>(token, `${P}?desplazamiento=${desde}`, a.signal).then(d => { if (!a.signal.aborted) setPagina(d); }).catch(e => { if (!a.signal.aborted) op.fallo(e); });return () => a.abort(); }, [token, desde, revision, op.fallo]);
  return <section><p role="status">{op.mensaje}</p>{op.denegado ? <p>Acceso no autorizado.</p> : <><h2>Referencias críticas y candidatos municipales</h2><MapaReclamos datos={mapa} />
    <button onClick={() => setRevision(n => n + 1)}>Actualizar ubicaciones</button>
    {!pagina.registros.length && <p>No hay ubicaciones registradas en esta página.</p>}
    <label>Ubicación<select value={id} onChange={e => setId(e.target.value)}><option value="">Seleccione</option>{id && !pagina.registros.some(p => p.id === id) && <option value={id}>{id}</option>}{pagina.registros.map(p => <option key={p.id} value={p.id}>{p.denominacion} · {p.validadoMunicipalmente ? 'Validado municipalmente' : 'Candidato sin validación'}</option>)}</select></label>
    <button disabled={desde === 0} onClick={() => setDesde(Math.max(0, desde - 50))}>Anterior</button><button disabled={pagina.siguiente === null} onClick={() => pagina.siguiente !== null && setDesde(pagina.siguiente)}>Siguiente</button>
    {opciones.puedeRegistrarPuntos && <Formulario titulo="Registrar ubicación para revisión municipal" ocupado={op.ocupado} construir={f => ({ solicitudId: crypto.randomUUID(), denominacion: f.get('denominacion'), descripcion: f.get('descripcion'),
      longitud: numero(f, 'longitud'), latitud: numero(f, 'latitud'), sustentoMunicipal: f.get('sustento'), reclamoId: opcional(f, 'reclamo'), inspeccionId: opcional(f, 'inspeccion') })} enviar={d => op.guardar<DetallePunto>(P, d, p => { setId(p.punto.id); setRevision(n => n + 1); })}>
      <p>Consulte ubicaciones existentes y agregue observaciones al mismo punto cuando corresponda; no se deduce recurrencia por contar denuncias.</p>
      <label>Denominación<input required name="denominacion" maxLength={200} /></label><label>Descripción<textarea required name="descripcion" maxLength={20000} /></label><CamposUbicacion obligatoria />
      <label>Observación/sustento municipal<textarea required name="sustento" maxLength={10000} /></label><label>Reclamo de origen (UUID opcional)<input name="reclamo" /></label><label>Inspección de soporte (UUID opcional, requiere permiso de consulta)<input name="inspeccion" /></label>
      <p>Este registro no confirma por sí solo un punto crítico oficial; requiere actuación explícita de validación.</p>
    </Formulario>}
    {id && <HistorialPunto key={`${token}:${id}`} token={token} id={id} opciones={opciones} vencer={vencer} actualizar={() => setRevision(n => n + 1)} />}
  </>}</section>;
}
function HistorialPunto({ token, id, opciones, vencer, actualizar }: { token: string; id: string; opciones: OpcionesReclamos; vencer: (s?: string) => void; actualizar: () => void }) {
  const [datos, setDatos] = useState<DetallePunto | null>(null), [revision, setRevision] = useState(0); const op = useOperaciones(token, vencer);
  useEffect(() => { const a = new AbortController(); solicitarReclamos<DetallePunto>(token, `${P}/${id}`, a.signal).then(d => { if (!a.signal.aborted) setDatos(d); }).catch(e => { if (!a.signal.aborted) op.fallo(e); });return () => a.abort(); }, [token, id, revision, op.fallo]);
  const recibir = (d: DetallePunto) => { setDatos(d); actualizar(); };
  return <section><p role="status">{op.mensaje}</p><button onClick={() => setRevision(n => n + 1)}>Consultar historial</button>
    {op.denegado || !datos ? <p>Detalle no disponible o cargando.</p> : <><h2>{datos.punto.denominacion}</h2><p>{datos.punto.descripcion}</p><p>Coordenadas [longitud, latitud]: [{datos.punto.longitud}, {datos.punto.latitud}] · EPSG:4326.</p>
      <p>{datos.punto.sustentoMunicipal} · registrante {datos.punto.usuarioId} · {datos.punto.registradoEn}</p><p>Reclamo {datos.punto.reclamoId ?? 'Sin vínculo'} · inspección {datos.punto.inspeccionId ?? 'Sin vínculo'}.</p>
      {datos.validacion ? <p>Validación municipal explícita: {datos.validacion.fundamento} · actor {datos.validacion.usuarioId} · {datos.validacion.registradaEn} · política {datos.validacion.referenciaPolitica}. No implica cumplimiento ni sanción.</p> : <p>Candidato sin validación municipal; no es referencia confirmada ni zona roja.</p>}
      <h3>Historia de observaciones</h3>{datos.observaciones.map(o => <p key={o.id}>{o.descripcion} · observada {o.observadaEn} · recibida {o.registradaEn} · autor {o.usuarioId} · reclamo {o.reclamoId ?? 'No asociado'} · inspección {o.inspeccionId ?? 'No asociada'}</p>)}
      {opciones.puedeRegistrarPuntos && <Formulario titulo="Agregar observación sin borrar antecedentes" ocupado={op.ocupado} construir={f => ({ solicitudId: crypto.randomUUID(), observadaEn: fecha(f, 'fecha'), descripcion: f.get('descripcion'), reclamoId: opcional(f, 'reclamo'), inspeccionId: opcional(f, 'inspeccion') })} enviar={d => op.guardar(`${P}/${id}/observaciones`, d, recibir)}>
        <label>Fecha observada (Bolivia)<input required name="fecha" type="datetime-local" step="1" /></label><label>Observación<textarea required name="descripcion" maxLength={20000} /></label><label>Reclamo relacionado (UUID opcional)<input name="reclamo" /></label><label>Inspección relacionada (UUID opcional)<input name="inspeccion" /></label>
      </Formulario>}
      {!datos.validacion && opciones.puedeValidarPuntos && (opciones.politicaPuntosHabilitada ? <Formulario titulo="Validar identificación municipal" ocupado={op.ocupado} construir={f => ({ confirmaValidacionMunicipal: true, fundamento: f.get('fundamento') })} enviar={d => op.guardar(`${P}/${id}/validacion`, d, recibir)}>
        <p>Política habilitada: {opciones.referenciaPoliticaPuntos}.</p><label>Fundamento humano de la identificación<textarea required name="fundamento" maxLength={10000} /></label><label><input required type="checkbox" />Confirmo la validación municipal; no se infiere de reportes, conteos ni GPS.</label>
      </Formulario> : <p>Validación bloqueada hasta habilitar política institucional PENDING-10. No se inventan criterios ni categorías.</p>)}
    </>}
  </section>;
}
