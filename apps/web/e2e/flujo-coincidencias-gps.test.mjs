import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía CoincidenciasEspacialesNavegadorIT. CU-18 (A3): coincidencias espaciales de muestras GPS con puntos de control (radio explícito) y geocercas,
// con JWT y permiso municipal GPS_COINCIDENCIAS_REVISAR. Una coincidencia observada NO confirma recolección, presencia del camión ni cumplimiento.
test('CU-18: A3 revisa coincidencias espaciales (observada, sin coincidencia, insuficiente, incierta) y A4 queda sin acceso', { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.revisor?.startsWith('coi-rev-') && d.operador?.startsWith('coi-op-') && d.sinPermiso?.startsWith('coi-sin-') && d.clave);
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
    await page.getByRole('link', { name: 'Revisar evidencia GPS' }).click();
    await page.getByRole('heading', { name: 'Revisión técnica de evidencia GPS' }).waitFor();
  };
  const revisar = async jornada => {
    await page.getByLabel('Jornada UUID').fill(jornada);
    const respuesta = page.waitForResponse(r => r.url().includes('/api/revision-gps/') && r.url().includes('/coincidencias'));
    await page.getByRole('button', { name: 'Revisar muestras recibidas' }).click();
    return (await respuesta).status();
  };
  const items = () => page.locator('section[aria-label="Resultado técnico de revisión"] li');
  try {
    await page.goto(new URL('/acceso', base).href);
    await ingresar(d.revisor);
    await abrir();
    await page.getByLabel('Revisión').selectOption('coincidencias');
    await page.getByLabel('Precisión reportada máxima (m)').fill('50'); // umbral provisional explícito

    // ---- Jornada con puntos de radio explícito, geocerca, versiones históricas y ventanas de vigencia ----
    assert.equal(await revisar(d.j.principal), 200);
    await page.getByRole('heading', { name: 'REVISION_TECNICA' }).waitFor();
    await page.getByText('Muestras recibidas 5; analizadas 5.', { exact: false }).waitFor();
    await items().first().waitFor();
    assert.equal(await items().count(), d.esperadas.length, 'Solo controles de la RutaVersion de la jornada y con vigencia alcanzada.');
    for (const e of d.esperadas) {
      const texto = `${e.nombre} v${e.version} · ${e.estado} · ${e.observadas} coincidencias entre ${e.muestras} muestras temporalmente aplicables.`;
      assert.equal(await items().filter({ hasText: texto }).count(), 1, `Control ${e.nombre} v${e.version}: ${e.estado}`);
    }
    // Ningún control de otra RutaVersion ni futuro.
    for (const n of d.excluidas) assert.equal(await page.getByText(n, { exact: false }).count(), 0, `${n} no aplica.`);
    // Trazabilidad de captura: primera/última coincidencia con sus marcas originales; radio explícito o geocerca sin radio.
    const a = items().filter({ hasText: `${d.nombreA} v1 ·` });
    await a.getByText(`Primera: ${d.primeraA} · Última: ${d.ultimaA}`, { exact: false }).waitFor();
    await a.getByText('Radio explícito: 100.0 m.', { exact: false }).waitFor();
    await items().filter({ hasText: `${d.nombreD} v1 ·` }).getByText('Geocerca poligonal sin radio.', { exact: false }).waitFor();
    await page.getByText('Solo muestras y vigencia coincidentes con RutaVersion; precisión y huecos limitan interpretación.', { exact: false }).first().waitFor();
    await page.getByText('no certifica integridad, presencia del camión ni cumplimiento', { exact: false }).waitFor();
    const cuerpo = await page.locator('main').innerText();
    assert.ok(!/recolección confirmada|presencia confirmada|\bcumple\b|\bincumple\b/i.test(cuerpo), 'Una coincidencia espacial no se convierte en recolección, presencia ni cumplimiento.');

    // ---- Cero muestras con referencias: todo insuficiente ----
    assert.equal(await revisar(d.j.cero), 200);
    await page.getByRole('heading', { name: 'EVIDENCIA_INSUFICIENTE_SIN_MUESTRAS_APLICABLES' }).waitFor(); // hay referencias, faltan muestras
    await page.getByText('Muestras recibidas 0; analizadas 0.', { exact: false }).waitFor();
    await items().first().waitFor();
    assert.equal(await items().filter({ hasText: '· EVIDENCIA_INSUFICIENTE ·' }).count(), d.esperadas.length);

    // ---- Cero referencias aplicables ----
    assert.equal(await revisar(d.j.sinReferencias), 200);
    await page.getByRole('heading', { name: 'EVIDENCIA_INSUFICIENTE_SIN_REFERENCIAS_APLICABLES' }).waitFor();
    await page.getByText('Sin referencias aplicables disponibles; no equivale a no haber pasado.').waitFor();
    assert.equal(await items().count(), 0);

    // ---- Límite de cómputo: 101 controles → 100 mostrados y resultado parcial explícito ----
    assert.equal(await revisar(d.j.limite), 200);
    await page.getByRole('heading', { name: 'REVISION_PARCIAL' }).waitFor();
    await page.locator('tbody tr').filter({ hasText: 'LIMITE_REFERENCIAS' }).getByText('Se muestran las primeras 100 versiones; no afirmar revisión total.').waitFor();
    assert.equal(await items().count(), 100);

    // ---- Jornada inexistente ----
    assert.equal(await revisar('00000000-0000-4000-8000-0000000000ff'), 404);
    await page.getByRole('alert').filter({ hasText: /\S/ }).waitFor();
    assert.equal(await page.locator('section[aria-label="Resultado técnico de revisión"]').count(), 0);
    assert.deepEqual(mutaciones, [], 'Solo lecturas.');

    // ================= A4 y cuenta sin permiso =================
    for (const nombre of [d.operador, d.sinPermiso]) {
      await page.getByRole('link', { name: 'Inicio', exact: true }).click();
      await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
      await page.getByRole('button', { name: 'Cerrar sesión' }).click();
      await ingresar(nombre);
      await abrir();
      await page.getByLabel('Revisión').selectOption('coincidencias');
      assert.equal(await revisar(d.j.principal), 403, `${nombre} sin acceso.`);
      await page.getByRole('alert').filter({ hasText: /\S/ }).waitFor();
      assert.equal(await page.locator('section[aria-label="Resultado técnico de revisión"]').count(), 0);
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
