ALTER TABLE route RENAME TO ruta;
ALTER TABLE route_version RENAME TO ruta_version;

ALTER TABLE ruta RENAME COLUMN code TO codigo;
ALTER TABLE ruta RENAME COLUMN name TO nombre;
ALTER TABLE ruta RENAME COLUMN active TO activo;
ALTER TABLE ruta RENAME COLUMN created_at TO creado_en;
ALTER TABLE ruta RENAME COLUMN updated_at TO actualizado_en;

ALTER TABLE ruta_version RENAME COLUMN route_id TO ruta_id;
ALTER TABLE ruta_version RENAME COLUMN version_number TO numero_version;
ALTER TABLE ruta_version RENAME COLUMN geometry TO geometria;
ALTER TABLE ruta_version RENAME COLUMN valid_from TO vigente_desde;
ALTER TABLE ruta_version RENAME COLUMN valid_to TO vigente_hasta;
ALTER TABLE ruta_version RENAME COLUMN change_reason TO motivo_cambio;
ALTER TABLE ruta_version RENAME COLUMN created_at TO creado_en;

-- PostgreSQL retains data and object identity when renaming tables/columns.
-- Constraint renames also rename their supporting primary/unique indexes.
ALTER TABLE ruta RENAME CONSTRAINT route_pkey TO ruta_pk;
ALTER TABLE ruta RENAME CONSTRAINT route_code_key TO ruta_codigo_unico;
ALTER TABLE ruta RENAME CONSTRAINT route_code_check TO ruta_codigo_no_vacio;
ALTER TABLE ruta RENAME CONSTRAINT route_name_check TO ruta_nombre_no_vacio;
ALTER TABLE ruta_version RENAME CONSTRAINT route_version_pkey TO ruta_version_pk;
ALTER TABLE ruta_version RENAME CONSTRAINT route_version_route_id_fkey TO ruta_version_ruta_fk;
ALTER TABLE ruta_version RENAME CONSTRAINT route_version_route_id_version_number_key TO ruta_version_numero_unico;
ALTER TABLE ruta_version RENAME CONSTRAINT route_version_version_number_check TO ruta_version_numero_positivo;
ALTER TABLE ruta_version RENAME CONSTRAINT route_version_check TO ruta_version_vigencia_valida;
ALTER TABLE ruta_version RENAME CONSTRAINT route_version_geometry_check TO ruta_version_tipo_srid_validos;
ALTER TABLE ruta_version RENAME CONSTRAINT route_version_geometry_check1 TO ruta_version_geometria_valida;
ALTER TABLE ruta_version RENAME CONSTRAINT route_version_geometry_check2 TO ruta_version_coordenadas_validas;
ALTER INDEX route_version_geometry_gist RENAME TO ruta_version_geometria_gist;
ALTER INDEX route_version_one_open RENAME TO ruta_version_una_abierta;

ALTER FUNCTION guard_route_version_history() RENAME TO proteger_historial_ruta_version;

-- PL/pgSQL record-field references must be updated explicitly after column renames.
CREATE OR REPLACE FUNCTION proteger_historial_ruta_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.ruta_id, NEW.numero_version, ST_AsEWKB(NEW.geometria),
           NEW.vigente_desde, NEW.motivo_cambio, NEW.creado_en)
       IS DISTINCT FROM
       ROW(OLD.id, OLD.ruta_id, OLD.numero_version, ST_AsEWKB(OLD.geometria),
           OLD.vigente_desde, OLD.motivo_cambio, OLD.creado_en)
       OR OLD.vigente_hasta IS NOT NULL OR NEW.vigente_hasta IS NULL OR NEW.vigente_hasta <= OLD.vigente_desde THEN
        RAISE EXCEPTION 'Las versiones históricas solo permiten el primer cierre de vigencia'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

ALTER TRIGGER route_version_history_guard ON ruta_version RENAME TO ruta_version_historial_inmutable;
