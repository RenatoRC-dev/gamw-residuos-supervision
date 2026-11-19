-- Endurecimiento de integridad histórica (corte INT, 2026-11-19). Migración ADITIVA: V1–V20 no se modifican.
-- No define política institucional de conservación o depuración: solo impide borrados/vaciados no autorizados por SQL directo.

-- INT-01 · La programación (jornada), la asignación histórica y la versión de ruta solo admitían restricciones de UPDATE.
-- Se bloquea DELETE. Siguen permitidas las operaciones legítimas existentes: marcar una jornada como sustituida
-- (reprogramación) y el PRIMER cierre administrativo de una asignación o de una versión de ruta (triggers V2/V6 intactos).
CREATE FUNCTION proteger_historia_planificacion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La historia de planificación (%) no se borra', TG_TABLE_NAME USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER jornada_sin_borrado BEFORE DELETE ON jornada FOR EACH ROW EXECUTE FUNCTION proteger_historia_planificacion();
CREATE TRIGGER asignacion_jornada_sin_borrado BEFORE DELETE ON asignacion_jornada FOR EACH ROW EXECUTE FUNCTION proteger_historia_planificacion();
CREATE TRIGGER ruta_version_sin_borrado BEFORE DELETE ON ruta_version FOR EACH ROW EXECUTE FUNCTION proteger_historia_planificacion();

-- INT-02 · TRUNCATE no dispara triggers de fila: las protecciones BEFORE DELETE de la evidencia inmutable se podían eludir.
-- Trigger de sentencia BEFORE TRUNCATE (también alcanza a TRUNCATE ... CASCADE) reutilizando proteger_vaciado_evidencia() de V20.
DO $$
DECLARE tabla text;
BEGIN
    FOREACH tabla IN ARRAY ARRAY[
        'jornada','asignacion_jornada','ruta_version',
        'concesion_permiso','concesion_periodo_operador','facultad_delegacion','actuacion_administrativa',
        'referencia_espacial_version',
        'inspeccion','evidencia_inspeccion','fiscalizacion','fiscalizacion_inspeccion','catalogo_resultado_fiscalizacion',
        'incidencia_operativa','accion_correctiva','respuesta_correctiva','respuesta_correctiva_evidencia',
        'verificacion_correctiva','catalogo_verificacion_correctiva',
        'reprogramacion_autorizada','actuacion_planificacion_operador',
        'catalogo_valoracion_reclamo','reclamo_institucional','revision_reclamo','relacion_reclamo','anexo_reclamo',
        'punto_critico','observacion_punto_critico','validacion_punto_critico'
    ] LOOP
        IF to_regclass('public.' || tabla) IS NULL THEN
            RAISE EXCEPTION 'Tabla histórica esperada no encontrada: %', tabla;
        END IF;
        EXECUTE format('CREATE TRIGGER %I BEFORE TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION proteger_vaciado_evidencia()',
            tabla || '_sin_vaciado', tabla);
    END LOOP;
END;
$$;

-- INT-03 · Consistencia compuesta: un evento INICIO/FIN, una posición GPS o una incidencia solo pueden referir una asignación
-- de SU MISMA jornada (la aplicación ya lo exige; ahora también la base). Antes de crear las claves se verifica la evidencia
-- existente: si hubiera inconsistencias, la migración se DETIENE con su recuento; nunca se corrigen ni borran en silencio.
DO $$
DECLARE eventos bigint; posiciones bigint; incidencias bigint;
BEGIN
    SELECT count(*) INTO eventos FROM evento_ejecucion_jornada e JOIN asignacion_jornada a ON a.id=e.asignacion_jornada_id WHERE a.jornada_id<>e.jornada_id;
    SELECT count(*) INTO posiciones FROM posicion_gps p JOIN asignacion_jornada a ON a.id=p.asignacion_jornada_id WHERE a.jornada_id<>p.jornada_id;
    SELECT count(*) INTO incidencias FROM incidencia_operativa i JOIN asignacion_jornada a ON a.id=i.asignacion_jornada_id WHERE a.jornada_id<>i.jornada_id;
    IF eventos+posiciones+incidencias>0 THEN
        RAISE EXCEPTION 'Evidencia con asignación de otra jornada (eventos=%, posiciones=%, incidencias=%): requiere revisión humana; no se corrige automáticamente.',
            eventos,posiciones,incidencias USING ERRCODE='23514';
    END IF;
END;
$$;
ALTER TABLE asignacion_jornada ADD CONSTRAINT asignacion_jornada_id_jornada_unico UNIQUE (id, jornada_id);

-- INT-07 (auditoría) · Defensa en profundidad: las vigencias de versiones de una MISMA ruta no se superponen (intervalos [desde, hasta)).
-- V2/V3 ya garantizaban una sola versión abierta; el servicio controlaba la secuencia. Sin extensiones nuevas (trigger + bloqueo de la ruta).
-- Antes de activarlo se comprueba la historia existente; ante superposiciones la migración se DETIENE sin rectificar nada.
DO $$
DECLARE superpuestas bigint;
BEGIN
    SELECT count(*) INTO superpuestas FROM ruta_version a JOIN ruta_version b ON a.ruta_id=b.ruta_id AND a.id<b.id
      AND tstzrange(a.vigente_desde,a.vigente_hasta,'[)') && tstzrange(b.vigente_desde,b.vigente_hasta,'[)');
    IF superpuestas>0 THEN
        RAISE EXCEPTION 'Versiones de ruta con vigencias superpuestas (%): requiere revisión humana; no se corrige automáticamente.',superpuestas USING ERRCODE='23514';
    END IF;
END;
$$;
CREATE FUNCTION exigir_vigencias_ruta_sin_superposicion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM 1 FROM ruta WHERE id=NEW.ruta_id FOR UPDATE;  -- serializa escrituras concurrentes de versiones de la misma ruta
    IF EXISTS (SELECT 1 FROM ruta_version v WHERE v.ruta_id=NEW.ruta_id AND v.id<>NEW.id
               AND tstzrange(v.vigente_desde,v.vigente_hasta,'[)') && tstzrange(NEW.vigente_desde,NEW.vigente_hasta,'[)')) THEN
        RAISE EXCEPTION 'La vigencia de la versión % de la ruta se superpone con otra versión histórica',NEW.numero_version USING ERRCODE='23P01';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER ruta_version_vigencias_sin_superposicion BEFORE INSERT OR UPDATE ON ruta_version
FOR EACH ROW EXECUTE FUNCTION exigir_vigencias_ruta_sin_superposicion();
ALTER TABLE evento_ejecucion_jornada ADD CONSTRAINT evento_ejecucion_asignacion_misma_jornada_fk
    FOREIGN KEY (asignacion_jornada_id, jornada_id) REFERENCES asignacion_jornada(id, jornada_id);
ALTER TABLE posicion_gps ADD CONSTRAINT posicion_gps_asignacion_misma_jornada_fk
    FOREIGN KEY (asignacion_jornada_id, jornada_id) REFERENCES asignacion_jornada(id, jornada_id);
ALTER TABLE incidencia_operativa ADD CONSTRAINT incidencia_asignacion_misma_jornada_fk
    FOREIGN KEY (asignacion_jornada_id, jornada_id) REFERENCES asignacion_jornada(id, jornada_id);
