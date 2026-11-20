import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía PuntosCriticosNavegadorIT. CU-28 (A3): registro de un candidato a punto crítico por la interfaz real con JWT, rechazo de
// coordenadas/antecedentes inválidos, reintento idempotente, 503 sin falso éxito, observaciones con historia, validación PENDING-10 cerrada
// para el validador y consulta de solo lectura. El candidato nunca se presenta como punto crítico oficial. Coordenadas sintéticas.
test('CU-28: A3 registra y observa un candidato; validación PENDING-10 cerrada; el lector consulta sin actuar', { timeout: 170000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.registrador?.startsWith('pc-reg-') && d.validador?.startsWith('pc-val-') && d.lector?.startsWith('pc-lec-') && d.clave && d.reclamo && d.inspeccion);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', x => errores.push(`${x.name}: ${x.message}`));
  const registros = [], observaciones = [];
  page.on('request', r => {
    if (r.method() !== 'POST') return;
    if (r.url().endsWith('/api/puntos-criticos')) registros.push(r.postData() ?? '');
    if (/\/api\/puntos-criticos\/[0-9a-f-]{36}\/observaciones$/.test(r.url())) observaciones.push(r.postData() ?? '');
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
  const abrirPuntos = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Gestionar reclamos institucionales y puntos críticos' }).click();
    const pestana = page.getByRole('button', { name: 'Puntos y observaciones', exact: true });
    await pestana.waitFor();
    await pestana.click();
    await page.getByRole('heading', { name: 'Referencias críticas y candidatos municipales', exact: true }).waitFor();
  };
  const formulario = titulo => page.locator('form').filter({ has: page.getByRole('heading', { name: titulo, exact: true }) });
  const diag = async (f, e) => {
    const estado = await f.evaluate(x => ({ botones: [...x.querySelectorAll('button')].map(b => [b.textContent, b.disabled]),
      fieldset: x.querySelector('fieldset')?.disabled, alerta: x.querySelector('[role=alert]')?.textContent })).catch(() => 'sin formulario');
    const estados = await page.locator('[role=status],[role=alert]').allTextContents().catch(() => []);
    console.log(`DIAG formulario: ${JSON.stringify(estado)} · mensajes: ${JSON.stringify(estados)}`);
    throw e;
  };
  const enviar = async f => { try { await f.getByRole('button', { name: /^(Registrar|Reintentar misma solicitud)$/ }).click({ timeout: 15000 }); } catch (e) { await diag(f, e); } };
  const llenar = async (f, etiqueta, valor) => { try { await f.getByLabel(etiqueta, { exact: true }).fill(valor, { timeout: 15000 }); } catch (e) { await diag(f, e); } };
  const mensaje = async texto => {
    try { await page.locator('[role=status]').filter({ hasText: texto }).first().waitFor({ timeout: 15000 }); }
    catch (e) { console.log(`DIAG mensajes: ${JSON.stringify(await page.locator('[role=status],[role=alert]').allTextContents())}`); throw e; }
  };
  const candidato = 'Candidato sin validación municipal; no es referencia confirmada ni zona roja.';
  const nombre = `Candidato sintético ${d.marca}`;
  // Fecha observada en hora de Bolivia (UTC-4), dos horas antes de ahora, con segundos (datetime-local step=1).
  const observada = new Date(Date.now() - 6 * 3600 * 1000).toISOString().slice(0, 19);
  try {
    // ================= Registrador municipal (consulta + registro de puntos; consulta de reclamos e inspecciones) =================
    await page.goto(new URL('/acceso', base).href);
    await ingresar(d.registrador);
    await abrirPuntos();
    const alta = formulario('Registrar ubicación para revisión municipal');
    await alta.waitFor();
    await page.getByText('Este registro no confirma por sí solo un punto crítico oficial; requiere actuación explícita de validación.').waitFor();
    await llenar(alta, 'Denominación', nombre);
    await llenar(alta, 'Descripción', `Acumulación observada en vía pública ${d.marca}`);
    await llenar(alta, 'Observación/sustento municipal', `Observación directa del fiscalizador ${d.marca}`);
    await llenar(alta, 'Longitud (EPSG:4326)', String(d.longitud));
    // Latitud fuera de rango: el navegador no envía la solicitud.
    await llenar(alta, 'Latitud (EPSG:4326)', '95');
    await enviar(alta);
    assert.equal(registros.length, 0, 'Coordenada fuera de EPSG:4326 no se envía.');
    await llenar(alta, 'Latitud (EPSG:4326)', String(d.latitud));
    // Reclamo inexistente: 400 definitivo; el formulario queda editable para corregir.
    await llenar(alta, 'Reclamo de origen (UUID opcional)', '00000000-0000-4000-8000-0000000000aa');
    const invalido = page.waitForResponse(r => r.url().endsWith('/api/puntos-criticos') && r.request().method() === 'POST');
    await enviar(alta);
    assert.equal((await invalido).status(), 400);
    await alta.getByText('Revise los campos. La solicitud inválida no se confirma.').waitFor();
    assert.equal(await alta.getByRole('button', { name: 'Reintentar misma solicitud' }).count(), 0, 'Un rechazo definitivo no fija la solicitud.');
    await llenar(alta, 'Reclamo de origen (UUID opcional)', d.reclamo);
    await llenar(alta, 'Inspección de soporte (UUID opcional, requiere permiso de consulta)', d.inspeccion);
    // Fallo de transporte: los datos quedan fijados y el reintento reutiliza la solicitud.
    await page.route('**/api/puntos-criticos', route => route.request().method() === 'POST' ? route.abort('failed') : route.continue(), { times: 1 });
    await enviar(alta);
    await alta.getByRole('button', { name: 'Reintentar misma solicitud' }).waitFor();
    const confirmado = page.waitForResponse(r => r.url().endsWith('/api/puntos-criticos') && r.request().method() === 'POST');
    await enviar(alta);
    const creado = await confirmado;
    assert.equal(creado.status(), 200);
    const punto = (await creado.json()).punto;
    assert.equal(registros.length, 3, 'Un rechazo, un envío fallido y un reintento.');
    assert.ok(solicitud(registros[1]) && solicitud(registros[1]) === solicitud(registros[2]), 'El reintento reenvía la misma solicitud.');
    assert.notEqual(solicitud(registros[0]), solicitud(registros[1]), 'El rechazo definitivo libera la solicitud.');
    await mensaje('Registro confirmado; origen e historia conservados.');
    // Detalle: coordenadas EPSG:4326 [lon, lat], autoría, antecedentes y estado explícito de candidato.
    await page.getByRole('heading', { name: nombre, exact: true }).waitFor();
    await page.getByText(`Coordenadas [longitud, latitud]: [${d.longitud}, ${d.latitud}] · EPSG:4326.`, { exact: true }).waitFor();
    await page.getByText(`registrante ${d.registradorId}`, { exact: false }).waitFor();
    await page.getByText(`Reclamo ${d.reclamo} · inspección ${d.inspeccion}.`, { exact: true }).waitFor();
    await page.getByText(candidato, { exact: true }).waitFor();
    await page.locator(`option[value="${punto.id}"]`).filter({ hasText: `${nombre} · Candidato sin validación` }).waitFor({ state: 'attached' });
    assert.equal(await page.getByText('Validado municipalmente').count(), 0);
    // Sin facultad de validar: no hay formulario de validación.
    assert.equal(await page.getByRole('heading', { name: 'Validar identificación municipal' }).count(), 0);

    // ---- Observación: el servidor responde 503: sin falso éxito; el reintento confirma una sola vez ----
    const obs = formulario('Agregar observación sin borrar antecedentes');
    await obs.waitFor();
    await llenar(obs, 'Fecha observada (Bolivia)', observada);
    await llenar(obs, 'Observación', `Persiste acumulación ${d.marca}`);
    await llenar(obs, 'Reclamo relacionado (UUID opcional)', d.reclamo);
    await page.route(`**/api/puntos-criticos/${punto.id}/observaciones`, route => route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"status":503}' }), { times: 1 });
    await enviar(obs);
    await mensaje('Operación no confirmada');
    assert.equal(await page.getByText(`Persiste acumulación ${d.marca}`, { exact: false }).count(), 0, 'Sin observación mostrada ante 503.');
    await obs.getByRole('button', { name: 'Reintentar misma solicitud' }).waitFor();
    await enviar(obs);
    const historia = page.locator('section section p').filter({ hasText: `Persiste acumulación ${d.marca} · observada` });
    await historia.waitFor();
    const textoHistoria = await historia.textContent();
    assert.ok(textoHistoria.includes(`autor ${d.registradorId}`) && textoHistoria.includes(`reclamo ${d.reclamo}`) && textoHistoria.includes('inspección No asociada'), textoHistoria);
    assert.equal(observaciones.length, 2);
    assert.equal(solicitud(observaciones[0]), solicitud(observaciones[1]), 'El reintento tras 503 reutiliza la solicitud.');
    // Una observación con reclamo asociado no oficializa el candidato.
    await page.getByText(candidato, { exact: true }).waitFor();
    await salir();

    // ================= Validador (consulta + validación): PENDING-10 cerrado, sin registro =================
    await ingresar(d.validador);
    await abrirPuntos();
    const selector = page.locator(`select:has(option[value="${punto.id}"])`);
    await selector.waitFor({ state: 'attached', timeout: 20000 });
    await selector.selectOption(punto.id);
    await page.getByRole('heading', { name: nombre, exact: true }).waitFor();
    await page.getByText('Validación bloqueada hasta habilitar política institucional PENDING-10. No se inventan criterios ni categorías.').waitFor();
    await page.getByText(candidato, { exact: true }).waitFor();
    assert.equal(await page.getByRole('heading', { name: 'Validar identificación municipal' }).count(), 0, 'Sin política no hay formulario de validación.');
    assert.equal(await page.getByRole('heading', { name: 'Registrar ubicación para revisión municipal' }).count(), 0);
    assert.equal(await page.getByRole('heading', { name: 'Agregar observación sin borrar antecedentes' }).count(), 0);
    await salir();

    // ================= Lector (solo PUNTO_CRITICO_CONSULTAR): historia completa, sin acciones =================
    await ingresar(d.lector);
    await abrirPuntos();
    const lectura = page.locator(`select:has(option[value="${punto.id}"])`);
    await lectura.waitFor({ state: 'attached', timeout: 20000 });
    await lectura.selectOption(punto.id);
    await page.getByRole('heading', { name: nombre, exact: true }).waitFor();
    await page.getByText(`Coordenadas [longitud, latitud]: [${d.longitud}, ${d.latitud}] · EPSG:4326.`, { exact: true }).waitFor();
    await page.locator('section section p').filter({ hasText: `Persiste acumulación ${d.marca} · observada` }).waitFor();
    await page.getByText(candidato, { exact: true }).waitFor();
    for (const titulo of ['Registrar ubicación para revisión municipal', 'Agregar observación sin borrar antecedentes', 'Validar identificación municipal'])
      assert.equal(await page.getByRole('heading', { name: titulo }).count(), 0, `El lector no ve «${titulo}».`);
    assert.equal(await page.getByText('Validación bloqueada hasta habilitar').count(), 0, 'Sin facultad de validar no se ofrece la acción.');
    const alertas = (await page.locator('[role=alert]').allTextContents()).filter(t => t.trim());
    assert.deepEqual(alertas, [], 'El lector no ve errores.');

    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } finally {
    await browser.close();
  }
});
