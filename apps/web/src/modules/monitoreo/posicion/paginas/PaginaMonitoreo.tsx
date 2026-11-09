import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useSesionActual } from '../../../administracion/identidad/aplicacion/contextoAutenticacion';
import { consultarMonitoreo, ErrorMonitoreo, type VistaMonitoreo } from '../api/monitoreo';
import { MapaMonitoreo } from '../componentes/MapaMonitoreo';

const configurado = Number(import.meta.env.VITE_MONITOREO_POLL_SEGUNDOS || 15);
const intervalo = Number.isFinite(configurado) ? Math.max(5, Math.min(300, configurado)) * 1000 : 15000;

export function PaginaMonitoreo() {
  const { sesion, cerrar } = useSesionActual();
  return <main><Link to="/">Inicio</Link><h1>Monitorear posición de jornada</h1>
    <p>Jornadas con INICIO y sin FIN registrados. GPS es evidencia; no determina recolección, fraude o cumplimiento.</p>
    {!sesion ? <Link to="/acceso">Autentíquese para consultar.</Link>
      : <Consulta key={sesion.tokenAcceso} token={sesion.tokenAcceso} vencer={cerrar} />}
  </main>;
}
function Consulta({ token, vencer }: { token: string; vencer: (s?: string) => void }) {
  const [vista, setVista] = useState<VistaMonitoreo | null>(null);
  const [recibida, setRecibida] = useState(0);
  const [seleccion, setSeleccion] = useState('');
  const [desplazamiento, setDesplazamiento] = useState(0);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState('');
  const [avisoMapa, setAvisoMapa] = useState('');
  const [revision, setRevision] = useState(0);
  const [, reloj] = useState(0);
  useEffect(() => { const t = window.setInterval(() => reloj(n => n + 1), 1000); return () => window.clearInterval(t); }, []);
  useEffect(() => {
    let detenido = false, ocupado = false, bloqueado = false, fallos = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let abort: AbortController | null = null;
    const actualizar = async () => {
      if (detenido || ocupado || bloqueado) return;
      ocupado = true; abort = new AbortController(); setCargando(true);
      try {
        const nueva = await consultarMonitoreo(token, desplazamiento, abort.signal);
        if (detenido) return;
        setVista(nueva); setRecibida(Date.now()); setError(''); fallos = 0;
        setSeleccion(anterior => nueva.jornadas.some(j => j.contexto.jornadaId === anterior) ? anterior : nueva.jornadas[0]?.contexto.jornadaId ?? '');
      } catch (e) {
        if (detenido) return;
        fallos = Math.min(fallos + 1, 3);
        if (e instanceof ErrorMonitoreo && [401, 403].includes(e.estado)) {
          bloqueado = true; setVista(null); setSeleccion('');
          if (e.estado === 401) vencer('Sesión vencida. Vuelva a autenticarse.');
        }
        setError(e instanceof ErrorMonitoreo ? e.message : 'Sin actualización de red. Se conserva la última evidencia recibida, no una ubicación actual garantizada.');
      } finally {
        ocupado = false;
        if (!detenido) {
          setCargando(false);
          if (!bloqueado) timer = setTimeout(actualizar, intervalo * (1 << fallos));
        }
      }
    };
    const reanudar = () => { if (!ocupado) { clearTimeout(timer); void actualizar(); } };
    const visible = () => { if (document.visibilityState === 'visible') reanudar(); };
    window.addEventListener('online', reanudar); document.addEventListener('visibilitychange', visible);
    void actualizar();
    return () => { detenido = true; clearTimeout(timer); abort?.abort(); window.removeEventListener('online', reanudar); document.removeEventListener('visibilitychange', visible); };
  }, [token, desplazamiento, revision, vencer]);
  const ahora = vista ? Date.parse(vista.consultadoEn) + Math.max(0, Date.now() - recibida) : Date.now();
  return <>
    <button disabled={cargando} onClick={() => setRevision(n => n + 1)}>Actualizar ahora</button>
    <p>Consulta periódica cada {intervalo / 1000} s; reintentos con espera progresiva. No es seguimiento instantáneo.</p>
    {cargando && <p role="status">Consultando posiciones autorizadas…</p>}
    {error && <p role="alert">{error}</p>}
    {vista && <>
      <p>Última consulta de servidor: {vista.consultadoEn}. Umbral técnico de antigüedad: {vista.umbralAntiguedadSegundos} s; no es un criterio contractual.</p>
      {!vista.jornadas.length && <p>No hay jornadas con INICIO y sin FIN en esta página autorizada.</p>}
      <label>Ruta de referencia de<select value={seleccion} onChange={e => setSeleccion(e.target.value)}>
        <option value="">Seleccione una jornada</option>{vista.jornadas.map(j => <option key={j.contexto.jornadaId} value={j.contexto.jornadaId}>{j.placa} · {j.contexto.codigoRuta} · {j.contexto.jornadaId}</option>)}
      </select></label>
      <p>Azul: referencia histórica seleccionada. Naranja: última muestra dentro del umbral técnico. Gris: lectura antigua; no implica detención.</p>
      {avisoMapa && <p role="status">{avisoMapa}</p>}
      <MapaMonitoreo jornadas={vista.jornadas} seleccion={seleccion} ahora={ahora} umbral={vista.umbralAntiguedadSegundos} aviso={setAvisoMapa} />
      <div style={{ overflowX: 'auto' }}><table><thead><tr><th>Jornada / empresa</th><th>Vehículo asignado</th><th>Captura / recepción</th><th>Posición y precisión</th><th>Antigüedad</th><th>Historia</th></tr></thead>
        <tbody>{vista.jornadas.map(j => {
          const p = j.ultimaPosicion;
          const edad = p ? Math.floor((ahora - Date.parse(p.capturadoEn)) / 1000) : null;
          return <tr key={j.contexto.jornadaId}><td>{j.contexto.jornadaId}<br />{j.contexto.empresaOperadora}<br />{j.contexto.codigoRuta} v{j.contexto.numeroVersion}</td>
            <td>{j.placa}<br />{j.vehiculoId}<br />La ubicación del teléfono no garantiza presencia del camión.</td>
            <td>{p ? <>{p.capturadoEn}<br />{p.recibidoEn}</> : 'Sin posiciones recibidas'}</td>
            <td>{p ? `${p.longitud}, ${p.latitud} · ${p.precisionMetros} m` : 'Ubicación no disponible'}</td>
            <td>{edad === null ? 'Sin dato' : edad < 0 ? 'Captura posterior al reloj de consulta' : `${edad} s${edad > vista.umbralAntiguedadSegundos ? ' · Sin lectura reciente' : ''}`}</td>
            <td><Link to={`/trayectorias?jornada=${encodeURIComponent(j.contexto.jornadaId)}`}>Consultar trayectoria histórica</Link><br />
              <Link to={`/revision-gps?jornada=${encodeURIComponent(j.contexto.jornadaId)}`}>Revisar evidencia GPS</Link><br />
              <Link to={`/fiscalizacion?jornada=${encodeURIComponent(j.contexto.jornadaId)}`}>Consultar expediente municipal</Link></td></tr>;
        })}</tbody></table></div>
      <button disabled={!desplazamiento || cargando} onClick={() => { setVista(null); setDesplazamiento(Math.max(0, desplazamiento - 50)); }}>Página anterior</button>
      <button disabled={vista.siguiente === null || cargando} onClick={() => { setVista(null); setDesplazamiento(vista.siguiente!); }}>Página siguiente</button>
    </>}
  </>;
}
