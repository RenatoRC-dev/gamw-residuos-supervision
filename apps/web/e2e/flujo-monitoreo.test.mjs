import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía MonitoreoMunicipalNavegadorIT. CU-15 (A3): última posición GPS recibida de jornadas con INICIO y sin FIN, con JWT y permiso municipal
// MONITOREO_JORNADA_CONSULTAR. GPS es evidencia: la ausencia de posición NO significa detención, omisión, fraude ni incumplimiento.
test('CU-15: A3 monitorea últimas posiciones recibidas (orden por captura, respaldo tabular, red y sesión)', { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.municipal?.startsWith('mon-mun-') && d.operador?.startsWith('mon-op-') && d.sinPermiso?.startsWith('mon-sin-') && d.clave);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', x => errores.push(x.name));
  let consultas = 0;
  page.on('request', r => { if (r.method() === 'GET' && r.url().includes('/api/monitoreo/jornadas')) consultas++; });
  const ingresar = async nombre => {
    await page.getByLabel('Nombre de usuario', { exact: true }).fill(nombre);
    await page.getByLabel('Contraseña', { exact: true }).fill(d.clave);
    const login = page.waitForResponse(r => r.url().endsWith('/api/autenticacion/login'));
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    assert.equal((await login).status(), 200);
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
  };
  const abrirMonitoreo = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Monitorear últimas posiciones' }).click();
    await page.getByRole('heading', { name: 'Monitorear posición de jornada' }).waitFor();
  };
  const fila = id => page.locator('tbody tr').filter({ hasText: id });
  try {
    // ================= A3 municipal =================
    await page.goto(new URL('/acceso', base).href);
    await ingresar(d.municipal);
    await abrirMonitoreo();
    // Solo jornadas con INICIO y sin FIN: a, t, s, n (empresa A) y b (empresa B). La cerrada y la no iniciada no aparecen.
    await page.waitForFunction(() => document.querySelectorAll('tbody tr').length === 5);
    for (const k of ['cerrada', 'sinInicio']) assert.equal(await page.getByText(d.jornadas[k]).count(), 0, `No se muestra ${k}.`);
    assert.equal(await page.getByText('Consulta periódica cada 5 s', { exact: false }).count(), 1);
    await page.getByText('No es seguimiento instantáneo.', { exact: false }).waitFor();
    // Mapa contenido y referencia histórica seleccionable; marcadores en WebGL, no en el DOM (respaldo tabular siempre disponible).
    const mapa = page.getByRole('region', { name: 'Mapa municipal de últimas posiciones' });
    await mapa.waitFor();
    assert.deepEqual(await mapa.evaluate(n => [getComputedStyle(n).position, getComputedStyle(n).overflow]), ['relative', 'hidden']);
    const referencia = page.locator('select').filter({ has: page.locator(`option[value="${d.jornadas.a}"]`) });
    assert.equal(await referencia.locator('option').count(), 6, 'Marcador + 5 jornadas.');
    await referencia.selectOption(d.jornadas.t);
    await referencia.selectOption(d.jornadas.a);

    // A: la última POR CAPTURA es la de captura más reciente aunque otra se haya RECIBIDO después (sincronización tardía).
    const a = fila(d.jornadas.a);
    await a.locator('td').filter({ hasText: d.a.cap }).filter({ hasText: d.a.rec }).waitFor(); // captura y recepción comparten celda
    await a.getByText(`${d.a.lon}, ${d.a.lat} · ${d.a.prec} m`, { exact: true }).waitFor();
    assert.equal(await a.getByText(`${d.a.tardiaLon}, ${d.a.tardiaLat}`, { exact: false }).count(), 0, 'La muestra recibida más tarde pero capturada antes no es la última.');
    assert.equal(await a.getByText('Sin lectura reciente').count(), 0);
    // T: empate de captura con desempate estable (captura_id mayor).
    await fila(d.jornadas.t).getByText(`${d.t.lon}, ${d.t.lat} · ${d.t.prec} m`, { exact: true }).waitFor();
    // S: antigua → se rotula, sin interpretarla como detención.
    await fila(d.jornadas.s).getByText('Sin lectura reciente', { exact: false }).waitFor();
    // N: sin GPS → no se fabrica posición ni se interpreta.
    const n = fila(d.jornadas.n);
    await n.getByText('Sin posiciones recibidas').waitFor();
    await n.getByText('Ubicación no disponible').waitFor();
    await n.getByText('Sin dato', { exact: true }).waitFor();
    // B: otra empresa; el monitoreo municipal es institucional.
    await fila(d.jornadas.b).getByText(`${d.b.lon}, ${d.b.lat} · ${d.b.prec} m`, { exact: true }).waitFor();
    // Ningún rótulo atribuye detención, omisión, fraude o incumplimiento a la fila.
    const cuerpo = await page.locator('tbody').innerText();
    assert.ok(!/detenid|omis|fraude|incumpl|no recolect/i.test(cuerpo), 'La tabla no interpreta la ausencia o antigüedad de GPS.');
    await a.getByRole('link', { name: 'Consultar trayectoria histórica' }).waitFor();
    assert.ok((await a.getByRole('link', { name: 'Consultar trayectoria histórica' }).getAttribute('href')).includes(`jornada=${d.jornadas.a}`));

    // Consulta periódica real (5 s): vuelve a pedir sin acción del usuario y no inventa posiciones.
    const antes = consultas;
    await page.waitForRequest(r => r.method() === 'GET' && r.url().includes('/api/monitoreo/jornadas'), { timeout: 15000 });
    assert.ok(consultas > antes);
    await n.getByText('Sin posiciones recibidas').waitFor();

    // Red no disponible: se conserva la última evidencia recibida, sin presentarla como ubicación actual.
    await contexto.setOffline(true);
    await page.getByRole('button', { name: 'Actualizar ahora' }).click();
    await page.getByText('Sin actualización de red. Se conserva la última evidencia recibida, no una ubicación actual garantizada.').waitFor();
    assert.equal(await page.locator('tbody tr').count(), 5, 'La evidencia conservada sigue visible.');
    await contexto.setOffline(false);
    await page.getByRole('button', { name: 'Actualizar ahora' }).click();
    await page.getByText('Sin actualización de red.', { exact: false }).waitFor({ state: 'detached' });

    // Sesión vencida (401 simulado en la próxima consulta): se cierra la sesión y no queda evidencia en pantalla.
    await page.route('**/api/monitoreo/jornadas*', route => route.fulfill({ status: 401, contentType: 'application/json', body: '{"mensaje":"Token vencido"}' }), { times: 1 });
    await page.getByRole('button', { name: 'Actualizar ahora' }).click();
    await page.getByRole('link', { name: 'Autentíquese para consultar.' }).waitFor();
    assert.equal(await page.locator('tbody tr').count(), 0);
    assert.equal(await page.getByText(d.jornadas.a).count(), 0);

    // ================= Operador A4 (periodo): sin permiso de monitoreo (D1: no se amplía) =================
    await page.getByRole('link', { name: 'Autentíquese para consultar.' }).click();
    await ingresar(d.operador);
    await abrirMonitoreo();
    await page.getByRole('alert').filter({ hasText: /\S/ }).waitFor();
    assert.equal(await page.locator('tbody tr').count(), 0, 'Sin filas para A4.');
    for (const id of Object.values(d.jornadas)) assert.equal(await page.getByText(id).count(), 0, 'Sin datos de otras empresas.');
    // ================= Cuenta sin permiso =================
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await ingresar(d.sinPermiso);
    await abrirMonitoreo();
    await page.getByRole('alert').filter({ hasText: /\S/ }).waitFor();
    assert.equal(await page.locator('tbody tr').count(), 0);
    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } catch (fallo) {
    console.log(`DIAG fallo: ${String(fallo?.message ?? fallo).split('\n')[0].slice(0, 300)}`);
    console.log(`DIAG url: ${page.url()}`);
    console.log(`DIAG texto: ${(await page.locator('main').innerText().catch(() => 'sin main')).replace(/\s+/g, ' ').slice(0, 700)}`);
    throw fallo;
  } finally {
    await browser.close();
  }
});
