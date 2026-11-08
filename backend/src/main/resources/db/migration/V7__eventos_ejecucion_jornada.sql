-- RF-09 técnico / CU-13 parcial: evidencia operativa inmutable, no resultado del servicio.
CREATE TABLE evento_ejecucion_jornada (
    id uuid CONSTRAINT evento_ejecucion_jornada_pk PRIMARY KEY,
    evento_cliente_id uuid NOT NULL CONSTRAINT evento_ejecucion_cliente_unico UNIQUE,
    jornada_id uuid NOT NULL CONSTRAINT evento_ejecucion_jornada_fk REFERENCES jornada(id),
    asignacion_jornada_id uuid NOT NULL CONSTRAINT evento_ejecucion_asignacion_fk REFERENCES asignacion_jornada(id),
    tipo_evento text NOT NULL CONSTRAINT evento_ejecucion_tipo_valido CHECK (tipo_evento IN ('INICIO','FIN')),
    capturado_en timestamptz NOT NULL,
    recibido_en timestamptz NOT NULL,
    CONSTRAINT evento_ejecucion_tipo_unico UNIQUE (jornada_id,tipo_evento)
);
CREATE INDEX evento_ejecucion_historia_idx ON evento_ejecucion_jornada(jornada_id,capturado_en);
CREATE INDEX evento_ejecucion_asignacion_idx ON evento_ejecucion_jornada(asignacion_jornada_id);

CREATE FUNCTION proteger_evento_ejecucion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La evidencia operativa persistida es inmutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER evento_ejecucion_inmutable BEFORE UPDATE ON evento_ejecucion_jornada
FOR EACH ROW EXECUTE FUNCTION proteger_evento_ejecucion();
