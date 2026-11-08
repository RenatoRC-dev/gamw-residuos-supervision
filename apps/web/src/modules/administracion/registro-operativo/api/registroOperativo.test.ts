import { describe, expect, it, vi } from 'vitest';
import { apiRegistroOperativo as api } from './registroOperativo';

describe('contratos del registro operativo', () => {
  it('conserva el CI como texto y los apellidos opcionales', async () => {
    const datos = { ci: '00123456-1A', nombres: 'Prueba', apellidoPaterno: null, apellidoMaterno: null, telefono: null };
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => datos });
    vi.stubGlobal('fetch', fetch);
    expect((await api.crearPersona(datos)).ci).toBe('00123456-1A');
    expect(fetch.mock.calls[0][0]).toBe('/api/personas');
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual(datos);
    expect(fetch.mock.calls[0][1].headers['X-GAMW-Development']).toBe('gamw-desarrollo');
  });
  it('cierra un vínculo sin enviar identidad personal ni borrar registros', async () => {
    const datos = { fechaInicio: '2026-01-01', fechaFin: '2026-02-01', activo: false };
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => datos });
    vi.stubGlobal('fetch', fetch);
    await api.actualizarVinculo('empresa', 'vinculo', datos);
    expect(fetch.mock.calls[0][0]).toBe('/api/empresas-operadoras/empresa/personal/vinculo');
    expect(fetch.mock.calls[0][1].method).toBe('PUT');
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual(datos);
    expect(fetch.mock.calls[0][1].body).not.toContain('personaId');
    expect(fetch.mock.calls[0][1].body).not.toContain('funcionOperativaIds');
  });
  it('desactiva empresas con PATCH y conserva errores del servidor', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: false, status: 409, json: async () => ({ detail: 'NIT duplicado.' }) });
    vi.stubGlobal('fetch', fetch);
    await expect(api.estadoEmpresa('empresa', false)).rejects.toThrow('NIT duplicado.');
    expect(fetch.mock.calls[0][1].method).toBe('PATCH');
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ activo: false });
  });
});
