import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía ReferenciasEspacialesNavegadorIT. CU-10: dibujo real sobre el lienzo WebGL de MapLibre/Terra Draw, persistencia en PostGIS y
// verificación del render leyendo píxeles. Coordenadas y radio sintéticos (no oficiales). Base compartida: el listado que recibe la página se
// restringe a la RutaVersion propia de la prueba (respuesta REAL del backend, filtrada), para que el encuadre del mapa sea determinista.
const MORADO = [124, 58, 237];          // capas persistidas (círculo y borde)
const RELLENO = [225, 216, 245];        // relleno 0.15 sobre fondo #f3f4f6
const FONDO = [243, 244, 246];

test('CU-10: puntos y geocercas dibujados en el mapa real, versionados y cerrados; lector sin gestión', { timeout: 170000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.gestor?.startsWith('re-ges-') && d.lector?.startsWith('re-lec-') && d.sinPermiso?.startsWith('re-sin-') && d.clave && d.rutaVersion);
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const contexto = await browser.newContext({ viewport: { width: 1280, height: 1000 } });
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', x => errores.push(`${x.name}: ${x.message}`));
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (tipo, atributos) {
      return original.call(this, tipo, tipo === 'webgl' || tipo === 'webgl2' ? { ...atributos, preserveDrawingBuffer: true } : atributos);
    };
  });
  let ultimaLista = [];
  await page.route('**/api/referencias-espaciales?*', async route => {
    const respuesta = await route.fetch();
    if (!respuesta.ok()) { await route.fulfill({ response: respuesta }); return; }
    const cuerpo = await respuesta.json();
    ultimaLista = cuerpo.referencias.filter(r => r.rutaVersionId === d.rutaVersion);
    await route.fulfill({ response: respuesta, json: { ...cuerpo, referencias: ultimaLista } });
  });
  const ingresar = async nombre => {
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
  const lienzo = () => page.locator('[aria-label="Editor de referencias espaciales"] canvas.maplibregl-canvas');
  const contar = ([r, g, b], tolerancia = 6) => page.evaluate(({ r, g, b, tolerancia }) => {
    const canvas = document.querySelector('[aria-label="Editor de referencias espaciales"] canvas.maplibregl-canvas');
    const gl = canvas?.getContext('webgl2') || canvas?.getContext('webgl');
    if (!gl) return -1;
    const px = new Uint8Array(gl.drawingBufferWidth * gl.drawingBufferHeight * 4);
    gl.readPixels(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight, gl.RGBA, gl.UNSIGNED_BYTE, px);
    let n = 0;
    for (let i = 0; i < px.length; i += 4) if (Math.abs(px[i] - r) <= tolerancia && Math.abs(px[i + 1] - g) <= tolerancia && Math.abs(px[i + 2] - b) <= tolerancia) n++;
    return n;
  }, { r, g, b, tolerancia });
  const esperarColor = async (color, minimo, descripcion) => {
    const limite = Date.now() + 15000; let n = 0;
    while (Date.now() < limite) { n = await contar(color); if (n >= minimo) return n; await page.waitForTimeout(150); }
    throw new Error(`${descripcion}: ${n} píxeles (mínimo ${minimo})`);
  };
  const abrir = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Gestionar referencias espaciales' }).click();
    await page.getByRole('heading', { name: 'Puntos de control y geocercas' }).waitFor();
    await esperarColor(FONDO, 5000, 'El mapa no terminó de pintar su fondo');
  };
  const items = () => page.locator('main ul > li');
  const clic = async (dx, dy) => {
    const caja = await lienzo().boundingBox();
    await page.mouse.click(caja.x + caja.width / 2 + dx, caja.y + caja.height / 2 + dy);
  };
  try {
    // ================= Gestor (REFERENCIA_ESPACIAL_CONSULTAR + GESTIONAR) =================
    await page.goto(new URL('/acceso', base).href);
    await ingresar(d.gestor);
    await abrir();
    assert.equal(await contar(MORADO), 0, 'Sin referencias propias todavía: nada persistido pintado.');
    await page.getByLabel('Nombre').fill(`Punto sintético ${d.sufijo}`);
    await page.getByLabel('Versión de ruta').selectOption(d.rutaVersion);
    await page.getByLabel('Motivo del registro, cambio o cierre').fill('Registro sintético de prueba');
    await page.getByRole('button', { name: 'Guardar referencia' }).click();
    await page.getByRole('alert').filter({ hasText: 'Dibuje o seleccione una geometría y su versión de ruta.' }).waitFor();

    // ---- FA-01 / CA-10-01: punto dibujado en el lienzo ----
    await page.getByRole('button', { name: 'Dibujar punto' }).click();
    await clic(0, 0);
    await page.getByText('Geometría: Point.').waitFor();
    await page.getByLabel('Radio explícito del punto (m)').fill('50');
    let guardado = page.waitForResponse(r => r.url().endsWith('/api/referencias-espaciales') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Guardar referencia' }).click();
    const respuestaPunto = await guardado;
    assert.equal(respuestaPunto.status(), 200);
    const enviadoPunto = respuestaPunto.request().postDataJSON();
    assert.equal(enviadoPunto.tipo, 'PUNTO');
    assert.equal(enviadoPunto.geometria.type, 'Point');
    assert.equal(enviadoPunto.radioMetros, 50);
    await page.getByRole('status').filter({ hasText: 'Versión 1 conservada' }).waitFor();
    // Recuperación desde PostGIS (GET real) y render de la capa persistida en el lienzo.
    await items().filter({ hasText: `Punto sintético ${d.sufijo} · PUNTO v1` }).waitFor();
    const recuperado = ultimaLista.find(r => r.nombre === `Punto sintético ${d.sufijo}` && r.numeroVersion === 1);
    assert.ok(recuperado, 'El listado (GET real) devuelve la referencia.');
    assert.deepEqual(recuperado.geometria, enviadoPunto.geometria, 'La geometría leída de PostGIS es la dibujada.');
    assert.equal(recuperado.rutaVersionId, d.rutaVersion);
    await esperarColor(MORADO, 40, 'El punto persistido no se pinta en el mapa');

    // ---- FA-02: geocerca poligonal dibujada en el lienzo ----
    await page.getByRole('button', { name: 'Nuevo registro' }).click();
    await page.getByRole('button', { name: 'Dibujar geocerca' }).click();
    for (const [dx, dy] of [[-120, -90], [120, -90], [120, 90], [-120, 90]]) await clic(dx, dy);
    await page.keyboard.press('Enter');
    await page.getByText('Geometría: Polygon.').waitFor();
    assert.equal(await page.getByLabel('Radio explícito del punto (m)').isDisabled(), true, 'La geocerca no lleva radio.');
    await page.getByLabel('Nombre').fill(`Geocerca sintética ${d.sufijo}`);
    await page.getByLabel('Motivo del registro, cambio o cierre').fill('Registro sintético de prueba');
    guardado = page.waitForResponse(r => r.url().endsWith('/api/referencias-espaciales') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Guardar referencia' }).click();
    const respuestaGeocerca = await guardado;
    assert.equal(respuestaGeocerca.status(), 200);
    const enviadaGeocerca = respuestaGeocerca.request().postDataJSON();
    assert.equal(enviadaGeocerca.tipo, 'GEOCERCA');
    assert.equal(enviadaGeocerca.geometria.type, 'Polygon');
    assert.equal(enviadaGeocerca.radioMetros, null);
    const anillo = enviadaGeocerca.geometria.coordinates[0];
    assert.ok(anillo.length >= 5, 'Anillo de 4 vértices cerrado.');
    assert.deepEqual(anillo[0], anillo[anillo.length - 1]);
    await items().filter({ hasText: `Geocerca sintética ${d.sufijo} · GEOCERCA v1` }).waitFor();
    assert.deepEqual(ultimaLista.find(r => r.nombre === `Geocerca sintética ${d.sufijo}`)?.geometria, enviadaGeocerca.geometria);
    await esperarColor(RELLENO, 300, 'El relleno de la geocerca persistida no se pinta');

    // ---- Versionado y cierre conservando historia (CA-10-04) ----
    const punto = items().filter({ hasText: `Punto sintético ${d.sufijo} · PUNTO v1` });
    await punto.getByRole('button', { name: 'Preparar nueva versión' }).click();
    await page.getByRole('heading', { name: `Nueva versión de Punto sintético ${d.sufijo}` }).waitFor();
    await page.getByLabel('Radio explícito del punto (m)').fill('80');
    await page.getByLabel('Motivo del registro, cambio o cierre').fill('Ajuste sintético del radio');
    guardado = page.waitForResponse(r => r.url().includes('/versiones') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Guardar nueva versión' }).click();
    assert.equal((await guardado).status(), 200);
    await page.getByRole('status').filter({ hasText: 'Versión 2 conservada' }).waitFor();
    const v2 = items().filter({ hasText: `Punto sintético ${d.sufijo} · PUNTO v2` });
    await v2.filter({ hasText: 'Abierta' }).waitFor();
    await items().filter({ hasText: `Punto sintético ${d.sufijo} · PUNTO v1` }).filter({ hasNotText: 'Abierta' }).waitFor();
    assert.equal(await items().filter({ hasText: `Punto sintético ${d.sufijo} · PUNTO v1` }).getByRole('button').count(), 0, 'v1 cerrada: sin acciones.');
    await page.getByLabel('Motivo del registro, cambio o cierre').fill('Cierre sintético');
    const cierre = page.waitForResponse(r => r.url().endsWith('/cierre'));
    await v2.getByRole('button', { name: 'Cerrar vigencia conservando historia' }).click();
    assert.equal((await cierre).status(), 204);
    await items().filter({ hasText: `Punto sintético ${d.sufijo} · PUNTO v2` }).filter({ hasNotText: 'Abierta' }).waitFor();
    assert.equal(await items().filter({ hasText: `Punto sintético ${d.sufijo}` }).count(), 2, 'Ambas versiones siguen consultables.');

    // ================= Lector (solo CONSULTAR): ve mapa y listado; la gestión no es un error de la consulta =================
    await salir();
    await ingresar(d.lector);
    await abrir();
    await items().filter({ hasText: `Geocerca sintética ${d.sufijo}` }).waitFor();
    await esperarColor(RELLENO, 300, 'El lector no ve la geocerca pintada');
    assert.equal(await page.getByRole('alert').count(), 0, 'La falta de GESTIONAR no se presenta como error de la consulta.');
    await page.getByRole('note').filter({ hasText: 'solo consulta' }).waitFor();

    // ================= Cuenta sin permiso espacial =================
    await salir();
    await ingresar(d.sinPermiso);
    await abrir().catch(() => {});
    await page.getByRole('alert').filter({ hasText: 'Permiso explícito de consulta/gestión espacial requerido.' }).waitFor();
    assert.equal(await items().count(), 0);
    assert.deepEqual(errores, [], 'Sin errores de página.');
  } catch (fallo) {
    console.log(`DIAG fallo: ${String(fallo?.message ?? fallo).split('\n')[0].slice(0, 300)}`);
    console.log(`DIAG url: ${page.url()}`);
    console.log(`DIAG texto: ${(await page.locator('main').innerText().catch(() => 'sin main')).replace(/\s+/g, ' ').slice(0, 900)}`);
    console.log(`DIAG pixeles morado=${await contar(MORADO).catch(() => '?')} relleno=${await contar(RELLENO).catch(() => '?')} fondo=${await contar(FONDO).catch(() => '?')}`);
    throw fallo;
  } finally {
    await browser.close();
  }
});
