import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useSesionActual } from '../../administracion/identidad/aplicacion/contextoAutenticacion';
import { apiReportesServicio, validarFiltrosReporte, type FiltrosReporte, type ReporteServicio } from '../api/reportesServicio';

const fecha = (v: string | null) => (v ? new Date(v).toLocaleString('es-BO', { timeZone: 'America/La_Paz' }) : '—');
const ETIQUETA: Record<string, string> = {
  CON_REGISTROS: 'Con registros', SIN_REGISTROS: 'Cero registros (consultado)', ACCESO_NO_PERMITIDO: 'Acceso no permitido (se desconoce si existen)',
  NO_CONSULTADO: 'No consultado', NO_APLICA: 'No aplica con estos filtros',
};
const VACIO: FiltrosReporte = { desde: '', hasta: '', periodoId: '', empresaId: '' };

/** CU-30 (corte descriptivo): recuentos de registros conservados. Sin porcentajes, cumplimiento, KPIs ni exportación. */
export function PaginaReportesServicio() {
  const { sesion } = useSesionActual();
  return <main><Link to="/">Inicio</Link><h1>Reportes descriptivos del servicio</h1>
    <p>Recuentos de registros conservados, con su definición, fuente y fecha del último registro. No son indicadores oficiales ni expresan cumplimiento.</p>
    {!sesion ? <p><Link to="/acceso">Autentíquese para consultar.</Link></p> : <Reporte key={sesion.tokenAcceso} />}
  </main>;
}

function Reporte() {
  const [filtros, setFiltros] = useState<FiltrosReporte>(VACIO);
  const [aplicados, setAplicados] = useState<FiltrosReporte | null>(null);
  const [reporte, setReporte] = useState<ReporteServicio | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!aplicados) return undefined;
    const abort = new AbortController();
    setCargando(true); setError(''); setReporte(null);
    apiReportesServicio.resumen(aplicados, abort.signal)
      .then(r => { if (!abort.signal.aborted) setReporte(r); })
      .catch(e => { if (!abort.signal.aborted) setError(mensaje(e)); })
      .finally(() => { if (!abort.signal.aborted) setCargando(false); });
    return () => abort.abort();
  }, [aplicados]);

  function consultar(e: FormEvent) {
    e.preventDefault();
    const problema = validarFiltrosReporte(filtros);
    if (problema) { setError(problema); setReporte(null); return; }
    setAplicados({ ...filtros });
  }
  return <>
    <form onSubmit={consultar} aria-label="Filtros del reporte">
      <label>Desde <input type="date" value={filtros.desde} onChange={e => setFiltros({ ...filtros, desde: e.target.value })} /></label>{' '}
      <label>Hasta <input type="date" value={filtros.hasta} onChange={e => setFiltros({ ...filtros, hasta: e.target.value })} /></label>{' '}
      <label>Periodo (ID) <input value={filtros.periodoId} onChange={e => setFiltros({ ...filtros, periodoId: e.target.value })} /></label>{' '}
      <label>Empresa (ID) <input value={filtros.empresaId} onChange={e => setFiltros({ ...filtros, empresaId: e.target.value })} /></label>{' '}
      <button type="submit" disabled={cargando}>Generar reporte</button>
      <p>Las fechas se interpretan como fecha de servicio en America/La_Paz. Los identificadores de empresa y periodo se obtienen del historial institucional.</p>
    </form>
    {error && <p role="alert">{error}</p>}
    {cargando && <p role="status">Consultando registros…</p>}
    {reporte && <section aria-label="Resultado del reporte">
      <p>{reporte.naturaleza}</p>
      <p>Alcance: {reporte.filtros.desde} → {reporte.filtros.hasta} ({reporte.filtros.zonaFechaServicio})
        {reporte.filtros.empresaId ? ` · empresa ${reporte.filtros.empresaId}` : ''}{reporte.filtros.periodoId ? ` · periodo ${reporte.filtros.periodoId}` : ''}
        {' '}· generado {fecha(reporte.generadoEn)}</p>
      <table><thead><tr><th>Recuento</th><th>Cantidad</th><th>Estado</th><th>Último registro</th><th>Definición y fuente</th></tr></thead>
        <tbody>{reporte.recuentos.map(r => <tr key={r.clave} data-recuento={r.clave}>
          <th scope="row">{r.nombre}</th>
          <td>{r.cantidad ?? '—'}</td>
          <td>{ETIQUETA[r.estado] ?? r.estado}</td>
          <td>{fecha(r.ultimoRegistro)}</td>
          <td>{r.definicion} Fuente: {r.fuente}.{r.permisoRequerido ? ` Permiso: ${r.permisoRequerido}.` : ''}{r.nota ? ` ${r.nota}` : ''}</td>
        </tr>)}</tbody></table>
      <ul aria-label="Limitaciones">{reporte.limitaciones.map(l => <li key={l}>{l}</li>)}</ul>
    </section>}
  </>;
}

function mensaje(e: unknown) {
  const texto = e instanceof Error ? e.message : '';
  if (texto.includes('(403)') || /autoriz|permiso|Forbidden/i.test(texto)) return 'No tiene autorización municipal para consultar reportes del servicio.';
  return texto || 'No se pudo generar el reporte. Los registros no fueron modificados; reintente.';
}
