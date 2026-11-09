ALTER TABLE concesion_permiso DROP CONSTRAINT concesion_permiso_permiso_check;
ALTER TABLE concesion_permiso ADD CONSTRAINT concesion_permiso_permiso_check CHECK (permiso IN (
 'TRAYECTORIA_JORNADA_CONSULTAR','MONITOREO_JORNADA_CONSULTAR','REFERENCIA_ESPACIAL_CONSULTAR',
 'REFERENCIA_ESPACIAL_GESTIONAR','GPS_INTEGRIDAD_REVISAR','GPS_COINCIDENCIAS_REVISAR','GPS_DESVIOS_REVISAR',
 'INSPECCION_REGISTRAR','FISCALIZACION_CONSULTAR','FISCALIZACION_EMITIR'));

-- Sin categorías ni permisos precargados: habilitación por provisión local controlada.
CREATE TABLE catalogo_resultado_fiscalizacion (
 id uuid PRIMARY KEY, codigo text NOT NULL UNIQUE CHECK(btrim(codigo)<>''),
 nombre text NOT NULL CHECK(btrim(nombre)<>''), referencia_aprobacion text NOT NULL CHECK(btrim(referencia_aprobacion)<>''),
 observaciones_obligatorias boolean NOT NULL, habilitado boolean NOT NULL,
 registrado_en timestamptz NOT NULL DEFAULT clock_timestamp(), operador_local text NOT NULL CHECK(btrim(operador_local)<>'')
);
CREATE TABLE inspeccion (
 id uuid PRIMARY KEY, solicitud_id uuid NOT NULL UNIQUE, huella_solicitud text NOT NULL,
 jornada_id uuid NOT NULL REFERENCES jornada(id), inspector_usuario_id uuid NOT NULL REFERENCES usuario(id),
 realizada_en timestamptz NOT NULL, registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 lugar_descripcion text, geometria geometry(Point,4326), observaciones text NOT NULL CHECK(btrim(observaciones)<>''),
 UNIQUE(id,jornada_id), CHECK (geometria IS NULL OR (NOT ST_IsEmpty(geometria)
   AND ST_X(geometria) BETWEEN -180 AND 180 AND ST_Y(geometria) BETWEEN -90 AND 90))
);
CREATE INDEX inspeccion_jornada_idx ON inspeccion(jornada_id,realizada_en,id);
CREATE INDEX inspeccion_ubicacion_idx ON inspeccion USING gist(geometria);
CREATE TABLE evidencia_inspeccion (
 id uuid PRIMARY KEY, inspeccion_id uuid NOT NULL REFERENCES inspeccion(id),
 autor_usuario_id uuid NOT NULL REFERENCES usuario(id), incorporada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 capturada_en timestamptz, nombre_original text NOT NULL, tipo_mime text NOT NULL CHECK(tipo_mime IN ('image/png','image/jpeg')),
 origen text NOT NULL CHECK(btrim(origen)<>''), descripcion text NOT NULL CHECK(btrim(descripcion)<>''),
 referencia_politica text NOT NULL CHECK(btrim(referencia_politica)<>''),
 sha256 text NOT NULL CHECK(sha256 ~ '^[0-9a-f]{64}$'), contenido bytea NOT NULL CHECK(octet_length(contenido) BETWEEN 1 AND 5242880)
);
CREATE INDEX evidencia_inspeccion_idx ON evidencia_inspeccion(inspeccion_id);
CREATE TABLE fiscalizacion (
 id uuid PRIMARY KEY, solicitud_id uuid NOT NULL UNIQUE, huella_solicitud text NOT NULL,
 jornada_id uuid NOT NULL UNIQUE REFERENCES jornada(id), fiscalizador_usuario_id uuid NOT NULL REFERENCES usuario(id),
 resultado_id uuid NOT NULL REFERENCES catalogo_resultado_fiscalizacion(id),
 observaciones text NOT NULL, registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 corte_evidencia_en timestamptz NOT NULL, antecedentes jsonb NOT NULL, UNIQUE(id,jornada_id)
);
CREATE TABLE fiscalizacion_inspeccion (
 fiscalizacion_id uuid NOT NULL, jornada_id uuid NOT NULL, inspeccion_id uuid NOT NULL,
 PRIMARY KEY(fiscalizacion_id,inspeccion_id),
 FOREIGN KEY(fiscalizacion_id,jornada_id) REFERENCES fiscalizacion(id,jornada_id),
 FOREIGN KEY(inspeccion_id,jornada_id) REFERENCES inspeccion(id,jornada_id)
);
CREATE FUNCTION proteger_antecedente_fiscal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Antecedente inmutable; rectificación no habilitada' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER inspeccion_inmutable BEFORE UPDATE OR DELETE ON inspeccion FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER evidencia_inspeccion_inmutable BEFORE UPDATE OR DELETE ON evidencia_inspeccion FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER fiscalizacion_inmutable BEFORE UPDATE OR DELETE ON fiscalizacion FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE TRIGGER fiscalizacion_inspeccion_inmutable BEFORE UPDATE OR DELETE ON fiscalizacion_inspeccion FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
CREATE FUNCTION proteger_catalogo_fiscalizacion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'El catálogo se retira, no se borra' USING ERRCODE='23514'; END IF;
 IF NOT OLD.habilitado OR NEW.habilitado OR
   ROW(NEW.id,NEW.codigo,NEW.nombre,NEW.referencia_aprobacion,NEW.observaciones_obligatorias,NEW.registrado_en,NEW.operador_local)
   IS DISTINCT FROM ROW(OLD.id,OLD.codigo,OLD.nombre,OLD.referencia_aprobacion,OLD.observaciones_obligatorias,OLD.registrado_en,OLD.operador_local) THEN
   RAISE EXCEPTION 'Solo se permite retirar una categoría; no cambiar su significado histórico' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER catalogo_fiscalizacion_protegido BEFORE UPDATE OR DELETE ON catalogo_resultado_fiscalizacion
 FOR EACH ROW EXECUTE FUNCTION proteger_catalogo_fiscalizacion();
