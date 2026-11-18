import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useSesionActual } from '../../administracion/identidad/aplicacion/contextoAutenticacion';
import { apiBitacora, ENTIDADES, validarFiltrosBitacora, type DetalleEvento, type EventoBitacora, type FiltrosBitacora } from '../api/bitacora';

const VACIO: FiltrosBitacora = { desde: '', hasta: '', actorId: '', accion: '', entidadTipo: '', entidadId: '' };
const formato = (v: string) => new Date(v).toLocaleString('es-BO', { timeZone: 'America/La_Paz' });
const ATRIBUCION: Record<string, string> = {
  USUARIO_AUTENTICADO: 'Usuario autenticado',
  OPERADOR_LOCAL_NO_AUTENTICADO: 'No atribuible: provisión local/técnica',
  NO_ATRIBUIBLE: 'No atribuible',
};
function mensaje(e: unknown) {
  const t = e instanceof Error ? e.message : '';
  if (t.includes('(403)') || /no autorizada|Forbidden/i.test(t)) return 'La consulta de bitácora no está autorizada para su cuenta.';
  return t || 'No se pudo leer la bitácora. Los eventos no fueron modificados; reintente.';
}

export function PaginaBitacora() {
  const { sesion } = useSesionActual();
  const [parametros, setParametros] = useSearchParams();
  return <main><Link to="/">Inicio</Link><h1>Bitácora de acciones relevantes</h1>
    <p>Consulta de solo lectura de eventos ya registrados. No se pueden editar ni eliminar eventos. Es independiente del historial operativo del servicio (CU-29).</p>
    {!sesion ? <p><Link to="/acceso">Autentíquese para consultar.</Link></p>
      : <Bitacora key={sesion.tokenAcceso} eventoId={parametros.get('evento') ?? ''} elegir={id => setParametros(id ? { evento: id } : {})} />}
  </main>;
}

function Bitacora({ eventoId, elegir }: { eventoId: string; elegir: (id: string) => void }) {
  const [filtros, setFiltros] = useState<FiltrosBitacora>(VACIO);
  const [aplicados, setAplicados] = useState<FiltrosBitacora | null>(null);
  const [desplazamiento, setDesplazamiento] = useState(0);
  const [lista, setLista] = useState<EventoBitacora[] | null>(null);
  const [siguiente, setSiguiente] = useState<number | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!aplicados) return undefined;
    const abort = new AbortController();
    setCargando(true); setError('');
    apiBitacora.buscar(aplicados, desplazamiento, abort.signal)
      .then(r => { if (!abort.signal.aborted) { setLista(r.eventos); setSiguiente(r.siguiente); } })
      .catch(e => { if (!abort.signal.aborted) { setLista(null); setError(mensaje(e)); } })
      .finally(() => { if (!abort.signal.aborted) setCargando(false); });
    return () => abort.abort();
  }, [aplicados, desplazamiento]);

  function buscar(e: FormEvent) {
    e.preventDefault();
    const problema = validarFiltrosBitacora(filtros);
    if (problema) { setError(problema); setLista(null); return; }
    setError(''); setDesplazamiento(0); setAplicados({ ...filtros });
  }
  if (eventoId) return <DetalleEventoBitacora id={eventoId} volver={() => elegir('')} />;
  return <>
    <form onSubmit={buscar} aria-label="Filtros de bitácora">
      <label>Desde <input type="date" value={filtros.desde} onChange={e => setFiltros({ ...filtros, desde: e.target.value })} /></label>{' '}
      <label>Hasta <input type="date" value={filtros.hasta} onChange={e => setFiltros({ ...filtros, hasta: e.target.value })} /></label>{' '}
      <label>Actor (ID de usuario) <input value={filtros.actorId} onChange={e => setFiltros({ ...filtros, actorId: e.target.value })} /></label>{' '}
      <label>Acción <input value={filtros.accion} onChange={e => setFiltros({ ...filtros, accion: e.target.value })} placeholder="p. ej. PERMISO_OTORGAR" /></label>{' '}
      <label>Entidad <select value={filtros.entidadTipo} onChange={e => setFiltros({ ...filtros, entidadTipo: e.target.value })}>
        <option value="">(todas)</option>{ENTIDADES.map(t => <option key={t} value={t}>{t}</option>)}</select></label>{' '}
      <label>ID de entidad <input value={filtros.entidadId} onChange={e => setFiltros({ ...filtros, entidadId: e.target.value })} /></label>{' '}
      <button type="submit" disabled={cargando}>Buscar</button>{' '}
      <button type="button" onClick={() => { setFiltros(VACIO); setAplicados(null); setLista(null); setError(''); }}>Limpiar</button>
      <p>Fechas en America/La_Paz. El catálogo de acciones y la retención son definiciones institucionales pendientes: se muestran las acciones tal como fueron registradas.</p>
    </form>
    {error && <p role="alert">{error}</p>}
    {cargando && <p role="status">Consultando bitácora…</p>}
    {!aplicados && !error && <p>Defina filtros (o ninguno) y pulse Buscar.</p>}
    {lista && lista.length === 0 && <p role="status">No hay eventos para esos filtros. No se creó ni modificó ningún registro.</p>}
    {lista && lista.length > 0 && <>
      <table><thead><tr><th>Fecha y hora</th><th>Actor</th><th>Acción</th><th>Entidad</th><th>Origen</th><th /></tr></thead>
        <tbody>{lista.map(ev => <tr key={ev.id}>
          <td>{formato(ev.ocurridoEn)}</td>
          <td>{ev.atribucion === 'USUARIO_AUTENTICADO' ? (ev.actorNombreUsuario ?? ev.actorUsuarioId) : <em>{ATRIBUCION[ev.atribucion]}</em>}</td>
          <td>{ev.accion}</td><td>{ev.entidadTipo} · {ev.entidadId}</td><td>{ev.fuente}</td>
          <td><button type="button" onClick={() => elegir(ev.id)}>Ver detalle</button></td></tr>)}</tbody></table>
      <p>
        <button type="button" disabled={desplazamiento === 0 || cargando} onClick={() => setDesplazamiento(Math.max(0, desplazamiento - 50))}>Anterior</button>{' '}
        <button type="button" disabled={siguiente === null || cargando} onClick={() => siguiente !== null && setDesplazamiento(siguiente)}>Siguiente</button>
      </p></>}
  </>;
}

