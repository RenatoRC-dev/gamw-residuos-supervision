import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía FlujoMunicipalNavegadorIT. CU-26 (A3): registro de un reclamo recibido por canal institucional, por la interfaz real con JWT y
// permiso RECLAMO_REGISTRAR. Sin anexos, sin catálogo oficial de canales, sin validación (CU-27) ni portal ciudadano (CU-25).
test('CU-26: A3 registra reclamos (sin y con jornada) en el navegador, idempotente, sin validación ni imputación', { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const { clave, jornada, registrador, marca } = {
    clave: process.env.GAMW_WEB_TEST_PASSWORD, jornada: process.env.GAMW_WEB_TEST_JORNADA,
    registrador: process.env.GAMW_WEB_TEST_REGISTRADOR_RECLAMO, marca: process.env.GAMW_WEB_TEST_MARCA_RECLAMO,
  };
  assert.ok(registrador?.startsWith('flujo-rec-') && clave && jornada && marca);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', e => errores.push(e.name));
  const cuerpos = [];
  page.on('request', r => { if (r.method() === 'POST' && r.url().endsWith('/api/reclamos-institucionales')) cuerpos.push(r.postData() ?? ''); });
  const solicitud = c => /"solicitudId":"([0-9a-f-]{36})"/.exec(c)?.[1];
  const bolivia = minutos => new Date(Date.now() - 4 * 3600e3 - minutos * 60e3).toISOString().slice(0, 19);
  try {
    await page.goto(new URL('/acceso', base).href);
    await page.getByLabel('Nombre de usuario', { exact: true }).fill(registrador);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    const login = page.waitForResponse(r => r.url().endsWith('/api/autenticacion/login'));
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    assert.equal((await login).status(), 200);
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Gestionar reclamos institucionales y puntos críticos' }).click();
    const formulario = page.locator('form').filter({ has: page.getByRole('heading', { name: 'Registrar reclamo recibido' }) });
    await formulario.waitFor();
    // La interfaz no ofrece validar ni revisar con este permiso.
    assert.equal(await page.getByRole('heading', { name: 'Agregar revisión humana' }).count(), 0);
    // Causa raíz verificada: el mapa debe estar contenido (hoja de estilo cargada); si no, se superpone al botón de envío.
    await page.locator('.maplibregl-map').first().waitFor(); // la página tiene más de un mapa; se verifican todos
    const cajas = await page.locator('.maplibregl-map').evaluateAll(ns => ns.map(n => { const s = getComputedStyle(n); return `${s.position}/${s.overflow}`; }));
    assert.ok(cajas.length >= 1 && cajas.every(c => c === 'relative/hidden'), `Cada mapa queda contenido en su caja: ${cajas}`);
    const boton = formulario.getByRole('button', { name: /^(Registrar|Reintentar misma solicitud)$/ });
    // Si el botón no es accionable, se imprime su estado real (DIAG) antes de fallar: diagnóstico sin ocultar el fallo.
    const enviar = () => ({ click: async () => {
      try { await boton.click({ timeout: 15000 }); }
      catch (e) {
        const estado = await formulario.evaluate(f => ({ botones: [...f.querySelectorAll('button')].map(b => [b.textContent, b.disabled]),
          fieldset: f.querySelector('fieldset')?.disabled, alerta: f.querySelector('[role=alert]')?.textContent })).catch(() => 'sin formulario');
        console.log(`DIAG click del formulario: ${JSON.stringify(estado)}`);
        throw e;
      }
    }, waitFor: () => boton.waitFor() });

    // Datos obligatorios: el formulario vacío no emite petición.
    await enviar().click();
    assert.equal(cuerpos.length, 0);
    assert.equal(await formulario.evaluate(f => f.checkValidity()), false);

    // ---- Reclamo A: SIN jornada. Primer envío con fallo de transporte; el reintento reutiliza la misma solicitud ----
    await formulario.getByLabel('Canal declarado (no catálogo oficial)').fill('Teléfono institucional (declarado)');
    await formulario.getByLabel('Recepción original (Bolivia UTC−04:00)').fill(bolivia(60));
    await formulario.getByLabel('Descripción recibida').fill(`Vecino informa acumulación de residuos ${marca} A`);
    await formulario.getByLabel('Lugar descrito (opcional)').fill('Calle sintética 1');
    await page.route('**/api/reclamos-institucionales', route => route.abort('failed'), { times: 1 });
    const intento = page.waitForRequest(r => r.method() === 'POST' && r.url().endsWith('/api/reclamos-institucionales'));
    await enviar().click();
    await intento;
    await formulario.getByRole('button', { name: 'Reintentar misma solicitud' }).waitFor();
    await enviar().click();
    await page.getByText('Registro confirmado; origen e historia conservados.').waitFor();
    assert.equal(cuerpos.length, 2, 'Un envío fallido y un reintento.');
    assert.ok(solicitud(cuerpos[0]) && solicitud(cuerpos[0]) === solicitud(cuerpos[1]), 'El reintento reenvía la misma solicitud.');
    await page.getByRole('heading', { name: /^Reclamo [0-9a-f-]{36}$/ }).waitFor();
    await page.getByText('jornada original No identificada', { exact: false }).waitFor();
    await page.getByText('Sin validación municipal registrada; consulte las valoraciones disponibles.').waitFor(); // detalle: no hay validación
    assert.equal(await page.getByText('Pertinencia validada municipalmente', { exact: false }).count(), 0, 'Registrar no valida el reclamo.');

    // ---- Reclamo B: CON jornada. Sin origen documentado o con jornada inexistente: la interfaz informa y no registra ----
    const antes = cuerpos.length;
    await formulario.getByLabel('Canal declarado (no catálogo oficial)').fill('WhatsApp institucional (declarado)');
    await formulario.getByLabel('Recepción original (Bolivia UTC−04:00)').fill(bolivia(30));
    await formulario.getByLabel('Descripción recibida').fill(`Reclamo con jornada comprobable ${marca} B`);
    await formulario.getByLabel('Jornada comprobable (UUID opcional)').fill(jornada);
    await enviar().click(); // sin referencias de origen
    await formulario.getByText('Revise los campos. La solicitud inválida no se confirma.').waitFor();
    assert.equal(cuerpos.length, antes + 1);
    await formulario.getByLabel('Jornada comprobable (UUID opcional)').fill('00000000-0000-4000-8000-0000000000ff');
    await formulario.getByLabel('Referencias de origen y sustento de la relación, si existe').fill('Registro de llamada sintético nº 456');
    await enviar().click(); // jornada inexistente
    await formulario.getByText('Revise los campos. La solicitud inválida no se confirma.').waitFor();
    assert.equal(cuerpos.length, antes + 2);
    await formulario.getByLabel('Jornada comprobable (UUID opcional)').fill(jornada);
    await enviar().click();
    await page.getByText('Registro confirmado; origen e historia conservados.').waitFor();
    assert.equal(cuerpos.length, antes + 3);
    await page.getByText(`jornada original ${jornada}`, { exact: false }).waitFor();

    // ---- CU-29: solo el reclamo con jornada válida cuenta en el historial de esa jornada ----
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Consultar historial institucional del servicio' }).click();
    await page.getByLabel('Jornada (ID)').fill(jornada);
    const filtrada = page.waitForResponse(r => r.url().includes('/api/historial-servicio/jornadas?') && r.url().includes(`jornadaId=${jornada}`));
    await page.getByRole('button', { name: 'Buscar', exact: true }).click();
    assert.equal((await filtrada).status(), 200);
    await page.waitForFunction(() => document.querySelectorAll('table tbody tr').length === 1);
    await page.locator('table tbody tr').getByRole('button', { name: 'Ver detalle' }).click();
    await page.getByText(/identidad histórica \(ID\)/).waitFor();
    const reclamos = page.locator('li').filter({ hasText: /^Reclamos vinculados/ });
    assert.equal(await reclamos.count(), 1);
    await reclamos.getByText('Con registros (1)').waitFor();

    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } finally {
    await browser.close();
  }
});
