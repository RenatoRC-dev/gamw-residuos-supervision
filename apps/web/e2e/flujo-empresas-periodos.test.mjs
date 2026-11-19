import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía EmpresasPeriodosNavegadorIT. CU-04/CU-05 (A1): empresas operadoras y periodos de servicio por la interfaz real con JWT y
// permisos municipales explícitos EMPRESAS_* y PERIODOS_*. Sin política de solapamiento inventada; sin permisos A4; D1 pendiente.
test('CU-04/CU-05: A1 gestiona empresas y periodos conservando historia; lector y cuenta sin permiso no modifican', { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.admin?.startsWith('emp-adm-') && d.lector?.startsWith('emp-lec-') && d.sinPermiso?.startsWith('emp-sin-') && d.clave);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', x => errores.push(x.name));
  const escrituras = [];
  page.on('request', r => { if (r.method() !== 'GET' && r.url().includes('/api/empresas-operadoras')) escrituras.push(`${r.method()} ${new URL(r.url()).pathname}`); });
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
  const formEmpresa = () => page.getByRole('form', { name: 'Datos de empresa' });
  const formPeriodo = () => page.getByRole('form', { name: 'Datos de periodo' });
  const periodos = () => page.locator('section[aria-labelledby="periodos-titulo"] li');
  const alerta = () => page.locator('main > p[role="alert"]');
  const guardado = () => page.getByRole('status').filter({ hasText: 'Cambios guardados.' }).waitFor();
  try {
    // ================= A1 con EMPRESAS_* y PERIODOS_* (sin PERSONAS_* ni VEHICULOS_*) =================
    await page.goto(new URL('/acceso', base).href);
    await ingresar(d.admin);
    await abrir();
    await fila(d.historica).waitFor();
    // Un permiso ausente de otra sección no oculta las autorizadas.
    await page.getByRole('note').filter({ hasText: 'Personas no disponibles:' }).waitFor();
    assert.equal(await alerta().count(), 0, 'La falta de PERSONAS_* no se presenta como error de empresas.');

    // ---- CU-04: registro, validación, NIT único, edición ----
    await formEmpresa().getByRole('button', { name: 'Crear empresa' }).click();
    assert.equal(escrituras.length, 0, 'Razón social obligatoria: sin petición.');
    await formEmpresa().getByLabel('Razón social').fill(d.nueva);
    await formEmpresa().getByLabel('NIT').fill(d.nit);
    await formEmpresa().getByLabel('Nombre comercial').fill('Comercial sintético');
    await formEmpresa().getByRole('button', { name: 'Crear empresa' }).click();
    await guardado();
    await fila(d.nueva).getByRole('cell', { name: d.nit, exact: true }).waitFor();
    await formEmpresa().getByLabel('Razón social').fill(d.duplicada);
    await formEmpresa().getByLabel('NIT').fill(d.nit);
    await formEmpresa().getByRole('button', { name: 'Crear empresa' }).click();
    await alerta().filter({ hasText: 'ya existe' }).waitFor();
    assert.equal(await fila(d.duplicada).count(), 0, 'NIT duplicado rechazado.');
    await fila(d.nueva).getByRole('button', { name: 'Editar empresa' }).click();
    await formEmpresa().getByRole('heading', { name: 'Editar empresa' }).waitFor();
    assert.equal(await formEmpresa().getByLabel('Razón social').inputValue(), d.nueva);
    await formEmpresa().getByLabel('Nombre comercial').fill('Comercial corregido');
    await formEmpresa().getByRole('button', { name: 'Guardar empresa' }).click();
    await guardado();

    // ---- CU-05: periodos de la empresa nueva (coexistentes permitidos, fechas inválidas rechazadas) ----
    await fila(d.nueva).getByRole('button', { name: 'Seleccionar empresa' }).click();
    await page.getByRole('heading', { name: `Empresa seleccionada: ${d.nueva}` }).waitFor();
    await page.getByRole('note').filter({ hasText: 'Personal no disponible:' }).waitFor();
    const crearPeriodo = async (inicio, fin) => {
      await formPeriodo().getByLabel('Inicio del periodo').fill(inicio);
      await formPeriodo().getByLabel('Fin del periodo').fill(fin);
      await formPeriodo().getByRole('button', { name: /^(Crear|Guardar) periodo$/ }).click();
    };
    await crearPeriodo('2027-01-01', '2027-12-31');
    await guardado();
    await periodos().filter({ hasText: '2027-01-01 — 2027-12-31 · Activo' }).waitFor();
    await crearPeriodo('2027-06-01', '2027-05-01');
    await alerta().filter({ hasText: /\S/ }).waitFor();
    assert.equal(await periodos().count(), 1, 'Fin anterior al inicio: rechazado sin crear periodo.');
    await crearPeriodo('2027-06-01', '2028-05-31');
    await guardado();
    await periodos().filter({ hasText: '2027-06-01 — 2028-05-31 · Activo' }).waitFor();
    assert.equal(await periodos().count(), 2, 'Periodos coexistentes: no se inventa prohibición de solapamiento.');
    await periodos().filter({ hasText: '2027-06-01 — 2028-05-31' }).getByRole('button', { name: 'Desactivar periodo' }).click();
    await periodos().filter({ hasText: '2027-06-01 — 2028-05-31 · Inactivo' }).waitFor();
    assert.equal(await periodos().count(), 2, 'Desactivar conserva el periodo.');

    // ---- CU-05 E3: corrección incompatible con una jornada ya registrada en el periodo histórico ----
    await fila(d.historica).getByRole('button', { name: 'Seleccionar empresa' }).click();
    await page.getByRole('heading', { name: `Empresa seleccionada: ${d.historica}` }).waitFor();
    const historico = periodos().filter({ hasText: '2026-01-01 — 2026-12-31' });
    await historico.getByRole('button', { name: 'Editar periodo' }).click();
    await formPeriodo().getByLabel('Fin del periodo').fill('2026-03-31');
    await formPeriodo().getByRole('button', { name: 'Guardar periodo' }).click();
    await alerta().filter({ hasText: 'jornada(s) ya registrada(s) fuera del periodo' }).waitFor();
    await periodos().filter({ hasText: '2026-01-01 — 2026-12-31 · Activo' }).waitFor();
    await formPeriodo().getByLabel('Fin del periodo').fill('2027-06-30');
    await formPeriodo().getByRole('button', { name: 'Guardar periodo' }).click();
    await guardado();
    await periodos().filter({ hasText: '2026-01-01 — 2027-06-30 · Activo' }).waitFor();

    // ---- CU-04: desactivación lógica conserva periodos ----
    await fila(d.historica).getByRole('button', { name: 'Desactivar empresa' }).click();
    await fila(d.historica).getByRole('cell', { name: 'Inactiva', exact: true }).waitFor();
    await fila(d.historica).getByRole('button', { name: 'Seleccionar empresa' }).click();
    await periodos().filter({ hasText: '2026-01-01 — 2027-06-30' }).waitFor();

    // ================= Lector (solo *_CONSULTAR): consulta, no modifica =================
    await salir();
    await ingresar(d.lector);
    await abrir();
    await fila(d.nueva).waitFor();
    const antes = escrituras.length;
    await formEmpresa().getByLabel('Razón social').fill(`Intento lector ${d.nit}`);
    await formEmpresa().getByRole('button', { name: 'Crear empresa' }).click();
    await alerta().filter({ hasText: /\S/ }).waitFor();
    assert.equal(escrituras.length, antes + 1);
    assert.equal(await fila(`Intento lector ${d.nit}`).count(), 0);

    // ================= Cuenta sin permisos =================
    await salir();
    await ingresar(d.sinPermiso);
    await abrir();
    await alerta().filter({ hasText: /\S/ }).waitFor();
    assert.equal(await page.locator('section[aria-labelledby="empresas-titulo"] tbody tr').count(), 0, 'Sin permiso: no se exponen empresas.');
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
