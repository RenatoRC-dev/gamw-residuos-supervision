import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { elegirJornadaPaginada } from './paginacion.mjs';

// Únicamente vía InspeccionCampoNavegadorIT. CU-20 (A3): registro de inspección en la interfaz real con JWT. Fecha futura rechazada y
// corregible, 503 sin falso éxito con reintento de la MISMA solicitud, una sola inspección, sin resultado de fiscalización; un lector con
// solo FISCALIZACION_CONSULTAR ve la inspección sin formulario. Fotografías deshabilitadas (PENDING-09). Datos sintéticos.
test('CU-20: A3 registra una inspección (rechazo, 503 y reintento) y el lector la consulta sin escribir', { timeout: 170000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.inspector?.startsWith('fc-ins-') && d.lector?.startsWith('fc-lec-') && d.clave && d.jornada && d.fechaFutura && d.fechaValida && d.observacion);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [], cuerpos = [], estados = [];
  page.on('pageerror', x => errores.push(`${x.name}: ${x.message}`));
  const ruta = `/api/fiscalizacion/jornadas/${d.jornada}/inspecciones`;
  page.on('request', r => { if (r.method() === 'POST' && r.url().endsWith(ruta)) cuerpos.push(r.postData() ?? ''); });
  page.on('response', r => { if (r.request().method() === 'POST' && r.url().endsWith(ruta)) estados.push(r.status()); });
  const solicitud = c => /"solicitudId":"([0-9a-f-]{36})"/.exec(c)?.[1];
  const ingresar = async nombre => {
    await page.goto(new URL('/acceso', base).href);
    await page.getByLabel('Nombre de usuario', { exact: true }).fill(nombre);
    await page.getByLabel('Contraseña', { exact: true }).fill(d.clave);
    const login = page.waitForResponse(r => r.url().endsWith('/api/autenticacion/login'));
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    assert.equal((await login).status(), 200);
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
  };
  const abrir = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Registrar inspección y fiscalizar jornada' }).click();
    await elegirJornadaPaginada(page, 'Jornada', 'Página siguiente', d.jornada);
    await page.getByRole('heading', { name: 'Inspecciones conservadas', exact: true }).waitFor();
  };
  const alerta = async texto => {
    try { await page.locator('[role=alert]').filter({ hasText: texto }).first().waitFor({ timeout: 15000 }); }
    catch (e) { console.log(`DIAG: ${JSON.stringify(await page.locator('[role=status],[role=alert]').allTextContents())}`); throw e; }
  };
  const articulos = () => page.locator('article').filter({ hasText: d.observacion });
  try {
    // ================= Inspector (INSPECCION_REGISTRAR + FISCALIZACION_CONSULTAR) =================
    await ingresar(d.inspector);
    await abrir();
    const formulario = page.getByRole('form', { name: 'Registrar inspección de campo' });
    await formulario.waitFor();
    await page.getByText('Fotografías deshabilitadas: política pendiente.', { exact: false }).waitFor();
    assert.equal(await formulario.locator('input[type=file]').count(), 0, 'Sin selector de archivos.');
    const confirmar = formulario.getByRole('button', { name: 'Confirmar registro de inspección' });
    // Fecha futura (Bolivia): 400, sin registro ni confirmación; los datos siguen editables.
    await page.getByLabel('Fecha/hora de inspección').fill(d.fechaFutura);
    await page.getByLabel('Observación original').fill(d.observacion);
    await page.getByLabel('Latitud opcional').fill('20.25');
    await page.getByLabel('Longitud opcional').fill('-10.75');
    await confirmar.click();
    await alerta('No se pudo completar la operación.');
    assert.deepEqual(estados, [400]);
    assert.equal(await articulos().count(), 0, 'Ninguna inspección con fecha futura.');
    assert.equal(await page.getByText('Registro confirmado por el servidor.', { exact: false }).count(), 0);
    // Fecha corregida, pero el servidor responde 503: sin falso éxito.
    await page.getByLabel('Fecha/hora de inspección').fill(d.fechaValida);
    let fallar = true;
    await page.route(`**${ruta}`, r => fallar
      ? r.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"status":503}' }) : r.continue());
    await confirmar.click();
    await page.waitForFunction(() => document.querySelector('form[aria-label="Registrar inspección de campo"] button:not([disabled])'));
    assert.equal(cuerpos.length, 2);
    assert.equal(await articulos().count(), 0, 'Sin inspección mostrada ante 503.');
    assert.equal(await page.getByText('Registro confirmado por el servidor.', { exact: false }).count(), 0, 'Sin confirmación ante 503.');
    await alerta('No se pudo completar la operación.');
    // Reintento: la misma solicitud; una sola inspección confirmada.
    fallar = false;
    await confirmar.click();
    await page.getByText('Registro confirmado por el servidor. Se conserva como antecedente histórico.').waitFor();
    await articulos().first().waitFor();
    assert.equal(await articulos().count(), 1);
    const articulo = await articulos().first().innerText();
    assert.ok(articulo.includes(`Inspector ${d.inspectorId}`) && articulo.includes('Coordenadas declaradas: -10.75, 20.25'), articulo);
    assert.equal(cuerpos.length, 3);
    assert.ok(solicitud(cuerpos[0]) && new Set(cuerpos.map(solicitud)).size === 1, 'Ningún intento persistió antes: se reutiliza la misma solicitud.');
    assert.equal(await page.getByRole('heading', { name: 'Resultado humano registrado' }).count(), 0, 'La inspección no crea resultado.');
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await page.getByRole('button', { name: 'Ingresar', exact: true }).waitFor();

    // ================= Lector (solo FISCALIZACION_CONSULTAR) =================
    await ingresar(d.lector);
    await abrir();
    await articulos().first().waitFor();
    assert.equal(await articulos().count(), 1);
    await page.getByText('Sin permiso de registro de inspección. La consulta no concede facultad de escritura.', { exact: true }).waitFor();
    assert.equal(await page.getByRole('form', { name: 'Registrar inspección de campo' }).count(), 0);
    assert.deepEqual((await page.locator('[role=alert]').allTextContents()).filter(t => t.trim()), [], 'El lector no ve errores.');

    assert.equal(cuerpos.length, 3, 'El lector no envía nada.');
    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } finally {
    await browser.close();
  }
});
