import { createBrowserRouter } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import { TechnicalHome } from './TechnicalHome';
const PaginaRutasReferencia = lazy(() => import('../modules/planificacion/rutas/paginas/PaginaRutasReferencia')
  .then(module => ({ default: module.PaginaRutasReferencia })));

export const appRoutes = [
  { path: '/', element: <TechnicalHome /> },
  { path: '/rutas', element: <Suspense fallback={<p role="status">Cargando editor de rutas…</p>}><PaginaRutasReferencia /></Suspense> },
  { path: '*', element: <main><h1>Página no encontrada</h1><a href="/">Inicio</a></main> },
];

export function createAppRouter() {
  return createBrowserRouter(appRoutes);
}
