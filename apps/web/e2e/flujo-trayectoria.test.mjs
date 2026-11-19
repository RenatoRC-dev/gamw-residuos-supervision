import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía TrayectoriaMunicipalNavegadorIT. CU-16 (A3): trayectoria GPS histórica de una jornada con JWT y permiso municipal
// TRAYECTORIA_JORNADA_CONSULTAR. Los intervalos sin muestras NO acreditan recorrido; la línea inferida está apagada por defecto.
test('CU-16: A3 consulta trayectorias (0, 1, N muestras, orden por captura, paginación, línea inferida) y A4 queda sin acceso', { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.municipal?.startsWith('tra-mun-') && d.operador?.startsWith('tra-op-') && d.sinPermiso?.startsWith('tra-sin-') && d.clave);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', x => errores.push(x.name));
  const mutaciones = [];
  page.on('request', r => { if (r.method() !== 'GET' && !r.url().endsWith('/api/autenticacion/login')) mutaciones.push(`${r.method()} ${r.url()}`); });
  const ingresar = async nombre => {
    await page.getByLabel('Nombre de usuario', { exact: true }).fill(nombre);
    await page.getByLabel('Contraseña', { exact: true }).fill(d.clave);
    const login = page.waitForResponse(r => r.url().endsWith('/api/autenticacion/login'));
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    assert.equal((await login).status(), 200);
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
  };
  const abrir = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Consultar trayectoria GPS de jornada' }).click();
    await page.getByRole('heading', { name: 'Trayectoria GPS de jornada' }).waitFor();
  };
  const selector = () => page.locator('select').filter({ has: page.locator('option', { hasText: 'Seleccione una jornada' }) });
  const elegir = async id => {
    await page.waitForFunction(i => [...document.querySelectorAll('option')].some(o => o.value === i), id);
    await selector().selectOption(id);
    await page.getByText(`Jornada: ${id}`, { exact: false }).waitFor();
  };
  const filas = () => page.locator('tbody tr');
  const columna = async n => filas().evaluateAll((rs, i) => rs.map(r => r.children[i].textContent), n);
  try {
    await page.goto(new URL('/acceso', base).href);
    await ingresar(d.municipal);
    await abrir();

    // ---- Cero muestras ----
    await elegir(d.j.cero);
    await page.getByText('No hay posiciones en esta consulta. No se fabrica una ubicación ni se concluye incumplimiento.').waitFor();
    assert.equal(await filas().count(), 0);
    await page.getByText('Todas las muestras recibidas dentro del corte consultado.', { exact: false }).waitFor();
    // Mapa contenido (los marcadores WebGL no se afirman por DOM) y línea inferida apagada por defecto.
    const mapa = page.locator('.maplibregl-map').first();
    await mapa.waitFor();
    assert.deepEqual(await mapa.evaluate(n => [getComputedStyle(n).position, getComputedStyle(n).overflow]), ['relative', 'hidden']);
    const unir = page.getByLabel('Unir muestras de esta página con trazos inferidos');
    assert.equal(await unir.isChecked(), false, 'La línea inferida está apagada por defecto.');
    assert.equal(await page.getByText('conexiones visuales inferidas', { exact: false }).count(), 0);

    // ---- Una muestra ----
    await elegir(d.j.una);
    await page.getByText('Una muestra aislada no representa un recorrido continuo.').waitFor();
    assert.equal(await filas().count(), 1);

    // ---- Varias muestras: orden por CAPTURA (no por recepción), desempate por captura_id ----
    await elegir(d.j.varias);
    await page.waitForFunction(() => document.querySelectorAll('tbody tr').length === 4);
    assert.deepEqual(await columna(0), d.orden.map(p => p.capturaId), 'Orden determinista por captura.');
    assert.deepEqual(await columna(1), d.orden.map(p => p.capturadoEn));
    assert.deepEqual(await columna(2), d.orden.map(p => p.recibidoEn), 'La recepción tardía conserva su hora real.');
    assert.deepEqual(await columna(3), d.orden.map(p => `${p.lon}, ${p.lat}`), 'Longitud, latitud.');
    assert.deepEqual(await columna(4), d.orden.map(p => String(p.precision)));
    assert.deepEqual(await columna(5), d.orden.map(() => d.asignacionVarias), 'Vínculo con la asignación original.');
    assert.ok(Date.parse(d.orden[0].recibidoEn) > Date.parse(d.orden[3].recibidoEn), 'La primera por captura fue la última recibida (sincronización tardía).');
    await page.getByText(`Versión histórica: ${d.versionOriginal}.`, { exact: false }).waitFor();
    await page.getByRole('heading', { name: /· versión 1$/ }).waitFor();
    await page.getByText('Los intervalos sin muestras no acreditan recorrido.', { exact: false }).waitFor();
    // Línea inferida: solo al activarla, y rotulada.
    await unir.check();
    await page.getByText('Los segmentos discontinuos son conexiones visuales inferidas, no recorrido físico confirmado.').waitFor();
    await unir.uncheck();
    assert.equal(await page.getByText('conexiones visuales inferidas', { exact: false }).count(), 0);

    // ---- Jornada con versión posterior de ruta: cada una conserva su referencia ----
    await elegir(d.j.v2);
    await page.getByRole('heading', { name: /· versión 2$/ }).waitFor();
    await page.getByText(`Versión histórica: ${d.versionNueva}.`, { exact: false }).waitFor();

    // ---- Paginación (500 + 5) con advertencia explícita de vista parcial ----
    await elegir(d.j.paginada);
    await page.waitForFunction(() => document.querySelectorAll('tbody tr').length === 500);
    await page.getByText('Muestras 1–500.', { exact: false }).waitFor();
    await page.getByText('Vista paginada; no representa toda la trayectoria en una sola página.', { exact: false }).waitFor();
    const primera = (await columna(0))[0];
    await page.getByRole('button', { name: 'Muestras siguientes' }).click();
    await page.waitForFunction(() => document.querySelectorAll('tbody tr').length === 5);
    await page.getByText('Muestras 501–505.', { exact: false }).waitFor();
    await page.getByText('Vista paginada', { exact: false }).waitFor();
    assert.ok(!(await columna(0)).includes(primera), 'Sin repetir muestras entre páginas.');
    assert.equal(await page.getByRole('button', { name: 'Muestras siguientes' }).isDisabled(), true, 'Límite de la última página.');
    await page.getByRole('button', { name: 'Muestras anteriores' }).click();
    await page.waitForFunction(() => document.querySelectorAll('tbody tr').length === 500);
    assert.equal((await columna(0))[0], primera);

    // La consulta no editó nada ni emitió hallazgos.
    assert.deepEqual(mutaciones, [], 'Solo lecturas.');
    assert.equal(await page.getByText(/\b(cumple|incumple)\b/i).count(), 0);

    // ================= A4 (periodo): sin acceso a trayectorias (D1: no se amplía) =================
    for (const nombre of [d.operador, d.sinPermiso]) {
      await page.getByRole('link', { name: 'Inicio', exact: true }).click();
      await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
      await page.getByRole('button', { name: 'Cerrar sesión' }).click();
      await ingresar(nombre);
      await abrir();
      await page.getByRole('alert').filter({ hasText: /\S/ }).waitFor();
      assert.equal(await page.locator('option[value]:not([value=""])').count(), 0, `${nombre}: sin jornadas.`);
      assert.equal(await filas().count(), 0);
      for (const id of Object.values(d.j)) assert.equal(await page.getByText(id).count(), 0);
    }
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
