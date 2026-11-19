import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Únicamente vía ReprogramacionOperadorNavegadorIT. CU-24 (A4): reprogramación como jornada sucesora por la interfaz real con JWT y concesión de PERIODO.
// La sucesora no hereda recursos, eventos, GPS ni resultados; las jornadas con evidencia permanecen bloqueadas. No hay política oficial ni aprobación automática.
test('CU-24: A4 reprograma dos veces en el navegador (cadena histórica, idempotente, sin herencia, bloqueo con evidencia)', { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const e = process.env;
  const { clave, operador, otro, consulta, j0, je, base0, ini1, ini2 } = {
    clave: e.GAMW_WEB_TEST_PASSWORD, operador: e.GAMW_WEB_TEST_OPERADOR, otro: e.GAMW_WEB_TEST_OPERADOR_OTRO, consulta: e.GAMW_WEB_TEST_CONSULTA,
    j0: e.GAMW_WEB_TEST_J0, je: e.GAMW_WEB_TEST_JE, base0: e.GAMW_WEB_TEST_INICIO_0, ini1: e.GAMW_WEB_TEST_INICIO_1, ini2: e.GAMW_WEB_TEST_INICIO_2,
  };
  assert.ok(operador?.startsWith('rep-op-') && otro?.startsWith('rep-opb-') && consulta?.startsWith('rep-con-') && clave && j0 && je);
  for (const f of [base0, ini1, ini2]) assert.match(f, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', x => errores.push(x.name));
  const posts = [];
  page.on('request', r => { if (r.method() === 'POST' && /\/api\/empresa\/jornadas\/[0-9a-f-]{36}\/reprogramaciones$/.test(r.url())) posts.push(r.postData() ?? ''); });
  const solicitud = c => /"solicitudId":"([0-9a-f-]{36})"/.exec(c)?.[1];
  const ingresar = async nombre => {
    await page.goto(new URL('/acceso', base).href);
    await page.getByLabel('Nombre de usuario', { exact: true }).fill(nombre);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    const login = page.waitForResponse(r => r.url().endsWith('/api/autenticacion/login'));
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    assert.equal((await login).status(), 200);
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
  };
  const abrir = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Reprogramar jornadas de periodos autorizados' }).click();
    await page.getByRole('heading', { name: 'Programación, recursos y reprogramación por periodo autorizado' }).waitFor();
    await page.getByText('Cargando jornadas del alcance autorizado…').waitFor({ state: 'detached' });
  };
  const salir = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await page.getByRole('button', { name: 'Ingresar', exact: true }).waitFor();
  };
  const cuantasJornadas = () => page.evaluate(() => {
    const l = [...document.querySelectorAll('label')].find(x => x.textContent.startsWith('Jornada'));
    return l ? l.querySelector('select').options.length - 1 : -1;
  });
  const seleccionar = async id => {
    await page.locator('select').filter({ has: page.locator(`option[value="${id}"]`) }).selectOption(id);
    await page.getByRole('heading', { name: `Programación histórica ${id}` }).waitFor();
  };
  const formulario = () => page.locator('form').filter({ has: page.getByRole('heading', { name: 'Crear jornada sucesora' }) });
  const id1Ref = {};
  try {
    // ================= A4 del periodo =================
    await ingresar(operador);
    await abrir();
    await page.waitForFunction(() => { const l = [...document.querySelectorAll('label')].find(x => x.textContent.startsWith('Jornada')); return l && l.querySelector('select').options.length >= 3; });
    await seleccionar(j0);
    await page.getByText('Programación inicial').waitFor();
    await page.getByRole('heading', { name: 'Asignaciones conservadas' }).waitFor();
    assert.equal(await page.locator('p').filter({ hasText: /· asignación [0-9a-f-]{36}:.*Abierta/ }).count(), 1, 'El antecedente conserva su asignación.');
    let f = formulario();
    await f.waitFor();

    // Sin motivo: la interfaz no emite petición.
    await f.getByLabel('Nuevo inicio').fill(ini1);
    await f.getByLabel('Confirmo nueva programación sin trasladar evidencia ni recursos históricos.').check();
    await f.getByRole('button', { name: 'Crear sucesora' }).click();
    assert.equal(posts.length, 0);
    assert.equal(await f.evaluate(x => x.checkValidity()), false);

    // Fechas inválidas (fin anterior al inicio): el servidor rechaza, la interfaz no confirma y se puede corregir.
    await f.getByLabel('Motivo obligatorio').fill('Motivo sintético 1: reprogramación solicitada por la empresa.');
    await f.getByLabel('Nuevo fin (opcional)').fill(base0);
    const rechazo = page.waitForResponse(r => /\/reprogramaciones$/.test(r.url()));
    await f.getByRole('button', { name: 'Crear sucesora' }).click();
    assert.equal((await rechazo).status(), 400, 'Fin anterior al inicio.');
    assert.equal(posts.length, 1);
    await f.getByRole('button', { name: 'Crear sucesora' }).waitFor();
    assert.equal(await page.getByRole('button', { name: /^Consultar sucesora/ }).count(), 0, 'Un rechazo no crea sucesora.');
    await f.getByLabel('Nuevo fin (opcional)').fill('');

    // Envío con fallo de transporte incierto: el reintento reutiliza la MISMA solicitud.
    await page.route(/\/api\/empresa\/jornadas\/[0-9a-f-]{36}\/reprogramaciones$/, route => route.abort('failed'), { times: 1 });
    const intento = page.waitForRequest(r => r.method() === 'POST' && /\/reprogramaciones$/.test(r.url()));
    await f.getByRole('button', { name: 'Crear sucesora' }).click();
    await intento;
    await f.getByRole('button', { name: 'Reintentar misma reprogramación' }).click();
    const aviso = page.locator('p[role=status]').filter({ hasText: /^Sucesora registrada: / });
    await aviso.waitFor();
    assert.equal(posts.length, 3, 'Rechazo + envío fallido + reintento.');
    assert.ok(solicitud(posts[1]) && solicitud(posts[1]) === solicitud(posts[2]), 'El reintento reenvía la misma solicitud.');
    assert.notEqual(solicitud(posts[0]), solicitud(posts[1]), 'Tras un rechazo definitivo, se usa una solicitud nueva.');
    for (const p of posts) assert.ok(!/"(periodo\w*|rutaVersion\w*|empresa\w*)"\s*:/i.test(p), 'Periodo, versión y empresa proceden del servidor.');
    const j1 = /Sucesora registrada: ([0-9a-f-]{36})/.exec(await aviso.innerText())[1];
    id1Ref.j1 = j1;

    // El antecedente queda como antecedente conservado, enlazado a su sucesora y sin permitir otra reprogramación.
    await page.getByText('Antecedente conservado').waitFor();
    await page.getByRole('button', { name: `Consultar sucesora ${j1}` }).waitFor();
    await page.getByText('Reprogramación no habilitada para este antecedente.', { exact: false }).waitFor();
    assert.equal(await page.getByRole('heading', { name: 'Crear jornada sucesora' }).count(), 0);

    // Sucesora: mismo periodo y versión, antecedente enlazado, motivo; sin recursos heredados y sin resultado alguno.
    await page.getByRole('button', { name: 'Consultar nueva jornada' }).click();
    await page.getByRole('heading', { name: `Programación histórica ${j1}` }).waitFor();
    await page.getByText('Motivo de origen: Motivo sintético 1: reprogramación solicitada por la empresa.').waitFor();
    await page.getByRole('button', { name: `Consultar antecedente ${j0}` }).waitFor();
    await page.getByText('Sin asignaciones; reprogramar no asigna recursos automáticamente.').waitFor();
    await page.getByText('Disponibilidad técnica de recursos: SIN_ASIGNACIONES.', { exact: false }).waitFor();
    assert.equal(await page.locator('p').filter({ hasText: /· asignación [0-9a-f-]{36}:/ }).count(), 0);
    assert.equal(await page.getByText(/\b(cumple|incumple)\b|\ben ejecución\b/i).count(), 0);

    // ---- Segunda reprogramación: la cadena completa se conserva ----
    f = formulario();
    await f.waitFor();
    await f.getByLabel('Nuevo inicio').fill(ini2);
    await f.getByLabel('Nuevo fin (opcional)').fill('');
    await f.getByLabel('Motivo obligatorio').fill('Motivo sintético 2: nueva reprogramación.');
    await f.getByLabel('Confirmo nueva programación sin trasladar evidencia ni recursos históricos.').check();
    await f.getByRole('button', { name: 'Crear sucesora' }).click();
    const aviso2 = page.locator('p[role=status]').filter({ hasText: /^Sucesora registrada: / });
    await aviso2.waitFor();
    assert.equal(posts.length, 4);
    const j2 = /Sucesora registrada: ([0-9a-f-]{36})/.exec(await aviso2.innerText())[1];
    assert.notEqual(j2, j1);
    await page.getByRole('button', { name: `Consultar sucesora ${j2}` }).waitFor();
    // Recorrido de la cadena por la interfaz: J2 → J1 → J0.
    await page.getByRole('button', { name: 'Consultar nueva jornada' }).click();
    await page.getByRole('heading', { name: `Programación histórica ${j2}` }).waitFor();
    await page.getByText('Motivo de origen: Motivo sintético 2: nueva reprogramación.').waitFor();
    await page.getByRole('button', { name: `Consultar antecedente ${j1}` }).click();
    await page.getByRole('heading', { name: `Programación histórica ${j1}` }).waitFor();
    await page.getByText('Motivo de origen: Motivo sintético 1: reprogramación solicitada por la empresa.').waitFor();
    await page.getByRole('button', { name: `Consultar antecedente ${j0}` }).click();
    await page.getByRole('heading', { name: `Programación histórica ${j0}` }).waitFor();
    await page.getByText('Motivo de origen: Programación inicial').waitFor();
    await page.getByRole('button', { name: `Consultar sucesora ${j1}` }).waitFor();
    assert.equal(await page.locator('p').filter({ hasText: /· asignación [0-9a-f-]{36}:.*Abierta/ }).count(), 1, 'La asignación original permanece intacta.');

    // ---- Jornada con evidencia: bloqueada ----
    await seleccionar(je);
    await page.getByText('Reprogramación no habilitada para este antecedente.', { exact: false }).waitFor();
    assert.equal(await page.getByRole('heading', { name: 'Crear jornada sucesora' }).count(), 0);
    assert.equal(posts.length, 4, 'Ninguna petición adicional.');

    // ================= Operador de consulta (sin JORNADA_REPROGRAMAR) =================
    await salir();
    await ingresar(consulta);
    await abrir();
    await page.waitForFunction(() => { const l = [...document.querySelectorAll('label')].find(x => x.textContent.startsWith('Jornada')); return l && l.querySelector('select').options.length >= 2; });
    await seleccionar(j2);
    await page.getByText('Reprogramación no habilitada para este antecedente.', { exact: false }).waitFor();
    assert.equal(await page.getByRole('heading', { name: 'Crear jornada sucesora' }).count(), 0, 'Consultar no habilita reprogramar.');

    // ================= Otro operador (otro periodo) =================
    await salir();
    await ingresar(otro);
    await abrir();
    assert.equal(await cuantasJornadas(), 0, 'No ve jornadas de otro periodo.');
    assert.equal(await page.locator(`option[value="${j0}"], option[value="${j1}"], option[value="${j2}"], option[value="${je}"]`).count(), 0);
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
