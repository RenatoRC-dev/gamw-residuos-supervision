import { describe, expect, it, vi } from 'vitest';
import { autenticacionApi, ErrorAutenticacion } from './autenticacion';

const identidad = { usuarioId: 'usuario-sintetico', personaNaturalId: 'persona-sintetica', nombreUsuario: 'cuenta.sintetica' };
const sesion = () => ({ ...identidad, tokenAcceso: 'token-sintetico', expiraEn: new Date(Date.now() + 60000).toISOString() });
describe('CU-01 contrato web', () => {
  it('envía solo credenciales; no usa cabecera técnica, cookies, Bearer ni redirecciones durante login', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => sesion() }); vi.stubGlobal('fetch', fetch);
    await autenticacionApi.iniciar('cuenta.sintetica', 'clave-sintetica', new AbortController().signal);
    const [url, opciones] = fetch.mock.calls[0];
    expect(url).toBe('/api/autenticacion/login');
    expect(opciones.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(JSON.parse(opciones.body)).toEqual({ nombreUsuario: 'cuenta.sintetica', contrasena: 'clave-sintetica' });
    expect(opciones).toMatchObject({ credentials: 'omit', redirect: 'error', cache: 'no-store' });
  });
  it('consulta identidad con Bearer exclusivamente', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => identidad }); vi.stubGlobal('fetch', fetch);
    expect(await autenticacionApi.actual('token-sintetico', new AbortController().signal)).toEqual(identidad);
    expect(fetch.mock.calls[0][1].headers).toEqual({ Authorization: 'Bearer token-sintetico' });
    expect(fetch.mock.calls[0][1].body).toBeUndefined();
  });
  it('rechaza token ausente, expiración inválida o vencida e identidad incompleta', async () => {
    for (const cambio of [{ tokenAcceso: '' }, { expiraEn: 'invalido' }, { expiraEn: '2000-01-01T00:00:00Z' }, { personaNaturalId: null }]) {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ...sesion(), ...cambio }) }));
      await expect(autenticacionApi.iniciar('cuenta', 'clave', new AbortController().signal)).rejects.toThrow();
    }
  });
  it('no propaga detalles sensibles del error del servidor', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({ detail: 'clave-sintetica-no-exponer' }) }));
    await expect(autenticacionApi.iniciar('cuenta', 'clave', new AbortController().signal)).rejects.toEqual(new ErrorAutenticacion(401));
  });
});