function DetalleEventoBitacora({ id, volver }: { id: string; volver: () => void }) {
  const [datos, setDatos] = useState<DetalleEvento | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    const abort = new AbortController();
    setDatos(null); setError('');
    apiBitacora.detalle(id, abort.signal)
      .then(d => { if (!abort.signal.aborted) setDatos(d); })
      .catch(e => { if (!abort.signal.aborted) setError(mensaje(e)); });
    return () => abort.abort();
  }, [id]);
  return <section aria-label="Detalle de evento">
    <p><button type="button" onClick={volver}>← Volver a resultados</button></p>
    {error && <p role="alert">{error}</p>}
    {!datos && !error && <p role="status">Cargando detalle…</p>}
    {datos && <>
      <h2>{datos.evento.accion}</h2>
      <dl>
        <dt>Fecha y hora</dt><dd>{formato(datos.evento.ocurridoEn)}</dd>
        <dt>Actor</dt><dd>{datos.evento.atribucion === 'USUARIO_AUTENTICADO' ? (datos.evento.actorNombreUsuario ?? datos.evento.actorUsuarioId) : ATRIBUCION[datos.evento.atribucion]}</dd>
        <dt>Entidad afectada</dt><dd>{datos.evento.entidadTipo} · {datos.evento.entidadId}</dd>
        <dt>Fuente</dt><dd>{datos.evento.fuente}</dd>
        <dt>Motivo registrado (texto libre: no se muestra)</dt><dd>{datos.motivoRegistrado ? `Sí (${datos.motivoLongitud} caracteres)` : 'No'}</dd>
      </dl>
      <h3>Estado anterior (solo campos permitidos; «camposOmitidos» cuenta los no mostrados)</h3><pre>{datos.anterior == null ? 'No conservado por la fuente.' : JSON.stringify(datos.anterior, null, 2)}</pre>
      <h3>Estado posterior (solo campos permitidos)</h3><pre>{datos.posterior == null ? 'No conservado por la fuente.' : JSON.stringify(datos.posterior, null, 2)}</pre>
      <ul>{datos.notas.map(n => <li key={n}>{n}</li>)}</ul>
    </>}
  </section>;
}
