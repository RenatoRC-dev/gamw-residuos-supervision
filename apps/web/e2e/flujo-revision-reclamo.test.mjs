import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía RevisionReclamosNavegadorIT. CU-27 (A3): revisión municipal de un reclamo institucional por la interfaz real con JWT:
// observaciones fácticas sin resultado oficial (catálogo pendiente), vínculos justificados con antecedentes existentes, rechazo de vínculos
// interjornada/inexistentes, reintento idempotente, respuesta 503 sin falso éxito y consulta de la historia con solo RECLAMO_CONSULTAR.
test('CU-27: A3 revisa y vincula un reclamo en el navegador; el lector consulta la historia sin actuar', { timeout: 170000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.revisor?.startsWith('rv-rev-') && d.lector?.startsWith('rv-lec-') && d.clave && d.reclamo && d.jornada1 && d.jornada2 && d.inspeccion);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', x => errores.push(`${x.name}: ${x.message}`));
  const revisiones = [], relaciones = [];
  page.on('request', r => {
    if (r.method() !== 'POST') return;
    if (r.url().endsWith(`/api/reclamos-institucionales/${d.reclamo}/revisiones`)) revisiones.push(r.postData() ?? '');
    if (r.url().endsWith(`/api/reclamos-institucionales/${d.reclamo}/relaciones`)) relaciones.push(r.postData() ?? '');
  });
  const solicitud = c => /"solicitudId":"([0-9a-f-]{36})"/.exec(c)?.[1];
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
  const abrirReclamo = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Gestionar reclamos institucionales y puntos críticos' }).click();
    // El nombre accesible del select incluye la opción elegida; se ubica por la opción del reclamo propio (primera página).
    const selector = page.locator(`select:has(option[value="${d.reclamo}"])`);
    try { await selector.waitFor({ state: 'attached', timeout: 20000 }); }
    catch (e) {
      const estado = await page.evaluate(() => ({ selects: [...document.querySelectorAll('select')].map(s => s.options.length),
        alertas: [...document.querySelectorAll('[role=alert],[role=status]')].map(x => x.textContent) }));
      console.log(`DIAG selector de reclamos: ${JSON.stringify(estado)}`);
      throw e;
    }
    await selector.selectOption(d.reclamo);
    await page.getByRole('heading', { name: `Reclamo ${d.reclamo}`, exact: true }).waitFor();
  };
  const formulario = titulo => page.locator('form').filter({ has: page.getByRole('heading', { name: titulo, exact: true }) });
  const diag = async (f, e) => {
    const estado = await f.evaluate(x => ({ botones: [...x.querySelectorAll('button')].map(b => [b.textContent, b.disabled]),
      fieldset: x.querySelector('fieldset')?.disabled, alerta: x.querySelector('[role=alert]')?.textContent })).catch(() => 'sin formulario');
    const estados = await page.locator('[role=status]').allTextContents().catch(() => []);
    console.log(`DIAG formulario: ${JSON.stringify(estado)} · estados: ${JSON.stringify(estados)}`);
    throw e;
  };
  const enviar = async f => { try { await f.getByRole('button', { name: /^(Registrar|Reintentar misma solicitud)$/ }).click({ timeout: 15000 }); } catch (e) { await diag(f, e); } };
  const llenar = async (f, etiqueta, valor) => { try { await f.getByLabel(etiqueta, { exact: true }).fill(valor, { timeout: 15000 }); } catch (e) { await diag(f, e); } };
  const mensaje = async texto => {
    try { await page.locator('[role=status]').filter({ hasText: texto }).first().waitFor({ timeout: 15000 }); }
    catch (e) { console.log(`DIAG mensajes: ${JSON.stringify(await page.locator('[role=status],[role=alert]').allTextContents())}`); throw e; }
  };
  try {
    // ================= Revisor municipal (RECLAMO_CONSULTAR + RECLAMO_REVISAR) =================
    await page.goto(new URL('/acceso', base).href);
    await ingresar(d.revisor);
    await abrirReclamo();
    // Procedencia original visible y sin validación.
    await page.getByText(`Canal Teléfono institucional (declarado) · recepción ${d.recibidoEn}`, { exact: false }).waitFor();
    await page.getByText(`registrante ${d.registradorId}`, { exact: false }).waitFor();
    await page.getByText('Sin validación municipal registrada; consulte las valoraciones disponibles.').waitFor();
    // Catálogo vacío: solo observación, sin resultados inventados.
    const revision = formulario('Agregar revisión humana');
    await revision.waitFor();
    await page.getByText('Catálogo aprobado pendiente: puede documentar revisión, no inventar resultados.').waitFor();
    assert.deepEqual(await revision.getByLabel('Resultado habilitado').locator('option').allTextContents(), ['Solo observación, sin decisión final']);

    // ---- Observación 1: fallo de transporte y reintento con la misma solicitud ----
    await llenar(revision, 'Observaciones', `Contrastado con la ruta; falta horario ${d.marca} 1`);
    await page.route(`**/api/reclamos-institucionales/${d.reclamo}/revisiones`, route => route.abort('failed'), { times: 1 });
    await enviar(revision);
    await revision.getByRole('button', { name: 'Reintentar misma solicitud' }).waitFor();
    await enviar(revision);
    await page.locator('article').filter({ hasText: `Observación sin decisión de catálogo · Contrastado con la ruta; falta horario ${d.marca} 1` }).waitFor();
    await page.locator('article').filter({ hasText: `Autor ${d.revisorId}` }).first().waitFor();
    assert.equal(revisiones.length, 2, 'Un envío fallido y un reintento.');
    assert.ok(solicitud(revisiones[0]) && solicitud(revisiones[0]) === solicitud(revisiones[1]), 'El reintento reenvía la misma solicitud.');
    assert.equal(await page.locator('article').filter({ hasText: 'Observación sin decisión de catálogo' }).count(), 1, 'Una sola revisión registrada.');

    // ---- Observación 2: el servidor responde 503: no hay falso éxito; el reintento confirma ----
    await llenar(revision, 'Observaciones', `Se pedirá información adicional ${d.marca} 2`);
    await page.route(`**/api/reclamos-institucionales/${d.reclamo}/revisiones`, route => route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"status":503}' }), { times: 1 });
    await enviar(revision);
    await mensaje('Operación no confirmada');
    assert.equal(await page.getByText('Registro confirmado; origen e historia conservados.').count(), 0, 'Sin confirmación ante 503.');
    await revision.getByRole('button', { name: 'Reintentar misma solicitud' }).waitFor();
    assert.equal(await page.locator('article').filter({ hasText: `${d.marca} 2` }).count(), 0);
    await enviar(revision);
    await page.locator('article').filter({ hasText: `Observación sin decisión de catálogo · Se pedirá información adicional ${d.marca} 2` }).waitFor();
    assert.equal(solicitud(revisiones[2]), solicitud(revisiones[3]), 'El reintento tras 503 reutiliza la solicitud.');

    // ---- Vínculos: jornada justificada; interjornada rechazada y corregible; inexistente; inspección de la misma jornada ----
    const vinculo = formulario('Vincular antecedente comprobable');
    const antecedente = texto => page.locator('section section p').filter({ hasText: texto });
    await vinculo.locator('select[name=tipo]').selectOption('JORNADA');
    await llenar(vinculo, 'UUID existente', d.jornada1);
    await llenar(vinculo, 'Fundamento de la relación', `Lugar y horario coinciden con la jornada ${d.marca}`);
    await enviar(vinculo);
    await antecedente(`JORNADA · ${d.jornada1} · Lugar y horario coinciden con la jornada ${d.marca} · actor ${d.revisorId}`).waitFor();

    await vinculo.locator('select[name=tipo]').selectOption('JORNADA');
    await llenar(vinculo, 'UUID existente', d.jornada2);
    await llenar(vinculo, 'Fundamento de la relación', 'Jornada distinta sin política interjornada');
    const conflicto = page.waitForResponse(r => r.url().endsWith(`/${d.reclamo}/relaciones`) && r.request().method() === 'POST');
    await enviar(vinculo);
    assert.equal((await conflicto).status(), 409);
    await mensaje('Referencia incompatible, registro repetido');
    await vinculo.getByText('Rechazada por conflicto con el historial; no se registró.', { exact: false }).waitFor();
    assert.equal(await vinculo.getByRole('button', { name: 'Reintentar misma solicitud' }).count(), 0, 'Un rechazo definitivo no fija la solicitud para reintento.');
    assert.equal(await antecedente(d.jornada2).count(), 0, 'El vínculo rechazado no se muestra.');

    // Tras un rechazo definitivo (409) el formulario debe permitir corregir los datos.
    await vinculo.locator('select[name=tipo]').selectOption('INSPECCION', { timeout: 15000 }).catch(e => diag(vinculo, e));
    await llenar(vinculo, 'UUID existente', '00000000-0000-4000-8000-0000000000aa');
    await llenar(vinculo, 'Fundamento de la relación', 'Inspección inexistente');
    await enviar(vinculo);
    await vinculo.getByText('Revise los campos. La solicitud inválida no se confirma.').waitFor();
    await llenar(vinculo, 'UUID existente', d.inspeccion);
    await llenar(vinculo, 'Fundamento de la relación', `La inspección verificó el mismo tramo ${d.marca}`);
    await enviar(vinculo);
    await antecedente(`INSPECCION · ${d.inspeccion} · La inspección verificó el mismo tramo ${d.marca}`).waitFor();
    assert.equal(relaciones.length, 4);
    // La jornada original del reclamo no se reescribe por los vínculos.
    await page.getByText('jornada original No identificada', { exact: false }).waitFor();
    await page.getByText('Sin validación municipal registrada; consulte las valoraciones disponibles.').waitFor();
    await salir();

    // ================= Lector (solo RECLAMO_CONSULTAR): historia completa, sin acciones =================
    await ingresar(d.lector);
    await abrirReclamo();
    await page.locator('article').filter({ hasText: `${d.marca} 1` }).waitFor();
    await page.locator('article').filter({ hasText: `${d.marca} 2` }).waitFor();
    await antecedente(`JORNADA · ${d.jornada1}`).waitFor();
    await antecedente(`INSPECCION · ${d.inspeccion}`).waitFor();
    assert.equal(await page.getByRole('heading', { name: 'Agregar revisión humana' }).count(), 0);
    assert.equal(await page.getByRole('heading', { name: 'Vincular antecedente comprobable' }).count(), 0);
    const alertas = (await page.locator('[role=alert]').allTextContents()).filter(t => t.trim());
    assert.deepEqual(alertas, [], 'El lector no ve errores.');

    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } finally {
    await browser.close();
  }
});
