import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { chromium } from 'playwright';

// CU-11/CU-12, RF-07/RF-08; RF-20 parcial. Solo datos sintéticos, sin informes ni capturas.
test('Slice 3A: programar, reasignar y reprogramar preservando historia', { timeout: 120000 }, async () => {
  const marca = `SINTETICA-S3A-${randomUUID()}`;
  const empresas = [], personas = [], vehiculos = [], rutas = [], periodos = [];
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } });
  const headers = { 'X-GAMW-Development': 'gamw-desarrollo' };
  const errores = [];
  page.on('pageerror', e => errores.push(e.message));
  async function solicitud(ruta, datos) {
    const respuesta = datos === undefined
      ? await page.request.get(`http://127.0.0.1:5173/api${ruta}`, { headers })
      : await page.request.post(`http://127.0.0.1:5173/api${ruta}`, { headers, data: datos });
    assert.equal(respuesta.status(), datos === undefined ? 200 : 201, await respuesta.text());
    return respuesta.json();
  }
  async function guardar(boton, ruta) {
    const pendiente = page.waitForResponse(r => new URL(r.url()).pathname === `/api${ruta}` && r.request().method() === 'POST');
    await page.getByRole('button', { name: boton, exact: true }).click();
    const respuesta = await pendiente;
    assert.equal(respuesta.status(), 201, await respuesta.text());
    const datos = await respuesta.json();
    await page.getByRole('status').filter({ hasText: 'Planificación guardada y recuperada del servidor.' }).waitFor();
    return datos;
  }
  try {
    const empresa = await solicitud('/empresas-operadoras', { razonSocial: marca });
    empresas.push(empresa.id);
    const periodo = await solicitud(`/empresas-operadoras/${empresa.id}/periodos`, { fechaInicio: '2026-01-01', fechaFin: '2026-12-31' });
    periodos.push(periodo.id);
    const funcion = (await solicitud('/empresas-operadoras/funciones-operativas')).find(f => f.codigo === 'SUPERVISOR_EMPRESA');
    assert.ok(funcion);
    const vinculosPersonas = [];
    for (let i = 0; i < 2; i++) {
      const p = await solicitud('/personas', { ci: `${marca}-${i}-1A`, nombres: `Responsable sintético ${i}` });
      personas.push(p.id);
      const vinculo = await solicitud(`/empresas-operadoras/${empresa.id}/personal`, {
        personaId: p.id, fechaInicio: '2026-01-01', fechaFin: null, activo: true, funcionOperativaIds: [funcion.id],
      });
      vinculosPersonas.push(vinculo);
    }
    const vinculosVehiculos = [];
    for (let i = 0; i < 2; i++) {
      const v = await solicitud('/vehiculos', { placa: `${marca}-${i}` });
      vehiculos.push(v.id);
      vinculosVehiculos.push(await solicitud(`/empresas-operadoras/${empresa.id}/vehiculos`, {
        vehiculoId: v.id, fechaInicio: '2026-01-01', fechaFin: null, activo: true,
      }));
    }
    const ruta = await solicitud('/rutas', { codigo: marca, nombre: 'Ruta sintética no oficial para planificación',
      geometria: { type: 'LineString', coordinates: [[-10,20],[-11,21]] }, vigenteDesde: '2025-01-01T00:00:00Z' });
    rutas.push(ruta.ruta.id);

    await page.goto('http://127.0.0.1:5173/jornadas');
    await page.getByLabel('Periodo de servicio', { exact: true }).selectOption(periodo.id);
    assert.equal(await page.getByLabel('Empresa del periodo', { exact: true }).innerText(), marca);
    await page.getByLabel('Ruta de referencia', { exact: true }).selectOption(ruta.ruta.id);
    await page.getByLabel('Inicio programado', { exact: true }).fill('2026-05-10T20:00:00');
    await page.getByLabel('Fin programado', { exact: true }).fill('2026-05-11T01:00:00');
    await page.getByLabel('Versión de ruta', { exact: true }).selectOption(ruta.version.id);
    const j1 = await guardar('Programar jornada', '/jornadas');
    const id1 = j1.jornada.id;
    assert.equal(j1.empresaOperadora.id, empresa.id);
    assert.equal(j1.jornada.rutaVersionId, ruta.version.id);
    assert.equal(j1.jornada.jornadaAntecedenteId, null);
    assert.equal(j1.asignacionActual, null);
    const original = j1.jornada;

    await page.getByLabel('Vínculo de vehículo', { exact: true }).selectOption(vinculosVehiculos[0].id);
    await page.getByLabel('Vínculo de responsable', { exact: true }).selectOption(vinculosPersonas[0].id);
    const a1 = await guardar('Asignar recursos', `/jornadas/${id1}/asignaciones`);
    assert.equal(a1.motivoCambio, null);
    await page.getByRole('button', { name: 'Reasignar recursos', exact: true }).click();
    await page.getByLabel('Vínculo de vehículo', { exact: true }).selectOption(vinculosVehiculos[0].id);
    await page.getByLabel('Vínculo de responsable', { exact: true }).selectOption(vinculosPersonas[1].id);
    await page.getByLabel('Motivo de reasignación').fill('Cambio sintético de responsable');
    const a2 = await guardar('Confirmar reasignación', `/jornadas/${id1}/reasignaciones`);
    await page.getByRole('button', { name: 'Reasignar recursos', exact: true }).click();
    await page.getByLabel('Vínculo de vehículo', { exact: true }).selectOption(vinculosVehiculos[1].id);
    await page.getByLabel('Vínculo de responsable', { exact: true }).selectOption(vinculosPersonas[1].id);
    await page.getByLabel('Motivo de reasignación').fill('Cambio sintético de vehículo');
    const a3 = await guardar('Confirmar reasignación', `/jornadas/${id1}/reasignaciones`);
    const historia = await solicitud(`/jornadas/${id1}/asignaciones`);
    assert.equal(historia.length, 3);
    assert.equal(historia[0].id, a1.id); assert.equal(historia[1].id, a2.id); assert.equal(historia[2].id, a3.id);
    assert.equal(historia[0].vinculoPersonaEmpresaId, vinculosPersonas[0].id);
    assert.equal(historia[0].vigenteHasta, a2.vigenteDesde);
    assert.equal(historia[1].vigenteHasta, a3.vigenteDesde);
    assert.equal(historia[2].vigenteHasta, null);

    await page.getByRole('button', { name: 'Reprogramar jornada', exact: true }).click();
    await page.getByLabel('Inicio programado', { exact: true }).fill('2026-05-12T20:00:00');
    await page.getByLabel('Fin programado', { exact: true }).fill('2026-05-13T01:00:00');
    await page.getByLabel('Versión de ruta', { exact: true }).selectOption(ruta.version.id);
    await page.getByLabel('Motivo de reprogramación').fill('Reprogramación sintética');
    const j2 = await guardar('Confirmar reprogramación', `/jornadas/${id1}/reprogramaciones`);
    assert.notEqual(j2.jornada.id, id1);
    assert.equal(j2.jornada.jornadaAntecedenteId, id1);
    assert.equal(j2.asignacionActual, null);
    assert.deepEqual(await solicitud(`/jornadas/${j2.jornada.id}/asignaciones`), []);
    const anterior = await solicitud(`/jornadas/${id1}`);
    assert.equal(anterior.jornada.vigente, false);
    for (const campo of ['periodoServicioId','rutaVersionId','inicioProgramado','finProgramado','creadoEn']) assert.equal(anterior.jornada[campo], original[campo]);
    assert.deepEqual(await solicitud(`/jornadas/${id1}/asignaciones`), historia);
    await page.getByRole('button', { name: 'Consultar antecedente', exact: true }).click();
    await page.getByText('Programación histórica sustituida', { exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Reprogramar jornada', exact: true }).count(), 0);
    await page.getByRole('button', { name: 'Consultar sucesora', exact: true }).click();
    await page.getByText('Sin asignación de recursos', { exact: true }).waitFor();
    await page.reload();
    const filas = page.getByRole('row').filter({ hasText: marca });
    await filas.nth(1).getByRole('button', { name: 'Consultar jornada', exact: true }).click();
    await page.getByRole('button', { name: 'Consultar antecedente', exact: true }).waitFor();
    assert.deepEqual(await solicitud(`/jornadas/${id1}/asignaciones`), historia);
    assert.deepEqual(errores, []);
  } finally {
    await browser.close();
    for (const id of [...periodos, ...empresas, ...personas, ...vehiculos, ...rutas]) assert.match(id, /^[0-9a-f-]{36}$/);
    const sql = [
      ...periodos.flatMap(id => [`DELETE FROM asignacion_jornada WHERE jornada_id IN (SELECT id FROM jornada WHERE periodo_servicio_id='${id}');`, `DELETE FROM jornada WHERE periodo_servicio_id='${id}';`]),
      ...empresas.flatMap(id => [
        `DELETE FROM vinculo_persona_funcion WHERE vinculo_persona_empresa_id IN (SELECT id FROM vinculo_persona_empresa WHERE empresa_operadora_id='${id}');`,
        `DELETE FROM vinculo_persona_empresa WHERE empresa_operadora_id='${id}';`,
        `DELETE FROM vinculo_vehiculo_empresa WHERE empresa_operadora_id='${id}';`,
        `DELETE FROM periodo_servicio WHERE empresa_operadora_id='${id}';`,
        `DELETE FROM empresa_operadora WHERE id='${id}';`,
      ]),
      ...personas.map(id => `DELETE FROM persona_natural WHERE id='${id}';`),
      ...vehiculos.map(id => `DELETE FROM vehiculo WHERE id='${id}';`),
      ...rutas.flatMap(id => [`DELETE FROM ruta_version WHERE ruta_id='${id}';`, `DELETE FROM ruta WHERE id='${id}';`]),
    ].join('\n');
    if (sql) execFileSync('docker', ['compose','-f','compose.dev.yml','exec','-T','db','psql','-X','-U','gamw_app_dev','-d','gamw_residuos_dev','-v','ON_ERROR_STOP=1','-c',sql],
      { cwd: new URL('../../../backend/', import.meta.url), stdio: 'pipe' });
  }
});
