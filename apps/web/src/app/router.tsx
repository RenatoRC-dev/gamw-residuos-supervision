import { createBrowserRouter } from 'react-router-dom';
import { TechnicalHome } from './TechnicalHome';

export const appRoutes = [
  { path: '/', element: <TechnicalHome /> },
  { path: '*', element: <main><h1>Página no encontrada</h1><a href="/">Inicio</a></main> },
];

export function createAppRouter() {
  return createBrowserRouter(appRoutes);
}
