ALTER TABLE concesion_permiso DROP CONSTRAINT concesion_permiso_permiso_check;
ALTER TABLE concesion_permiso ADD CONSTRAINT concesion_permiso_permiso_check CHECK(permiso IN (
 'TRAYECTORIA_JORNADA_CONSULTAR','MONITOREO_JORNADA_CONSULTAR','REFERENCIA_ESPACIAL_CONSULTAR','REFERENCIA_ESPACIAL_GESTIONAR',
 'GPS_INTEGRIDAD_REVISAR','GPS_COINCIDENCIAS_REVISAR','GPS_DESVIOS_REVISAR','INSPECCION_REGISTRAR',
 'FISCALIZACION_CONSULTAR','FISCALIZACION_EMITIR','CORRECTIVA_CONSULTAR','CORRECTIVA_SOLICITAR','CORRECTIVA_VERIFICAR',
 'USUARIOS_CONSULTAR','USUARIOS_GESTIONAR','PERMISOS_ADMINISTRAR','RECLAMO_CONSULTAR','RECLAMO_REGISTRAR','RECLAMO_REVISAR',
 'PUNTO_CRITICO_CONSULTAR','PUNTO_CRITICO_REGISTRAR','PUNTO_CRITICO_VALIDAR'));
DO $$ DECLARE c record; BEGIN
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='facultad_delegacion'::regclass AND contype='c'
  AND pg_get_constraintdef(oid) LIKE '%TRAYECTORIA_JORNADA_CONSULTAR%' LOOP
  EXECUTE format('ALTER TABLE facultad_delegacion DROP CONSTRAINT %I',c.conname);
 END LOOP;
END $$;
ALTER TABLE facultad_delegacion ADD CONSTRAINT facultad_delegacion_catalogo_check CHECK(
 (periodo_servicio_id IS NULL AND NOT permite_historico AND permiso IN (
 'TRAYECTORIA_JORNADA_CONSULTAR','MONITOREO_JORNADA_CONSULTAR','REFERENCIA_ESPACIAL_CONSULTAR','REFERENCIA_ESPACIAL_GESTIONAR',
 'GPS_INTEGRIDAD_REVISAR','GPS_COINCIDENCIAS_REVISAR','GPS_DESVIOS_REVISAR','INSPECCION_REGISTRAR',
 'FISCALIZACION_CONSULTAR','FISCALIZACION_EMITIR','CORRECTIVA_CONSULTAR','CORRECTIVA_SOLICITAR','CORRECTIVA_VERIFICAR',
 'RECLAMO_CONSULTAR','RECLAMO_REGISTRAR','RECLAMO_REVISAR','PUNTO_CRITICO_CONSULTAR','PUNTO_CRITICO_REGISTRAR','PUNTO_CRITICO_VALIDAR'))
 OR (periodo_servicio_id IS NOT NULL AND permiso IN ('CORRECTIVA_CONSULTAR','CORRECTIVA_RESPONDER','JORNADA_REPROGRAMAR')));

CREATE TABLE reclamo_institucional (
 id uuid PRIMARY KEY, solicitud_id uuid NOT NULL UNIQUE, huella_solicitud text NOT NULL,
 canal_declarado text NOT NULL CHECK(btrim(canal_declarado)<>''), recibido_en timestamptz NOT NULL,
 descripcion text NOT NULL CHECK(btrim(descripcion)<>''), lugar_descripcion text NOT NULL, referencias_origen text NOT NULL,
 geometria geometry(Point,4326), jornada_id uuid REFERENCES jornada(id),
 usuario_id uuid NOT NULL REFERENCES usuario(id), registrado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK(geometria IS NULL OR (NOT ST_IsEmpty(geometria) AND ST_X(geometria) BETWEEN -180 AND 180 AND ST_Y(geometria) BETWEEN -90 AND 90))
);
CREATE INDEX reclamo_recepcion_idx ON reclamo_institucional(recibido_en DESC,id);
CREATE INDEX reclamo_ubicacion_idx ON reclamo_institucional USING gist(geometria);
CREATE INDEX reclamo_jornada_idx ON reclamo_institucional(jornada_id);
CREATE TABLE catalogo_valoracion_reclamo (
 id uuid PRIMARY KEY, codigo text NOT NULL UNIQUE CHECK(btrim(codigo)<>''), nombre text NOT NULL CHECK(btrim(nombre)<>''),
 referencia_aprobacion text NOT NULL CHECK(btrim(referencia_aprobacion)<>''), observaciones_obligatorias boolean NOT NULL,
 es_validacion boolean NOT NULL, habilitado boolean NOT NULL, registrado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 operador_local text NOT NULL CHECK(btrim(operador_local)<>'')
);
CREATE FUNCTION proteger_catalogo_reclamo() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'El catálogo se retira, no se borra' USING ERRCODE='23514'; END IF;
 IF NOT OLD.habilitado OR NEW.habilitado OR (to_jsonb(NEW)-'habilitado') IS DISTINCT FROM (to_jsonb(OLD)-'habilitado') THEN
  RAISE EXCEPTION 'Solo se permite retirar una categoría sin cambiar su significado' USING ERRCODE='23514';
 END IF; RETURN NEW;
