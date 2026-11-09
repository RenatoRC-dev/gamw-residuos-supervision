import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía AutenticacionWebIT: Vite/HTTP y PostgreSQL nuevos, sin datos existentes.
test('CU-01: autenticación web real, credenciales inválidas y APIs cerradas', { timeout: 90000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  assert.ok(base.port && !['8080', '5173', '5433'].includes(base.port));
  const usuario = process.env.GAMW_WEB_TEST_USER;
  const inactivo = process.env.GAMW_WEB_TEST_INACTIVE;
  const clave = process.env.GAMW_WEB_TEST_PASSWORD;
  assert.ok(usuario?.startsWith('auto-web-') && inactivo?.startsWith('auto-web-') && clave);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', e => errores.push(e.name));
  const cabeceras = [];
  page.on('request', r => {
    if (r.url().includes('/api/autenticacion/')) {
      const h = r.headers();
      cabeceras.push({ login: r.url().endsWith('/login'), bearer: !!h.authorization, desarrollo: !!h['x-gamw-development'] });
    }
  });
  async function ingresar(nombre, contrasena, estado) {
    await page.getByLabel('Nombre de usuario', { exact: true }).fill(nombre);
    await page.getByLabel('Contraseña', { exact: true }).fill(contrasena);
    const esperar = page.waitForResponse(r => r.url().endsWith('/api/autenticacion/login') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    const r = await esperar;
    assert.equal(r.status(), estado, 'Estado HTTP de autenticación');
    return estado === 200 ? r.json() : null;
  }
  try {
    await page.goto(new URL('/acceso', base).href);
    await ingresar(usuario, 'incorrecta-sintetica', 401);
    await page.getByRole('status').filter({ hasText: 'Credenciales inválidas.' }).waitFor();
    assert.equal(await page.getByLabel('Contraseña', { exact: true }).inputValue(), '');
    await ingresar(inactivo, clave, 401);
    await page.getByRole('status').filter({ hasText: 'Credenciales inválidas.' }).waitFor();
    const sesion = await ingresar(usuario, clave, 200);
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
    assert.equal(await page.getByText(usuario, { exact: true }).count(), 1);
    const html = await page.content();
    assert.ok(!html.includes(clave) && !html.includes(sesion.tokenAcceso), 'No renderizar contraseña o JWT.');
    const actual = page.waitForResponse(r => r.url().endsWith('/api/autenticacion/actual'));
    await page.getByRole('button', { name: 'Verificar identidad' }).click();
    assert.equal((await actual).status(), 200);
    await page.getByRole('status').filter({ hasText: 'Identidad verificada con el servidor.' }).waitFor();
    for (const ruta of ['/api/personas', '/api/empresas-operadoras', '/api/vehiculos', '/api/rutas', '/api/jornadas']) {
      const r = await page.request.get(new URL(ruta, base).href, { headers: { Authorization: `Bearer ${sesion.tokenAcceso}` } });
      assert.equal(r.status(), 403, 'La autenticación no concede acceso global de negocio.');
    }
    const falso = await page.request.get(new URL('/api/autenticacion/actual', base).href,
      { headers: { 'X-GAMW-Development': 'gamw-desarrollo' } });
    assert.equal(falso.status(), 401);
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
    await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
    await page.getByRole('form', { name: 'Inicio de sesión' }).waitFor();
    assert.equal(await page.getByLabel('Contraseña', { exact: true }).inputValue(), '');
    await ingresar(usuario, clave, 200);
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
    await page.reload();
    await page.getByRole('form', { name: 'Inicio de sesión' }).waitFor();
    assert.equal(await page.getByRole('heading', { name: 'Usuario autenticado' }).count(), 0);
    assert.ok(cabeceras.length >= 5 && cabeceras.every(h => !h.desarrollo && h.bearer === !h.login));
    assert.deepEqual(errores, []);
  } finally { await contexto.close(); await browser.close(); }
});
