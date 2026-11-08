import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { chromium } from 'playwright';

// Run with the development backend + Vite already listening. No report/screenshot files.
// Only synthetic fixtures owned by this test are removed from the development Docker DB.
test('CU-09: dibujar, persistir, recuperar y versionar una ruta sintética', { timeout: 90000 }, async () => {
  const codigo = `SINTETICA-E2E-${randomUUID()}`;
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const headers = { 'X-GAMW-Development': 'rutas-poc' };
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  try {
    // Preserve only the test browser's WebGL buffer for in-memory rendering assertions.
    await page.addInitScript(() => {
      const original = HTMLCanvasElement.prototype.getContext;
      HTMLCanvasElement.prototype.getContext = function (type, attributes) {
        return original.call(this, type, type === 'webgl' || type === 'webgl2'
          ? { ...attributes, preserveDrawingBuffer: true } : attributes);
      };
    });
    // External provider availability is separate from institutional persistence.
    // A synthetic background makes this test deterministic while exercising real WebGL and Terra Draw.
    await page.route('https://demotiles.maplibre.org/style.json', route => route.fulfill({
      json: { version: 8, sources: {}, layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#f0f2f1' } }] },
    }));
    await page.goto('http://127.0.0.1:5173/rutas');
    await page.getByText('Mapa disponible', { exact: true }).waitFor();
    const canvas = page.getByTestId('mapa-ruta').locator('canvas');
    await page.getByRole('button', { name: 'Dibujar línea', exact: true }).click();
    for (const [x, y] of [[150, 150], [280, 210], [400, 160]]) await canvas.click({ position: { x, y } });
    await page.keyboard.press('Enter');
    await page.getByRole('button', { name: 'Editar borrador', exact: true }).click();
    const bounds = await canvas.boundingBox();
    assert.ok(bounds);
    await page.mouse.move(bounds.x + 150, bounds.y + 150);
    await page.mouse.down();
    await page.mouse.move(bounds.x + 175, bounds.y + 175, { steps: 8 });
    await page.mouse.up();
    await page.getByLabel('Código', { exact: true }).fill(codigo);
    await page.getByLabel('Nombre', { exact: true }).fill('Geometría sintética E2E — no oficial');
    await page.getByLabel('Vigente desde').fill('2026-01-01T00:00:00');
    const firstResponse = page.waitForResponse(response => response.url().endsWith('/api/rutas') && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Guardar ruta', exact: true }).click();
    const response = await firstResponse;
    assert.equal(response.status(), 201);
    const sent = response.request().postDataJSON().geometria;
    const creada = await response.json();
    assert.equal(creada.version.numeroVersion, 1);
    assert.equal(sent.type, 'LineString');
    assert.ok(sent.coordinates.length >= 2);
    await page.getByText('Versión guardada y recuperada del servidor.', { exact: true }).waitFor();
    const id = creada.ruta.id;
    const obtenerVersion = async number => {
      const result = await page.request.get(`http://127.0.0.1:5173/api/rutas/${id}/versiones/${number}`, { headers });
      assert.equal(result.status(), 200);
      return result.json();
    };
    assert.deepEqual((await obtenerVersion(1)).geometria, sent);
    const sqlResult = execFileSync('docker', ['compose', '-f', 'compose.dev.yml', 'exec', '-T', 'db', 'psql', '-X',
      '-U', 'gamw_app_dev', '-d', 'gamw_residuos_dev', '-v', 'ON_ERROR_STOP=1', '-At', '-c',
      `SELECT json_build_object('srid', ST_SRID(v.geometria), 'type', GeometryType(v.geometria), 'geometria', ST_AsGeoJSON(v.geometria,17,0)::json) FROM ruta_version v JOIN ruta r ON r.id=v.ruta_id WHERE r.codigo='${codigo}' AND v.numero_version=1;`],
    { cwd: new URL('../../../backend/', import.meta.url), encoding: 'utf8' });
    const stored = JSON.parse(sqlResult.trim());
    assert.equal(stored.srid, 4326);
    assert.equal(stored.type, 'LINESTRING');
    assert.deepEqual(stored.geometria, sent);

    // A full page reload discards client memory, then explicitly retrieves persisted history.
    await page.reload();
    await page.getByText('Mapa disponible', { exact: true }).waitFor();
    await page.getByLabel('Ruta guardada', { exact: true }).selectOption(id);
    await page.getByRole('button', { name: 'Recargar versión', exact: true }).click();
    assert.deepEqual((await obtenerVersion(1)).geometria, sent);
    await page.waitForFunction(() => {
      const container = document.querySelector('[data-testid="mapa-ruta"]');
      const canvas = container.querySelector('canvas');
      const gl = canvas.getContext('webgl2') || canvas.getContext('webgl');
      if (!gl) return false;
      const pixels = new Uint8Array(gl.drawingBufferWidth * gl.drawingBufferHeight * 4);
      gl.readPixels(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
      let count = 0;
      for (let i = 0; i < pixels.length; i += 4) {
        if (Math.abs(pixels[i] - 21) < 4 && Math.abs(pixels[i + 1] - 101) < 4 && Math.abs(pixels[i + 2] - 192) < 4) count++;
      }
      return count > 10;
    }, undefined, { timeout: 10000 }).catch(async error => {
      const diagnostic = await page.evaluate(() => {
        const canvas = document.querySelector('[data-testid="mapa-ruta"] canvas');
        const gl = canvas.getContext('webgl2') || canvas.getContext('webgl');
        if (!gl) return { context: false };
        const pixels = new Uint8Array(gl.drawingBufferWidth * gl.drawingBufferHeight * 4);
        gl.readPixels(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
        const colors = new Map();
        for (let i = 0; i < pixels.length; i += 4) {
          const key = pixels.slice(i, i + 4).join(','); colors.set(key, (colors.get(key) || 0) + 1);
        }
        return { width: canvas.width, height: canvas.height, attributes: gl.getContextAttributes(),
          colors: [...colors].sort((a, b) => b[1] - a[1]).slice(0, 8), alerts: [...document.querySelectorAll('[role="alert"]')].map(element => element.textContent) };
      });
      assert.fail(`${error.message}; rendering=${JSON.stringify(diagnostic)}; errors=${JSON.stringify(errors)}`);
    });

    await page.getByRole('button', { name: 'Editar como nueva versión', exact: true }).click();
    await page.getByRole('button', { name: 'Dibujar línea', exact: true }).click();
    for (const [x, y] of [[180, 240], [320, 280], [430, 200]]) await canvas.click({ position: { x, y } });
    await page.keyboard.press('Enter');
    await page.getByLabel('Motivo del cambio').fill('Cambio sintético E2E');
    const nextResponse = page.waitForResponse(response => response.url().endsWith(`/api/rutas/${id}/versiones`) && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Guardar nueva versión', exact: true }).click();
    const next = await nextResponse;
    assert.equal(next.status(), 201);
    assert.equal((await next.json()).numeroVersion, 2);
    await page.getByText('Versión guardada y recuperada del servidor.', { exact: true }).waitFor();
    assert.deepEqual((await obtenerVersion(1)).geometria, sent);
    assert.notDeepEqual((await obtenerVersion(2)).geometria, sent);
    await page.getByLabel('Versión', { exact: true }).selectOption('1');
    assert.deepEqual((await obtenerVersion(1)).geometria, sent);
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
    // Code is generated locally from a UUID, never from external/user-controlled input.
    assert.match(codigo, /^SINTETICA-E2E-[0-9a-f-]+$/);
    execFileSync('docker', ['compose', '-f', 'compose.dev.yml', 'exec', '-T', 'db', 'psql', '-X',
      '-U', 'gamw_app_dev', '-d', 'gamw_residuos_dev', '-v', 'ON_ERROR_STOP=1', '-c',
      `DELETE FROM ruta_version WHERE ruta_id IN (SELECT id FROM ruta WHERE codigo = '${codigo}'); DELETE FROM ruta WHERE codigo = '${codigo}';`],
    { cwd: new URL('../../../backend/', import.meta.url), stdio: 'pipe' });
  }
});
