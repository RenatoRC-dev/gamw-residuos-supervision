import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { chromium } from 'playwright';

// CU-04/CU-05/CU-07 parcial. Solo datos sintéticos propios; sin informes ni capturas.
test('Slice 2A: registro operativo e historia persona-empresa desde la web', { timeout: 90000 }, async () => {
  const marca = `SINTETICA-S2A-${randomUUID()}`;
  const empresasPropias = [];
  const personasPropias = [];
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  const headers = { 'X-GAMW-Development': 'gamw-desarrollo' };
  const errores = [];
  page.on('pageerror', e => errores.push(e.message));
  async function leer(ruta) {
    const respuesta = await page.request.get(`http://127.0.0.1:5173/api${ruta}`, { headers });
    assert.equal(respuesta.status(), 200); return respuesta.json();
  }
  async function guardar(boton, ruta, metodo, estado) {
    const pendiente = page.waitForResponse(r => new URL(r.url()).pathname === `/api${ruta}` && r.request().method() === metodo);
    await page.getByRole('button', { name: boton, exact: true }).click();
    const respuesta = await pendiente;
    assert.equal(respuesta.status(), estado, await respuesta.text());
    if (metodo === 'PUT' && ruta.includes('/personal/')) {
      assert.equal(Object.hasOwn(respuesta.request().postDataJSON(), 'funcionOperativaIds'), false);
    }
    const datos = await respuesta.json();
    await page.getByRole('status').filter({ hasText: 'Cambios guardados.' }).waitFor();
    return datos;
  }
  try {
    await page.goto('http://127.0.0.1:5173/operadores');
    await page.getByLabel('Razón social').fill(marca);
    const empresa = await guardar('Crear empresa', '/empresas-operadoras', 'POST', 201);
    empresasPropias.push(empresa.id);
    assert.equal(empresa.nit, null);
    const filaEmpresa = page.getByRole('row').filter({ hasText: marca });
    await filaEmpresa.getByRole('button', { name: 'Editar empresa', exact: true }).click();
    await page.getByLabel('NIT', { exact: true }).fill(`000-${marca}`);
    await page.getByLabel('Nombre comercial').fill('Prueba sintética no oficial');
    await guardar('Guardar empresa', `/empresas-operadoras/${empresa.id}`, 'PUT', 200);

    await page.getByLabel('CI', { exact: true }).fill(`${marca}-1A`);
    await page.getByLabel('Nombres', { exact: true }).fill('Persona sintética');
    const persona = await guardar('Crear persona', '/personas', 'POST', 201);
    personasPropias.push(persona.id);
    assert.equal(persona.ci, `${marca}-1A`);
    assert.equal(persona.apellidoPaterno, null);
    assert.equal(persona.apellidoMaterno, null);
    await page.getByRole('button', { name: 'Editar persona', exact: true }).click();
    await page.getByLabel('Teléfono', { exact: true }).fill('001-ABC');
    await guardar('Guardar persona', `/personas/${persona.id}`, 'PUT', 200);
    assert.equal((await guardar('Desactivar persona', `/personas/${persona.id}/estado`, 'PATCH', 200)).activo, false);
    assert.equal((await guardar('Activar persona', `/personas/${persona.id}/estado`, 'PATCH', 200)).activo, true);
    const identidad = await leer(`/personas/${persona.id}`);

    await page.getByLabel('Inicio del periodo').fill('2026-01-01');
    await page.getByLabel('Fin del periodo').fill('2026-12-31');
    const periodo = await guardar('Crear periodo', `/empresas-operadoras/${empresa.id}/periodos`, 'POST', 201);
    await page.getByRole('button', { name: 'Editar periodo', exact: true }).click();
    await page.getByLabel('Fin del periodo').fill('2027-12-31');
    await guardar('Guardar periodo', `/empresas-operadoras/${empresa.id}/periodos/${periodo.id}`, 'PUT', 200);
    assert.equal((await guardar('Desactivar periodo', `/empresas-operadoras/${empresa.id}/periodos/${periodo.id}/estado`, 'PATCH', 200)).activo, false);
    assert.equal((await guardar('Activar periodo', `/empresas-operadoras/${empresa.id}/periodos/${periodo.id}/estado`, 'PATCH', 200)).activo, true);

    await page.getByLabel('Persona del vínculo').selectOption(persona.id);
    await page.getByLabel('Inicio del vínculo').fill('2026-01-01');
    await page.getByRole('checkbox', { name: 'Conductor', exact: true }).check();
    await page.getByRole('checkbox', { name: 'Supervisor de empresa', exact: true }).check();
    const vinculo = await guardar('Asociar persona', `/empresas-operadoras/${empresa.id}/personal`, 'POST', 201);
    assert.equal(vinculo.funcionesOperativas.length, 2);
    await page.getByRole('button', { name: 'Editar vínculo', exact: true }).click();
    await page.getByLabel('Fin del vínculo').fill('2026-02-01');
    await page.getByRole('checkbox', { name: 'Vínculo activo', exact: true }).uncheck();
    const cerrado = await guardar('Guardar vínculo', `/empresas-operadoras/${empresa.id}/personal/${vinculo.id}`, 'PUT', 200);
    assert.equal(cerrado.fechaFin, '2026-02-01'); assert.equal(cerrado.activo, false);
    assert.deepEqual(cerrado.funcionesOperativas, vinculo.funcionesOperativas);
    assert.deepEqual(await leer(`/personas/${persona.id}`), identidad);

    await page.getByLabel('Inicio del vínculo').fill('2026-03-01');
    await page.getByRole('checkbox', { name: 'Conductor', exact: true }).check();
    const segundo = await guardar('Asociar persona', `/empresas-operadoras/${empresa.id}/personal`, 'POST', 201);
    assert.notEqual(segundo.id, vinculo.id);
    assert.equal(segundo.funcionesOperativas.length, 1);
    const historial = await leer(`/empresas-operadoras/${empresa.id}/personal`);
    assert.deepEqual(historial.find(v => v.id === vinculo.id).funcionesOperativas, vinculo.funcionesOperativas);
    assert.equal((await leer(`/empresas-operadoras/${empresa.id}/personal`)).length, 2);

    await page.getByLabel('Razón social').fill(`${marca}-SEGUNDA`);
    const otra = await guardar('Crear empresa', '/empresas-operadoras', 'POST', 201);
    empresasPropias.push(otra.id);
    await page.getByLabel('Persona del vínculo').selectOption(persona.id);
    await page.getByLabel('Inicio del vínculo').fill('2026-04-01');
    await page.getByRole('checkbox', { name: 'Supervisor de empresa', exact: true }).check();
    const otro = await guardar('Asociar persona', `/empresas-operadoras/${otra.id}/personal`, 'POST', 201);
    assert.equal(otro.personaId, persona.id);

    const periodosAntes = await leer(`/empresas-operadoras/${empresa.id}/periodos`);
    const personalAntes = await leer(`/empresas-operadoras/${empresa.id}/personal`);
    const filaPrimera = page.getByRole('row').filter({ hasText: `000-${marca}` });
    const desactivacion = page.waitForResponse(r => r.url().endsWith(`/api/empresas-operadoras/${empresa.id}/estado`) && r.request().method() === 'PATCH');
    await filaPrimera.getByRole('button', { name: 'Desactivar empresa', exact: true }).click();
    assert.equal((await (await desactivacion).json()).activo, false);
    await page.getByRole('status').filter({ hasText: 'Cambios guardados.' }).waitFor();
    assert.deepEqual(await leer(`/empresas-operadoras/${empresa.id}/periodos`), periodosAntes);
    assert.deepEqual(await leer(`/empresas-operadoras/${empresa.id}/personal`), personalAntes);
    assert.deepEqual(await leer(`/personas/${persona.id}`), identidad);
    const reactivacion = page.waitForResponse(r => r.url().endsWith(`/api/empresas-operadoras/${empresa.id}/estado`) && r.request().method() === 'PATCH');
    await filaPrimera.getByRole('button', { name: 'Activar empresa', exact: true }).click();
    assert.equal((await (await reactivacion).json()).activo, true);
    await page.getByRole('status').filter({ hasText: 'Cambios guardados.' }).waitFor();
    await page.reload();
    await page.getByRole('row').filter({ hasText: `000-${marca}` }).getByRole('button', { name: 'Seleccionar empresa', exact: true }).click();
    await page.getByRole('region', { name: 'Personal de la empresa', exact: true }).getByRole('button', { name: 'Editar vínculo', exact: true }).nth(1).waitFor();
    assert.deepEqual(await leer(`/empresas-operadoras/${empresa.id}/personal`), personalAntes);
    assert.deepEqual(errores, []);
  } finally {
    await browser.close();
    for (const id of [...empresasPropias, ...personasPropias]) assert.match(id, /^[0-9a-f-]{36}$/);
    const sql = [
      ...empresasPropias.flatMap(id => [
        `DELETE FROM vinculo_persona_funcion WHERE vinculo_persona_empresa_id IN (SELECT id FROM vinculo_persona_empresa WHERE empresa_operadora_id='${id}');`,
        `DELETE FROM vinculo_persona_empresa WHERE empresa_operadora_id='${id}';`,
        `DELETE FROM periodo_servicio WHERE empresa_operadora_id='${id}';`,
        `DELETE FROM empresa_operadora WHERE id='${id}';`,
      ]),
      ...personasPropias.map(id => `DELETE FROM persona_natural WHERE id='${id}';`),
    ].join('\n');
    if (sql) execFileSync('docker', ['compose', '-f', 'compose.dev.yml', 'exec', '-T', 'db', 'psql', '-X',
      '-U', 'gamw_app_dev', '-d', 'gamw_residuos_dev', '-v', 'ON_ERROR_STOP=1', '-c', sql],
    { cwd: new URL('../../../backend/', import.meta.url), stdio: 'pipe' });
  }
});
