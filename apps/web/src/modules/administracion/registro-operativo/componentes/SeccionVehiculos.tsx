import { useEffect, useState, type FormEvent } from 'react';
import { apiVehiculos as api, type MarcaVehiculo, type ModeloVehiculo, type TipoVehiculo,
  type Vehiculo, type VinculoVehiculoEmpresa } from '../api/vehiculos';

const texto = (datos: FormData, campo: string) => String(datos.get(campo) ?? '');
const opcional = (valor: string) => valor.trim() || null;
const numeroOpcional = (valor: string) => {
  if (valor === '') return null;
  const numero = Number(valor);
  if (!Number.isFinite(numero)) throw new Error('Ingrese un valor numérico finito.');
  return numero;
};

/** CU-06 / RF-04. Identidad del vehículo y segmentos históricos de empresa independientes. */
export function SeccionVehiculos({ empresaId, empresaNombre }: { empresaId: string; empresaNombre: string }) {
  const [vehiculos, setVehiculos] = useState<Vehiculo[]>([]);
  const [marcas, setMarcas] = useState<MarcaVehiculo[]>([]);
  const [modelos, setModelos] = useState<ModeloVehiculo[]>([]);
  const [tipos, setTipos] = useState<TipoVehiculo[]>([]);
  const [vinculos, setVinculos] = useState<VinculoVehiculoEmpresa[]>([]);
  const [vehiculoId, setVehiculoId] = useState('');
  const [editado, setEditado] = useState<Vehiculo | null>(null);
  const [vinculoEditado, setVinculoEditado] = useState<VinculoVehiculoEmpresa | null>(null);
  const [marcaId, setMarcaId] = useState('');
  const [modeloId, setModeloId] = useState('');
  const [tipoId, setTipoId] = useState('');
  const [claveVehiculo, setClaveVehiculo] = useState(0);
  const [claveVinculo, setClaveVinculo] = useState(0);
  const [claveCatalogo, setClaveCatalogo] = useState(0);
  const [ocupado, setOcupado] = useState(true);
  const [mensaje, setMensaje] = useState('');
  const [error, setError] = useState('');

  async function recargar(signal?: AbortSignal) {
    const [v, m, mo, t, vi] = await Promise.all([api.listar(signal), api.marcas(signal), api.modelos(undefined, signal), api.tipos(signal), api.vinculos(empresaId, signal)]);
    if (!signal?.aborted) { setVehiculos(v); setMarcas(m); setModelos(mo); setTipos(t); setVinculos(vi); }
  }
  useEffect(() => {
    const abort = new AbortController();
    setOcupado(true);
    void recargar(abort.signal).catch(e => {
      if (!abort.signal.aborted) setError(e instanceof Error ? e.message : 'No se pudo cargar el registro vehicular.');
    }).finally(() => { if (!abort.signal.aborted) setOcupado(false); });
    return () => abort.abort();
    // El componente se monta de nuevo al cambiar empresa; no conserva respuestas de otra empresa.
  }, [empresaId]);

  async function ejecutar(accion: () => Promise<void>) {
    setOcupado(true); setMensaje(''); setError('');
    try { await accion(); await recargar(); setMensaje('Cambios vehiculares guardados.'); }
    catch (e) { setError(e instanceof Error ? e.message : 'No se pudo guardar el registro vehicular.'); }
    finally { setOcupado(false); }
  }
  function nuevoVehiculo() {
    setEditado(null); setMarcaId(''); setModeloId(''); setTipoId(''); setClaveVehiculo(c => c + 1);
  }
  function editarVehiculo(vehiculo: Vehiculo) {
    setEditado(vehiculo);
    setModeloId(vehiculo.modeloVehiculoId ?? '');
    setMarcaId(modelos.find(m => m.id === vehiculo.modeloVehiculoId)?.marcaVehiculoId ?? '');
    setTipoId(vehiculo.tipoVehiculoId ?? '');
    setClaveVehiculo(c => c + 1);
  }
  function guardarVehiculo(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const datos = new FormData(event.currentTarget);
    void ejecutar(async () => {
      const entrada = { placa: texto(datos, 'placa'), modeloVehiculoId: modeloId || null, tipoVehiculoId: tipoId || null,
        anio: numeroOpcional(texto(datos, 'anio')), capacidadCargaKg: numeroOpcional(texto(datos, 'capacidadCargaKg')),
        capacidadVolumenM3: numeroOpcional(texto(datos, 'capacidadVolumenM3')) };
      if ((entrada.capacidadCargaKg !== null && entrada.capacidadCargaKg <= 0)
          || (entrada.capacidadVolumenM3 !== null && entrada.capacidadVolumenM3 <= 0)) {
        throw new Error('Las capacidades en kg y m³ deben ser mayores que cero cuando se proporcionan.');
      }
      const vehiculo = editado ? await api.actualizar(editado.id, entrada) : await api.crear(entrada);
      setVehiculoId(vehiculo.id); nuevoVehiculo();
    });
  }
  function guardarVinculo(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const datos = new FormData(event.currentTarget);
    void ejecutar(async () => {
      const cierre = { fechaFin: opcional(texto(datos, 'fechaFin')), activo: datos.has('activo') };
      if (vinculoEditado) await api.cerrarVinculo(empresaId, vinculoEditado.id, cierre);
      else await api.crearVinculo(empresaId, { ...cierre, vehiculoId, fechaInicio: texto(datos, 'fechaInicio') });
      setVinculoEditado(null); setClaveVinculo(c => c + 1);
    });
  }
  function crearCatalogo(event: FormEvent<HTMLFormElement>, catalogo: 'marca' | 'modelo' | 'tipo') {
    event.preventDefault(); const datos = new FormData(event.currentTarget);
    void ejecutar(async () => {
      if (catalogo === 'marca') { const m = await api.crearMarca(texto(datos, 'nombre')); setMarcaId(m.id); setModeloId(''); }
      if (catalogo === 'modelo') {
        const m = await api.crearModelo(texto(datos, 'marcaVehiculoId'), texto(datos, 'nombre')); setMarcaId(m.marcaVehiculoId); setModeloId(m.id);
      }
      if (catalogo === 'tipo') { const t = await api.crearTipo(texto(datos, 'codigo'), texto(datos, 'nombre')); setTipoId(t.id); }
      setClaveCatalogo(c => c + 1);
    });
  }
  const seleccionado = vehiculos.find(v => v.id === vehiculoId);
  const placa = (id: string) => vehiculos.find(v => v.id === id)?.placa ?? 'Vehículo no disponible';
  return <section aria-labelledby="vehiculos-titulo">
    <h3 id="vehiculos-titulo">Vehículos de la empresa</h3>
    <p>El vehículo conserva su identidad al cambiar de empresa. Cierre/desactive el vínculo actual y cree otro para la nueva empresa.</p>
    {error && <p role="alert">{error}</p>}
    {(ocupado || mensaje) && <p role="status">{ocupado ? 'Consultando registro vehicular…' : mensaje}</p>}
    <fieldset disabled={ocupado}><legend>Registro vehicular</legend>
      <h4>Vínculos históricos con {empresaNombre}</h4>
      {!vinculos.length && <p>No hay vínculos vehiculares registrados para esta empresa.</p>}
      <ul>{vinculos.map(v => <li key={v.id}>{placa(v.vehiculoId)} · {v.fechaInicio} — {v.fechaFin ?? 'Sin cierre'} · {v.activo ? 'Activo' : 'Inactivo'}
        <button onClick={() => { setVinculoEditado(v); setVehiculoId(v.vehiculoId); setClaveVinculo(c => c + 1); }}>Editar cierre vehicular</button>
      </li>)}</ul>
      <label>Vehículo existente<select aria-label="Vehículo existente" value={vehiculoId} onChange={e => setVehiculoId(e.target.value)}>
        <option value="">Seleccione un vehículo</option>
        {vehiculos.map(v => <option key={v.id} value={v.id}>{v.placa}{v.activo ? '' : ' · Inactivo'}</option>)}
      </select></label>
      {seleccionado && <>
        <button onClick={() => editarVehiculo(seleccionado)}>Editar vehículo</button>
        <button onClick={() => void ejecutar(async () => { await api.estado(seleccionado.id, !seleccionado.activo); })}>{seleccionado.activo ? 'Desactivar vehículo' : 'Activar vehículo'}</button>
      </>}
      <form key={`vehiculo-${claveVehiculo}`} aria-label="Datos de vehículo" onSubmit={guardarVehiculo}>
        <h4>{editado ? 'Editar datos del vehículo' : 'Registrar vehículo independiente'}</h4>
        <label>Placa<input name="placa" required defaultValue={editado?.placa ?? ''} /></label>
        <label>Marca del vehículo<select aria-label="Marca del vehículo" value={marcaId} onChange={e => { setMarcaId(e.target.value); setModeloId(''); }}>
          <option value="">Sin marca seleccionada</option>
          {marcas.filter(m => m.activo || m.id === marcaId).map(m => <option key={m.id} value={m.id}>{m.nombre}{m.activo ? '' : ' · Inactiva'}</option>)}
        </select></label>
        <label>Modelo del vehículo<select aria-label="Modelo del vehículo" value={modeloId} onChange={e => {
          setModeloId(e.target.value); if (e.target.value) setMarcaId(modelos.find(m => m.id === e.target.value)?.marcaVehiculoId ?? '');
        }}>
          <option value="">Sin modelo</option>
          {modelos.filter(m => (!marcaId || m.marcaVehiculoId === marcaId) && (m.activo || m.id === modeloId)).map(m => <option key={m.id} value={m.id}>{m.nombre}{m.activo ? '' : ' · Inactivo'}</option>)}
        </select></label>
        <label>Tipo del vehículo<select aria-label="Tipo del vehículo" value={tipoId} onChange={e => setTipoId(e.target.value)}>
          <option value="">Sin tipo</option>
          {tipos.filter(t => t.activo || t.id === tipoId).map(t => <option key={t.id} value={t.id}>{t.nombre}{t.activo ? '' : ' · Inactivo'}</option>)}
        </select></label>
        <label>Año<input name="anio" type="number" min="1900" step="1" defaultValue={editado?.anio ?? ''} /></label>
        <label>Capacidad de carga (kg)<input name="capacidadCargaKg" type="number" min="0" step="any" defaultValue={editado?.capacidadCargaKg ?? ''} /></label>
        <label>Capacidad de volumen (m³)<input name="capacidadVolumenM3" type="number" min="0" step="any" defaultValue={editado?.capacidadVolumenM3 ?? ''} /></label>
        <button type="submit">{editado ? 'Guardar vehículo' : 'Crear vehículo'}</button>
        {editado && <button type="button" onClick={nuevoVehiculo}>Cancelar edición vehicular</button>}
      </form>
      <details><summary>Crear marca, modelo o tipo necesario para el registro</summary>
        <p>Registre únicamente valores conocidos. No hay marcas, modelos ni tipos institucionales precargados.</p>
        <form key={`marca-${claveCatalogo}`} aria-label="Crear marca vehicular" onSubmit={e => crearCatalogo(e, 'marca')}>
          <label>Nombre de marca<input name="nombre" required /></label><button type="submit">Crear marca</button>
        </form>
        <form key={`modelo-${claveCatalogo}`} aria-label="Crear modelo vehicular" onSubmit={e => crearCatalogo(e, 'modelo')}>
          <label>Marca del nuevo modelo<select aria-label="Marca del nuevo modelo" name="marcaVehiculoId" required defaultValue={marcaId}>
            <option value="">Seleccione una marca</option>{marcas.filter(m => m.activo).map(m => <option key={m.id} value={m.id}>{m.nombre}</option>)}
          </select></label>
          <label>Nombre de modelo<input name="nombre" required /></label><button type="submit">Crear modelo</button>
        </form>
        <form key={`tipo-${claveCatalogo}`} aria-label="Crear tipo vehicular" onSubmit={e => crearCatalogo(e, 'tipo')}>
          <label>Código de tipo<input name="codigo" required /></label><label>Nombre de tipo<input name="nombre" required /></label>
          <button type="submit">Crear tipo</button>
        </form>
      </details>
      <form key={`vinculo-vehiculo-${claveVinculo}`} aria-label="Vínculo vehicular" onSubmit={guardarVinculo}>
        {vinculoEditado ? <p>Identidad histórica: {placa(vinculoEditado.vehiculoId)} · Empresa: {empresaNombre} · Inicio: {vinculoEditado.fechaInicio}.
          Solo puede cambiar el cierre y estado.</p> : <>
          <label>Vehículo del vínculo<select aria-label="Vehículo del vínculo" required value={vehiculoId} onChange={e => setVehiculoId(e.target.value)}>
            <option value="">Seleccione un vehículo</option>{vehiculos.map(v => <option key={v.id} value={v.id}>{v.placa}</option>)}
          </select></label>
          <label>Inicio del vínculo vehicular<input name="fechaInicio" type="date" required /></label>
        </>}
        <label>Fin del vínculo vehicular<input name="fechaFin" type="date" defaultValue={vinculoEditado?.fechaFin ?? ''} /></label>
        <label><input name="activo" type="checkbox" defaultChecked={vinculoEditado?.activo ?? true} />Vínculo vehicular activo</label>
        <button type="submit">{vinculoEditado ? 'Guardar cierre vehicular' : 'Vincular vehículo'}</button>
        {vinculoEditado && <button type="button" onClick={() => { setVinculoEditado(null); setClaveVinculo(c => c + 1); }}>Nuevo vínculo vehicular</button>}
      </form>
    </fieldset>
  </section>;
}
