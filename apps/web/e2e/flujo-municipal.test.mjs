import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía FlujoMunicipalNavegadorIT: navegador real, Vite y backend/PostgreSQL nuevos, REST real y datos sintéticos.
// El conductor (A5) ya ejecutó la jornada por la API real: INICIO, GPS desordenado con un hueco temporal, reintento duplicado y FIN.
test('Flujo municipal: trayectoria, expediente e historial con permisos separados y sin dictamen automático', { timeout: 150000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  assert.ok(base.port && !['8080', '5173', '5433'].includes(base.port));
  const { fiscal, supervisor, clave, jornada } = {
    fiscal: process.env.GAMW_WEB_TEST_FISCAL, supervisor: process.env.GAMW_WEB_TEST_SUPERVISOR,
    clave: process.env.GAMW_WEB_TEST_PASSWORD, jornada: process.env.GAMW_WEB_TEST_JORNADA,
  };
  assert.ok(fiscal?.startsWith('flujo-') && supervisor?.startsWith('flujo-') && clave && jornada);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', e => errores.push(e.name));
  // La sesión vive solo en memoria: se navega con enlaces de la propia aplicación (sin recargar).
  const irInicio = async () => { await page.getByRole('link', { name: 'Inicio', exact: true }).click(); };
  const ingresar = async (nombre) => {
    await page.goto(new URL('/acceso', base).href);
    await page.getByLabel('Nombre de usuario', { exact: true }).fill(nombre);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    const respuesta = page.waitForResponse(r => r.url().endsWith('/api/autenticacion/login'));
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    assert.equal((await respuesta).status(), 200);
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
  };
  const salir = async () => {
    await irInicio();
    await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await page.getByRole('button', { name: 'Ingresar', exact: true }).waitFor();
  };
  try {
    // ---------- Fiscalizador: trayectoria ----------
    await ingresar(fiscal);
    await irInicio();
    await page.getByRole('link', { name: 'Consultar trayectoria GPS de jornada' }).click();
    await page.getByLabel('Jornada autorizada').selectOption(jornada);
    await page.getByRole('region', { name: 'Evidencia de la jornada' }).waitFor();
    const filas = page.locator('section[aria-label="Evidencia de la jornada"] table tbody tr');
    await filas.nth(2).waitFor();
    assert.equal(await filas.count(), 3, 'Tres muestras recibidas (el reintento duplicado no se duplica).');
    const capturas = await filas.evaluateAll(trs => trs.map(tr => tr.children[1].textContent));
    assert.deepEqual([...capturas].sort(), capturas, 'Orden cronológico por captura, no por recepción.');
    const gps = await filas.evaluateAll(trs => trs.map(tr => tr.children[3].textContent));
    assert.deepEqual(gps, ['-10.7, 20.1', '-10.8, 20.2', '-10.9, 20.3'], 'Coordenadas originales en orden [longitud, latitud].');
    const intervalos = new Date(capturas[2]).getTime() - new Date(capturas[1]).getTime();
    assert.ok(intervalos > 200000, 'El fixture contiene un hueco temporal real entre muestras.');
    await page.getByText('Los intervalos sin muestras no acreditan recorrido.').waitFor();
    const unir = page.getByLabel('Unir muestras de esta página con trazos inferidos');
    assert.equal(await unir.isChecked(), false, 'Por defecto no se unen muestras: no se presenta recorrido continuo.');
    await unir.check();
    await page.getByText('Los segmentos discontinuos son conexiones visuales inferidas, no recorrido físico confirmado.').waitFor();
    // ---------- Fiscalizador: expediente ----------
    await page.getByRole('link', { name: 'Consultar expediente de inspección y fiscalización' }).click();
    await page.getByRole('heading', { name: 'Ejecución registrada' }).waitFor();
    await page.getByText(/^INICIO · captura/).waitFor();
    await page.getByText(/^FIN · captura/).waitFor();
    await page.getByText('GPS recibidos: 3.', { exact: false }).waitFor();
    await page.getByText('GPS recibido: evidencia técnica, no dictamen').waitFor();
    await page.getByRole('heading', { name: /Otras referencias de la jornada/ }).waitFor();
    assert.ok(await page.getByText('Acceso no permitido (se desconoce si existen)').count() >= 3, 'Sin permiso propio no se sabe si existen incidencias, acciones o reclamos.');
    // ---------- Historial CU-29 ----------
    await irInicio();
    await page.getByRole('link', { name: 'Consultar historial institucional del servicio' }).click();
    await page.getByRole('button', { name: 'Buscar', exact: true }).click();
    await page.getByRole('button', { name: 'Ver detalle' }).first().waitFor();
    await page.getByLabel('Jornada (ID)').fill(jornada);
    await page.getByRole('button', { name: 'Buscar', exact: true }).click();
    await page.getByRole('button', { name: 'Ver detalle' }).click();
    await page.getByText(/identidad histórica \(ID\)/).waitFor();
    await page.getByText('Con registros (3)').waitFor(); // trayectoria, con su permiso propio
    await page.getByText(/^Sin registros/).first().waitFor(); // inspecciones: ausencia, no incumplimiento
    // Ninguna pantalla afirma cumplimiento o incumplimiento por GPS.
    const texto = await page.locator('main').innerText();
    assert.ok(!/\b(cumple|incumple|sanción|multa)\b/i.test(texto), 'No debe emitirse un veredicto automático.');
    await salir();

    // ---------- Supervisor sin concesiones municipales: denegaciones ----------
    await ingresar(supervisor);
    await irInicio();
    await page.getByRole('link', { name: 'Registrar inspección y fiscalizar jornada' }).click();
    await page.getByRole('alert').first().waitFor();
    assert.equal(await page.getByLabel('Jornada').locator('option').count(), 1, 'Sin jornadas visibles sin permiso.');
    await irInicio();
    await page.getByRole('link', { name: 'Consultar historial institucional del servicio' }).click();
    await page.getByRole('button', { name: 'Buscar', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: 'No tiene autorización municipal' }).waitFor();
    await irInicio();
    await page.getByRole('link', { name: 'Consultar trayectoria GPS de jornada' }).click();
    await page.getByRole('alert').first().waitFor();
    assert.equal(await page.locator('section[aria-label="Evidencia de la jornada"]').count(), 0);
    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } finally {
    await browser.close();
  }
});
