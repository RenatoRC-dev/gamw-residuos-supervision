-- RF-10/RF-11: evidencia de ubicación, nunca resultado de recolección.
CREATE TABLE posicion_gps (
    id uuid PRIMARY KEY,
    captura_id uuid NOT NULL,
    jornada_id uuid NOT NULL REFERENCES jornada(id),
    asignacion_jornada_id uuid NOT NULL REFERENCES asignacion_jornada(id),
    usuario_id uuid NOT NULL REFERENCES usuario(id),
    capturado_en timestamptz NOT NULL,
    recibido_en timestamptz NOT NULL,
    geometria geometry(Point,4326) NOT NULL,
    precision_metros double precision NOT NULL CHECK (precision_metros >= 0 AND precision_metros < 'Infinity'::float8),
    CONSTRAINT posicion_gps_captura_unica UNIQUE(jornada_id,captura_id),
    CONSTRAINT posicion_gps_coordenadas CHECK (
        NOT ST_IsEmpty(geometria) AND ST_X(geometria) BETWEEN -180 AND 180
        AND ST_Y(geometria) BETWEEN -90 AND 90)
);
CREATE INDEX posicion_gps_historia_idx ON posicion_gps(jornada_id,capturado_en);
CREATE INDEX posicion_gps_geometria_idx ON posicion_gps USING gist(geometria);
CREATE INDEX posicion_gps_usuario_idx ON posicion_gps(usuario_id);
CREATE FUNCTION proteger_posicion_gps() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La evidencia GPS persistida es inmutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER posicion_gps_inmutable BEFORE UPDATE OR DELETE ON posicion_gps
FOR EACH ROW EXECUTE FUNCTION proteger_posicion_gps();
