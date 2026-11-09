import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { readTechnicalHealth } from '../shared/api/health';

export function TechnicalHome() {
  const [status, setStatus] = useState('Consultando disponibilidad…');

  useEffect(() => {
    const controller = new AbortController();
    readTechnicalHealth(controller.signal)
      .then(() => { if (!controller.signal.aborted) setStatus('Backend disponible'); })
      .catch(() => { if (!controller.signal.aborted) setStatus('Backend no disponible'); });
    return () => controller.abort();
  }, []);

  return (
    <main>
      <p>Gobierno Autónomo Municipal de Warnes</p>
      <h1>GAMW · Base técnica</h1>
      <p><Link to="/acceso">Autenticarse en GAMW</Link></p>
      <p><Link to="/trayectorias">Consultar trayectoria GPS de jornada</Link></p>
      <p><Link to="/monitoreo">Monitorear últimas posiciones</Link></p>
      <p><Link to="/referencias">Gestionar referencias espaciales</Link></p>
      <p><Link to="/revision-gps">Revisar evidencia GPS</Link></p>
      <p><Link to="/fiscalizacion">Registrar inspección y fiscalizar jornada</Link></p>
      <p><Link to="/rutas">Digitalizar ruta de referencia</Link></p>
      <p><Link to="/operadores">Gestionar empresas, periodos y personal</Link></p>
      <p><Link to="/jornadas">Programar jornadas y asignar recursos</Link></p>
      <p role="status">{status}</p>
      <p>Disponibilidad del proceso; no certifica la base de datos.</p>
    </main>
  );
}
