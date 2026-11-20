import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía ReportesServicioNavegadorIT. CU-30 (corte descriptivo): recuentos de registros conservados con filtros por fecha de servicio y
// empresa; cero consultado frente a acceso no permitido; sin porcentajes, cumplimiento, exportación ni escrituras. A2/A4 sin acceso (D1).
test('CU-30: reporte descriptivo por la interfaz real con JWT y permisos municipales', { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.completo?.startsWith('rp-adm-') && d.basico?.startsWith('rp-bas-') && d.sinPermiso?.startsWith('rp-sin-') && d.clave);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', x => errores.push(x.name));
  const escrituras = [], reportes = [];
  page.on('request', r => {
    const ruta = new URL(r.url()).pathname;
    if (r.method() !== 'GET' && ruta.startsWith('/api/') && !ruta.endsWith('/autenticacion/login')) escrituras.push(`${r.method()} ${ruta}`);
    if (ruta.endsWith('/api/reportes-servicio/resumen')) reportes.push(r.url());
  });
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
    await page.getByRole('link', { name: 'Consultar reportes descriptivos del servicio' }).click();
    await page.getByRole('heading', { name: 'Reportes descriptivos del servicio' }).waitFor();
  };
  const salir = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await page.getByRole('button', { name: 'Ingresar', exact: true }).waitFor();
  };
  const filtros = () => page.getByRole('form', { name: 'Filtros del reporte' });
  const generar = async (desde, hasta, empresa = '') => {
    await filtros().getByLabel('Desde').fill(desde);
    await filtros().getByLabel('Hasta').fill(hasta);
    await filtros().getByLabel('Empresa (ID)').fill(empresa);
    const respuesta = page.waitForResponse(r => r.url().includes('/api/reportes-servicio/resumen'));
    await filtros().getByRole('button', { name: 'Generar reporte' }).click();
    return (await respuesta).status();
  };
  const celda = (clave, columna) => page.locator(`tr[data-recuento="${clave}"] td`).nth(columna);
  const cantidad = clave => celda(clave, 0).innerText();
  const estado = clave => celda(clave, 1).innerText();
  try {
    // ================= Cuenta municipal con permisos de todos los módulos fuente =================
    await page.goto(new URL('/acceso', base).href);
    await ingresar(d.completo);
    await abrir();
    // Validación previa: rango contradictorio, sin petición.
    await filtros().getByLabel('Desde').fill('2026-03-31');
    await filtros().getByLabel('Hasta').fill('2026-03-01');
    await filtros().getByRole('button', { name: 'Generar reporte' }).click();
    await page.getByRole('alert').filter({ hasText: 'Rango de fechas contradictorio' }).waitFor();
    assert.equal(reportes.length, 0, 'Rango inválido: no se consulta.');

    // CA-30-01/02: filtros de fecha de servicio y empresa; recuentos de cada fuente.
    assert.equal(await generar('2026-03-01', '2026-03-31', d.empresa), 200);
    await page.getByRole('region', { name: 'Resultado del reporte' }).waitFor();
    assert.equal(await cantidad('jornadas'), '5');
    assert.equal(await cantidad('jornadasSustituidas'), '1');
    assert.equal(await cantidad('posicionesGps'), '4');
    assert.equal(await cantidad('incidencias'), '1');
    assert.equal(await cantidad('inspecciones'), '1');
    assert.equal(await cantidad('reclamosVinculados'), '1');
    assert.equal(await estado('jornadas'), 'Con registros');
    assert.equal(await estado('reclamosRecibidos'), 'No aplica con estos filtros');
    assert.equal(await estado('eventosEjecucion'), 'No consultado');
    // CA-30-03/08: definición, fuente y referencia temporal visibles.
    assert.match(await celda('jornadas', 3).innerText(), /Fuente: jornada\./);
    assert.notEqual(await celda('posicionesGps', 2).innerText(), '—', 'Último registro de la fuente.');
    assert.match(await page.getByRole('region', { name: 'Resultado del reporte' }).innerText(), /America\/La_Paz/);
    assert.match(await page.getByRole('list', { name: 'Limitaciones' }).innerText(), /sin porcentajes, cumplimiento/);
    assert.equal(await page.getByText('%', { exact: false }).count(), 0, 'Sin porcentajes.');

    // CA-30-04: cero consultado (empresa sin jornadas) no es «sin datos».
    assert.equal(await generar('2026-03-01', '2026-03-31', d.empresaVacia), 200);
    await page.locator('tr[data-recuento="jornadas"]').waitFor();
    assert.equal(await cantidad('jornadas'), '0');
    assert.equal(await estado('jornadas'), 'Cero registros (consultado)');

    // ================= Cuenta con solo JORNADAS_CONSULTAR: fuentes sin permiso propio sin cifra =================
    await salir();
    await ingresar(d.basico);
    await abrir();
    assert.equal(await generar('2026-03-01', '2026-03-31', d.empresa), 200);
    await page.locator('tr[data-recuento="jornadas"]').waitFor();
    assert.equal(await cantidad('jornadas'), '5');
    for (const clave of ['posicionesGps', 'incidencias', 'inspecciones', 'reclamosVinculados']) {
      assert.equal(await cantidad(clave), '—', `${clave} sin cifra`);
      assert.equal(await estado(clave), 'Acceso no permitido (se desconoce si existen)');
    }

    // ================= Cuenta sin JORNADAS_CONSULTAR =================
    await salir();
    await ingresar(d.sinPermiso);
    await abrir();
    assert.equal(await generar('2026-03-01', '2026-03-31', d.empresa), 403);
    await page.getByRole('alert').filter({ hasText: 'No tiene autorización municipal para consultar reportes del servicio.' }).waitFor();
    assert.equal(await page.getByRole('region', { name: 'Resultado del reporte' }).count(), 0);

    assert.deepEqual(escrituras, [], 'CA-30-07: la interfaz no envía escrituras.');
    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } catch (fallo) {
    console.log(`DIAG fallo: ${String(fallo?.message ?? fallo).split('\n')[0].slice(0, 300)}`);
    console.log(`DIAG url: ${page.url()}`);
    console.log(`DIAG texto: ${(await page.locator('main').innerText().catch(() => 'sin main')).replace(/\s+/g, ' ').slice(0, 900)}`);
    throw fallo;
  } finally {
    await browser.close();
  }
});
