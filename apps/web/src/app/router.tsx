import { createBrowserRouter } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import { TechnicalHome } from './TechnicalHome';
const ReferenceRoutesPage = lazy(() => import('../modules/planning/routes/pages/ReferenceRoutesPage')
  .then(module => ({ default: module.ReferenceRoutesPage })));

export const appRoutes = [
  { path: '/', element: <TechnicalHome /> },
  { path: '/rutas', element: <Suspense fallback={<p role="status">Cargando editor de rutas…</p>}><ReferenceRoutesPage /></Suspense> },
  { path: '*', element: <main><h1>Página no encontrada</h1><a href="/">Inicio</a></main> },
];

export function createAppRouter() {
  return createBrowserRouter(appRoutes);
}
