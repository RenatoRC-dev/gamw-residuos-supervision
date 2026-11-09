import { createBrowserRouter, Outlet } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import { TechnicalHome } from './TechnicalHome';
import { ProveedorAutenticacion } from '../modules/administracion/identidad/aplicacion/contextoAutenticacion';
const PaginaRutasReferencia = lazy(() => import('../modules/planificacion/rutas/paginas/PaginaRutasReferencia')
  .then(module => ({ default: module.PaginaRutasReferencia })));
const PaginaOperadores = lazy(() => import('../modules/administracion/registro-operativo/paginas/PaginaOperadores')
  .then(module => ({ default: module.PaginaOperadores })));
const PaginaJornadas = lazy(() => import('../modules/planificacion/jornadas/paginas/PaginaJornadas')
  .then(module => ({ default: module.PaginaJornadas })));
const PaginaAcceso = lazy(() => import('../modules/administracion/identidad/paginas/PaginaAcceso')
  .then(module => ({ default: module.PaginaAcceso })));
const PaginaTrayectorias = lazy(() => import('../modules/monitoreo/trayectorias/paginas/PaginaTrayectorias')
  .then(module => ({ default: module.PaginaTrayectorias })));
const PaginaMonitoreo = lazy(() => import('../modules/monitoreo/posicion/paginas/PaginaMonitoreo')
  .then(module => ({ default: module.PaginaMonitoreo })));
const PaginaReferenciasEspaciales = lazy(() => import('../modules/planificacion/referencias/paginas/PaginaReferenciasEspaciales')
  .then(module => ({ default: module.PaginaReferenciasEspaciales })));
const PaginaRevisionGps = lazy(() => import('../modules/monitoreo/revision/paginas/PaginaRevisionGps')
  .then(module => ({ default: module.PaginaRevisionGps })));
const PaginaFiscalizacion = lazy(() => import('../modules/fiscalizacion/paginas/PaginaFiscalizacion')
  .then(module => ({ default: module.PaginaFiscalizacion })));

export const appRoutes = [{ element: <ProveedorAutenticacion><Outlet /></ProveedorAutenticacion>, children: [
  { path: '/', element: <TechnicalHome /> },
  { path: '/acceso', element: <Suspense fallback={<p role="status">Cargando acceso…</p>}><PaginaAcceso /></Suspense> },
  { path: '/trayectorias', element: <Suspense fallback={<p role="status">Cargando trayectorias…</p>}><PaginaTrayectorias /></Suspense> },
  { path: '/monitoreo', element: <Suspense fallback={<p role="status">Cargando monitoreo…</p>}><PaginaMonitoreo /></Suspense> },
  { path: '/referencias', element: <Suspense fallback={<p role="status">Cargando referencias…</p>}><PaginaReferenciasEspaciales /></Suspense> },
  { path: '/revision-gps', element: <Suspense fallback={<p role="status">Cargando revisión…</p>}><PaginaRevisionGps /></Suspense> },
  { path: '/fiscalizacion', element: <Suspense fallback={<p role="status">Cargando fiscalización…</p>}><PaginaFiscalizacion /></Suspense> },
  { path: '/jornadas', element: <Suspense fallback={<p role="status">Cargando planificación…</p>}><PaginaJornadas /></Suspense> },
  { path: '/operadores', element: <Suspense fallback={<p role="status">Cargando registro operativo…</p>}><PaginaOperadores /></Suspense> },
  { path: '/rutas', element: <Suspense fallback={<p role="status">Cargando editor de rutas…</p>}><PaginaRutasReferencia /></Suspense> },
  { path: '*', element: <main><h1>Página no encontrada</h1><a href="/">Inicio</a></main> },
] }];

export function createAppRouter() {
  return createBrowserRouter(appRoutes);
}
