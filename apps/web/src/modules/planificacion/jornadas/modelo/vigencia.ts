export function cubreFecha(fecha: string, desde: string, hasta: string | null): boolean {
  return fecha >= desde && (hasta === null || fecha <= hasta);
}
export function versionAplicable(inicio: string, desde: string, hasta: string | null): boolean {
  const instante = Date.parse(inicio);
  return Number.isFinite(instante) && instante >= Date.parse(desde) && (hasta === null || instante <= Date.parse(hasta));
}
export function fechaHoraLocal(iso: string) {
  const fecha = new Date(iso);
  return new Date(fecha.getTime() - fecha.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}
