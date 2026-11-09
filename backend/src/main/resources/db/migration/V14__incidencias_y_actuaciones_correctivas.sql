ALTER TABLE concesion_permiso DROP CONSTRAINT concesion_permiso_permiso_check;
ALTER TABLE concesion_permiso ADD CONSTRAINT concesion_permiso_permiso_check CHECK (permiso IN (
 'TRAYECTORIA_JORNADA_CONSULTAR','MONITOREO_JORNADA_CONSULTAR','REFERENCIA_ESPACIAL_CONSULTAR',
 'REFERENCIA_ESPACIAL_GESTIONAR','GPS_INTEGRIDAD_REVISAR','GPS_COINCIDENCIAS_REVISAR','GPS_DESVIOS_REVISAR',
 'INSPECCION_REGISTRAR','FISCALIZACION_CONSULTAR','FISCALIZACION_EMITIR',
 'CORRECTIVA_CONSULTAR','CORRECTIVA_SOLICITAR','CORRECTIVA_VERIFICAR'));

CREATE TABLE incidencia_operativa (
 id uuid PRIMARY KEY, solicitud_id uuid NOT NULL UNIQUE, huella_solicitud text NOT NULL,
 jornada_id uuid NOT NULL REFERENCES jornada(id), asignacion_jornada_id uuid NOT NULL REFERENCES asignacion_jornada(id),
 usuario_id uuid NOT NULL REFERENCES usuario(id), ocurrida_en timestamptz NOT NULL,
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(), descripcion text NOT NULL CHECK(btrim(descripcion)<>''),
 evidencia_declarada text NOT NULL, lugar_descripcion text NOT NULL,
 UNIQUE(id,jornada_id)
);
CREATE INDEX incidencia_jornada_idx ON incidencia_operativa(jornada_id,ocurrida_en,id);

CREATE TABLE accion_correctiva (
 id uuid PRIMARY KEY, solicitud_id uuid NOT NULL UNIQUE, huella_solicitud text NOT NULL,
 jornada_id uuid NOT NULL REFERENCES jornada(id), usuario_id uuid NOT NULL REFERENCES usuario(id),
 incidencia_id uuid, inspeccion_id uuid, fiscalizacion_id uuid,
 descripcion text NOT NULL CHECK(btrim(descripcion)<>''), creada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 antecedentes jsonb NOT NULL, UNIQUE(id,jornada_id),
 CHECK(num_nonnulls(incidencia_id,inspeccion_id,fiscalizacion_id)=1),
 FOREIGN KEY(incidencia_id,jornada_id) REFERENCES incidencia_operativa(id,jornada_id),
 FOREIGN KEY(inspeccion_id,jornada_id) REFERENCES inspeccion(id,jornada_id),
 FOREIGN KEY(fiscalizacion_id,jornada_id) REFERENCES fiscalizacion(id,jornada_id)
);
CREATE INDEX accion_correctiva_jornada_idx ON accion_correctiva(jornada_id,creada_en,id);
-- La atención empresarial tiene identidad propia; D1-A4 no habilitado en este incremento.
CREATE TABLE respuesta_correctiva (
 id uuid PRIMARY KEY, accion_id uuid NOT NULL REFERENCES accion_correctiva(id),
 usuario_id uuid NOT NULL REFERENCES usuario(id), comunicada_en timestamptz NOT NULL,
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(), descripcion text NOT NULL CHECK(btrim(descripcion)<>''),
 evidencia_declarada text NOT NULL, UNIQUE(id,accion_id)
);
CREATE INDEX respuesta_correctiva_accion_idx ON respuesta_correctiva(accion_id,registrada_en,id);
CREATE TABLE catalogo_verificacion_correctiva (
 id uuid PRIMARY KEY, codigo text NOT NULL UNIQUE CHECK(btrim(codigo)<>''), nombre text NOT NULL CHECK(btrim(nombre)<>''),
 referencia_aprobacion text NOT NULL CHECK(btrim(referencia_aprobacion)<>''), observaciones_obligatorias boolean NOT NULL,
 habilitado boolean NOT NULL, registrado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 operador_local text NOT NULL CHECK(btrim(operador_local)<>'')
);
-- Catálogo vacío: no se inventan resultados ni estados/transiciones oficiales.
CREATE TABLE verificacion_correctiva (
 id uuid PRIMARY KEY, solicitud_id uuid NOT NULL UNIQUE, huella_solicitud text NOT NULL,
 accion_id uuid NOT NULL REFERENCES accion_correctiva(id), respuesta_id uuid NOT NULL,
 usuario_id uuid NOT NULL REFERENCES usuario(id), resultado_id uuid NOT NULL REFERENCES catalogo_verificacion_correctiva(id),
 observaciones text NOT NULL, inspeccion_id uuid, jornada_id uuid NOT NULL,
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(respuesta_id,accion_id) REFERENCES respuesta_correctiva(id,accion_id),
 FOREIGN KEY(accion_id,jornada_id) REFERENCES accion_correctiva(id,jornada_id),
 FOREIGN KEY(inspeccion_id,jornada_id) REFERENCES inspeccion(id,jornada_id),
 UNIQUE(respuesta_id)
);
CREATE INDEX verificacion_correctiva_accion_idx ON verificacion_correctiva(accion_id,registrada_en,id);
CREATE TRIGGER incidencia_inmutable BEFORE UPDATE OR DELETE ON incidencia_operativa FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER accion_correctiva_inmutable BEFORE UPDATE OR DELETE ON accion_correctiva FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER respuesta_correctiva_inmutable BEFORE UPDATE OR DELETE ON respuesta_correctiva FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER verificacion_correctiva_inmutable BEFORE UPDATE OR DELETE ON verificacion_correctiva FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER catalogo_verificacion_protegido BEFORE UPDATE OR DELETE ON catalogo_verificacion_correctiva FOR EACH ROW EXECUTE FUNCTION proteger_catalogo_fiscalizacion();
