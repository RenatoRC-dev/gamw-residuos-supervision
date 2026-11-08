import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { routesApi, type ReferenceRoute, type RouteVersion } from '../api/routes';
import { RouteMap, type RouteMapCommands } from '../components/RouteMap';
import { parseLineString, type LineStringGeometry } from '../model/geometry';

function localDateTime(now = new Date()) {
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}

export function ReferenceRoutesPage() {
  const [routes, setRoutes] = useState<ReferenceRoute[]>([]);
  const [selected, setSelected] = useState<ReferenceRoute | null>(null);
  const [versions, setVersions] = useState<RouteVersion[]>([]);
  const [persisted, setPersisted] = useState<RouteVersion | null>(null);
  const [draft, setDraft] = useState<LineStringGeometry | null>(null);
  const [append, setAppend] = useState(false);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [validFrom, setValidFrom] = useState(localDateTime);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const map = useRef<RouteMapCommands>(null);

  useEffect(() => {
    const abort = new AbortController();
    routesApi.list(abort.signal).then(setRoutes).catch(error => {
      if (!abort.signal.aborted) setError(error instanceof Error ? error.message : 'No se pudieron consultar las rutas.');
    });
    return () => abort.abort();
  }, []);

  async function load(id: string, number?: number) {
    const [route, history] = await Promise.all([routesApi.get(id), routesApi.versions(id)]);
    const version = await routesApi.version(id, number ?? history[0].versionNumber);
    map.current?.clearDraft();
    setSelected(route); setVersions(history); setPersisted(version); setAppend(false);
    setCode(route.code); setName(route.name); setDraft(null); setReason('');
  }

  async function perform(action: () => Promise<void>) {
    setBusy(true); setError(''); setMessage('');
    try { await action(); }
    catch (error) { setError(error instanceof Error ? error.message : 'No se pudo completar la operación.'); }
    finally { setBusy(false); }
  }

  function newRoute() {
    map.current?.clearDraft(); setSelected(null); setVersions([]); setPersisted(null); setDraft(null);
    setAppend(false); setCode(''); setName(''); setReason(''); setValidFrom(localDateTime()); setMessage(''); setError('');
  }

  function beginVersion() {
    if (!persisted) return;
    const nextStart = new Date(Math.max(Date.now(), Date.parse(versions[0]?.validFrom ?? persisted.validFrom) + 1000));
    setAppend(true); setValidFrom(localDateTime(nextStart)); setReason(''); setMessage('');
    map.current?.editGeometry(persisted.geometry);
  }

  function save(event: FormEvent) {
    event.preventDefault();
    void perform(async () => {
      const geometry = parseLineString(draft);
      const input = { geometry, validFrom: new Date(validFrom).toISOString(), changeReason: reason || undefined };
      if (append && selected) {
        const created = await routesApi.append(selected.id, input);
        await load(selected.id, created.versionNumber);
      } else {
        const created = await routesApi.create({ ...input, code: code.trim(), name: name.trim() });
        await load(created.route.id, created.version.versionNumber);
      }
      setRoutes(await routesApi.list());
      setMessage('Versión guardada y recuperada del servidor.');
    });
  }

  return <main className="routes-page">
    <Link to="/">Inicio</Link>
    <h1>Rutas operativas de referencia</h1>
    <p>POC local. No se incluyen rutas ni límites oficiales precargados. Digitalizar una referencia no certifica recolección.</p>
    {error && <p role="alert" className="error">{error}</p>}
    {message && <p role="status" className="success">{message}</p>}
    <div className="routes-layout">
      <RouteMap ref={map} persisted={persisted?.geometry ?? null} onDraft={setDraft} disabled={busy} />
      <aside>
        <button type="button" onClick={newRoute} disabled={busy}>Nueva ruta</button>
        <label>Ruta guardada<select aria-label="Ruta guardada" value={selected?.id ?? ''} disabled={busy}
          onChange={event => { const id = event.target.value; if (id) void perform(() => load(id)); else newRoute(); }}>
          <option value="">Seleccione una ruta</option>
          {routes.map(route => <option key={route.id} value={route.id}>{route.code} · {route.name}</option>)}
        </select></label>
        {persisted && <section aria-label="Versión persistida">
          <label>Versión<select aria-label="Versión" value={persisted.versionNumber} disabled={busy}
            onChange={event => { const number = Number(event.target.value); if (selected) void perform(() => load(selected.id, number)); }}>
            {versions.map(version => <option key={version.id} value={version.versionNumber}>Versión {version.versionNumber}</option>)}
          </select></label>
          <p>Vigencia desde {persisted.validFrom}{persisted.validTo ? ` hasta ${persisted.validTo}` : ' · abierta'}</p>
          <button type="button" disabled={busy} onClick={() => { if (selected) void perform(() => load(selected.id, persisted.versionNumber)); }}>Recargar versión</button>
          <button type="button" disabled={busy} onClick={beginVersion}>Editar como nueva versión</button>
        </section>}
        <form onSubmit={save}>
          <h2>{append ? 'Nueva versión' : selected ? 'Ruta recuperada' : 'Crear ruta'}</h2>
          <label>Código<input required maxLength={64} value={code} disabled={busy || !!selected} onChange={event => setCode(event.target.value)} /></label>
          <label>Nombre<input required maxLength={160} value={name} disabled={busy || !!selected} onChange={event => setName(event.target.value)} /></label>
          <label>Vigente desde<input type="datetime-local" step="1" required value={validFrom} disabled={busy || (!!selected && !append)} onChange={event => setValidFrom(event.target.value)} /></label>
          <label>Motivo del cambio<input maxLength={500} value={reason} disabled={busy || (!!selected && !append)} onChange={event => setReason(event.target.value)} /></label>
          <p>{draft ? `${draft.coordinates.length} posiciones en el borrador` : 'Sin borrador terminado'}</p>
          <button type="submit" disabled={busy || !draft || (!!selected && !append)}>{busy ? 'Procesando…' : append ? 'Guardar nueva versión' : 'Guardar ruta'}</button>
        </form>
      </aside>
    </div>
  </main>;
}
