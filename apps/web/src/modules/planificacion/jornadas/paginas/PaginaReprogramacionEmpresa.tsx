import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useSesionActual } from '../../../administracion/identidad/aplicacion/contextoAutenticacion';
import { jornadasEmpresaApi, ErrorReprogramacion, type PlanEmpresa, type PeriodoPlan, type VersionPlan, type RecursosPlan } from '../api/jornadasEmpresa';

export function PaginaReprogramacionEmpresa() {
  const { sesion, cerrar } = useSesionActual();
  return <main><Link to="/">Inicio</Link><h1>Programación, recursos y reprogramación por periodo autorizado</h1>
    <p>Solo periodos expresamente autorizados. No se trasladan INICIO, FIN, GPS, incidencias ni asignaciones a otra jornada.</p>
    {sesion ? <Seleccion key={sesion.tokenAcceso} token={sesion.tokenAcceso} vencer={cerrar} /> : <Link to="/acceso">Inicie sesión.</Link>}
  </main>;
}
function Seleccion({ token, vencer }: { token: string; vencer: (s?: string) => void }) {
  const [lista, setLista] = useState<PlanEmpresa[]>([]), [desde, setDesde] = useState(0), [siguiente, setSiguiente] = useState<number | null>(null);
  const [id, setId] = useState(''), [error, setError] = useState(''), [cargando, setCargando] = useState(false), [revision, setRevision] = useState(0);
  useEffect(() => {
    const a = new AbortController(); setCargando(true); setError('');
    jornadasEmpresaApi.listar(token, desde, a.signal).then(r => { if (!a.signal.aborted) { setLista(r.jornadas); setSiguiente(r.siguiente); } })
      .catch(e => { if (!a.signal.aborted) { setLista([]); setId(''); setError((e as Error).message); if (e instanceof ErrorReprogramacion && e.estado === 401) vencer('Sesión vencida.'); } })
      .finally(() => { if (!a.signal.aborted) setCargando(false); });
    return () => a.abort();
  }, [token, desde, revision, vencer]);
  return <><p role="alert">{error}</p>{cargando && <p role="status">Cargando jornadas del alcance autorizado…</p>}
    {!cargando && !lista.length && <p>Sin jornadas disponibles en esta página.</p>}
    <button disabled={cargando} onClick={() => setRevision(n => n + 1)}>Actualizar jornadas</button>
    <Programar token={token} vencer={vencer} creado={j => { setId(j.id);setRevision(n => n + 1); }} />
    <label>Jornada<select value={id} onChange={e => setId(e.target.value)}><option value="">Seleccione</option>
      {id && !lista.some(j => j.id === id) && <option value={id}>{id}</option>}
      {lista.map(j => <option key={j.id} value={j.id}>{j.inicioProgramado} · {j.empresa} · {j.ruta} v{j.numeroVersion} · {j.vigente ? 'vigente' : 'antecedente'}</option>)}</select></label>
    <button disabled={desde === 0 || cargando} onClick={() => setDesde(Math.max(0, desde - 50))}>Anterior</button>
    <button disabled={siguiente === null || cargando} onClick={() => siguiente !== null && setDesde(siguiente)}>Siguiente</button>
    {id && <Detalle key={`${token}:${id}`} token={token} id={id} vencer={vencer} seleccionar={setId} refrescar={() => setRevision(n => n + 1)} />}</>;
}
function Detalle({ token, id, vencer, seleccionar, refrescar }: { token: string; id: string; vencer: (s?: string) => void; seleccionar: (id: string) => void; refrescar: () => void }) {
  const [plan, setPlan] = useState<PlanEmpresa | null>(null), [error, setError] = useState(''), [ocupado, setOcupado] = useState(false), [revision, setRevision] = useState(0);
  const [sucesora, setSucesora] = useState<string | null>(null);
  const viva = useRef<AbortController | null>(null);
  useEffect(() => { const a = new AbortController(); viva.current = a; return () => a.abort(); }, []);
  function fallo(e: unknown) {
    setError(e instanceof Error ? e.message : 'No se confirmó la operación.');
    if (e instanceof ErrorReprogramacion && (e.estado === 401 || e.estado === 403)) setPlan(null);
    if (e instanceof ErrorReprogramacion && e.estado === 401) vencer('Sesión vencida.');
  }
  useEffect(() => {
    const a = new AbortController();
    jornadasEmpresaApi.obtener(token, id, a.signal).then(p => { if (!a.signal.aborted) setPlan(p); })
      .catch(e => { if (!a.signal.aborted) fallo(e); });
    return () => a.abort();
  // Instancia aislada por principal/token y jornada.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, id, revision]);
  async function enviar(datos: unknown): Promise<boolean> {
    if (ocupado || !viva.current || viva.current.signal.aborted) return false;
    setOcupado(true); setError('');
    try {
      const nueva = await jornadasEmpresaApi.reprogramar(token, id, datos, viva.current.signal);
      if (viva.current.signal.aborted) return false;
      setSucesora(nueva.id); setRevision(n => n + 1); refrescar(); return true;
    } catch (e) { if (!viva.current.signal.aborted) { fallo(e); if (e instanceof ErrorReprogramacion && e.estado === 400) throw e; } return false; }
    finally { if (!viva.current.signal.aborted) setOcupado(false); }
  }
  return <section><p role="alert">{error}</p>{sucesora && <p role="status">Sucesora registrada: {sucesora}. <button onClick={() => seleccionar(sucesora)}>Consultar nueva jornada</button></p>}
    <button disabled={ocupado} onClick={() => { setError(''); setRevision(n => n + 1); }}>Consultar estado actual</button>
    {!plan ? <p>Contexto no disponible o cargando.</p> : <>
      <h2>Programación histórica {plan.id}</h2><p>{plan.empresa} · periodo {plan.periodoServicioId}</p>
      <p>{plan.inicioProgramado} — {plan.finProgramado ?? 'Sin fin programado'} · {plan.vigente ? 'Vigente' : 'Antecedente conservado'}</p>
      <p>Ruta {plan.ruta} · versión histórica {plan.numeroVersion} ({plan.rutaVersionId})</p>
      <p>Motivo de origen: {plan.motivo ?? 'Programación inicial'}</p><p>{plan.limitacion}</p>
      {plan.antecedenteId && <button onClick={() => seleccionar(plan.antecedenteId!)}>Consultar antecedente {plan.antecedenteId}</button>}
      {plan.sucesoraId && <button onClick={() => seleccionar(plan.sucesoraId!)}>Consultar sucesora {plan.sucesoraId}</button>}
      <h3>Asignaciones conservadas</h3>{!plan.asignaciones.length && <p>Sin asignaciones; reprogramar no asigna recursos automáticamente.</p>}
      <p>Disponibilidad técnica de recursos: {plan.estadoAsignacion}. Una sucesora sin asignación no es ejecutable ni recibe evidencia del antecedente.</p>
      {plan.asignaciones.map(a => <p key={a.id}>{a.placa} · {a.responsable} · asignación {a.id}: {a.vigenteDesde} — {a.vigenteHasta ?? 'Abierta'}</p>)}
      {plan.puedeAsignar && <AsignarRecursos key={`${id}:${plan.estadoAsignacion}`} token={token} plan={plan} vencer={vencer} confirmado={p => { setPlan(p);refrescar(); }} />}
      {plan.puedeReprogramar ? <Formulario plan={plan} ocupado={ocupado} enviar={enviar} /> : <p>Reprogramación no habilitada para este antecedente. No se altera ni se reasigna su evidencia.</p>}
    </>}
  </section>;
}
function Programar({ token, vencer, creado }: { token: string; vencer: (s?: string) => void; creado: (p: PlanEmpresa) => void }) {
  const [periodos,setPeriodos]=useState<PeriodoPlan[]>([]),[periodo,setPeriodo]=useState(''),[versiones,setVersiones]=useState<VersionPlan[]>([]),[error,setError]=useState(''),[ocupado,setOcupado]=useState(false);
  const [pendiente,setPendiente]=useState<object|null>(null);const vida=useRef<AbortController|null>(null);
  useEffect(()=>{const a=new AbortController();vida.current=a;jornadasEmpresaApi.periodos(token,a.signal).then(p=>{if(!a.signal.aborted)setPeriodos(p);}).catch(e=>{if(!a.signal.aborted)setError((e as Error).message);});return()=>a.abort();},[token]);
  useEffect(()=>{if(!periodo){setVersiones([]);return;}const a=new AbortController();setVersiones([]);jornadasEmpresaApi.referencias(token,periodo,a.signal).then(v=>{if(!a.signal.aborted)setVersiones(v);}).catch(e=>{if(!a.signal.aborted)setError((e as Error).message);});return()=>a.abort();},[token,periodo]);
  async function guardar(e:FormEvent<HTMLFormElement>){e.preventDefault();if(ocupado || !vida.current)return;const form=e.currentTarget,f=new FormData(form);
    const datos=pendiente??{solicitudId:crypto.randomUUID(),rutaVersionId:f.get('version'),inicioProgramado:new Date(`${f.get('inicio')}-04:00`).toISOString(),finProgramado:f.get('fin')?new Date(`${f.get('fin')}-04:00`).toISOString():null};
    setPendiente(datos);setOcupado(true);setError('');try{const p=await jornadasEmpresaApi.programar(token,periodo,datos,vida.current.signal);if(!vida.current.signal.aborted){setPendiente(null);form.reset();creado(p);}}
    catch(err){if(!vida.current.signal.aborted){setError((err as Error).message);if(err instanceof ErrorReprogramacion && err.estado===400)setPendiente(null);if(err instanceof ErrorReprogramacion && err.estado===401)vencer('Sesión vencida.');}}
    finally{if(!vida.current.signal.aborted)setOcupado(false);}}
  return <section><h2>Programar dentro de un periodo concedido</h2><p role="alert">{error}</p>
    {!periodos.some(p=>p.puedeProgramar)&&<p>Sin concesión explícita JORNADA_PROGRAMAR; puede consultar los contextos que tenga autorizados.</p>}
    <label>Periodo<select value={periodo} disabled={ocupado||!!pendiente} onChange={e=>setPeriodo(e.target.value)}><option value="">Seleccione</option>{periodos.filter(p=>p.puedeProgramar).map(p=><option key={p.id} value={p.id}>{p.empresa} · {p.fechaInicio} — {p.fechaFin} · {p.id}</option>)}</select></label>
    {periodo&&<form onSubmit={guardar}><fieldset disabled={ocupado||!!pendiente}><label>Versión de referencia<select required name="version" defaultValue=""><option value="">Seleccione</option>{versiones.map(v=><option key={v.id} value={v.id}>{v.ruta} v{v.numeroVersion} · {v.vigenteDesde} — {v.vigenteHasta??'Vigencia abierta'}</option>)}</select></label>
      <label>Inicio (Bolivia UTC−04:00)<input required name="inicio" type="datetime-local" step="1"/></label><label>Fin opcional<input name="fin" type="datetime-local" step="1"/></label></fieldset>
      <button disabled={ocupado}>{pendiente?'Reintentar misma programación':'Crear programación sin copiar recursos'}</button></form>}
  </section>;
}
function AsignarRecursos({token,plan,vencer,confirmado}:{token:string;plan:PlanEmpresa;vencer:(s?:string)=>void;confirmado:(p:PlanEmpresa)=>void}){
  const [recursos,setRecursos]=useState<RecursosPlan|null>(null),[error,setError]=useState(''),[ocupado,setOcupado]=useState(false),[pendiente,setPendiente]=useState<object|null>(null);const vida=useRef<AbortController|null>(null);
  const reasignar=plan.estadoAsignacion==='CON_ASIGNACION';
  useEffect(()=>{const a=new AbortController();vida.current=a;jornadasEmpresaApi.recursos(token,plan.id,a.signal).then(r=>{if(!a.signal.aborted)setRecursos(r);}).catch(e=>{if(!a.signal.aborted)setError((e as Error).message);});return()=>a.abort();},[token,plan.id]);
  async function guardar(e:FormEvent<HTMLFormElement>){e.preventDefault();if(ocupado||!vida.current)return;const f=new FormData(e.currentTarget);
    const datos=pendiente??{solicitudId:crypto.randomUUID(),vinculoVehiculoEmpresaId:f.get('vehiculo'),vinculoPersonaEmpresaId:f.get('persona'),motivoCambio:reasignar?f.get('motivo'):null};
    setPendiente(datos);setOcupado(true);setError('');try{const p=await jornadasEmpresaApi.asignar(token,plan.id,datos,reasignar,vida.current.signal);if(!vida.current.signal.aborted){setPendiente(null);confirmado(p);}}
    catch(err){if(!vida.current.signal.aborted){setError((err as Error).message);if(err instanceof ErrorReprogramacion&&err.estado===400)setPendiente(null);if(err instanceof ErrorReprogramacion&&err.estado===401)vencer('Sesión vencida.');}}
    finally{if(!vida.current.signal.aborted)setOcupado(false);}}
  return <form onSubmit={guardar}><h3>{reasignar?'Reasignar con historial':'Asignar recursos expresamente'}</h3><p role="alert">{error}</p>
    <p>Solo vínculos de la empresa derivada del periodo y elegibles en la fecha programada. No se copian recursos del antecedente.</p>
    {recursos&&(!recursos.vehiculos.length||!recursos.personas.length)&&<p>Faltan vínculos elegibles. Un administrador autorizado debe registrarlos; no se inventan recursos.</p>}
    <fieldset disabled={ocupado||!!pendiente||!recursos}><label>Vehículo<select required name="vehiculo" defaultValue=""><option value="">Seleccione</option>{recursos?.vehiculos.map(v=><option key={v.vinculoId} value={v.vinculoId}>{v.nombre} · vínculo {v.vinculoId}</option>)}</select></label>
      <label>Responsable<select required name="persona" defaultValue=""><option value="">Seleccione</option>{recursos?.personas.map(p=><option key={p.vinculoId} value={p.vinculoId}>{p.nombre} · vínculo {p.vinculoId}</option>)}</select></label>
      {reasignar&&<label>Motivo obligatorio<textarea required name="motivo" maxLength={10000}/></label>}<label><input type="checkbox" required/>Confirmo asignación en esta jornada; los antecedentes permanecen intactos.</label></fieldset>
    <button disabled={ocupado||!recursos}>{pendiente?'Reintentar misma asignación':reasignar?'Registrar reasignación histórica':'Asignar a esta jornada'}</button>
  </form>;
}
function Formulario({ plan, ocupado, enviar }: { plan: PlanEmpresa; ocupado: boolean; enviar: (s: unknown) => Promise<boolean> }) {
  const [pendiente, setPendiente] = useState<object | null>(null);
  const fechaBolivia = (iso: string | null) => iso ? new Date(Date.parse(iso) - 4 * 3600000).toISOString().slice(0, 19) : '';
  async function registrar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form = e.currentTarget, f = new FormData(form);
    const solicitud = pendiente ?? { solicitudId: crypto.randomUUID(), inicioProgramado: new Date(`${String(f.get('inicio'))}-04:00`).toISOString(),
      finProgramado: f.get('fin') ? new Date(`${String(f.get('fin'))}-04:00`).toISOString() : null, motivoReprogramacion: f.get('motivo') };
    setPendiente(solicitud);
    try { if (await enviar(solicitud)) setPendiente(null); } catch { setPendiente(null); }
  }
  return <form onSubmit={registrar}><h3>Crear jornada sucesora</h3><fieldset disabled={ocupado || !!pendiente}>
    <p>Se mantienen periodo y versión de ruta del antecedente. Fechas en Bolivia UTC−04:00.</p>
    <label>Nuevo inicio<input required name="inicio" type="datetime-local" step="1" defaultValue={fechaBolivia(plan.inicioProgramado)} /></label>
    <label>Nuevo fin (opcional)<input name="fin" type="datetime-local" step="1" defaultValue={fechaBolivia(plan.finProgramado)} /></label>
    <label>Motivo obligatorio<textarea required maxLength={10000} name="motivo" /></label>
    <label><input required type="checkbox" />Confirmo nueva programación sin trasladar evidencia ni recursos históricos.</label>
  </fieldset><button disabled={ocupado}>{pendiente ? 'Reintentar misma reprogramación' : 'Crear sucesora'}</button>
    {pendiente && <p>Solicitud conservada para reintento idempotente; consulte el historial ante una respuesta incierta.</p>}</form>;
}
