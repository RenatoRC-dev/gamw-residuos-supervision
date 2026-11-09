import { useEffect, useRef, useState, type FormEvent } from 'react';
import { fiscalizacionApi, type ExpedienteFiscal } from '../api/fiscalizacion';

export function FormularioDecisionFiscal({ token, jornada, expediente, guardado, fallo }: {
  token: string; jornada: string; expediente: ExpedienteFiscal; guardado: () => void; fallo: (e: unknown) => void;
}) {
  const [solicitud] = useState(() => crypto.randomUUID());
  const [resultado, setResultado] = useState(''), [observaciones, setObservaciones] = useState('');
  const [inspecciones, setInspecciones] = useState<string[]>([]), [ocupado, setOcupado] = useState(false), [confirmado, setConfirmado] = useState(false);
  const peticion = useRef<AbortController | null>(null);
  useEffect(() => () => peticion.current?.abort(), []);
  const categoria = expediente.resultadosHabilitados.find(r => r.id === resultado);
  const enviar = async (e: FormEvent) => {
    e.preventDefault(); if (!confirmado || !categoria) return;
    const abort = new AbortController(); peticion.current = abort; setOcupado(true);
    try {
      await fiscalizacionApi.emitir(token, jornada, { solicitudId: solicitud, resultadoId: resultado, observaciones, inspeccionesReferenciadas: inspecciones }, abort.signal);
      if (!abort.signal.aborted) guardado();
    } catch (e) { if (!abort.signal.aborted) fallo(e); }
    finally { if (!abort.signal.aborted) setOcupado(false); }
  };
  if (expediente.fiscalizacion) return <p>Existe determinación registrada. La rectificación no está habilitada; no se reemplaza el antecedente.</p>;
  if (!expediente.resultadosHabilitados.length) return <section><h2>Revisión sin dictamen</h2>
    <p>Catálogo institucional no habilitado. Puede revisar evidencia, pero no registrar categorías inventadas ni una determinación definitiva.</p></section>;
  return <form onSubmit={enviar} aria-label="Registrar determinación humana">
    <h2>Determinación del fiscalizador autorizado</h2>
    <label>Resultado del catálogo habilitado<select required value={resultado} onChange={e => setResultado(e.target.value)}>
      <option value="">Seleccione explícitamente</option>{expediente.resultadosHabilitados.map(r => <option key={r.id} value={r.id}>{r.codigo} · {r.nombre}</option>)}</select></label>
    {categoria && <p>Referencia de aprobación declarada: {categoria.referenciaAprobacion}</p>}
    <label>Fundamentación y observaciones<textarea required={categoria?.observacionesObligatorias} maxLength={20000} value={observaciones} onChange={e => setObservaciones(e.target.value)} /></label>
    <fieldset><legend>Inspecciones utilizadas como antecedente</legend>
      {expediente.inspecciones.map(i => <label key={i.id}><input type="checkbox" checked={inspecciones.includes(i.id)} onChange={e =>
        setInspecciones(v => e.target.checked ? [...v, i.id] : v.filter(id => id !== i.id))} />{i.realizadaEn} · {i.observaciones}</label>)}
      {!expediente.inspecciones.length && <p>Sin inspecciones conservadas; no se presume evidencia negativa.</p>}
    </fieldset>
    <label><input type="checkbox" required checked={confirmado} onChange={e => setConfirmado(e.target.checked)} />Confirmo registrar mi valoración humana sobre esta jornada, sin delegar el resultado al GPS.</label>
    <p>Se conservarán el contexto y los identificadores GPS recibidos al emitir, junto con las inspecciones seleccionadas. No genera sanciones.</p>
    <button disabled={ocupado || !confirmado || !categoria}>Confirmar determinación municipal</button>
  </form>;
}
