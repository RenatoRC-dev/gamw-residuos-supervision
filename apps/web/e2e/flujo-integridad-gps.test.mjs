import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía IntegridadGpsNavegadorIT. CU-17 (A3): revisión técnica de continuidad e integridad de evidencia GPS con JWT y permiso municipal
// GPS_INTEGRIDAD_REVISAR. Umbrales PROVISIONALES (no se inventan políticas). Las señales son para revisión humana: no son sanciones, fraude ni incumplimiento.
test('CU-17: A3 revisa integridad GPS (huecos, precisión, recepción, salto, insuficiencia) sin dictaminar; A4 queda sin acceso', { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.revisor?.startsWith('int-rev-') && d.operador?.startsWith('int-op-') && d.sinPermiso?.startsWith('int-sin-') && d.clave);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', x => errores.push(x.name));
  const peticiones = [];
  page.on('request', r => { if (r.url().includes('/api/revision-gps/')) peticiones.push(`${r.method()} ${r.url()}`); });
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
    const respuesta = page.waitForResponse(r => r.url().includes('/api/revision-gps/'));
    await page.getByRole('button', { name: 'Revisar muestras recibidas' }).click();
    return (await respuesta).status();
  };
  const tipos = () => page.locator('tbody tr').evaluateAll(rs => rs.map(r => r.children[0].textContent).sort());
  try {
    await page.goto(new URL('/acceso', base).href);
    await ingresar(d.revisor);
    await abrir();
    await page.getByText('Umbrales técnicos PROVISIONALES, no contractuales').waitFor();
    assert.equal(await page.getByLabel('Revisión').inputValue(), 'integridad');

    // Umbrales explícitos de la prueba: separación temporal de 60 s (los demás, por defecto).
    await page.getByLabel('Separación temporal (s)').fill('60');
    // Valores no positivos: la interfaz no emite petición.
    await page.getByLabel('Precisión reportada máxima (m)').fill('0');
    await page.getByLabel('Jornada UUID').fill(d.j.principal);
    await page.getByRole('button', { name: 'Revisar muestras recibidas' }).click();
    assert.equal(peticiones.length, 0, 'Umbral inválido: sin petición.');
    await page.getByLabel('Precisión reportada máxima (m)').fill('50');

    // ---- Jornada con huecos antes/entre/después, precisión, recepción diferida/anterior, mismo instante y salto ----
    assert.equal(await revisar(d.j.principal), 200);
    await page.getByRole('heading', { name: 'REVISION_TECNICA' }).waitFor();
    await page.getByText('Muestras recibidas 6; analizadas 6.', { exact: false }).waitFor();
    await page.getByText('Solo se evalúa la evidencia recibida; pueden faltar capturas offline.').waitFor();
    await page.getByText('Ubicación simulada, permisos GPS y eventos del dispositivo: NO DISPONIBLES; no se inventan.').waitFor();
    assert.ok((await page.getByText('Parámetros efectivamente usados:', { exact: false }).innerText()).includes('"huecoSegundos":60'));
    assert.deepEqual(await tipos(), d.esperados.principal, 'Señales esperadas con los umbrales explícitos.');
    for (const e of await page.locator('tbody tr').evaluateAll(rs => rs.map(r => r.children[1].textContent))) assert.equal(e, 'POR_REVISAR', 'Todas quedan por revisar; ninguna es dictamen.');
    const fila = t => page.locator('tbody tr').filter({ has: page.locator('td:first-child', { hasText: new RegExp(`^${t}$`) }) });
    assert.match(await fila('INICIO_SIN_MUESTRAS').first().innerText(), /200 s/);
    assert.match(await fila('PRECISION_LIMITADA').innerText(), /80 m/);
    const salto = Number((await fila('SALTO_POR_REVISAR').locator('td').nth(3).innerText()).split(' ')[0]);
    assert.ok(salto > 120, `Velocidad candidata ${salto} km/h supera el umbral provisional.`);
    await fila('FIN_SIN_MUESTRAS_RECIENTES').getByText('No se rellena el intervalo ni se presupone GPS detenido.').waitFor();
    await fila('RECEPCION_ANTERIOR_CAPTURA').getByText('Revisar relojes; no prueba manipulación.').waitFor();
    await fila('POSICIONES_DISTINTAS_MISMO_INSTANTE').getByText('No calcular velocidad con intervalo cero.').waitFor();
    await page.getByText('Señales para revisión humana; no son sanciones, fraude comprobado ni cumplimiento de recolección.').waitFor();
    const cuerpo = await page.locator('main').innerText();
    assert.ok(!/pérdida de (internet|señal|conexión)|camión detenido(?! )|cumple\b/i.test(cuerpo.replace(/no prueba camión detenido/gi, '')), 'No se atribuye causa a los huecos.');
    assert.ok(cuerpo.includes('No se conoce la causa ni se interpola el intervalo.'));

    // ---- Cero muestras (con INICIO) y una sola muestra: indeterminado, no dictamen ----
    assert.equal(await revisar(d.j.cero), 200);
    await page.getByRole('heading', { name: 'EVIDENCIA_INSUFICIENTE' }).waitFor();
    await page.getByText('Muestras recibidas 0; analizadas 0.', { exact: false }).waitFor();
    assert.equal(await page.getByText(/\b(cumple|incumple)\b/i).count(), 0);
    assert.equal(await revisar(d.j.una), 200);
    await page.getByRole('heading', { name: 'EVIDENCIA_INSUFICIENTE' }).waitFor();
    await page.getByText('Sin señales listadas bajo estos parámetros; no certifica continuidad, autenticidad ni ausencia de detenciones.').waitFor();

    // ---- Límites exactos (hueco = 60 s y precisión = 50 m no son señal) ----
    assert.equal(await revisar(d.j.limite), 200);
    await page.getByRole('heading', { name: 'REVISION_TECNICA' }).waitFor();
    await page.getByText('Sin señales listadas bajo estos parámetros', { exact: false }).waitFor();

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
      assert.equal(await revisar(d.j.principal), 403, `${nombre} sin acceso.`);
      await page.getByRole('alert').filter({ hasText: /\S/ }).waitFor();
      assert.equal(await page.locator('section[aria-label="Resultado técnico de revisión"]').count(), 0);
      assert.equal(await page.locator('tbody tr').count(), 0);
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