END; $$;
CREATE TRIGGER catalogo_reclamo_protegido BEFORE UPDATE OR DELETE ON catalogo_valoracion_reclamo FOR EACH ROW EXECUTE FUNCTION proteger_catalogo_reclamo();
CREATE TABLE revision_reclamo (
 id uuid PRIMARY KEY, solicitud_id uuid NOT NULL UNIQUE, huella_solicitud text NOT NULL,
 reclamo_id uuid NOT NULL REFERENCES reclamo_institucional(id), usuario_id uuid NOT NULL REFERENCES usuario(id),
 resultado_id uuid REFERENCES catalogo_valoracion_reclamo(id), observaciones text NOT NULL CHECK(btrim(observaciones)<>''),
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX reclamo_valoracion_unica_idx ON revision_reclamo(reclamo_id) WHERE resultado_id IS NOT NULL;
CREATE INDEX revision_reclamo_historia_idx ON revision_reclamo(reclamo_id,registrada_en,id);
CREATE TABLE relacion_reclamo (
 id uuid PRIMARY KEY, reclamo_id uuid NOT NULL REFERENCES reclamo_institucional(id),
 jornada_id uuid REFERENCES jornada(id), incidencia_id uuid REFERENCES incidencia_operativa(id),
 accion_correctiva_id uuid REFERENCES accion_correctiva(id), inspeccion_id uuid REFERENCES inspeccion(id),
 fundamento text NOT NULL CHECK(btrim(fundamento)<>''), usuario_id uuid NOT NULL REFERENCES usuario(id),
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK(num_nonnulls(jornada_id,incidencia_id,accion_correctiva_id,inspeccion_id)=1)
);
CREATE INDEX relacion_reclamo_origen_idx ON relacion_reclamo(reclamo_id,registrada_en,id);
CREATE UNIQUE INDEX relacion_reclamo_jornada_idx ON relacion_reclamo(reclamo_id,jornada_id) WHERE jornada_id IS NOT NULL;
CREATE UNIQUE INDEX relacion_reclamo_incidencia_idx ON relacion_reclamo(reclamo_id,incidencia_id) WHERE incidencia_id IS NOT NULL;
CREATE UNIQUE INDEX relacion_reclamo_accion_idx ON relacion_reclamo(reclamo_id,accion_correctiva_id) WHERE accion_correctiva_id IS NOT NULL;
CREATE UNIQUE INDEX relacion_reclamo_inspeccion_idx ON relacion_reclamo(reclamo_id,inspeccion_id) WHERE inspeccion_id IS NOT NULL;
CREATE TABLE anexo_reclamo (
 id uuid PRIMARY KEY, reclamo_id uuid NOT NULL REFERENCES reclamo_institucional(id), usuario_id uuid NOT NULL REFERENCES usuario(id),
 origen text NOT NULL CHECK(btrim(origen)<>''), descripcion text NOT NULL CHECK(btrim(descripcion)<>''), capturado_en timestamptz,
 registrado_en timestamptz NOT NULL DEFAULT clock_timestamp(), referencia_politica text NOT NULL CHECK(btrim(referencia_politica)<>''),
 nombre_original text NOT NULL, tipo_mime text NOT NULL CHECK(tipo_mime IN ('image/jpeg','image/png')),
 sha256 text NOT NULL CHECK(sha256 ~ '^[0-9a-f]{64}$'), contenido bytea NOT NULL CHECK(octet_length(contenido) BETWEEN 1 AND 5242880)
);
CREATE INDEX anexo_reclamo_origen_idx ON anexo_reclamo(reclamo_id,registrado_en,id);
CREATE TABLE punto_critico (
 id uuid PRIMARY KEY, solicitud_id uuid NOT NULL UNIQUE, huella_solicitud text NOT NULL,
 denominacion text NOT NULL CHECK(btrim(denominacion)<>''), descripcion text NOT NULL CHECK(btrim(descripcion)<>''),
 geometria geometry(Point,4326) NOT NULL, sustento_municipal text NOT NULL CHECK(btrim(sustento_municipal)<>''),
 reclamo_id uuid REFERENCES reclamo_institucional(id), inspeccion_id uuid REFERENCES inspeccion(id),
 usuario_id uuid NOT NULL REFERENCES usuario(id), registrado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK(NOT ST_IsEmpty(geometria) AND ST_X(geometria) BETWEEN -180 AND 180 AND ST_Y(geometria) BETWEEN -90 AND 90)
);
CREATE INDEX punto_critico_geometria_idx ON punto_critico USING gist(geometria);
CREATE INDEX punto_critico_registro_idx ON punto_critico(registrado_en DESC,id);
CREATE TABLE observacion_punto_critico (
 id uuid PRIMARY KEY, solicitud_id uuid NOT NULL UNIQUE, huella_solicitud text NOT NULL,
 punto_id uuid NOT NULL REFERENCES punto_critico(id), usuario_id uuid NOT NULL REFERENCES usuario(id),
 observada_en timestamptz NOT NULL, descripcion text NOT NULL CHECK(btrim(descripcion)<>''),
 inspeccion_id uuid REFERENCES inspeccion(id), reclamo_id uuid REFERENCES reclamo_institucional(id),
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX observacion_punto_historia_idx ON observacion_punto_critico(punto_id,observada_en,id);
CREATE TABLE validacion_punto_critico (
 punto_id uuid PRIMARY KEY REFERENCES punto_critico(id), usuario_id uuid NOT NULL REFERENCES usuario(id),
 fundamento text NOT NULL CHECK(btrim(fundamento)<>''), referencia_politica text NOT NULL CHECK(btrim(referencia_politica)<>''),
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER reclamo_inmutable BEFORE UPDATE OR DELETE ON reclamo_institucional FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER revision_reclamo_inmutable BEFORE UPDATE OR DELETE ON revision_reclamo FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER relacion_reclamo_inmutable BEFORE UPDATE OR DELETE ON relacion_reclamo FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER anexo_reclamo_inmutable BEFORE UPDATE OR DELETE ON anexo_reclamo FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER punto_critico_inmutable BEFORE UPDATE OR DELETE ON punto_critico FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER observacion_punto_inmutable BEFORE UPDATE OR DELETE ON observacion_punto_critico FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER validacion_punto_inmutable BEFORE UPDATE OR DELETE ON validacion_punto_critico FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
