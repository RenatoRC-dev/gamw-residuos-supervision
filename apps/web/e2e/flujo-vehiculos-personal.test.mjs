import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía VehiculosPersonalNavegadorIT. CU-06 (vehículos) y CU-07 (responsables operativos) por la interfaz real con JWT y
// permisos municipales explícitos (A1). Sin catálogos, políticas de solapamiento ni cuentas móviles inventadas; D1 pendiente.
test('CU-06/CU-07: A1 gestiona vehículos, personas y vínculos históricos; el lector no modifica', { timeout: 170000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.admin?.startsWith('vp-adm-') && d.lector?.startsWith('vp-lec-') && d.clave);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', x => errores.push(x.name));
  const escrituras = [];
  page.on('request', r => { if (r.method() !== 'GET' && r.url().includes('/api/') && !r.url().includes('/autenticacion/')) escrituras.push(`${r.method()} ${new URL(r.url()).pathname}`); });
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
    await page.getByRole('link', { name: 'Gestionar empresas, periodos y personal' }).click();
    await page.getByRole('heading', { name: 'Registro de operadores' }).waitFor();
    await page.getByRole('status').filter({ hasText: 'Cargando…' }).waitFor({ state: 'detached' });
  };
  const salir = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await page.getByRole('button', { name: 'Ingresar', exact: true }).waitFor();
  };
  const fila = razon => page.locator('section[aria-labelledby="empresas-titulo"] tbody tr').filter({ has: page.getByRole('cell', { name: razon, exact: true }) });
  const seleccionar = async razon => {
    await fila(razon).getByRole('button', { name: 'Seleccionar empresa' }).click();
    await page.getByRole('heading', { name: `Empresa seleccionada: ${razon}` }).waitFor();
    await page.getByRole('status').filter({ hasText: 'Consultando registro vehicular…' }).waitFor({ state: 'detached' });
  };
  const alerta = () => page.locator('main > p[role="alert"]');
  const formPersona = () => page.getByRole('form', { name: 'Datos de persona' });
  const formVinculo = () => page.getByRole('form', { name: 'Datos de vínculo' });
  const formVinculoPersona = async () => (await page.getByLabel('Persona existente').locator('option:checked').innerText()).replace(/\s+$/, '');
  const personal = () => page.locator('section[aria-labelledby="personal-titulo"] li');
  const seccionVehiculos = () => page.locator('section[aria-labelledby="vehiculos-titulo"]');
  const alertaVehicular = () => seccionVehiculos().locator('p[role="alert"]');
  const formVehiculo = () => page.getByRole('form', { name: 'Datos de vehículo' });
  const formVinculoVehicular = () => page.getByRole('form', { name: 'Vínculo vehicular' });
  const vinculosVehiculares = () => seccionVehiculos().locator('li');
  const listo = () => page.getByRole('status').filter({ hasText: 'Guardando…' }).waitFor({ state: 'detached' });
  const listoVehicular = () => page.getByRole('status').filter({ hasText: 'Consultando registro vehicular…' }).waitFor({ state: 'detached' });
  try {
    // ================= A1 con PERSONAS_*, VEHICULOS_* y EMPRESAS_CONSULTAR =================
    await page.goto(new URL('/acceso', base).href);
    await ingresar(d.admin);
    await abrir();
    await fila(d.empresaA).waitFor();

    // ---- CU-07: persona sin cuenta de acceso (CA-07-01) y CI único (E1) ----
    await formPersona().getByLabel('CI').fill(d.ci);
    await formPersona().getByLabel('Nombres').fill(d.nombres);
    await formPersona().getByRole('button', { name: 'Crear persona' }).click();
    await listo();
    await page.getByRole('status').filter({ hasText: 'Cambios guardados.' }).waitFor();
    await formPersona().getByLabel('CI').fill(d.ci);
    await formPersona().getByLabel('Nombres').fill('Duplicado sintético');
    await formPersona().getByRole('button', { name: 'Crear persona' }).click();
    await alerta().filter({ hasText: 'ya existe' }).waitFor();
    // La persona creada queda seleccionada; el intento duplicado no la cambia.
    assert.equal(await formVinculoPersona(), `${d.ci} · ${d.nombres}`);

    // ---- CU-07: vínculo histórico con funciones válidas (CA-07-02/03) ----
    await seleccionar(d.empresaA);
    await formVinculo().getByLabel('Inicio del vínculo').fill('2026-01-01');
    const antesSinFuncion = escrituras.length;
    await formVinculo().getByRole('button', { name: 'Asociar persona' }).click();
    await alerta().filter({ hasText: 'Seleccione al menos una función operativa activa.' }).waitFor();
    assert.equal(escrituras.length, antesSinFuncion, 'Sin funciones: no se envía la petición.');
    await formVinculo().getByLabel('Conductor', { exact: true }).check();
    await formVinculo().getByRole('button', { name: 'Asociar persona' }).click();
    await personal().filter({ hasText: `${d.ci} · ${d.nombres} · 2026-01-01 — Sin cierre · Activo` }).filter({ hasText: 'Conductor' }).waitFor();

    // ---- CU-07 FA-01 / CA-07-05: cambio de función = cerrar el segmento y crear otro ----
    await personal().filter({ hasText: `${d.ci} · ${d.nombres} · 2026-01-01` }).getByRole('button', { name: 'Editar vínculo' }).click();
    await formVinculo().getByText('Funciones históricas: Conductor.').waitFor();
    await formVinculo().getByLabel('Fin del vínculo').fill('2026-03-31');
    await formVinculo().getByLabel('Vínculo activo').uncheck();
    await formVinculo().getByRole('button', { name: 'Guardar vínculo' }).click();
    await personal().filter({ hasText: `${d.ci} · ${d.nombres} · 2026-01-01 — 2026-03-31 · Inactivo` }).filter({ hasText: 'Conductor' }).waitFor();
    await formVinculo().getByLabel('Inicio del vínculo').fill('2026-04-01');
    await formVinculo().getByLabel('Supervisor de empresa', { exact: true }).check();
    await formVinculo().getByRole('button', { name: 'Asociar persona' }).click();
    await personal().filter({ hasText: `${d.ci} · ${d.nombres} · 2026-04-01 — Sin cierre · Activo` }).filter({ hasText: 'Supervisor de empresa' }).waitFor();
    assert.equal(await personal().filter({ hasText: `${d.ci} · ` }).count(), 2, 'Se conserva el segmento anterior.');

    // ---- CU-07 E5 / CA-07-08: el cierre no deja fuera una jornada ya asignada con el vínculo ----
    const asignado = personal().filter({ hasText: `${d.ciAsignado} · ` });
    await asignado.getByRole('button', { name: 'Editar vínculo' }).click();
    await formVinculo().getByLabel('Fin del vínculo').fill('2026-03-31');
    await formVinculo().getByRole('button', { name: 'Guardar vínculo' }).click();
    await alerta().filter({ hasText: 'fuera del vínculo de la persona' }).waitFor();
    await asignado.filter({ hasText: '2026-01-01 — Sin cierre · Activo' }).waitFor();
    await formVinculo().getByRole('button', { name: 'Nuevo vínculo' }).click();

    // ---- CU-06: vehículo independiente con placa única (CA-06-01, E1) y vínculo histórico (CA-06-03) ----
    await formVehiculo().getByLabel('Placa').fill(d.placa);
    await formVehiculo().getByRole('button', { name: 'Crear vehículo' }).click();
    await listoVehicular();
    await seccionVehiculos().getByRole('status').filter({ hasText: 'Cambios vehiculares guardados.' }).waitFor();
    await formVehiculo().getByLabel('Placa').fill(d.placa);
    await formVehiculo().getByRole('button', { name: 'Crear vehículo' }).click();
    await alertaVehicular().filter({ hasText: 'ya existe' }).waitFor();
    await formVinculoVehicular().getByLabel('Vehículo del vínculo').selectOption({ label: d.placa });
    await formVinculoVehicular().getByLabel('Inicio del vínculo vehicular').fill('2026-01-01');
    await formVinculoVehicular().getByRole('button', { name: 'Vincular vehículo' }).click();
    await vinculosVehiculares().filter({ hasText: `${d.placa} · 2026-01-01 — Sin cierre · Activo` }).waitFor();
    // FA-02 / CA-06-04: cierre del vínculo.
    await vinculosVehiculares().filter({ hasText: `${d.placa} · 2026-01-01` }).getByRole('button', { name: 'Editar cierre vehicular' }).click();
    await formVinculoVehicular().getByLabel('Fin del vínculo vehicular').fill('2026-03-31');
    await formVinculoVehicular().getByLabel('Vínculo vehicular activo').uncheck();
    await formVinculoVehicular().getByRole('button', { name: 'Guardar cierre vehicular' }).click();
    await vinculosVehiculares().filter({ hasText: `${d.placa} · 2026-01-01 — 2026-03-31 · Inactivo` }).waitFor();
    // E4: cierre incompatible con una jornada ya asignada con el vínculo.
    await vinculosVehiculares().filter({ hasText: `${d.placaAsignada} · ` }).getByRole('button', { name: 'Editar cierre vehicular' }).click();
    await formVinculoVehicular().getByLabel('Fin del vínculo vehicular').fill('2026-03-31');
    await formVinculoVehicular().getByRole('button', { name: 'Guardar cierre vehicular' }).click();
    await alertaVehicular().filter({ hasText: 'fuera del vínculo vehicular' }).waitFor();
    await vinculosVehiculares().filter({ hasText: `${d.placaAsignada} · 2026-01-01 — Sin cierre · Activo` }).waitFor();
    await formVinculoVehicular().getByRole('button', { name: 'Nuevo vínculo vehicular' }).click();

    // ---- CA-06-07 y CA-07-06: el mismo vehículo y la misma persona operan para otra empresa y regresan ----
    await seleccionar(d.empresaB);
    await formVinculoVehicular().getByLabel('Vehículo del vínculo').selectOption({ label: d.placa });
    await formVinculoVehicular().getByLabel('Inicio del vínculo vehicular').fill('2026-04-01');
    await formVinculoVehicular().getByLabel('Fin del vínculo vehicular').fill('2026-06-30');
    await formVinculoVehicular().getByRole('button', { name: 'Vincular vehículo' }).click();
    await vinculosVehiculares().filter({ hasText: `${d.placa} · 2026-04-01 — 2026-06-30 · Activo` }).waitFor();
    await formVinculo().getByLabel('Persona del vínculo').selectOption({ label: `${d.ci} · ${d.nombres}` });
    await formVinculo().getByLabel('Inicio del vínculo').fill('2026-05-01');
    await formVinculo().getByLabel('Conductor', { exact: true }).check();
    await formVinculo().getByRole('button', { name: 'Asociar persona' }).click();
    await personal().filter({ hasText: `${d.ci} · ${d.nombres} · 2026-05-01 — Sin cierre · Activo` }).waitFor();
    await seleccionar(d.empresaA);
    await formVinculoVehicular().getByLabel('Vehículo del vínculo').selectOption({ label: d.placa });
    await formVinculoVehicular().getByLabel('Inicio del vínculo vehicular').fill('2026-07-01');
    await formVinculoVehicular().getByRole('button', { name: 'Vincular vehículo' }).click();
    await vinculosVehiculares().filter({ hasText: `${d.placa} · 2026-07-01 — Sin cierre · Activo` }).waitFor();
    assert.equal(await vinculosVehiculares().filter({ hasText: `${d.placa} · ` }).count(), 2, 'El regreso crea un vínculo nuevo y conserva el anterior.');
    assert.equal(await personal().filter({ hasText: `${d.ci} · ` }).count(), 2, 'Los segmentos de la empresa A siguen visibles.');

    // ================= Lector (solo *_CONSULTAR): consulta, no modifica =================
    await salir();
    await ingresar(d.lector);
    await abrir();
    await seleccionar(d.empresaA);
    await personal().filter({ hasText: `${d.ci} · ${d.nombres} · 2026-04-01` }).waitFor();
    await vinculosVehiculares().filter({ hasText: `${d.placa} · 2026-07-01` }).waitFor();
    const antes = escrituras.length;
    await formPersona().getByLabel('CI').fill(`${d.ci}-LECTOR`);
    await formPersona().getByLabel('Nombres').fill('Intento lector');
    await formPersona().getByRole('button', { name: 'Crear persona' }).click();
    await alerta().filter({ hasText: /\S/ }).waitFor();
    await formVehiculo().getByLabel('Placa').fill(`${d.placa}L`);
    await formVehiculo().getByRole('button', { name: 'Crear vehículo' }).click();
    await alertaVehicular().filter({ hasText: /\S/ }).waitFor();
    assert.equal(escrituras.length, antes + 2, 'El lector intenta dos escrituras y ambas son rechazadas por el servidor.');
    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } catch (fallo) {
    console.log(`DIAG fallo: ${String(fallo?.message ?? fallo).split('\n')[0].slice(0, 300)}`);
    console.log(`DIAG url: ${page.url()}`);
    console.log(`DIAG escrituras: ${escrituras.slice(-6).join(' | ')}`);
    console.log(`DIAG texto: ${(await page.locator('main').innerText().catch(() => 'sin main')).replace(/\s+/g, ' ').slice(0, 900)}`);
    throw fallo;
  } finally {
    await browser.close();
  }
});
