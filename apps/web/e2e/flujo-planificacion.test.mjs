import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía PlanificacionOperadorNavegadorIT. CU-11/CU-12 (A4): programación y asignación/reasignación por la interfaz real con JWT y
// concesiones de PERIODO. Programar y asignar NO implican ejecución ni cumplimiento. CU-24 (reprogramación) queda fuera de este corte.
test('CU-11/12: A4 programa, asigna y reasigna en el navegador (idempotente, aislado por periodo)', { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const e = process.env;
  const { clave, operador, otro, periodo, version, inicio } = {
    clave: e.GAMW_WEB_TEST_PASSWORD, operador: e.GAMW_WEB_TEST_OPERADOR, otro: e.GAMW_WEB_TEST_OPERADOR_OTRO,
    periodo: e.GAMW_WEB_TEST_PERIODO, version: e.GAMW_WEB_TEST_VERSION, inicio: e.GAMW_WEB_TEST_INICIO,
  };
  const vehiculos = [e.GAMW_WEB_TEST_PLACA_1, e.GAMW_WEB_TEST_PLACA_2], personas = [e.GAMW_WEB_TEST_PERSONA_1, e.GAMW_WEB_TEST_PERSONA_2];
  const noElegibles = [e.GAMW_WEB_TEST_PLACA_OTRA_EMPRESA, e.GAMW_WEB_TEST_PLACA_INACTIVA, e.GAMW_WEB_TEST_PLACA_VENCIDA, e.GAMW_WEB_TEST_PERSONA_OTRA_EMPRESA];
  assert.ok(operador?.startsWith('plan-op-') && otro?.startsWith('plan-opb-') && clave && periodo && version && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(inicio));
  assert.ok(vehiculos.every(Boolean) && personas.every(Boolean) && noElegibles.every(Boolean));
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', x => errores.push(x.name));
  const posts = { programar: [], asignar: [] };
  page.on('request', r => {
    if (r.method() !== 'POST') return;
    if (r.url().endsWith(`/api/empresa/periodos/${periodo}/jornadas`)) posts.programar.push(r.postData() ?? '');
    if (/\/api\/empresa\/jornadas\/[0-9a-f-]{36}\/(asignaciones|reasignaciones)$/.test(r.url())) posts.asignar.push(r.postData() ?? '');
  });
  const solicitud = c => /"solicitudId":"([0-9a-f-]{36})"/.exec(c)?.[1];
  const ingresar = async (nombre) => {
    await page.goto(new URL('/acceso', base).href);
    await page.getByLabel('Nombre de usuario', { exact: true }).fill(nombre);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    const login = page.waitForResponse(r => r.url().endsWith('/api/autenticacion/login'));
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    assert.equal((await login).status(), 200);
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
  };
  const abrirPlanificacion = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Reprogramar jornadas de periodos autorizados' }).click();
    await page.getByRole('heading', { name: 'Programación, recursos y reprogramación por periodo autorizado' }).waitFor();
    await page.getByText('Cargando jornadas del alcance autorizado…').waitFor({ state: 'detached' });
  };
  const opciones = (etiqueta) => page.evaluate(t => {
    const l = [...document.querySelectorAll('label')].find(x => x.textContent.startsWith(t));
    return l ? [...l.querySelector('select').options].map(o => o.textContent) : null;
  }, etiqueta);
  try {
    // ================= Operador A4 del periodo =================
    await ingresar(operador);
    await abrirPlanificacion();
    // Solo su periodo concedido (más el marcador).
    await page.waitForFunction(() => { const l = [...document.querySelectorAll('label')].find(x => x.textContent.startsWith('Periodo')); return l && l.querySelector('select').options.length >= 2; });
    assert.equal((await opciones('Periodo')).length, 2, 'Solo el periodo expresamente concedido.');
    await page.getByLabel('Periodo').selectOption(periodo);
    const programar = page.locator('form').filter({ has: page.getByRole('button', { name: /^(Crear programación sin copiar recursos|Reintentar misma programación)$/ }) });
    await programar.waitFor();
    await page.waitForFunction(v => [...document.querySelectorAll('select[name=version] option')].some(o => o.value === v), version);
    // Campos obligatorios: sin completar no se envía nada.
    await programar.getByRole('button', { name: 'Crear programación sin copiar recursos' }).click();
    assert.equal(posts.programar.length, 0);
    assert.equal(await programar.evaluate(f => f.checkValidity()), false);
    await programar.getByLabel('Versión de referencia').selectOption(version);
    await programar.getByLabel('Inicio (Bolivia UTC−04:00)').fill(inicio);
    // Primer envío con fallo de transporte (respuesta incierta): el reintento reutiliza la MISMA solicitud.
    await page.route(`**/api/empresa/periodos/${periodo}/jornadas`, route => route.abort('failed'), { times: 1 });
    const intento = page.waitForRequest(r => r.method() === 'POST' && r.url().endsWith(`/api/empresa/periodos/${periodo}/jornadas`));
    await programar.getByRole('button', { name: 'Crear programación sin copiar recursos' }).click();
    await intento;
    await programar.getByRole('button', { name: 'Reintentar misma programación' }).click();
    await page.getByRole('heading', { name: /^Programación histórica [0-9a-f-]{36}$/ }).waitFor();
    assert.equal(posts.programar.length, 2, 'Un envío fallido y un reintento.');
    assert.ok(solicitud(posts.programar[0]) && solicitud(posts.programar[0]) === solicitud(posts.programar[1]), 'El reintento reenvía la misma solicitud.');
    assert.ok(!/empresa/i.test(posts.programar[0]), 'La empresa no viaja en el cuerpo: se deriva del periodo.');
    // La programación no implica ejecución ni cumplimiento, y no asigna recursos.
    await page.getByText('Sin asignaciones; reprogramar no asigna recursos automáticamente.').waitFor();
    await page.getByText('Disponibilidad técnica de recursos: SIN_ASIGNACIONES.', { exact: false }).waitFor();
    // (La limitación institucional «Jornadas ejecutadas o fiscalizadas bloqueadas…» es una advertencia, no un estado.)
    assert.equal(await page.getByText(/\b(cumple|incumple)\b|\ben ejecución\b/i).count(), 0);

    // ---------- CU-12: asignación (recursos elegibles de la empresa derivada del periodo) ----------
    const asignar = page.locator('form').filter({ has: page.getByRole('heading', { name: 'Asignar recursos expresamente' }) });
    await asignar.waitFor();
    await page.waitForFunction(() => { const l = [...document.querySelectorAll('label')].find(x => x.textContent.startsWith('Vehículo')); return l && l.querySelector('select').options.length >= 3; });
    const listaVehiculos = (await opciones('Vehículo')).join('|'), listaPersonas = (await opciones('Responsable')).join('|');
    for (const v of vehiculos) assert.ok(listaVehiculos.includes(v), `Elegible: ${v}`);
    for (const p of personas) assert.ok(listaPersonas.includes(p), `Elegible: ${p}`);
    for (const x of noElegibles) assert.ok(!listaVehiculos.includes(x) && !listaPersonas.includes(x), `No elegible (otra empresa, inactivo o vínculo vencido): ${x}`);
    // Obligatorios: sin completar no hay petición.
    await asignar.getByRole('button', { name: 'Asignar a esta jornada' }).click();
    assert.equal(posts.asignar.length, 0);
    assert.equal(await asignar.evaluate(f => f.checkValidity()), false);
    const elegir = async (formulario, etiqueta, texto) => {
      const valor = await page.evaluate(([t, x]) => { const l = [...document.querySelectorAll('label')].find(y => y.textContent.startsWith(t)); return [...l.querySelector('select').options].find(o => o.textContent.includes(x)).value; }, [etiqueta, texto]);
      await formulario.getByLabel(etiqueta).selectOption(valor);
    };
    await elegir(asignar, 'Vehículo', vehiculos[0]);
    await elegir(asignar, 'Responsable', personas[0]);
    await asignar.getByLabel('Confirmo asignación en esta jornada; los antecedentes permanecen intactos.').check();
    await page.route(/\/api\/empresa\/jornadas\/[0-9a-f-]{36}\/asignaciones$/, route => route.abort('failed'), { times: 1 });
    const intentoAsignar = page.waitForRequest(r => r.method() === 'POST' && /\/asignaciones$/.test(r.url()));
    await asignar.getByRole('button', { name: 'Asignar a esta jornada' }).click();
    await intentoAsignar;
    await asignar.getByRole('button', { name: 'Reintentar misma asignación' }).click();
    await page.getByRole('heading', { name: 'Reasignar con historial' }).waitFor(); // ya hay una asignación abierta
    assert.equal(posts.asignar.length, 2, 'Un envío fallido y un reintento.');
    assert.ok(solicitud(posts.asignar[0]) === solicitud(posts.asignar[1]) && solicitud(posts.asignar[0]), 'El reintento reenvía la misma solicitud.');
    await page.locator('p', { hasText: `${vehiculos[0]} · ${personas[0]} · asignación` }).filter({ hasText: 'Abierta' }).waitFor();

    // ---------- Reasignación con historial (una sola asignación abierta) ----------
    const reasignar = page.locator('form').filter({ has: page.getByRole('heading', { name: 'Reasignar con historial' }) });
    await elegir(reasignar, 'Vehículo', vehiculos[1]);
    await elegir(reasignar, 'Responsable', personas[1]);
    await reasignar.getByLabel('Motivo obligatorio').fill('Cambio sintético de recurso por avería.');
    await reasignar.getByLabel('Confirmo asignación en esta jornada; los antecedentes permanecen intactos.').check();
    await reasignar.getByRole('button', { name: 'Registrar reasignación histórica' }).click();
    await page.locator('p', { hasText: `${vehiculos[1]} · ${personas[1]} · asignación` }).filter({ hasText: 'Abierta' }).waitFor();
    const lineas = page.locator('p').filter({ hasText: /· asignación [0-9a-f-]{36}:/ });
    assert.equal(await lineas.count(), 2, 'Historial: la asignación anterior se conserva.');
    assert.equal(await page.locator('p').filter({ hasText: /· asignación [0-9a-f-]{36}:.*Abierta/ }).count(), 1, 'Exactamente UNA asignación abierta.');
    assert.equal(await page.locator('p').filter({ hasText: `${vehiculos[0]} · ${personas[0]} · asignación` }).filter({ hasText: 'Abierta' }).count(), 0, 'La anterior quedó cerrada, no borrada.');
    assert.equal(await page.getByText(/\b(cumple|incumple)\b|\ben ejecución\b/i).count(), 0);

    // ================= Otro operador (otro periodo/empresa) =================
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await page.getByRole('button', { name: 'Ingresar', exact: true }).waitFor();
    await ingresar(otro);
    await abrirPlanificacion();
    await page.waitForFunction(() => { const l = [...document.querySelectorAll('label')].find(x => x.textContent.startsWith('Periodo')); return l && l.querySelector('select').options.length >= 2; });
    const periodosOtro = await opciones('Periodo');
    assert.equal(periodosOtro.length, 2, 'Solo SU periodo.');
    assert.ok(!periodosOtro.join('|').includes(periodo), 'No ve el periodo del otro operador.');
    assert.equal((await opciones('Jornada')).length, 1, 'No ve jornadas de otro periodo (solo el marcador).');
    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } catch (fallo) {
    // Diagnóstico (no oculta el fallo): primera línea del error, URL y texto visible, para entender fallos que solo ocurren en CI.
    console.log(`DIAG fallo: ${String(fallo?.message ?? fallo).split('\n')[0].slice(0, 300)}`);
    console.log(`DIAG url: ${page.url()}`);
    console.log(`DIAG texto: ${(await page.locator('main').innerText().catch(() => 'sin main')).replace(/\s+/g, ' ').slice(0, 700)}`);
    throw fallo;
  } finally {
    await browser.close();
  }
});
