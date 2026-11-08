import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { chromium } from 'playwright';

// CU-06 / RF-04. Catálogos y registros sintéticos propios; sin informes, logs ni capturas.
test('Slice 2B: un vehículo conserva sus vínculos A → B → A', { timeout: 120000 }, async () => {
  const marca = randomUUID();
  const empresas = [], marcas = [], modelos = [], tipos = [], vehiculos = [];
  const nombreA = `Empresa A sintética ${marca}`;
  const nombreB = `Empresa B sintética ${marca}`;
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  const errores = [];
  page.on('pageerror', error => errores.push(error.message));
  const headers = { 'X-GAMW-Development': 'gamw-desarrollo' };
  async function leer(ruta) {
    const respuesta = await page.request.get(`http://127.0.0.1:5173/api${ruta}`, { headers });
    assert.equal(respuesta.status(), 200); return respuesta.json();
  }
  async function guardar(boton, ruta, metodo, estado, vehicular = true) {
    const pendiente = page.waitForResponse(r => new URL(r.url()).pathname === `/api${ruta}` && r.request().method() === metodo);
    await page.getByRole('button', { name: boton, exact: true }).click();
    const respuesta = await pendiente;
    assert.equal(respuesta.status(), estado, await respuesta.text());
    if (metodo === 'PUT' && ruta.includes('/empresas-operadoras/')) {
      assert.deepEqual(Object.keys(respuesta.request().postDataJSON()).sort(), ['activo', 'fechaFin']);
    }
    const datos = await respuesta.json();
    await page.getByRole('status').filter({ hasText: vehicular ? 'Cambios vehiculares guardados.' : 'Cambios guardados.' }).waitFor();
    return datos;
  }
  try {
    await page.goto('http://127.0.0.1:5173/operadores');
    await page.getByLabel('Razón social', { exact: true }).fill(nombreA);
    const empresaA = await guardar('Crear empresa', '/empresas-operadoras', 'POST', 201, false);
    empresas.push(empresaA.id);
    const seccion = page.getByRole('region', { name: 'Vehículos de la empresa', exact: true });
    await seccion.getByText('Crear marca, modelo o tipo necesario para el registro', { exact: true }).click();
    await page.getByLabel('Nombre de marca', { exact: true }).fill(`Marca sintética ${marca}`);
    const marcaCreada = await guardar('Crear marca', '/vehiculos/marcas', 'POST', 201);
    marcas.push(marcaCreada.id);
    await page.getByLabel('Marca del nuevo modelo').selectOption(marcaCreada.id);
    await page.getByLabel('Nombre de modelo', { exact: true }).fill(`Modelo sintético ${marca}`);
    const modelo = await guardar('Crear modelo', '/vehiculos/modelos', 'POST', 201);
    modelos.push(modelo.id);
    await page.getByLabel('Código de tipo').fill(`SINTETICO-${marca}`);
    await page.getByLabel('Nombre de tipo').fill(`Tipo sintético ${marca}`);
    const tipo = await guardar('Crear tipo', '/vehiculos/tipos', 'POST', 201);
    tipos.push(tipo.id);

    const placa = `001-${marca}`;
    await page.getByLabel('Placa', { exact: true }).fill(placa);
    await page.getByLabel('Año', { exact: true }).fill('2026');
    await page.getByLabel('Capacidad de carga (kg)').fill('1050.125');
    await page.getByLabel('Capacidad de volumen (m³)').fill('12.345');
    const vehiculo = await guardar('Crear vehículo', '/vehiculos', 'POST', 201);
    vehiculos.push(vehiculo.id);
    assert.equal(vehiculo.placa, placa.toUpperCase());
    assert.equal(vehiculo.modeloVehiculoId, modelo.id);
    assert.equal(vehiculo.tipoVehiculoId, tipo.id);
    assert.equal(vehiculo.capacidadCargaKg, 1050.125);
    assert.equal(vehiculo.capacidadVolumenM3, 12.345);
    assert.equal(Object.hasOwn(vehiculo, 'empresaOperadoraId'), false);
    assert.equal(Object.hasOwn(vehiculo, 'conductorId'), false);

    assert.equal(await page.getByLabel('Vehículo del vínculo', { exact: true }).count(), 1,
      'Después de registrar el vehículo debe estar disponible el formulario de vínculo.');
    await page.getByLabel('Vehículo del vínculo', { exact: true }).selectOption(vehiculo.id);
    await page.getByLabel('Inicio del vínculo vehicular').fill('2026-01-01');
    const primero = await guardar('Vincular vehículo', `/empresas-operadoras/${empresaA.id}/vehiculos`, 'POST', 201);
    await seccion.getByRole('button', { name: 'Editar cierre vehicular', exact: true }).click();
    assert.equal(await page.getByLabel('Inicio del vínculo vehicular').count(), 0);
    assert.equal(await page.getByLabel('Vehículo del vínculo', { exact: true }).count(), 0);
    await page.getByLabel('Fin del vínculo vehicular').fill('2027-12-31');
    await page.getByRole('checkbox', { name: 'Vínculo vehicular activo', exact: true }).uncheck();
    const cerrado = await guardar('Guardar cierre vehicular', `/empresas-operadoras/${empresaA.id}/vehiculos/${primero.id}`, 'PUT', 200);
    assert.equal(cerrado.id, primero.id); assert.equal(cerrado.vehiculoId, vehiculo.id);
    assert.equal(cerrado.empresaOperadoraId, empresaA.id); assert.equal(cerrado.fechaInicio, '2026-01-01');
    assert.equal(cerrado.activo, false);
    assert.deepEqual(await leer(`/vehiculos/${vehiculo.id}`), vehiculo);

    await page.getByRole('button', { name: 'Editar vehículo', exact: true }).click();
    await page.getByLabel('Capacidad de carga (kg)').fill('2000.5');
    const actualizado = await guardar('Guardar vehículo', `/vehiculos/${vehiculo.id}`, 'PUT', 200);
    assert.equal(actualizado.id, vehiculo.id); assert.equal(actualizado.capacidadCargaKg, 2000.5);
    assert.deepEqual(await leer(`/empresas-operadoras/${empresaA.id}/vehiculos`), [cerrado]);
    assert.equal((await guardar('Desactivar vehículo', `/vehiculos/${vehiculo.id}/estado`, 'PATCH', 200)).activo, false);
    assert.equal((await guardar('Activar vehículo', `/vehiculos/${vehiculo.id}/estado`, 'PATCH', 200)).activo, true);

    await page.getByLabel('Razón social', { exact: true }).fill(nombreB);
    const empresaB = await guardar('Crear empresa', '/empresas-operadoras', 'POST', 201, false);
    empresas.push(empresaB.id);
    await page.getByLabel('Vehículo del vínculo', { exact: true }).selectOption(vehiculo.id);
    await page.getByLabel('Inicio del vínculo vehicular').fill('2028-01-01');
    const segundo = await guardar('Vincular vehículo', `/empresas-operadoras/${empresaB.id}/vehiculos`, 'POST', 201);
    assert.equal(segundo.vehiculoId, vehiculo.id); assert.notEqual(segundo.id, primero.id);
    await page.getByRole('row').filter({ hasText: nombreA }).getByRole('button', { name: 'Seleccionar empresa', exact: true }).click();
    await page.getByLabel('Vehículo del vínculo', { exact: true }).selectOption(vehiculo.id);
    await page.getByLabel('Inicio del vínculo vehicular').fill('2029-01-01');
    const tercero = await guardar('Vincular vehículo', `/empresas-operadoras/${empresaA.id}/vehiculos`, 'POST', 201);
    assert.equal(tercero.vehiculoId, vehiculo.id);
    const historialA = await leer(`/empresas-operadoras/${empresaA.id}/vehiculos`);
    assert.equal(historialA.length, 2); assert.deepEqual(historialA.find(v => v.id === primero.id), cerrado);
    assert.deepEqual(await leer(`/empresas-operadoras/${empresaB.id}/vehiculos`), [segundo]);
    assert.equal((await leer('/vehiculos')).filter(v => v.id === vehiculo.id).length, 1);
    await page.reload();
    await page.getByRole('row').filter({ hasText: nombreA }).getByRole('button', { name: 'Seleccionar empresa', exact: true }).click();
    await page.getByRole('region', { name: 'Vehículos de la empresa', exact: true }).getByRole('button', { name: 'Editar cierre vehicular', exact: true }).nth(1).waitFor();
    assert.deepEqual(await leer(`/empresas-operadoras/${empresaA.id}/vehiculos`), historialA);
    assert.deepEqual(errores, []);
  } finally {
    await browser.close();
    for (const id of [...empresas, ...vehiculos, ...marcas, ...modelos, ...tipos]) assert.match(id, /^[0-9a-f-]{36}$/);
    const sql = [
      ...vehiculos.flatMap(id => [`DELETE FROM vinculo_vehiculo_empresa WHERE vehiculo_id='${id}';`, `DELETE FROM vehiculo WHERE id='${id}';`]),
      ...modelos.map(id => `DELETE FROM modelo_vehiculo WHERE id='${id}';`),
      ...marcas.map(id => `DELETE FROM marca_vehiculo WHERE id='${id}';`),
      ...tipos.map(id => `DELETE FROM tipo_vehiculo WHERE id='${id}';`),
      ...empresas.map(id => `DELETE FROM empresa_operadora WHERE id='${id}';`),
    ].join('\n');
    if (sql) execFileSync('docker', ['compose', '-f', 'compose.dev.yml', 'exec', '-T', 'db', 'psql', '-X',
      '-U', 'gamw_app_dev', '-d', 'gamw_residuos_dev', '-v', 'ON_ERROR_STOP=1', '-c', sql],
    { cwd: new URL('../../../backend/', import.meta.url), stdio: 'pipe' });
  }
});
