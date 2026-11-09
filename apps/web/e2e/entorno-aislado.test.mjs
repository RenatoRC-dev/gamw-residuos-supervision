import { test } from 'node:test';
import assert from 'node:assert/strict';
import { baseAislada } from './entorno-aislado.mjs';

test('rechaza la ejecución sin autorización explícita del arnés', () => {
  assert.throws(() => baseAislada({ GAMW_WEB_TEST_BASE: 'http://127.0.0.1:45678' }));
});
test('rechaza destinos de desarrollo, externos o con datos adicionales', () => {
  for (const destino of ['http://127.0.0.1:5173', 'http://127.0.0.1:8080', 'http://127.0.0.1:5433',
    'https://127.0.0.1:45678', 'http://example.invalid:45678', 'http://127.0.0.1',
    'http://127.0.0.1:45678/ruta', 'http://127.0.0.1:45678/?destino=otro',
    'http://usuario:clave@127.0.0.1:45678']) {
    assert.throws(() => baseAislada({ GAMW_WEB_INTEGRACION: 'true', GAMW_WEB_TEST_BASE: destino }));
  }
});
test('acepta exclusivamente el origen loopback de prueba', () => {
  assert.equal(baseAislada({ GAMW_WEB_INTEGRACION: 'true', GAMW_WEB_TEST_BASE: 'http://127.0.0.1:45678' }),
    'http://127.0.0.1:45678');
});
