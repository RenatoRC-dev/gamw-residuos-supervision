import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useSesionActual } from '../aplicacion/contextoAutenticacion';
import { administracionApi, ErrorAdministracion, type Cuenta, type Persona, type Opciones, type Pagina, type Concesion, type Actuacion } from '../api/administracion';

export function PaginaAdministracionUsuarios() {
  const { sesion, cerrar } = useSesionActual();
  return <main><Link to="/">Inicio</Link><h1>Administración institucional de usuarios y permisos</h1>
    <p>Usuario, PersonaNatural, función operativa y rol de sistema son conceptos distintos. No se crean privilegios por nombre ni por autenticación.</p>
    {sesion ? <Administracion key={sesion.tokenAcceso} token={sesion.tokenAcceso} vencer={cerrar} /> : <Link to="/acceso">Inicie sesión.</Link>}
  </main>;
}
function Administracion({ token, vencer }: { token: string; vencer: (s?: string) => void }) {
  const [opciones, setOpciones] = useState<Opciones | null>(null), [lista, setLista] = useState<Pagina<Cuenta>>({ registros: [], siguiente: null });
  const [desde, setDesde] = useState(0), [id, setId] = useState(''), [revision, setRevision] = useState(0), [error, setError] = useState(''), [cargando, setCargando] = useState(false);
  useEffect(() => {
    const a = new AbortController(); setCargando(true); setError('');
    Promise.all([administracionApi.opciones(token, a.signal), administracionApi.listar(token, desde, a.signal)]).then(([o, l]) => {
      if (!a.signal.aborted) { setOpciones(o); setLista(l); }
    }).catch(e => { if (!a.signal.aborted) { setOpciones(null); setLista({ registros: [], siguiente: null }); setId(''); setError((e as Error).message); if (e instanceof ErrorAdministracion && e.estado === 401) vencer('Sesión vencida.'); } })
      .finally(() => { if (!a.signal.aborted) setCargando(false); });
    return () => a.abort();
  }, [token, desde, revision, vencer]);
  return <><p role="alert">{error}</p>{cargando && <p role="status">Consultando administración autorizada…</p>}
    <button disabled={cargando} onClick={() => setRevision(n => n + 1)}>Actualizar permisos y cuentas</button>
    {opciones && <><p>{opciones.pendientes}</p><p>Catálogo institucional de roles pendiente; se administran únicamente permisos técnicos explícitos autorizados.</p>
      {!lista.registros.length && !cargando && <p>Sin cuentas en esta página.</p>}
      <label>Cuenta<select value={id} onChange={e => setId(e.target.value)}><option value="">Seleccione</option>
        {id && !lista.registros.some(c => c.id === id) && <option value={id}>{id}</option>}
        {lista.registros.map(c => <option key={c.id} value={c.id}>{c.nombreUsuario} · {c.nombres} · {c.activo ? 'Activa' : 'Inactiva'}{c.administracionProtegida ? ' · Administración protegida' : ''}</option>)}</select></label>
      <button disabled={desde === 0 || cargando} onClick={() => setDesde(Math.max(0, desde - 50))}>Anterior</button>
      <button disabled={lista.siguiente === null || cargando} onClick={() => lista.siguiente !== null && setDesde(lista.siguiente)}>Siguiente</button>
      {opciones.puedeGestionar && (opciones.provisionHabilitada
        ? <Crear key={token} token={token} referencia={opciones.referenciaProvision} vencer={vencer} creado={() => setRevision(n => n + 1)} />
        : <p>Creación bloqueada hasta habilitar explícitamente el procedimiento de provisión/entrega de credenciales.</p>)}
      {id && <Detalle key={`${token}:${id}`} token={token} id={id} opciones={opciones} vencer={vencer} actualizar={() => setRevision(n => n + 1)} />}
    </>}
  </>;
}
function Crear({ token, referencia, vencer, creado }: { token: string; referencia: string; vencer: (s?: string) => void; creado: () => void }) {
  const [personas, setPersonas] = useState<Persona[]>([]), [persona, setPersona] = useState(''), [ocupado, setOcupado] = useState(false), [mensaje, setMensaje] = useState('');
  const vida = useRef<AbortController | null>(null);
  useEffect(() => { const a = new AbortController(); vida.current = a; return () => a.abort(); }, []);
  async function buscar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (ocupado || !vida.current) return;
    const ci = String(new FormData(e.currentTarget).get('ci')); setOcupado(true); setMensaje(''); setPersonas([]); setPersona('');
    try { const p = await administracionApi.personas(token, ci, vida.current.signal); if (!vida.current.signal.aborted) { setPersonas(p); if (!p.length) setMensaje('Persona no encontrada. Regístrela mediante el flujo de personas; aquí no se crea otra identidad.'); } }
    catch (err) { if (!vida.current.signal.aborted) { setMensaje((err as Error).message); if (err instanceof ErrorAdministracion && err.estado === 401) vencer('Sesión vencida.'); } }
    finally { if (!vida.current.signal.aborted) setOcupado(false); }
  }
  async function guardar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if (ocupado || !vida.current || !persona) return;
    const form = e.currentTarget, f = new FormData(form), clave = form.elements.namedItem('contrasena') as HTMLInputElement;
    setOcupado(true); setMensaje('');
    const operacion = administracionApi.guardar(token, '/usuarios', 'POST', { personaNaturalId: persona, nombreUsuario: f.get('nombre'), contrasena: clave.value, motivo: f.get('motivo') }, vida.current.signal);
    clave.value = ''; f.delete('contrasena'); // Sin estado React, almacenamiento del navegador ni retención para reintento.
    try { await operacion; if (!vida.current.signal.aborted) { setMensaje('Cuenta creada sin permisos automáticos. Entregue la credencial exclusivamente según el procedimiento indicado.'); form.reset(); setPersona(''); setPersonas([]); creado(); } }
    catch (err) { if (!vida.current.signal.aborted) { setMensaje((err as Error).message); if (err instanceof ErrorAdministracion && err.estado === 401) vencer('Sesión vencida.'); } }
    finally { if (!vida.current.signal.aborted) setOcupado(false); }
  }
  return <section><h2>Provisionar cuenta sin privilegios implícitos</h2><p>Procedimiento habilitado: {referencia}. Entrega manual; sin envío automático ni recuperación de contraseña.</p>
    <p role="status">{mensaje}</p><form onSubmit={buscar}><label>CI textual exacto<input required name="ci" maxLength={200} /></label><button disabled={ocupado}>Buscar PersonaNatural</button></form>
    <label>Persona existente<select value={persona} disabled={ocupado} onChange={e => setPersona(e.target.value)}><option value="">Seleccione</option>{personas.map(p => <option disabled={!p.activo} key={p.id} value={p.id}>{p.ci} · {p.nombres} {p.apellidoPaterno} {p.apellidoMaterno}{p.activo ? '' : ' · Inactiva'}</option>)}</select></label>
    <form onSubmit={guardar}><fieldset disabled={ocupado || !persona}>
      <label>Nombre de acceso<input required name="nombre" maxLength={100} autoComplete="off" /></label>
      <label>Contraseña inicial (mínimo técnico 12 caracteres; máximo 72 bytes UTF-8)<input required minLength={12} maxLength={72} name="contrasena" type="password" autoComplete="new-password" /></label>
      <label>Motivo de provisión<textarea required name="motivo" maxLength={4000} /></label>
      <label><input required type="checkbox" />Confirmo la identidad y el procedimiento de entrega autorizado.</label>
      <button>Crear cuenta</button></fieldset></form>
  </section>;
}
function Detalle({ token, id, opciones, vencer, actualizar }: { token: string; id: string; opciones: Opciones; vencer: (s?: string) => void; actualizar: () => void }) {
  const [cuenta, setCuenta] = useState<Cuenta | null>(null), [concesiones, setConcesiones] = useState<Pagina<Concesion>>({ registros: [], siguiente: null });
  const [actuaciones, setActuaciones] = useState<Pagina<Actuacion>>({ registros: [], siguiente: null }), [desdePermisos, setDesdePermisos] = useState(0), [desdeHistoria, setDesdeHistoria] = useState(0);
  const [revision, setRevision] = useState(0), [ocupado, setOcupado] = useState(false), [mensaje, setMensaje] = useState('');
  const vida = useRef<AbortController | null>(null);
  useEffect(() => { const a = new AbortController(); vida.current = a; return () => a.abort(); }, []);
  function fallo(e: unknown) {
    setMensaje(e instanceof Error ? e.message : 'Operación no confirmada.');
    if (e instanceof ErrorAdministracion && (e.estado === 401 || e.estado === 403)) setCuenta(null);
    if (e instanceof ErrorAdministracion && e.estado === 401) vencer('Sesión vencida.');
  }
  useEffect(() => {
    const a = new AbortController();
    Promise.all([administracionApi.cuenta(token, id, a.signal), administracionApi.actuaciones(token, id, desdeHistoria, a.signal),
      opciones.puedeAdministrarPermisos ? administracionApi.concesiones(token, id, desdePermisos, a.signal) : Promise.resolve({ registros: [], siguiente: null } as Pagina<Concesion>)]).then(([c, h, p]) => {
      if (!a.signal.aborted) { setCuenta(c); setActuaciones(h); setConcesiones(p); }
    }).catch(e => { if (!a.signal.aborted) fallo(e); });
    return () => a.abort();
  // Instancia aislada por token y cuenta, sin identidad derivada del formulario.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, id, revision, desdePermisos, desdeHistoria, opciones.puedeAdministrarPermisos]);
  async function operar(ruta: string, metodo: string, datos: unknown): Promise<boolean> {
    if (ocupado || !vida.current || vida.current.signal.aborted) return false;
    setOcupado(true); setMensaje('');
    try { await administracionApi.guardar(token, `/usuarios/${id}${ruta}`, metodo, datos, vida.current.signal);
      if (vida.current.signal.aborted) return false;
      setMensaje('Actuación confirmada y registrada sin eliminar historia.'); setRevision(n => n + 1); actualizar(); return true;
    } catch (e) { if (!vida.current.signal.aborted) fallo(e); return false; }
    finally { if (!vida.current.signal.aborted) setOcupado(false); }
  }
  const facultadRevocar = (c: Concesion) => opciones.facultades.some(f => f.permiso === c.permiso && f.periodoServicioId === c.periodoServicioId && (!c.incluyeHistorico || f.permiteHistorico));
  return <section><p role="status">{mensaje}</p><button disabled={ocupado} onClick={() => setRevision(n => n + 1)}>Actualizar cuenta e historial</button>
    {!cuenta ? <p>Detalle no disponible o cargando.</p> : <>
      <h2>{cuenta.nombreUsuario} · {cuenta.activo ? 'Activa' : 'Inactiva'}</h2><p>{cuenta.nombres} {cuenta.apellidoPaterno} {cuenta.apellidoMaterno} · PersonaNatural {cuenta.personaNaturalId} · cuenta {cuenta.id}</p>
      <p>Creación {cuenta.creadoEn} · actualización {cuenta.actualizadoEn}. No se muestran contraseñas ni hashes.</p>
      {cuenta.administracionProtegida ? <p>Cuenta administrativa protegida: cambios reservados a provisión local hasta aprobar política institucional.</p> : <>
        {opciones.puedeGestionar && <form onSubmit={async e => { e.preventDefault(); const f = new FormData(e.currentTarget); await operar('', 'PUT', { nombreUsuario: f.get('nombre'), motivo: f.get('motivo') }); }}>
          <fieldset disabled={ocupado}><label>Nombre de acceso<input required name="nombre" defaultValue={cuenta.nombreUsuario} maxLength={100} /></label><label>Motivo<textarea name="motivo" required maxLength={4000} /></label><button>Actualizar nombre</button></fieldset></form>}
        {opciones.puedeGestionar && cuenta.activo && <form onSubmit={async e => { e.preventDefault(); const f = new FormData(e.currentTarget); await operar('/estado', 'PATCH', { activo: false, motivo: f.get('motivo') }); }}>
          <fieldset disabled={ocupado}><label>Motivo de desactivación<textarea name="motivo" required maxLength={4000} /></label><label><input required type="checkbox" />Confirmo deshabilitar acceso sin borrar la persona ni sus actuaciones.</label><button>Desactivar cuenta</button></fieldset></form>}
        {!cuenta.activo && <p>Reactivación bloqueada hasta resolver credenciales y sesiones anteriores; no se rehabilitan JWT implícitamente.</p>}
      </>}
      {opciones.puedeAdministrarPermisos && <><h3>Concesiones explícitas e historial</h3>{!concesiones.registros.length && <p>Sin concesiones en esta página.</p>}
        {concesiones.registros.map(c => <article key={`${c.periodoServicioId ?? 'municipal'}:${c.id}`}><p>{c.permiso} · {c.periodoServicioId ? `Periodo ${c.periodoServicioId}` : 'Municipal GAMW'} · histórico {c.incluyeHistorico ? 'autorizado' : 'no ampliado'} · otorgado {c.otorgadoEn} · {c.revocadoEn ? `Revocado ${c.revocadoEn}` : 'Vigente'}</p>
          {!c.revocadoEn && !cuenta.administracionProtegida && facultadRevocar(c) && <form onSubmit={async e => { e.preventDefault(); const f = new FormData(e.currentTarget); await operar(`/permisos/${c.id}/revocacion`, 'POST', { porPeriodo: c.periodoServicioId !== null, motivo: f.get('motivo') }); }}>
            <fieldset disabled={ocupado}><label>Motivo de revocación<input required name="motivo" maxLength={4000} /></label><button>Revocar esta concesión</button></fieldset></form>}</article>)}
        <button disabled={desdePermisos === 0 || ocupado} onClick={() => setDesdePermisos(Math.max(0, desdePermisos - 50))}>Permisos anteriores</button>
        <button disabled={concesiones.siguiente === null || ocupado} onClick={() => concesiones.siguiente !== null && setDesdePermisos(concesiones.siguiente)}>Más permisos</button>
        {!cuenta.administracionProtegida && cuenta.activo && <Otorgamiento opciones={opciones} ocupado={ocupado} enviar={c => operar('/permisos', 'POST', c)} />}
      </>}
      <h3>Actuaciones administrativas</h3>{actuaciones.registros.map(a => <p key={a.id}>{a.registradaEn} · {a.tipo} · actor {a.actorUsuarioId} · {a.motivo}</p>)}
      <button disabled={desdeHistoria === 0 || ocupado} onClick={() => setDesdeHistoria(Math.max(0, desdeHistoria - 50))}>Historial anterior</button>
      <button disabled={actuaciones.siguiente === null || ocupado} onClick={() => actuaciones.siguiente !== null && setDesdeHistoria(actuaciones.siguiente)}>Más actuaciones</button>
    </>}
  </section>;
}
function Otorgamiento({ opciones, ocupado, enviar }: { opciones: Opciones; ocupado: boolean; enviar: (c: unknown) => Promise<boolean> }) {
  const [id, setId] = useState(''); const facultad = opciones.facultades.find(f => f.id === id);
  if (!opciones.facultades.length) return <p>Sin límites de delegación provisionados localmente: no puede otorgar permisos, aunque tenga capacidad administrativa.</p>;
  return <form onSubmit={async e => {
    e.preventDefault(); if (!facultad) return; const form = e.currentTarget, f = new FormData(form);
    if (await enviar({ permiso: facultad.permiso, periodoServicioId: facultad.periodoServicioId, incluyeHistorico: f.get('historico') === 'on', motivo: f.get('motivo') })) { form.reset(); setId(''); }
  }}><h4>Otorgar dentro de una facultad explícita</h4><fieldset disabled={ocupado}>
    <label>Permiso y ámbito<select required value={id} onChange={e => setId(e.target.value)}><option value="">Seleccione</option>
      {opciones.facultades.map(f => <option key={f.id} value={f.id}>{f.permiso} · {f.periodoServicioId ? `${f.empresa} · periodo ${f.periodoServicioId} (${f.fechaInicio} — ${f.fechaFin})` : 'Municipal GAMW'}</option>)}</select></label>
    {facultad?.periodoServicioId && facultad.permiteHistorico && <label><input key={facultad.id} type="checkbox" name="historico" />Autorizar explícitamente jornadas/acciones anteriores a la concesión, solo de este periodo</label>}
    <label>Motivo y fundamento<textarea required name="motivo" maxLength={4000} /></label><label><input required type="checkbox" />Confirmo permiso y ámbito específicos; no concede rol administrativo ni otros periodos.</label>
    <button disabled={!facultad}>Otorgar permiso</button></fieldset></form>;
}
