import { useEffect, useRef, useState, type FormEvent } from 'react';
import { fiscalizacionApi, type ExpedienteFiscal } from '../api/fiscalizacion';

export function FormularioInspeccion({ token, jornada, politica, guardado, fallo }: {
  token: string; jornada: string; politica: ExpedienteFiscal['politicaEvidencias']; guardado: () => void; fallo: (e: unknown) => void;
}) {
  const [solicitud, setSolicitud] = useState(() => crypto.randomUUID());
  const [fecha, setFecha] = useState(''), [observaciones, setObservaciones] = useState(''), [lugar, setLugar] = useState('');
  const [latitud, setLatitud] = useState(''), [longitud, setLongitud] = useState(''), [ocupado, setOcupado] = useState(false);
  const [archivos, setArchivos] = useState<{ archivo: File; origen: string; descripcion: string; capturadaEn: string }[]>([]);
  const peticion = useRef<AbortController | null>(null), selector = useRef<HTMLInputElement>(null);
  useEffect(() => () => peticion.current?.abort(), []);
  const enviar = async (e: FormEvent) => {
    e.preventDefault(); const abort = new AbortController(); peticion.current = abort; setOcupado(true);
    try {
      const datos = { solicitudId: solicitud, realizadaEn: new Date(`${fecha}-04:00`).toISOString(), lugarDescripcion: lugar || null,
        observaciones, latitud: latitud === '' ? null : Number(latitud), longitud: longitud === '' ? null : Number(longitud),
        adjuntos: archivos.map(a => ({ origen: a.origen, descripcion: a.descripcion,
          capturadaEn: a.capturadaEn ? new Date(`${a.capturadaEn}-04:00`).toISOString() : null })) };
      const formulario = new FormData(); formulario.append('datos', JSON.stringify(datos));
      archivos.forEach(a => formulario.append('archivos', a.archivo));
      await fiscalizacionApi.inspeccionar(token, jornada, formulario, abort.signal);
      if (!abort.signal.aborted) {
        setSolicitud(crypto.randomUUID()); setObservaciones(''); setArchivos([]);
        if (selector.current) selector.current.value = ''; guardado();
      }
    } catch (e) { if (!abort.signal.aborted) fallo(e); }
    finally { if (!abort.signal.aborted) setOcupado(false); }
  };
  return <form onSubmit={enviar} aria-label="Registrar inspección de campo">
    <h2>Inspección: observación humana de campo</h2>
    <p>No genera un resultado de fiscalización. Fecha declarada en Bolivia (UTC−04:00); el servidor conserva por separado la fecha de registro.</p>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(220px,1fr))', gap: 12 }}>
      <label>Fecha/hora de inspección<input type="datetime-local" step="1" required value={fecha} onChange={e => setFecha(e.target.value)} /></label>
      <label>Lugar observado, opcional<input maxLength={2000} value={lugar} onChange={e => setLugar(e.target.value)} /></label>
      <label>Latitud opcional<input type="number" min="-90" max="90" step="any" value={latitud} onChange={e => setLatitud(e.target.value)} /></label>
      <label>Longitud opcional<input type="number" min="-180" max="180" step="any" value={longitud} onChange={e => setLongitud(e.target.value)} /></label>
    </div>
    <label>Observación original<textarea required maxLength={20000} value={observaciones} onChange={e => setObservaciones(e.target.value)} /></label>
    <p>Autor tomado de la sesión autenticada; coordenadas y fotografías no son obligatorias.</p>
    {!politica.fotografiasHabilitadas ? <p>Fotografías deshabilitadas: política pendiente. Puede registrar la observación sin archivos.</p> : <>
      <p>Política habilitada: {politica.referenciaPolitica}. Hasta {politica.maximoArchivos} JPEG/PNG, {politica.maximoBytes} bytes por archivo.</p>
      <input ref={selector} type="file" accept="image/png,image/jpeg" multiple disabled={ocupado} onChange={e => {
        const seleccionados = Array.from(e.target.files ?? []);
        if (seleccionados.length > politica.maximoArchivos || seleccionados.some(f => f.size > politica.maximoBytes)) {
          e.target.value = ''; setArchivos([]); fallo(new Error('Selección de archivos fuera de límites.')); return;
        }
        setArchivos(seleccionados.map(archivo => ({ archivo, origen: '', descripcion: '', capturadaEn: '' })));
      }} />
      {archivos.map((a, i) => <fieldset key={i}><legend>{a.archivo.name}</legend>
        <label>Procedencia declarada<input required maxLength={2000} value={a.origen} onChange={e => setArchivos(v => v.map((x, n) => n === i ? { ...x, origen: e.target.value } : x))} /></label>
        <label>Descripción<input required maxLength={4000} value={a.descripcion} onChange={e => setArchivos(v => v.map((x, n) => n === i ? { ...x, descripcion: e.target.value } : x))} /></label>
        <label>Fecha de captura conocida, opcional (Bolivia)<input type="datetime-local" step="1" value={a.capturadaEn} onChange={e => setArchivos(v => v.map((x, n) => n === i ? { ...x, capturadaEn: e.target.value } : x))} /></label>
      </fieldset>)}
    </>}
    <button disabled={ocupado}>Confirmar registro de inspección</button>
    <p>Si se pierde la respuesta, actualice el historial o reintente el mismo contenido. No se sustituye una inspección existente.</p>
  </form>;
}
