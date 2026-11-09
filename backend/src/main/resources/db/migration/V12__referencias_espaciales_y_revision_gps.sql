ALTER TABLE concesion_permiso DROP CONSTRAINT concesion_permiso_permiso_check;
ALTER TABLE concesion_permiso ADD CONSTRAINT concesion_permiso_permiso_check CHECK (permiso IN (
 'TRAYECTORIA_JORNADA_CONSULTAR','MONITOREO_JORNADA_CONSULTAR',
 'REFERENCIA_ESPACIAL_CONSULTAR','REFERENCIA_ESPACIAL_GESTIONAR',
 'GPS_INTEGRIDAD_REVISAR','GPS_COINCIDENCIAS_REVISAR','GPS_DESVIOS_REVISAR'));

CREATE TABLE referencia_espacial (id uuid PRIMARY KEY);
CREATE TABLE referencia_espacial_version (
 id uuid PRIMARY KEY,
 referencia_id uuid NOT NULL REFERENCES referencia_espacial(id),
 numero_version integer NOT NULL CHECK (numero_version>0),
 ruta_version_id uuid NOT NULL REFERENCES ruta_version(id),
 nombre text NOT NULL CHECK (btrim(nombre)<>''),
 tipo text NOT NULL CHECK (tipo IN ('PUNTO','GEOCERCA')),
 geometria geometry(Geometry,4326) NOT NULL,
 radio_metros double precision,
 vigente_desde timestamptz NOT NULL,
 vigente_hasta timestamptz,
 motivo text NOT NULL CHECK (btrim(motivo)<>''),
 creado_por uuid NOT NULL REFERENCES usuario(id),
 cerrado_por uuid REFERENCES usuario(id),
 motivo_cierre text,
 UNIQUE(referencia_id,numero_version),
 CHECK (NOT ST_IsEmpty(geometria) AND ST_IsValid(geometria) AND ST_NDims(geometria)=2
   AND ST_XMin(Box3D(geometria))>=-180 AND ST_XMax(Box3D(geometria))<=180
   AND ST_YMin(Box3D(geometria))>=-90 AND ST_YMax(Box3D(geometria))<=90),
 CHECK ((tipo='PUNTO' AND GeometryType(geometria)='POINT' AND radio_metros>0 AND radio_metros<=10000 AND radio_metros IS NOT NULL)
     OR (tipo='GEOCERCA' AND GeometryType(geometria) IN ('POLYGON','MULTIPOLYGON') AND radio_metros IS NULL)),
 CHECK ((vigente_hasta IS NULL AND cerrado_por IS NULL AND motivo_cierre IS NULL)
     OR (vigente_hasta>vigente_desde AND cerrado_por IS NOT NULL AND motivo_cierre IS NOT NULL AND btrim(motivo_cierre)<>''))
);
CREATE UNIQUE INDEX referencia_espacial_version_abierta ON referencia_espacial_version(referencia_id) WHERE vigente_hasta IS NULL;
CREATE INDEX referencia_espacial_version_geometria ON referencia_espacial_version USING gist(geometria);
CREATE INDEX referencia_espacial_version_ruta ON referencia_espacial_version(ruta_version_id,vigente_desde);
CREATE FUNCTION proteger_referencia_espacial_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'No se borra historia espacial' USING ERRCODE='23514'; END IF;
 IF OLD.vigente_hasta IS NOT NULL OR NEW.vigente_hasta IS NULL
    OR ROW(NEW.id,NEW.referencia_id,NEW.numero_version,NEW.ruta_version_id,NEW.nombre,NEW.tipo,
           ST_AsEWKB(NEW.geometria),NEW.radio_metros,NEW.vigente_desde,NEW.motivo,NEW.creado_por)
       IS DISTINCT FROM ROW(OLD.id,OLD.referencia_id,OLD.numero_version,OLD.ruta_version_id,OLD.nombre,OLD.tipo,
           ST_AsEWKB(OLD.geometria),OLD.radio_metros,OLD.vigente_desde,OLD.motivo,OLD.creado_por) THEN
   RAISE EXCEPTION 'Solo se permite cerrar la vigencia; modificar crea otra versión' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER referencia_espacial_version_inmutable BEFORE UPDATE OR DELETE ON referencia_espacial_version
 FOR EACH ROW EXECUTE FUNCTION proteger_referencia_espacial_version();
