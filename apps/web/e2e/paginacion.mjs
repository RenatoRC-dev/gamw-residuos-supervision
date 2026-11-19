// Base PostgreSQL compartida por la JVM de pruebas: la jornada de un E2E puede no estar en la PRIMERA página de un listado paginado
// (otras pruebas dejan jornadas legítimas y, desde V21, la historia de planificación no se borra). Se avanza con la paginación real
// de la interfaz hasta encontrar el identificador exacto; no se asume un total ni una posición fija.
export async function elegirJornadaPaginada(page, etiqueta, botonSiguiente, id, maximoPaginas = 40) {
  const select = page.getByLabel(etiqueta);
  const opcionesCargadas = () => page.waitForFunction(([texto]) => {
    const label = [...document.querySelectorAll('label')].find(l => l.textContent.startsWith(texto));
    const s = label?.querySelector('select');
    return !!s && !s.disabled && [...s.options].some(o => o.value);
  }, [etiqueta]);
  const primera = () => select.evaluate(s => [...s.options].find(o => o.value)?.value ?? '');
  await opcionesCargadas();
  for (let pagina = 0; pagina < maximoPaginas; pagina++) {
    if (await select.locator(`option[value="${id}"]`).count() > 0) { await select.selectOption(id); return; }
    const siguiente = page.getByRole('button', { name: botonSiguiente, exact: true });
    if (await siguiente.isDisabled()) break;
    const anterior = await primera();
    await siguiente.click();
    await page.waitForFunction(([texto, previa]) => {
      const label = [...document.querySelectorAll('label')].find(l => l.textContent.startsWith(texto));
      const s = label?.querySelector('select');
      const v = s && [...s.options].find(o => o.value)?.value;
      return !!s && !s.disabled && !!v && v !== previa;
    }, [etiqueta, anterior]);
  }
  throw new Error(`La jornada ${id} no aparece en el listado paginado «${etiqueta}».`);
}
