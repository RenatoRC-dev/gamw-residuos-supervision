import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useSesionActual } from '../../administracion/identidad/aplicacion/contextoAutenticacion';
import { fiscalizacionApi, ErrorFiscal, type ContextoFiscal, type ExpedienteFiscal, type EvidenciaCampo } from '../api/fiscalizacion';
import type { JornadaTrayectoria, DetalleTrayectoria } from '../../monitoreo/trayectorias/api/trayectorias';
import { MapaTrayectoria } from '../../monitoreo/trayectorias/componentes/MapaTrayectoria';
import { PanelRevisionGps } from '../../monitoreo/revision/paginas/PaginaRevisionGps';
import { FormularioInspeccion } from '../componentes/FormularioInspeccion';
import { FormularioDecisionFiscal } from '../componentes/FormularioDecisionFiscal';

export function PaginaFiscalizacion() {
  const { sesion, cerrar } = useSesionActual(); const [busqueda] = useSearchParams();
  return <main><Link to="/">Inicio</Link><h1>Inspección y fiscalización municipal</h1>
    <p>La observación de campo, las muestras GPS, los candidatos técnicos y la determinación humana son antecedentes diferentes.</p>
    {sesion ? <Seleccion key={sesion.tokenAcceso} token={sesion.tokenAcceso} inicial={busqueda.get('jornada') ?? ''} vencer={cerrar} /> : <Link to="/acceso">Autentíquese para continuar.</Link>}
  </main>;
}
function Seleccion({ token, inicial, vencer }: { token: string; inicial: string; vencer: (s?: string) => void }) {
  const [jornadas, setJornadas] = useState<JornadaTrayectoria[]>([]), [desde, setDesde] = useState(0), [siguiente, setSiguiente] = useState<number | null>(null);
  const [id, setId] = useState(inicial), [error, setError] = useState(''), [cargando, setCargando] = useState(false);
  useEffect(() => {
    const abort = new AbortController(); setCargando(true); setError('');
    fiscalizacionApi.jornadas(token, desde, abort.signal).then(r => {
      if (!abort.signal.aborted) { setJornadas(r.jornadas); setSiguiente(r.siguiente); }
    }).catch(e => { if (!abort.signal.aborted) { setJornadas([]); setId(''); setError((e as Error).message); if (e instanceof ErrorFiscal && e.estado === 401) vencer('Sesión vencida.'); } })
      .finally(() => { if (!abort.signal.aborted) setCargando(false); });
    return () => abort.abort();
  }, [token, desde, vencer]);
  return <>
    {cargando && <p role="status">Consultando jornadas autorizadas…</p>}{error && <p role="alert">{error}</p>}
    {!cargando && !error && !jornadas.length && <p>Sin jornadas disponibles en esta página autorizada.</p>}
    <label>Jornada<select value={id} disabled={cargando} onChange={e => setId(e.target.value)}><option value="">Seleccione una jornada</option>
      {id && !jornadas.some(j => j.jornadaId === id) && <option value={id}>{id}</option>}
      {jornadas.map(j => <option key={j.jornadaId} value={j.jornadaId}>{j.inicioProgramado} · {j.empresaOperadora} · {j.codigoRuta} v{j.numeroVersion} · {j.jornadaId}</option>)}
    </select></label>
    <button disabled={!desde || cargando} onClick={() => setDesde(Math.max(0, desde - 50))}>Página anterior</button>
    <button disabled={siguiente === null || cargando} onClick={() => setDesde(siguiente!)}>Página siguiente</button>
    {id && <Expediente key={`${token}:${id}`} token={token} jornada={id} vencer={vencer} />}
  </>;
}
export function Contexto({ datos }: { datos: ContextoFiscal }) {
  return <section aria-label="Contexto operacional e histórico">
    <h2>{datos.empresa.razonSocial} · {datos.ruta.codigo} · versión {datos.rutaVersion.numeroVersion}</h2>
    <p>Jornada {datos.jornada.id} · Periodo {datos.periodo.id}: {datos.periodo.fechaInicio} — {datos.periodo.fechaFin}</p>
    <p>Programación {datos.jornada.inicioProgramado} — {datos.jornada.finProgramado ?? 'Fin no informado'} · RutaVersion {datos.rutaVersion.id}</p>
    <h3>Recursos históricos</h3>
    {!datos.asignaciones.length && <p>Sin asignaciones conservadas.</p>}
    <ul>{datos.asignaciones.map(a => <li key={a.asignacion.id}>{a.placa} ({a.vehiculoId}) · {a.nombres} {a.apellidoPaterno} {a.apellidoMaterno} ({a.personaId})
      <br />Asignación {a.asignacion.id}: {a.asignacion.vigenteDesde} — {a.asignacion.vigenteHasta ?? 'Abierta'}</li>)}</ul>
    <h3>Ejecución registrada</h3>
    {!datos.eventos.length && <p>Sin eventos recibidos; no se presume ausencia de servicio.</p>}
    <ul>{datos.eventos.map(e => <li key={e.id}>{e.tipoEvento} · captura {e.capturadoEn} · recepción {e.recibidoEn} · {e.usuarioId ?? 'Actor histórico sin atribución'} · asignación {e.asignacionJornadaId}</li>)}</ul>
    <p>GPS recibidos: {datos.gps.cantidad}. Última captura {datos.gps.ultimaCaptura ?? 'No disponible'}; última recepción {datos.gps.ultimaRecepcion ?? 'No disponible'}.</p>
  </section>;
}
function Expediente({ token, jornada, vencer }: { token: string; jornada: string; vencer: (s?: string) => void }) {
  const [datos, setDatos] = useState<ExpedienteFiscal | null>(null), [gps, setGps] = useState<DetalleTrayectoria | null>(null);
  const [revision, setRevision] = useState(0), [pagina, setPagina] = useState({ desde: 0, corte: null as string | null });
  const [error, setError] = useState(''), [aviso, setAviso] = useState(''), [avisoMapa, setAvisoMapa] = useState(''), [cargando, setCargando] = useState(false);
  const descargas = useRef(new Set<AbortController>());
  useEffect(() => () => { descargas.current.forEach(c => c.abort()); descargas.current.clear(); }, []);
  const fallo = useCallback((e: unknown) => {
    setError(e instanceof Error ? e.message : 'No se pudo completar la operación.');
    if (e instanceof ErrorFiscal && e.estado === 401) vencer('Sesión vencida.');
    if (e instanceof ErrorFiscal && e.estado === 403) { setDatos(null); setGps(null); }
  }, [vencer]);
  useEffect(() => {
    const abort = new AbortController(); setCargando(true); setError('');
    fiscalizacionApi.expediente(token, jornada, abort.signal).then(r => { if (!abort.signal.aborted) setDatos(r); })
      .catch(e => { if (!abort.signal.aborted) fallo(e); }).finally(() => { if (!abort.signal.aborted) setCargando(false); });
    return () => abort.abort();
  }, [token, jornada, revision, fallo]);
  useEffect(() => {
    const abort = new AbortController(); setGps(null);
    fiscalizacionApi.gps(token, jornada, pagina.desde, pagina.corte, abort.signal).then(r => { if (!abort.signal.aborted) setGps(r); })
      .catch(e => { if (!abort.signal.aborted) fallo(e); });
    return () => abort.abort();
  }, [token, jornada, pagina, fallo]);
  const descargar = async (evidencia: EvidenciaCampo) => {
    const abort = new AbortController(); descargas.current.add(abort);
    try { await fiscalizacionApi.descargar(token, jornada, evidencia, abort.signal); }
    catch (e) { if (!abort.signal.aborted) fallo(e); }
    finally { descargas.current.delete(abort); }
  };
  const guardado = () => { setAviso('Registro confirmado por el servidor. Se conserva como antecedente histórico.'); setRevision(n => n + 1); };
  return <>
    <button onClick={() => { setRevision(n => n + 1); setPagina({ desde: 0, corte: null }); }} disabled={cargando}>Actualizar antecedentes</button>
    {cargando && <p role="status">Consultando expediente…</p>}{error && <p role="alert">{error}</p>}{aviso && <p role="status">{aviso}</p>}
    {datos && <>
      <Contexto datos={datos.contexto} /><p>{datos.limitaciones}</p>
      <p><Link to={`/correctivas?jornada=${jornada}`}>Incidencias y actuaciones correctivas (permiso independiente)</Link></p>
      <h2>GPS recibido: evidencia técnica, no dictamen</h2>
      <p>La cantidad recibida no informa cuántos registros siguen pendientes en teléfonos. No se inventan muestras ni cumplimiento.</p>
      {avisoMapa && <p role="status">{avisoMapa}</p>}
      {gps && <><MapaTrayectoria datos={gps} unir={false} aviso={setAvisoMapa} />
        <p>Muestras de esta página: {gps.seguimiento.trayectoria.features.length}; corte {gps.seguimiento.corteRecepcion}.
          {gps.seguimiento.siguiente !== null || pagina.desde > 0 ? ' Vista parcial paginada.' : ' Solo capturas recibidas en el corte.'}</p>
        {!gps.seguimiento.trayectoria.features.length && <p>Sin muestras GPS en la consulta; no es incumplimiento automático.</p>}
        <button disabled={!pagina.desde} onClick={() => setPagina({ desde: Math.max(0, pagina.desde - 500), corte: gps.seguimiento.corteRecepcion })}>GPS anteriores</button>
        <button disabled={gps.seguimiento.siguiente === null} onClick={() => setPagina({ desde: gps.seguimiento.siguiente!, corte: gps.seguimiento.corteRecepcion })}>GPS siguientes</button>
      </>}
      <details><summary>Revisiones técnicas CU-17/18/19 (permisos independientes)</summary><PanelRevisionGps token={token} inicial={jornada} /></details>
      <h2>Inspecciones conservadas</h2>
      {!datos.inspecciones.length && <p>No hay inspecciones recibidas.</p>}
      {datos.inspecciones.map(i => <article key={i.id}><h3>Inspección {i.id}</h3>
        <p>Inspector {i.inspectorUsuarioId} · realizada {i.realizadaEn} · registrada {i.registradaEn}</p>
        <p>{i.observaciones}</p><p>Lugar: {i.lugarDescripcion ?? 'No informado'} · Coordenadas declaradas: {i.longitud ?? 'No disponible'}, {i.latitud ?? 'No disponible'}</p>
        {!i.evidencias.length && <p>Sin fotografías anexas; no se deduce ausencia de inspección.</p>}
        <ul>{i.evidencias.map(e => <li key={e.id}>{e.nombreOriginal} · {e.tipoMime} · {e.tamanoBytes} bytes · SHA-256 {e.sha256}
          <br />Procedencia: {e.origen} · {e.descripcion} · Captura declarada: {e.capturadaEn ?? 'Desconocida'} · Incorporación: {e.incorporadaEn}
          <br />Política: {e.referenciaPolitica} · Autor: {e.autorUsuarioId} <button onClick={() => void descargar(e)}>Descargar evidencia autorizada</button></li>)}</ul>
      </article>)}
      {datos.puedeRegistrarInspeccion ? <FormularioInspeccion token={token} jornada={jornada} politica={datos.politicaEvidencias} guardado={guardado} fallo={fallo} />
        : <p>Sin permiso de registro de inspección. La consulta no concede facultad de escritura.</p>}
      {datos.fiscalizacion && <section aria-label="Determinación histórica registrada"><h2>Resultado humano registrado</h2>
        <p>{datos.fiscalizacion.resultado.codigo} · {datos.fiscalizacion.resultado.nombre}</p>
        <p>Fiscalizador {datos.fiscalizacion.fiscalizadorUsuarioId} · Registro {datos.fiscalizacion.registradaEn} · Corte {datos.fiscalizacion.corteEvidenciaEn}</p>
        <p>{datos.fiscalizacion.observaciones}</p><p>Referencia de catálogo: {datos.fiscalizacion.resultado.referenciaAprobacion}</p>
        <p>Inspecciones referenciadas: {datos.fiscalizacion.inspeccionesReferenciadas.join(', ') || 'Ninguna seleccionada'}.</p>
        <p>GPS referenciados al emitir: {datos.fiscalizacion.antecedentes.gps.cantidad}. Identificadores conservados en el antecedente.</p>
        {(datos.fiscalizacion.posicionesNoReferenciadas > 0 || datos.fiscalizacion.inspeccionesPosteriores > 0) && <p role="status">
          Hay {datos.fiscalizacion.posicionesNoReferenciadas} posiciones no incluidas en el antecedente y {datos.fiscalizacion.inspeccionesPosteriores} inspecciones posteriores. La determinación original no se modifica; rectificación pendiente de procedimiento.</p>}
        <details><summary>Contexto original conservado al emitir</summary><Contexto datos={datos.fiscalizacion.antecedentes} /></details>
      </section>}
      {datos.puedeEmitirResultado ? <FormularioDecisionFiscal token={token} jornada={jornada} expediente={datos} guardado={guardado} fallo={fallo} />
        : <p>No dispone de permiso para emitir resultados. Puede mantener una revisión sin dictamen.</p>}
    </>}
  </>;
}
