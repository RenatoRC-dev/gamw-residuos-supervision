import { createContext, useContext, type ReactNode } from 'react';
import { useAutenticacion } from './useAutenticacion';

const Contexto = createContext<ReturnType<typeof useAutenticacion> | null>(null);

export function ProveedorAutenticacion({ children }: { children: ReactNode }) {
  const sesion = useAutenticacion();
  return <Contexto.Provider value={sesion}>{children}</Contexto.Provider>;
}

export function useSesionActual() {
  const sesion = useContext(Contexto);
  if (!sesion) throw new Error('Falta el contexto de autenticación.');
  return sesion;
}
