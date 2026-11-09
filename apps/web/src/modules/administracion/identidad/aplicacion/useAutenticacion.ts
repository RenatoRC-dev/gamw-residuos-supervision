import { useCallback, useEffect, useRef, useState } from 'react';
import { autenticacionApi, ErrorAutenticacion, type ApiAutenticacion, type SesionAutenticada } from '../api/autenticacion';

// La identidad web vive solo en memoria. Ningún rol o permiso se deduce del JWT.
export function useAutenticacion(api: ApiAutenticacion = autenticacionApi) {
  const [sesion, setSesion] = useState<SesionAutenticada | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [aviso, setAviso] = useState('');
  const revision = useRef(0);
  const peticion = useRef<AbortController | null>(null);

  const cerrar = useCallback((mensaje = 'Sesión cerrada en este navegador.') => {
    ++revision.current;
    peticion.current?.abort();
    setSesion(null); setOcupado(false); setAviso(mensaje);
  }, []);

  useEffect(() => () => { ++revision.current; peticion.current?.abort(); }, []);
  useEffect(() => {
    if (!sesion) return;
    const revisar = () => { if (Date.now() >= Date.parse(sesion.expiraEn)) cerrar('Sesión vencida. Vuelva a autenticarse.'); };
    revisar();
    const intervalo = window.setInterval(revisar, 1000);
    return () => window.clearInterval(intervalo);
  }, [sesion, cerrar]);

  const iniciar = async (nombre: string, clave: string) => {
    const intento = ++revision.current;
    peticion.current?.abort();
    const controlador = new AbortController(); peticion.current = controlador;
    setSesion(null); setOcupado(true); setAviso('');
    try {
      const nueva = await api.iniciar(nombre, clave, controlador.signal);
      if (intento !== revision.current || controlador.signal.aborted) return;
      const actual = await api.actual(nueva.tokenAcceso, controlador.signal);
      if (intento !== revision.current) return;
      if (actual.usuarioId !== nueva.usuarioId || actual.personaNaturalId !== nueva.personaNaturalId || Date.parse(nueva.expiraEn) <= Date.now()) {
        throw new Error('Identidad inconsistente.');
      }
      setSesion({ ...nueva, ...actual });
    } catch (e) {
      if (intento === revision.current) setAviso(e instanceof ErrorAutenticacion && e.estado === 401
        ? 'Credenciales inválidas.' : 'No se pudo validar la sesión. Revise la conexión y vuelva a ingresar.');
    } finally { if (intento === revision.current) setOcupado(false); }
  };

  const verificar = async () => {
    if (!sesion || ocupado) return;
    const intento = ++revision.current;
    peticion.current?.abort();
    const controlador = new AbortController(); peticion.current = controlador;
    setOcupado(true); setAviso('');
    try {
      const actual = await api.actual(sesion.tokenAcceso, controlador.signal);
      if (intento !== revision.current) return;
      if (actual.usuarioId !== sesion.usuarioId || actual.personaNaturalId !== sesion.personaNaturalId) {
        cerrar('La identidad no pudo verificarse. Vuelva a ingresar.'); return;
      }
      setSesion({ ...sesion, ...actual }); setAviso('Identidad verificada con el servidor.');
    } catch (e) {
      if (intento !== revision.current) return;
      if (e instanceof ErrorAutenticacion && [401, 403].includes(e.estado)) cerrar('Sesión no válida. Vuelva a autenticarse.');
      else setAviso('No se pudo verificar la identidad. Revise la conexión.');
    } finally { if (intento === revision.current) setOcupado(false); }
  };
  return { sesion, ocupado, aviso, iniciar, verificar, cerrar };
}
