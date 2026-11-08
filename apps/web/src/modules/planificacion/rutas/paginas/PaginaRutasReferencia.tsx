import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { apiRutas, type Ruta, type RutaVersion } from '../api/rutas';
import { MapaRuta, type ComandosMapaRuta } from '../componentes/MapaRuta';
import { validarLineString, type GeometriaLineString } from '../modelo/geometria';

function fechaHoraLocal(now = new Date()) {
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}

export function PaginaRutasReferencia() {
  const [rutas, setRutas] = useState<Ruta[]>([]);
  const [seleccionada, setSeleccionada] = useState<Ruta | null>(null);
  const [versiones, setVersiones] = useState<RutaVersion[]>([]);
  const [persistida, setPersistida] = useState<RutaVersion | null>(null);
  const [borrador, setBorrador] = useState<GeometriaLineString | null>(null);
  const [agregar, setAgregar] = useState(false);
  const [codigo, setCodigo] = useState('');
  const [nombre, setNombre] = useState('');
  const [vigenteDesde, setVigenteDesde] = useState(fechaHoraLocal);
  const [motivoCambio, setMotivoCambio] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const map = useRef<ComandosMapaRuta>(null);

  useEffect(() => {
    const abort = new AbortController();
    apiRutas.listar(abort.signal).then(setRutas).catch(error => {
      if (!abort.signal.aborted) setError(error instanceof Error ? error.message : 'No se pudieron consultar las rutas.');
    });
    return () => abort.abort();
  }, []);

  async function cargar(id: string, numeroVersion?: number) {
    const [ruta, historial] = await Promise.all([apiRutas.obtener(id), apiRutas.versiones(id)]);
    const version = await apiRutas.obtenerVersion(id, numeroVersion ?? historial[0].numeroVersion);
    map.current?.limpiarBorrador();
    setSeleccionada(ruta); setVersiones(historial); setPersistida(version); setAgregar(false);
    setCodigo(ruta.codigo); setNombre(ruta.nombre); setBorrador(null); setMotivoCambio('');
  }

  async function ejecutar(action: () => Promise<void>) {
    setBusy(true); setError(''); setMessage('');
    try { await action(); }
    catch (error) { setError(error instanceof Error ? error.message : 'No se pudo completar la operación.'); }
    finally { setBusy(false); }
  }

  function nuevaRuta() {
    map.current?.limpiarBorrador(); setSeleccionada(null); setVersiones([]); setPersistida(null); setBorrador(null);
    setAgregar(false); setCodigo(''); setNombre(''); setMotivoCambio(''); setVigenteDesde(fechaHoraLocal()); setMessage(''); setError('');
  }

  function iniciarVersion() {
    if (!persistida) return;
    const siguienteVigencia = new Date(Math.max(Date.now(), Date.parse(versiones[0]?.vigenteDesde ?? persistida.vigenteDesde) + 1000));
    setAgregar(true); setVigenteDesde(fechaHoraLocal(siguienteVigencia)); setMotivoCambio(''); setMessage('');
    map.current?.editarGeometria(persistida.geometria);
  }

  function guardar(event: FormEvent) {
    event.preventDefault();
    void ejecutar(async () => {
      const geometria = validarLineString(borrador);
      const input = { geometria, vigenteDesde: new Date(vigenteDesde).toISOString(), motivoCambio: motivoCambio || undefined };
      if (agregar && seleccionada) {
        const creada = await apiRutas.agregarVersion(seleccionada.id, input);
        await cargar(seleccionada.id, creada.numeroVersion);
      } else {
        const creada = await apiRutas.crear({ ...input, codigo: codigo.trim(), nombre: nombre.trim() });
        await cargar(creada.ruta.id, creada.version.numeroVersion);
      }
      setRutas(await apiRutas.listar());
      setMessage('Versión guardada y recuperada del servidor.');
    });
  }

  return <main className="pagina-rutas">
    <Link to="/">Inicio</Link>
    <h1>Rutas operativas de referencia</h1>
    <p>POC local. No se incluyen rutas ni límites oficiales precargados. Digitalizar una referencia no certifica recolección.</p>
    {error && <p role="alert" className="error">{error}</p>}
    {message && <p role="status" className="success">{message}</p>}
    <div className="disposicion-rutas">
      <MapaRuta ref={map} persistida={persistida?.geometria ?? null} alCambiarBorrador={setBorrador} disabled={busy} />
      <aside>
        <button type="button" onClick={nuevaRuta} disabled={busy}>Nueva ruta</button>
        <label>Ruta guardada<select aria-label="Ruta guardada" value={seleccionada?.id ?? ''} disabled={busy}
          onChange={event => { const id = event.target.value; if (id) void ejecutar(() => cargar(id)); else nuevaRuta(); }}>
          <option value="">Seleccione una ruta</option>
          {rutas.map(ruta => <option key={ruta.id} value={ruta.id}>{ruta.codigo} · {ruta.nombre}</option>)}
        </select></label>
        {persistida && <section aria-label="Versión persistida">
          <label>Versión<select aria-label="Versión" value={persistida.numeroVersion} disabled={busy}
            onChange={event => { const numeroVersion = Number(event.target.value); if (seleccionada) void ejecutar(() => cargar(seleccionada.id, numeroVersion)); }}>
            {versiones.map(version => <option key={version.id} value={version.numeroVersion}>Versión {version.numeroVersion}</option>)}
          </select></label>
          <p>Vigencia desde {persistida.vigenteDesde}{persistida.vigenteHasta ? ` hasta ${persistida.vigenteHasta}` : ' · abierta'}</p>
          <button type="button" disabled={busy} onClick={() => { if (seleccionada) void ejecutar(() => cargar(seleccionada.id, persistida.numeroVersion)); }}>Recargar versión</button>
          <button type="button" disabled={busy} onClick={iniciarVersion}>Editar como nueva versión</button>
        </section>}
        <form onSubmit={guardar}>
          <h2>{agregar ? 'Nueva versión' : seleccionada ? 'Ruta recuperada' : 'Crear ruta'}</h2>
          <label>Código<input required maxLength={64} value={codigo} disabled={busy || !!seleccionada} onChange={event => setCodigo(event.target.value)} /></label>
          <label>Nombre<input required maxLength={160} value={nombre} disabled={busy || !!seleccionada} onChange={event => setNombre(event.target.value)} /></label>
          <label>Vigente desde<input type="datetime-local" step="1" required value={vigenteDesde} disabled={busy || (!!seleccionada && !agregar)} onChange={event => setVigenteDesde(event.target.value)} /></label>
          <label>Motivo del cambio<input maxLength={500} value={motivoCambio} disabled={busy || (!!seleccionada && !agregar)} onChange={event => setMotivoCambio(event.target.value)} /></label>
          <p>{borrador ? `${borrador.coordinates.length} posiciones en el borrador` : 'Sin borrador terminado'}</p>
          <button type="submit" disabled={busy || !borrador || (!!seleccionada && !agregar)}>{busy ? 'Procesando…' : agregar ? 'Guardar nueva versión' : 'Guardar ruta'}</button>
        </form>
      </aside>
    </div>
  </main>;
}
