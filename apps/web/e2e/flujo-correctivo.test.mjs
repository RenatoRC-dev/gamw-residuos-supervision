import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { elegirJornadaPaginada } from './paginacion.mjs';

// Únicamente vía FlujoMunicipalNavegadorIT. Fase 1: el fiscalizador solicita la acción correctiva en la interfaz. La atención de la empresa se
// registra por REST (no por la UI) entre fases. Fase 2: el fiscalizador verifica en la interfaz con una categoría SINTÉTICA de prueba (no oficial)
// y una cuenta que solo puede solicitar ve el historial sin poder verificar.
test(`Flujo correctivo (fase ${process.env.GAMW_WEB_TEST_FASE}): solicitud y verificación humana en el navegador`, { timeout: 140000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const fase = process.env.GAMW_WEB_TEST_FASE;
  const { clave, jornada, incidencia, categoria } = {
    clave: process.env.GAMW_WEB_TEST_PASSWORD, jornada: process.env.GAMW_WEB_TEST_JORNADA,
    incidencia: process.env.GAMW_WEB_TEST_INCIDENCIA, categoria: process.env.GAMW_WEB_TEST_CATEGORIA,
  };
  const fiscal = process.env.GAMW_WEB_TEST_FISCAL_CORRECTIVO, solo = process.env.GAMW_WEB_TEST_SOLO_SOLICITA;
  assert.ok(fiscal?.startsWith('flujo-fc-') && solo?.startsWith('flujo-solo-') && clave && jornada && incidencia && categoria);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [];
  page.on('pageerror', e => errores.push(e.name));
  const ingresar = async (nombre) => {
    await page.goto(new URL('/acceso', base).href);
    await page.getByLabel('Nombre de usuario', { exact: true }).fill(nombre);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    const respuesta = page.waitForResponse(r => r.url().endsWith('/api/autenticacion/login'));
    await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
    assert.equal((await respuesta).status(), 200);
    await page.getByRole('heading', { name: 'Usuario autenticado', exact: true }).waitFor();
  };
  // La sesión vive solo en memoria: se navega con enlaces de la aplicación, sin recargar.
  const abrirCorrectivas = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Incidencias y actuaciones correctivas' }).click();
    await elegirJornadaPaginada(page, 'Jornada', 'Siguiente', jornada);
    await page.getByRole('heading', { name: 'Incidencias operativas' }).waitFor();
  };
  const salir = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await page.getByRole('button', { name: 'Ingresar', exact: true }).waitFor();
  };
  const abrirAtencionEmpresarial = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Atender correctivas de periodos autorizados' }).click();
    await page.getByRole('heading', { name: 'Atención empresarial de acciones correctivas' }).waitFor();
  };
  // Número de opciones del selector de acciones (incluye el marcador «Seleccione»), tras terminar la consulta de acciones autorizadas.
  const opcionesAcciones = async () => {
    await page.getByText('Consultando acciones autorizadas…').waitFor({ state: 'detached' });
    return page.evaluate(() => {
      const etiqueta = [...document.querySelectorAll('label')].find(l => l.textContent.startsWith('Acción correctiva'));
      return etiqueta.querySelector('select').options.length;
    });
  };
  // CU-22 (actor A4): la atención se registra por la INTERFAZ real con JWT y concesión de periodo.
  const operadorA4 = async () => {
    const operador = process.env.GAMW_WEB_TEST_OPERADOR, otro = process.env.GAMW_WEB_TEST_OPERADOR_OTRO;
    assert.ok(operador?.startsWith('flujo-op-') && otro?.startsWith('flujo-opb-'));
    const cuerpos = [];
    page.on('request', r => { if (r.method() === 'POST' && r.url().includes('/api/empresa/correctivas/') && r.url().endsWith('/respuestas')) cuerpos.push(r.postData()); });
    await ingresar(operador);
    await abrirAtencionEmpresarial();
    assert.equal(await opcionesAcciones(), 2, 'Solo la acción del periodo concedido (más el marcador).');
    await page.getByLabel('Acción correctiva').selectOption({ index: 1 });
    await page.getByRole('heading', { name: 'Solicitud municipal' }).waitFor();
    await page.locator('section p', { hasText: 'Retirar el bloqueo y comunicar la evidencia de atención.' }).waitFor(); // solicitud original conservada
    await page.getByText('Sin respuesta empresarial registrada.').waitFor();
    await page.getByText('Sin verificación; la atención no se declara validada automáticamente.').waitFor();
    // El operador NO tiene ninguna superficie de verificación municipal.
    assert.equal(await page.getByRole('button', { name: /Registrar verificación/ }).count(), 0);
    assert.equal(await page.getByRole('heading', { name: /verificación municipal/i }).count(), 0);
    // Campos obligatorios: sin completar no se envía nada.
    const formulario = page.locator('form').filter({ has: page.getByRole('heading', { name: 'Comunicar atención empresarial' }) });
    await page.getByRole('button', { name: 'Registrar atención', exact: true }).click();
    assert.equal(cuerpos.length, 0, 'Formulario incompleto: ninguna petición.');
    assert.equal(await formulario.evaluate(f => f.checkValidity()), false);
    // Fecha/hora en horario de Bolivia (UTC−04:00), no futura.
    const bolivia = new Date(Date.now() - 4 * 3600e3 - 5 * 60e3).toISOString().slice(0, 19);
    await page.getByLabel('Fecha/hora de comunicación (Bolivia UTC−04:00)').fill(bolivia);
    await page.getByLabel('Observaciones y atención realizada').fill('Atención sintética: bloqueo retirado y contenedor liberado.');
    await page.getByLabel('Confirmo la respuesta empresarial, sin sustituir la solicitud ni emitir una verificación municipal.').check();
    // Primer envío con fallo de transporte (respuesta incierta): el reintento debe reutilizar la MISMA solicitud (idempotencia).
    await page.route('**/api/empresa/correctivas/*/respuestas', route => route.abort('failed'), { times: 1 });
    await page.getByRole('button', { name: 'Registrar atención', exact: true }).click();
    await page.getByRole('button', { name: 'Reintentar misma respuesta' }).waitFor();
    await page.getByText('Solicitud fijada para reintento idempotente', { exact: false }).waitFor();
    await page.getByRole('button', { name: 'Reintentar misma respuesta' }).click();
    await page.getByText('Respuesta registrada. No equivale a verificación municipal.').waitFor();
    await page.getByRole('heading', { name: /^Respuesta / }).waitFor();
    assert.equal(cuerpos.length, 2, 'Un envío fallido y un reintento.');
    assert.equal(cuerpos[0], cuerpos[1], 'El reintento reenvía exactamente la misma solicitud.');
    await page.getByText('Sin verificación; la atención no se declara validada automáticamente.').waitFor(); // la respuesta no es verificación
    assert.equal(await page.getByRole('button', { name: /Registrar verificación/ }).count(), 0);
    // Otro operador (otro periodo/empresa): no ve acciones ajenas.
    await salir();
    await ingresar(otro);
    await abrirAtencionEmpresarial();
    assert.equal(await opcionesAcciones(), 1, 'Solo el marcador: ninguna acción de otro periodo.');
    await page.getByText('No hay acciones disponibles en los periodos expresamente concedidos.').waitFor();
  };
  try {
    if (fase === '1b') {
      await operadorA4();
      assert.deepEqual(errores, [], 'Sin errores de página.');
      assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
      assert.equal((await contexto.cookies()).length, 0);
      return;
    }
    await ingresar(fiscal);
    // `ingresar` deja la pantalla de acceso; el enlace "Inicio" existe en ella.
    await abrirCorrectivas();
    await page.locator('article p', { hasText: incidencia }).first().waitFor(); // texto de la incidencia (no el <option> del formulario)
    if (fase === '1') {
      await page.getByText('Sin acciones; una incidencia no genera una acción automáticamente.').waitFor();
      await page.getByLabel('Antecedente').selectOption({ index: 1 });
      await page.getByLabel('Corrección requerida').fill('Retirar el bloqueo y comunicar la evidencia de atención.');
      await page.getByRole('button', { name: 'Registrar solicitud' }).click();
      await page.getByText('Registro confirmado por el servidor; historial anterior conservado.').waitFor();
      await page.getByRole('heading', { name: /^Acción / }).waitFor();
      await page.getByText('Sin atención empresarial disponible.', { exact: false }).waitFor();
    } else {
      await page.getByRole('heading', { name: /^Atención / }).waitFor();
      await page.getByRole('heading', { name: 'Registrar verificación municipal' }).waitFor();
      await page.getByLabel('Atención a evaluar').selectOption({ index: 1 });
      await page.getByLabel('Resultado aprobado').selectOption({ label: categoria });
      await page.getByLabel('Observaciones').fill('Verificación humana sintética de la atención recibida.');
      await page.getByLabel('Confirmo la evaluación humana; no constituye sanción ni cierre automático.').check();
      await page.getByRole('button', { name: 'Registrar verificación', exact: true }).click();
      await page.getByText('Registro confirmado por el servidor; historial anterior conservado.').waitFor();
      await page.getByRole('heading', { name: /^Verificación humana:/ }).waitFor();
      assert.equal(await page.getByRole('button', { name: 'Registrar verificación', exact: true }).count(), 0, 'La atención ya evaluada no se vuelve a verificar.');
      // Cuenta que solo puede solicitar: ve el historial, no puede verificar.
      await page.getByRole('link', { name: 'Inicio', exact: true }).click();
      await page.getByRole('link', { name: 'Autenticarse en GAMW' }).click();
      await page.getByRole('button', { name: 'Cerrar sesión' }).click();
      await page.getByRole('button', { name: 'Ingresar', exact: true }).waitFor();
      await ingresar(solo);
      await abrirCorrectivas();
      await page.getByRole('heading', { name: /^Verificación humana:/ }).waitFor();
      await page.getByRole('heading', { name: 'Solicitar corrección municipal' }).waitFor();
      assert.equal(await page.getByRole('heading', { name: 'Registrar verificación municipal' }).count(), 0);
      assert.equal(await page.getByRole('button', { name: 'Registrar verificación', exact: true }).count(), 0, 'Sin CORRECTIVA_VERIFICAR no hay formulario de verificación.');
    }
    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } finally {
    await browser.close();
  }
});
