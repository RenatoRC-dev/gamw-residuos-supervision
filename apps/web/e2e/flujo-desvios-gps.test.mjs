import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía DesviosDetencionesNavegadorIT. CU-19 (A3): separaciones respecto de la RutaVersion histórica y detenciones CANDIDATAS, con JWT y permiso
// municipal GPS_DESVIOS_REVISAR. Umbrales provisionales explícitos. Nada de esto es fraude, vehículo detenido, recolección omitida, sanción ni cumplimiento.
test('CU-19: A3 revisa separaciones y detenciones candidatas sin dictaminar; A4 queda sin acceso', { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.revisor?.startsWith('des-rev-') && d.operador?.startsWith('des-op-') && d.sinPermiso?.startsWith('des-sin-') && d.clave);
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
    await page.getByLabel('Revisión').selectOption('desvios');
  };
  const revisar = async jornada => {
    await page.getByLabel('Jornada UUID').fill(jornada);
    const respuesta = page.waitForResponse(r => r.url().includes('/api/revision-gps/') && r.url().includes('/desvios'));
    await page.getByRole('button', { name: 'Revisar muestras recibidas' }).click();
    return (await respuesta).status();
  };
  const tipos = () => page.locator('tbody tr').evaluateAll(rs => rs.map(r => r.children[0].textContent).sort());
  try {
    await page.goto(new URL('/acceso', base).href);
    await ingresar(d.revisor);
    await abrir();
    // Umbrales provisionales explícitos de la prueba.
    for (const [etiqueta, valor] of [['Separación temporal (s)', '120'], ['Precisión reportada máxima (m)', '50'], ['Separación de ruta (m)', '100'],
      ['Radio técnico de agrupación (m)', '25'], ['Duración candidata de detención (s)', '180']]) await page.getByLabel(etiqueta).fill(valor);

    // ---- Jornada con separaciones, una detención candidata y falsos candidatos rechazados ----
    assert.equal(await revisar(d.j.principal), 200);
    await page.getByRole('heading', { name: 'REVISION_TECNICA' }).waitFor();
    await page.locator('tbody tr').first().waitFor();
    assert.deepEqual(await tipos(), d.esperados, 'Solo las señales esperadas: sin detenciones por huecos, duplicados, baja precisión, dispersión o poco tiempo.');
    for (const e of await page.locator('tbody tr').evaluateAll(rs => rs.map(r => r.children[1].textContent))) assert.equal(e, 'POR_REVISAR');
    const fila = t => page.locator('tbody tr').filter({ has: page.locator('td:first-child', { hasText: new RegExp(`^${t}$`) }) });
    const detencion = fila('DETENCION_CANDIDATA');
    await detencion.getByText('200 s', { exact: false }).waitFor();
    await detencion.getByText(`${d.detencion.desde}`, { exact: false }).waitFor();
    await detencion.getByText(`${d.detencion.hasta}`, { exact: false }).waitFor();
    await detencion.getByText('no prueba camión detenido', { exact: false }).waitFor();
    await fila('DESVIO_CANDIDATO').getByText('no tramo recorrido ni incumplimiento', { exact: false }).waitFor();
    assert.equal(await fila('SEPARACION_INDETERMINADA_POR_PRECISION').count(), 2, 'Precisión reportada vuelve incierta la separación.');
    await page.getByText(`RutaVersion ${d.versionHistorica}`, { exact: false }).waitFor();
    await page.getByText('Incidencias operativas y verificación de causas no están implementadas en esta revisión; no se inventa una explicación.').waitFor();
    const cuerpo = (await page.locator('main').innerText()).replace(/no prueba camión detenido|no son sanciones, fraude comprobado ni cumplimiento|no tramo recorrido ni incumplimiento|no certifica integridad, presencia del camión ni cumplimiento/gi, '');
    assert.ok(!/fraude|vehículo detenido|camión detenido|recolección omitida|sanci|\bcumple\b|incumpl/i.test(cuerpo), 'Sin veredictos automáticos.');

    // ---- Cero candidatos ----
    assert.equal(await revisar(d.j.sinCandidatos), 200);
    await page.getByRole('heading', { name: 'REVISION_TECNICA' }).waitFor();
    await page.getByText('Sin señales listadas bajo estos parámetros; no certifica continuidad, autenticidad ni ausencia de detenciones.').waitFor();

    // ---- Cero y una muestra: insuficiente, no «revisión parcial» ----
    for (const j of [d.j.cero, d.j.una]) {
      assert.equal(await revisar(j), 200);
      await page.getByRole('heading', { name: 'EVIDENCIA_INSUFICIENTE' }).waitFor();
    }

    // ---- Parámetro inválido: sin petición; jornada inexistente ----
    await page.getByLabel('Radio técnico de agrupación (m)').fill('0');
    let revisiones = 0;
    const contarRevision = r => { if (r.url().includes('/api/revision-gps/')) revisiones++; };
    page.on('request', contarRevision);
    await page.getByLabel('Jornada UUID').fill(d.j.principal);
    await page.getByRole('button', { name: 'Revisar muestras recibidas' }).click();
    assert.equal(await page.locator('form').evaluate(f => f.checkValidity()), false);
    assert.equal(revisiones, 0, 'Umbral inválido: sin petición.');
    page.off('request', contarRevision);
    await page.getByLabel('Radio técnico de agrupación (m)').fill('25');
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
