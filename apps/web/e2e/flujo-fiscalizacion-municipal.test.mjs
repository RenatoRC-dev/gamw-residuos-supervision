import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { elegirJornadaPaginada } from './paginacion.mjs';

// Únicamente vía FiscalizacionMunicipalNavegadorIT. CU-21 (A3): revisión municipal del expediente en la interfaz real con JWT: contexto
// histórico, INICIO/FIN, GPS recibido, inspección, fuentes con permiso propio, 503 sin datos inventados, PENDING-05 (revisión sin dictamen),
// lectura sin facultades de escritura y denegación a un operador de empresa (A4). Datos sintéticos.
test('CU-21: A3 revisa el expediente sin dictamen (PENDING-05); el lector no escribe; A4 no accede', { timeout: 170000 }, async () => {
  assert.equal(process.env.GAMW_WEB_INTEGRACION, 'true', 'Use el aprovisionador aislado de Maven.');
  const base = new URL(process.env.GAMW_WEB_TEST_BASE);
  assert.equal(base.hostname, '127.0.0.1');
  const d = JSON.parse(process.env.GAMW_WEB_TEST_DATOS);
  assert.ok(d.revisor?.startsWith('fm-rev-') && d.lector?.startsWith('fm-lec-') && d.operador?.startsWith('fm-op-') && d.clave && d.jornada);
  const browser = await chromium.launch();
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  const errores = [], escrituras = [];
  page.on('pageerror', x => errores.push(`${x.name}: ${x.message}`));
  page.on('request', r => { if (r.method() === 'POST' && r.url().includes('/api/fiscalizacion/')) escrituras.push(r.url()); });
  const ingresar = async nombre => {
    await page.goto(new URL('/acceso', base).href);
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
  const abrirFiscalizacion = async () => {
    await page.getByRole('link', { name: 'Inicio', exact: true }).click();
    await page.getByRole('link', { name: 'Registrar inspección y fiscalizar jornada' }).click();
    await page.getByRole('heading', { name: 'Inspección y fiscalización municipal', exact: true }).waitFor();
  };
  const texto = async (t, exact = false) => {
    try { await page.getByText(t, { exact }).first().waitFor({ timeout: 15000 }); }
    catch (e) { console.log(`DIAG «${t}»: ${JSON.stringify(await page.locator('[role=status],[role=alert],h2,h3').allTextContents())}`); throw e; }
  };
  const referencia = modulo => page.locator('li').filter({ has: page.getByRole('link', { name: modulo, exact: true }) });
  const expedienteCompleto = async () => {
    await page.getByRole('heading', { name: `${d.empresa} · ${d.codigoRuta} · versión 1`, exact: true }).waitFor();
    await texto(`Jornada ${d.jornada} · Periodo ${d.periodo}`);
    await texto(`${d.placa} (${d.vehiculo})`);
    await texto(/^INICIO · captura/);
    await texto(/^FIN · captura/);
    await texto('GPS recibidos: 3.');
    await page.getByRole('heading', { name: `Inspección ${d.inspeccion}`, exact: true }).waitFor();
    await texto(`Inspector ${d.inspector} · realizada`);
    await texto(d.observacionInspeccion, true);
    await texto('Muestras de esta página: 3');
    await texto('GPS recibido: evidencia técnica, no dictamen', true);
  };
  try {
    // ================= A3 revisor (consulta + emisión + reclamos): sin catálogo aprobado, sin dictamen =================
    await ingresar(d.revisor);
    await abrirFiscalizacion();
    // Primera consulta del expediente con 503: no se muestran datos parciales ni inventados; luego se actualiza.
    // (En desarrollo, StrictMode repite el efecto y aborta la primera petición: se falla mientras dure la bandera, no «una vez».)
    let fallar = true;
    await page.route(`**/api/fiscalizacion/jornadas/${d.jornada}`, route => fallar
      ? route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"status":503}' }) : route.continue());
    await elegirJornadaPaginada(page, 'Jornada', 'Página siguiente', d.jornada);
    await texto('No se pudo completar la operación.');
    assert.equal(await page.getByText('GPS recibidos:', { exact: false }).count(), 0, 'Sin expediente ante 503.');
    assert.equal(await page.getByRole('heading', { name: 'Revisión sin dictamen' }).count(), 0);
    fallar = false;
    await page.getByRole('button', { name: 'Actualizar antecedentes', exact: true }).click();
    await expedienteCompleto();
    assert.equal(await page.locator('[role=alert]').filter({ hasText: /\S/ }).count(), 0, 'El error previo se limpia al recargar.');
    // Fuentes con permiso propio: reclamos contados; incidencias y acciones desconocidas (no «sin registros»).
    await referencia('Reclamos vinculados (directos o por relación)').getByText('Con registros (1)').waitFor();
    for (const m of ['Incidencias operativas', 'Acciones correctivas'])
      await referencia(m).getByText('Acceso no permitido (se desconoce si existen)').waitFor();
    if (d.catalogoVacio) {
      // PENDING-05 (JVM aislada de navegador, CI): puede emitir por permiso, pero sin catálogo habilitado solo hay revisión sin dictamen.
      await page.getByRole('heading', { name: 'Revisión sin dictamen', exact: true }).waitFor();
      await texto('Catálogo institucional no habilitado. Puede revisar evidencia, pero no registrar categorías inventadas ni una determinación definitiva.', true);
      assert.equal(await page.getByRole('button', { name: 'Confirmar determinación municipal' }).count(), 0);
    } else {
      // `verify` local completo: solo fixtures sintéticas de otra prueba (ya comprobado en Java que no hay catálogo oficial). No se emite.
      const opciones = (await page.getByLabel('Resultado del catálogo habilitado').locator('option').allTextContents()).slice(1);
      assert.ok(opciones.length > 0 && opciones.every(o => o.startsWith('CAT-SINT-')), `Solo fixtures sintéticas: ${JSON.stringify(opciones)}`);
      assert.ok(await page.getByRole('button', { name: 'Confirmar determinación municipal' }).isDisabled(), 'Sin confirmación explícita no se emite.');
    }
    assert.equal(await page.getByRole('heading', { name: 'Resultado humano registrado' }).count(), 0);
    await texto('Sin permiso de registro de inspección. La consulta no concede facultad de escritura.', true);
    await salir();

    // ================= Lector (solo FISCALIZACION_CONSULTAR): expediente completo, ninguna escritura =================
    await ingresar(d.lector);
    await abrirFiscalizacion();
    await elegirJornadaPaginada(page, 'Jornada', 'Página siguiente', d.jornada);
    await expedienteCompleto();
    for (const m of ['Incidencias operativas', 'Acciones correctivas', 'Reclamos vinculados (directos o por relación)'])
      await referencia(m).getByText('Acceso no permitido (se desconoce si existen)').waitFor();
    await texto('No dispone de permiso para emitir resultados. Puede mantener una revisión sin dictamen.', true);
    await texto('Sin permiso de registro de inspección. La consulta no concede facultad de escritura.', true);
    assert.equal(await page.locator('form').count(), 1, 'Solo el formulario técnico de revisión GPS (permisos propios), sin inspección ni resultado.');
    assert.equal(await page.getByRole('button', { name: 'Confirmar determinación municipal' }).count(), 0);
    assert.deepEqual((await page.locator('[role=alert]').allTextContents()).filter(t => t.trim()), [], 'El lector no ve errores.');
    await salir();

    // ================= Operador de empresa (A4, concesión de su periodo): sin acceso municipal =================
    await ingresar(d.operador);
    await abrirFiscalizacion();
    await texto('Permiso municipal específico requerido.', true);
    assert.equal(await page.locator(`option[value="${d.jornada}"]`).count(), 0, 'A4 no lista jornadas municipales.');
    assert.equal(await page.getByText('Sin jornadas disponibles en esta página autorizada.').count(), 0, 'La denegación no se presenta como ausencia de jornadas.');

    assert.deepEqual(escrituras, [], 'Ninguna escritura de fiscalización desde la interfaz.');
    assert.deepEqual(errores, [], 'Sin errores de página.');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
    assert.equal((await contexto.cookies()).length, 0);
  } finally {
    await browser.close();
  }
});
