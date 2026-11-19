import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía FlujoMunicipalNavegadorIT. CU-20 (A3): registro de una inspección de campo SIN fotografías por la interfaz real con JWT y
// permiso INSPECCION_REGISTRAR. Es una observación humana: no crea resultado de fiscalización. Fotografías deshabilitadas (política pendiente).
test('CU-20: A3 registra una inspección sin fotografías en el navegador (idempotente) y la ve en expediente e historial', { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const { clave, jornada, inspector, fecha, observacion } = {
    clave: process.env.GAMW_WEB_TEST_PASSWORD, jornada: process.env.GAMW_WEB_TEST_JORNADA, inspector: process.env.GAMW_WEB_TEST_INSPECTOR,
    fecha: process.env.GAMW_WEB_TEST_FECHA_INSPECCION, observacion: process.env.GAMW_WEB_TEST_OBSERVACION_INSPECCION,
  };
  assert.ok(inspector?.startsWith('flujo-insp-') && clave && jornada && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(fecha) && observacion);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', e => errores.push(e.name));
  const cuerpos = [];
  page.on('request', r => { if (r.method() === 'POST' && r.url().endsWith(`/api/fiscalizacion/jornadas/${jornada}/inspecciones`)) cuerpos.push(r.postData() ?? ''); });
  try {
    await page.goto(new URL('/acceso', base).href);
    await page.getByLabel('Nombre de usuario', { exact: true }).fill(inspector);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    const login = page.waitForResponse(r => r.url().endsWith('/api/autenticacion/login'));
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    assert.equal((await login).status(), 200);
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
    // La sesión vive en memoria: navegación solo con enlaces de la aplicación.
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Registrar inspección y fiscalizar jornada' }).click();
    await page.getByLabel('Jornada').selectOption(jornada);
    const formulario = page.getByRole('form', { name: 'Registrar inspección de campo' });
    await formulario.waitFor();
    await page.getByText('Fotografías deshabilitadas: política pendiente.', { exact: false }).waitFor();
    assert.equal(await formulario.locator('input[type=file]').count(), 0, 'Sin selector de archivos: fotografías deshabilitadas.');

    // Datos obligatorios: el formulario vacío no envía nada.
    const enviar = formulario.getByRole('button', { name: 'Confirmar registro de inspección' });
    await enviar.click();
    assert.equal(cuerpos.length, 0);
    assert.equal(await formulario.evaluate(f => f.checkValidity()), false);
    // Coordenadas inválidas: la latitud fuera de rango impide el envío.
    await page.getByLabel('Fecha/hora de inspección').fill(fecha);
    await page.getByLabel('Observación original').fill(observacion);
    await page.getByLabel('Latitud opcional').fill('91');
    await page.getByLabel('Longitud opcional').fill('-10.75');
    await enviar.click();
    assert.equal(cuerpos.length, 0, 'Latitud 91: ninguna petición.');
    assert.equal(await formulario.evaluate(f => f.checkValidity()), false);
    await page.getByLabel('Latitud opcional').fill('20.25');
    await page.getByLabel('Lugar observado, opcional').fill('Esquina sintética CU-20');

    // Primer envío con fallo de transporte (respuesta incierta): el reintento reutiliza la MISMA solicitud (idempotencia).
    await page.route(`**/api/fiscalizacion/jornadas/${jornada}/inspecciones`, route => route.abort('failed'), { times: 1 });
    const intento = page.waitForRequest(r => r.method() === 'POST' && r.url().endsWith(`/api/fiscalizacion/jornadas/${jornada}/inspecciones`));
    await enviar.click();
    await intento; // la petición abortada ya fue emitida por el navegador
    assert.equal(cuerpos.length, 1);
    await enviar.click();
    await page.getByText('Registro confirmado por el servidor. Se conserva como antecedente histórico.').waitFor();
    assert.equal(cuerpos.length, 2, 'Un envío fallido y un reintento.');
    const solicitud = c => /"solicitudId":"([0-9a-f-]{36})"/.exec(c)?.[1];
    assert.ok(solicitud(cuerpos[0]) && solicitud(cuerpos[0]) === solicitud(cuerpos[1]), 'El reintento reenvía la misma solicitud.');

    // La inspección aparece en el expediente: observación, autor, fechas, coordenadas [longitud, latitud]; sin fotografías ni resultado.
    await page.getByRole('heading', { name: /^Inspección [0-9a-f-]{36}$/ }).waitFor();
    await page.locator('article p', { hasText: observacion }).waitFor();
    await page.getByText('Coordenadas declaradas: -10.75, 20.25').waitFor();
    await page.getByText('Sin fotografías anexas; no se deduce ausencia de inspección.').waitFor();
    assert.equal(await page.getByRole('heading', { name: 'Resultado humano registrado' }).count(), 0, 'Registrar una inspección no crea un resultado de fiscalización.');
    assert.equal(await page.getByRole('heading', { name: /^Inspección [0-9a-f-]{36}$/ }).count(), 1, 'Una sola inspección (sin duplicado).');
    const texto = await page.locator('main').innerText();
    assert.ok(!/\b(cumple|incumple)\b/i.test(texto), 'No debe emitirse un veredicto automático.');

    // Historial CU-29: la inspección se cuenta con el permiso propio.
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Consultar historial institucional del servicio' }).click();
    await page.getByLabel('Jornada (ID)').fill(jornada);
    const filtrada = page.waitForResponse(r => r.url().includes('/api/historial-servicio/jornadas?') && r.url().includes(`jornadaId=${jornada}`));
    await page.getByRole('button', { name: 'Buscar', exact: true }).click();
    assert.equal((await filtrada).status(), 200);
    await page.waitForFunction(() => document.querySelectorAll('table tbody tr').length === 1);
    await page.locator('table tbody tr').getByRole('button', { name: 'Ver detalle' }).click();
    await page.getByText(/identidad histórica \(ID\)/).waitFor(); // detalle cargado
    const inspecciones = page.locator('li').filter({ hasText: /^Inspecciones/ });
    assert.equal(await inspecciones.count(), 1);
    await inspecciones.getByText('Con registros (1)').waitFor();

    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } finally {
    await browser.close();
  }
});
