import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useSesionActual } from '../aplicacion/contextoAutenticacion';

export function PaginaAcceso() {
  const { sesion, ocupado, aviso, iniciar, verificar, cerrar } = useSesionActual();
  const [nombre, setNombre] = useState('');
  const [clave, setClave] = useState('');
  async function ingresar(e: FormEvent) {
    e.preventDefault();
    const enviada = clave; setClave('');
    await iniciar(nombre, enviada);
  }
  return <main>
    <Link to="/">Inicio</Link>
    <h1>Acceso GAMW</h1>
    {aviso && <p role="status">{aviso}</p>}
    {sesion ? <section aria-label="Sesión autenticada">
      <h2>Usuario autenticado</h2>
      <p>{sesion.nombreUsuario}</p>
      <dl><dt>Identidad de acceso</dt><dd>{sesion.usuarioId}</dd><dt>Persona natural</dt><dd>{sesion.personaNaturalId}</dd></dl>
      <p>Vencimiento de sesión: {new Date(sesion.expiraEn).toLocaleString('es-BO')}</p>
      <p>La autenticación identifica la cuenta. Cada operación requiere su autorización correspondiente.</p>
      <button disabled={ocupado} onClick={() => void verificar()}>Verificar identidad</button>
      <button onClick={() => { setClave(''); cerrar(); }}>Cerrar sesión</button>
    </section> : <form method="post" onSubmit={e => void ingresar(e)} aria-label="Inicio de sesión">
      <label>Nombre de usuario<input required name="nombreUsuario" autoComplete="username" value={nombre} onChange={e => setNombre(e.target.value)} disabled={ocupado} /></label>
      <label>Contraseña<input required name="contrasena" type="password" autoComplete="current-password" value={clave} onChange={e => setClave(e.target.value)} disabled={ocupado} /></label>
      <button disabled={ocupado} type="submit">{ocupado ? 'Verificando…' : 'Ingresar'}</button>
      {ocupado && <button type="button" onClick={() => cerrar('Acceso cancelado.')}>Cancelar acceso</button>}
    </form>}
    <p>La sesión se conserva solo en memoria. Al recargar el navegador deberá ingresar nuevamente.</p>
  </main>;
}
