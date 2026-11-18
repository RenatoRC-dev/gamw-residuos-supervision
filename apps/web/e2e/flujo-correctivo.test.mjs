import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

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
    await page.getByLabel('Jornada').selectOption(jornada);
    await page.getByRole('heading', { name: 'Incidencias operativas' }).waitFor();
  };
  try {
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
