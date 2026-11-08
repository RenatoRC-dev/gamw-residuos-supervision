-- CU-11/CU-12; RF-07/RF-08 y fundamento parcial de RF-20. Vigencia administrativa, no ejecución.
CREATE TABLE jornada (
    id uuid CONSTRAINT jornada_pk PRIMARY KEY,
    periodo_servicio_id uuid NOT NULL CONSTRAINT jornada_periodo_fk REFERENCES periodo_servicio(id),
    ruta_version_id uuid NOT NULL CONSTRAINT jornada_ruta_version_fk REFERENCES ruta_version(id),
    inicio_programado timestamptz NOT NULL,
    fin_programado timestamptz,
    jornada_antecedente_id uuid CONSTRAINT jornada_antecedente_fk REFERENCES jornada(id)
        CONSTRAINT jornada_antecedente_unico UNIQUE,
    motivo_reprogramacion text,
    vigente boolean NOT NULL,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT jornada_intervalo_valido CHECK (fin_programado IS NULL OR fin_programado > inicio_programado),
    CONSTRAINT jornada_antecedente_distinto CHECK (jornada_antecedente_id IS NULL OR jornada_antecedente_id <> id),
    CONSTRAINT jornada_motivo_coherente CHECK (
        (jornada_antecedente_id IS NULL AND motivo_reprogramacion IS NULL) OR
        (jornada_antecedente_id IS NOT NULL AND motivo_reprogramacion IS NOT NULL AND length(btrim(motivo_reprogramacion)) > 0))
);
CREATE INDEX jornada_periodo_idx ON jornada(periodo_servicio_id);
CREATE INDEX jornada_ruta_version_idx ON jornada(ruta_version_id);
CREATE INDEX jornada_inicio_idx ON jornada(inicio_programado);

CREATE TABLE asignacion_jornada (
    id uuid CONSTRAINT asignacion_jornada_pk PRIMARY KEY,
    jornada_id uuid NOT NULL CONSTRAINT asignacion_jornada_jornada_fk REFERENCES jornada(id),
    vinculo_vehiculo_empresa_id uuid NOT NULL CONSTRAINT asignacion_jornada_vehiculo_fk REFERENCES vinculo_vehiculo_empresa(id),
    vinculo_persona_empresa_id uuid NOT NULL CONSTRAINT asignacion_jornada_persona_fk REFERENCES vinculo_persona_empresa(id),
    vigente_desde timestamptz NOT NULL,
    vigente_hasta timestamptz,
    motivo_cambio text CONSTRAINT asignacion_jornada_motivo_no_vacio CHECK (motivo_cambio IS NULL OR length(btrim(motivo_cambio)) > 0),
    creado_en timestamptz NOT NULL,
    CONSTRAINT asignacion_jornada_intervalo_valido CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde)
);
CREATE UNIQUE INDEX asignacion_jornada_una_abierta ON asignacion_jornada(jornada_id) WHERE vigente_hasta IS NULL;
CREATE INDEX asignacion_jornada_historia_idx ON asignacion_jornada(jornada_id,vigente_desde);
CREATE INDEX asignacion_jornada_vehiculo_idx ON asignacion_jornada(vinculo_vehiculo_empresa_id);
CREATE INDEX asignacion_jornada_persona_idx ON asignacion_jornada(vinculo_persona_empresa_id);

-- Protección adicional de identidad histórica, coherente con la baseline de versiones de ruta.
CREATE FUNCTION proteger_programacion_jornada() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id,NEW.periodo_servicio_id,NEW.ruta_version_id,NEW.inicio_programado,NEW.fin_programado,
           NEW.jornada_antecedente_id,NEW.motivo_reprogramacion,NEW.creado_en)
       IS DISTINCT FROM
       ROW(OLD.id,OLD.periodo_servicio_id,OLD.ruta_version_id,OLD.inicio_programado,OLD.fin_programado,
           OLD.jornada_antecedente_id,OLD.motivo_reprogramacion,OLD.creado_en)
       OR NOT OLD.vigente OR NEW.vigente THEN
        RAISE EXCEPTION 'La programación histórica solo permite marcar la jornada como sustituida' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER jornada_historia_inmutable BEFORE UPDATE ON jornada
FOR EACH ROW EXECUTE FUNCTION proteger_programacion_jornada();

CREATE FUNCTION proteger_asignacion_jornada() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id,NEW.jornada_id,NEW.vinculo_vehiculo_empresa_id,NEW.vinculo_persona_empresa_id,
           NEW.vigente_desde,NEW.motivo_cambio,NEW.creado_en)
       IS DISTINCT FROM
       ROW(OLD.id,OLD.jornada_id,OLD.vinculo_vehiculo_empresa_id,OLD.vinculo_persona_empresa_id,
           OLD.vigente_desde,OLD.motivo_cambio,OLD.creado_en)
       OR OLD.vigente_hasta IS NOT NULL OR NEW.vigente_hasta IS NULL THEN
        RAISE EXCEPTION 'La asignación histórica solo permite su primer cierre administrativo' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER asignacion_jornada_historia_inmutable BEFORE UPDATE ON asignacion_jornada
FOR EACH ROW EXECUTE FUNCTION proteger_asignacion_jornada();
