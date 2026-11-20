import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { elegirJornadaPaginada } from './paginacion.mjs';

// Únicamente vía VerificacionCorrectivaNavegadorIT. CU-23 (A3): verificación humana de la atención A4 en la interfaz real con JWT y categoría
// SINTÉTICA de prueba (PENDING-07). Un rechazo definitivo (409) libera los datos para corregirlos; un resultado incierto (503) conserva la misma
// solicitud para reintentar; nunca hay falso éxito. El lector y la empresa ven la cadena histórica sin poder verificar.
test('CU-23: A3 verifica la atención (409 corregible, 503 reintentable); lector y A4 consultan sin verificar', { timeout: 170000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.verificador?.startsWith('vc-ver-') && d.lector?.startsWith('vc-lec-') && d.operador?.startsWith('vc-op-') && d.clave && d.accion && d.respuesta && d.categoria);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [], cuerpos = [];
  page.on('pageerror', x => errores.push(`${x.name}: ${x.message}`));
  const ruta = `/api/correctivas/acciones/${d.accion}/verificaciones`;
  page.on('request', r => { if (r.method() === 'POST' && r.url().endsWith(ruta)) cuerpos.push(r.postData() ?? ''); });
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
  const salir = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await page.getByRole('button', { name: 'Ingresar', exact: true }).waitFor();
  };
  const abrirMunicipal = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Incidencias y actuaciones correctivas' }).click();
    await elegirJornadaPaginada(page, 'Jornada', 'Siguiente', d.jornada);
    await page.getByRole('heading', { name: `Acción ${d.accion}`, exact: true }).waitFor();
  };
  const accion = () => page.locator('article').filter({ has: page.getByRole('heading', { name: `Acción ${d.accion}`, exact: true }) });
  const diag = async (f, e) => {
    const estado = await f.evaluate(x => ({ botones: [...x.querySelectorAll('button')].map(b => [b.textContent, b.disabled]),
      fieldset: x.querySelector('fieldset')?.disabled })).catch(() => 'sin formulario');
    console.log(`DIAG formulario: ${JSON.stringify(estado)} · mensajes: ${JSON.stringify(await page.locator('[role=status],[role=alert]').allTextContents())}`);
    throw e;
  };
  const cadenaVisible = async () => {
    const a = accion();
    await a.getByText(d.descripcionAccion, { exact: true }).waitFor();
    await a.getByText(`Solicitante ${d.solicitanteId}`, { exact: false }).waitFor();
    await a.getByRole('heading', { name: `Atención ${d.respuesta}`, exact: true }).waitFor();
    await a.getByText(`Autor ${d.operadorId} · comunicación`, { exact: false }).waitFor();
  };
  try {
    // ================= Verificador municipal (CORRECTIVA_CONSULTAR + CORRECTIVA_VERIFICAR) =================
    await ingresar(d.verificador);
    await abrirMunicipal();
    await cadenaVisible();
    assert.equal(await accion().getByRole('heading', { name: /^Verificación humana/ }).count(), 0, 'La atención empresarial no se autoverifica.');
    const form = accion().locator('form').filter({ has: page.getByRole('heading', { name: 'Registrar verificación municipal', exact: true }) });
    await form.waitFor();
    const completar = async () => {
      try {
        await form.locator('select[name=respuesta]').selectOption(d.respuesta, { timeout: 15000 });
        await form.locator('select[name=resultado]').selectOption(d.categoria);
        await form.locator('textarea[name=observaciones]').fill(`Comprobado en visita ${d.marca}`);
        await form.getByRole('checkbox').check();
      } catch (e) { await diag(form, e); }
    };
    const enviar = async () => { try { await form.getByRole('button', { name: /^(Registrar verificación|Reintentar misma verificación)$/ }).click({ timeout: 15000 }); } catch (e) { await diag(form, e); } };
    let modo = '409';
    await page.route(`**${ruta}`, r => modo === '409' ? r.fulfill({ status: 409, contentType: 'application/problem+json', body: '{"status":409}' })
      : modo === '503' ? r.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"status":503}' }) : r.continue());
    // 1) Rechazo definitivo (409): mensaje, sin éxito, y el formulario queda editable con una solicitud nueva.
    await completar();
    await enviar();
    await page.locator('[role=alert]').filter({ hasText: 'Actuación incompatible, ya evaluada o catálogo no habilitado.' }).waitFor();
    assert.equal(await page.getByText('Registro confirmado por el servidor', { exact: false }).count(), 0);
    try { await form.getByRole('button', { name: 'Registrar verificación', exact: true }).waitFor({ timeout: 15000 }); } catch (e) { await diag(form, e); }
    assert.equal(await form.locator('fieldset').evaluate(f => f.disabled), false, 'Un rechazo definitivo no bloquea el formulario.');
    // 2) Resultado incierto (503): datos fijados y «Reintentar misma verificación».
    modo = '503';
    await completar();
    await enviar();
    try { await form.getByRole('button', { name: 'Reintentar misma verificación', exact: true }).waitFor({ timeout: 15000 }); } catch (e) { await diag(form, e); }
    assert.equal(await page.getByText('Registro confirmado por el servidor', { exact: false }).count(), 0, 'Sin falso éxito ante 503.');
    // 3) Reintento real: la misma solicitud se confirma una sola vez.
    modo = 'real';
    await enviar();
    await page.getByText('Registro confirmado por el servidor; historial anterior conservado.').waitFor();
    const verif = accion().locator('section').filter({ has: page.getByRole('heading', { name: `Verificación humana: ${d.nombreCategoria}`, exact: true }) });
    await verif.waitFor();
    await verif.getByText(`Respuesta ${d.respuesta} · autor ${d.verificadorId}`, { exact: false }).waitFor();
    await verif.getByText(`Comprobado en visita ${d.marca}`, { exact: true }).waitFor();
    assert.equal(cuerpos.length, 3);
    assert.notEqual(solicitud(cuerpos[0]), solicitud(cuerpos[1]), 'Tras un rechazo definitivo se genera una solicitud nueva.');
    assert.equal(solicitud(cuerpos[1]), solicitud(cuerpos[2]), 'Tras un resultado incierto se reintenta la misma solicitud.');
    assert.equal(await accion().locator('form').count(), 0, 'Atención ya evaluada: no se ofrece otra verificación.');
    await salir();

    // ================= Lector (solo CORRECTIVA_CONSULTAR) =================
    await ingresar(d.lector);
    await abrirMunicipal();
    await cadenaVisible();
    await accion().getByRole('heading', { name: `Verificación humana: ${d.nombreCategoria}`, exact: true }).waitFor();
    assert.equal(await page.locator('form').filter({ has: page.getByRole('heading', { name: 'Registrar verificación municipal' }) }).count(), 0);
    assert.deepEqual((await page.locator('[role=alert]').allTextContents()).filter(t => t.trim()), [], 'El lector no ve errores.');
    await salir();

    // ================= Operador de empresa (A4, concesión de su periodo) =================
    await ingresar(d.operador);
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Atender correctivas de periodos autorizados' }).click();
    const selector = page.getByLabel('Acción correctiva');
    await page.locator(`option[value="${d.accion}"]`).waitFor({ state: 'attached', timeout: 20000 });
    await selector.selectOption(d.accion);
    await page.getByRole('heading', { name: 'Verificaciones municipales registradas', exact: true }).waitFor();
    await page.getByText(`Respuesta ${d.respuesta} · fiscalizador ${d.verificadorId}`, { exact: false }).waitFor();
    assert.equal(await page.getByRole('button', { name: /Registrar verificación/ }).count(), 0, 'La empresa no verifica.');

    assert.equal(cuerpos.length, 3, 'Ni el lector ni la empresa envían verificaciones.');
    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } finally {
    await browser.close();
  }
});
