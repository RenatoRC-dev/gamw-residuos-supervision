import { createContext, Fragment, useContext, useLayoutEffect, useRef, type ReactNode } from 'react';
import { configurarAccesoHttp } from '../../../../shared/api/http';
import { useAutenticacion } from './useAutenticacion';

const Contexto = createContext<ReturnType<typeof useAutenticacion> | null>(null);

export function ProveedorAutenticacion({ children }: { children: ReactNode }) {
  const sesion = useAutenticacion();
  const cerrarActual=useRef(sesion.cerrar); cerrarActual.current=sesion.cerrar;
  useLayoutEffect(() => configurarAccesoHttp(sesion.sesion?.tokenAcceso ?? null, mensaje => cerrarActual.current(mensaje)), [sesion.sesion?.tokenAcceso]);
  return <Contexto.Provider value={sesion}><Fragment key={sesion.sesion?.usuarioId ?? 'sin-sesion'}>{children}</Fragment></Contexto.Provider>;
}

export function useSesionActual() {
  const sesion = useContext(Contexto);
  if (!sesion) throw new Error('Falta el contexto de autenticación.');
  return sesion;
}
