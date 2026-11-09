import assert from 'node:assert/strict';

// Estos recorridos escriben fixtures: solo los puede lanzar el arnés PostgreSQL aislado.
export function baseAislada(entorno = process.env) {
  assert.equal(entorno.GAMW_WEB_INTEGRACION, 'true', 'Se requiere el arnés aislado; no usar servidores de desarrollo.');
  const base = new URL(entorno.GAMW_WEB_TEST_BASE);
  assert.equal(base.protocol, 'http:');
  assert.equal(base.hostname, '127.0.0.1');
  assert.ok(base.port && !['8080', '5173', '5433'].includes(base.port));
  assert.equal(base.pathname, '/');
  assert.equal(base.username + base.password + base.search + base.hash, '');
  return base.origin;
}
