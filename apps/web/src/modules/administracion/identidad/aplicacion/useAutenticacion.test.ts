import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useAutenticacion } from './useAutenticacion';
import { ErrorAutenticacion, type SesionAutenticada } from '../api/autenticacion';

const identidad = { usuarioId: 'A', personaNaturalId: 'PA', nombreUsuario: 'cuenta-sintetica-A' };
const sesion = (): SesionAutenticada => ({ ...identidad, tokenAcceso: 'token-A', expiraEn: new Date(Date.now() + 60000).toISOString() });
const api = () => ({ iniciar: vi.fn().mockImplementation(async () => sesion()), actual: vi.fn().mockResolvedValue(identidad) });

describe('CU-01 ciclo de sesión en memoria', () => {
  it('valida identidad persistida antes de autenticar y no escribe almacenamiento web', async () => {
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    const servidor = api(); const { result } = renderHook(() => useAutenticacion(servidor));
    await act(async () => { await result.current.iniciar('cuenta', 'clave'); });
    expect(result.current.sesion?.usuarioId).toBe('A'); expect(servidor.actual).toHaveBeenCalledTimes(1);
    expect(storage).not.toHaveBeenCalled();
    act(() => result.current.cerrar()); expect(result.current.sesion).toBeNull();
  });
  it('rechaza identidades divergentes', async () => {
    const servidor = api(); servidor.actual.mockResolvedValue({ ...identidad, personaNaturalId: 'otra-persona' });
    const { result } = renderHook(() => useAutenticacion(servidor));
    await act(async () => { await result.current.iniciar('cuenta', 'clave'); });
    expect(result.current.sesion).toBeNull(); expect(result.current.aviso).toContain('No se pudo validar');
  });
  it('un fallo de red no autentica ni expone detalles del transporte', async () => {
    const servidor = api(); servidor.iniciar.mockRejectedValue(new TypeError('detalle-sensible-sintetico'));
    const { result } = renderHook(() => useAutenticacion(servidor));
    await act(async () => { await result.current.iniciar('cuenta', 'clave'); });
    expect(result.current.sesion).toBeNull(); expect(result.current.ocupado).toBe(false);
    expect(result.current.aviso).toContain('Revise la conexión');
    expect(result.current.aviso).not.toContain('detalle-sensible'); expect(servidor.actual).not.toHaveBeenCalled();
  });
  it('un login tardío no restaura sesión cancelada ni consulta con su Bearer', async () => {
    const servidor = api(); let resolver!: (s: SesionAutenticada) => void;
    servidor.iniciar.mockImplementation(() => new Promise<SesionAutenticada>(r => { resolver = r; }));
    const { result } = renderHook(() => useAutenticacion(servidor)); let pendiente!: Promise<void>;
    act(() => { pendiente = result.current.iniciar('cuenta', 'clave'); });
    act(() => result.current.cerrar());
    await act(async () => { resolver(sesion()); await pendiente; });
    expect(result.current.sesion).toBeNull(); expect(servidor.actual).not.toHaveBeenCalled();
  });
  it('401 actual elimina la sesión y requiere reautenticación', async () => {
    const servidor = api(); const { result } = renderHook(() => useAutenticacion(servidor));
    await act(async () => { await result.current.iniciar('cuenta', 'clave'); });
    servidor.actual.mockRejectedValueOnce(new ErrorAutenticacion(401));
    await act(async () => { await result.current.verificar(); });
    expect(result.current.sesion).toBeNull(); expect(result.current.aviso).toContain('Vuelva a autenticarse');
  });
  it('expira localmente sin enviar solicitudes automáticas ni conservar identidad', async () => {
    vi.useFakeTimers(); const servidor = api(); const vista = renderHook(() => useAutenticacion(servidor));
    try {
      await act(async () => { await vista.result.current.iniciar('cuenta', 'clave'); });
      act(() => { vi.advanceTimersByTime(61000); });
      expect(vista.result.current.sesion).toBeNull(); expect(vista.result.current.aviso).toContain('Sesión vencida');
      expect(servidor.actual).toHaveBeenCalledTimes(1);
    } finally { vista.unmount(); vi.useRealTimers(); }
  });
  it('un 401 tardío de A no cierra la nueva sesión B', async () => {
    const servidor = api(); let rechazar!: (e: Error) => void;
    servidor.actual.mockImplementationOnce(() => new Promise((_r, no) => { rechazar = no; }));
    const { result } = renderHook(() => useAutenticacion(servidor)); let anterior!: Promise<void>;
    act(() => { anterior = result.current.iniciar('A', 'clave'); });
    await act(async () => { await Promise.resolve(); });
    servidor.iniciar.mockResolvedValue({ ...sesion(), tokenAcceso: 'token-B', usuarioId: 'B', personaNaturalId: 'PB', nombreUsuario: 'B' });
    servidor.actual.mockResolvedValue({ usuarioId: 'B', personaNaturalId: 'PB', nombreUsuario: 'B' });
    await act(async () => { await result.current.iniciar('B', 'clave'); });
    await act(async () => { rechazar(new ErrorAutenticacion(401)); await anterior; });
    expect(result.current.sesion?.usuarioId).toBe('B');
  });
  it('una verificación tardía no revive la sesión expirada', async () => {
    vi.useFakeTimers(); const servidor = api(); const vista = renderHook(() => useAutenticacion(servidor));
    try {
      await act(async () => { await vista.result.current.iniciar('cuenta', 'clave'); });
      let resolver!: (i: typeof identidad) => void;
      servidor.actual.mockImplementationOnce(() => new Promise(r => { resolver = r; }));
      let pendiente!: Promise<void>; act(() => { pendiente = vista.result.current.verificar(); });
      act(() => { vi.advanceTimersByTime(61000); });
      await act(async () => { resolver(identidad); await pendiente; });
      expect(vista.result.current.sesion).toBeNull();
    } finally { vista.unmount(); vi.useRealTimers(); }
  });
});
