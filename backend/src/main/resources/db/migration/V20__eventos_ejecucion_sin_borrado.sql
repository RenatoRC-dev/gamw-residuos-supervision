-- H-03 (auditoría 2026-11-19) · RF-09/RF-11 · CU-13, CU-15/16/21/29.
-- V7 protegía INICIO/FIN solo frente a UPDATE. La evidencia operativa original tampoco se borra ni se vacía.
-- Migración aditiva: V7 no se modifica; se reutiliza la función proteger_evento_ejecucion() (mismo mensaje y SQLSTATE 23514).
-- No crea ningún mecanismo administrativo de conservación o depuración: esa política institucional no está aprobada.
CREATE TRIGGER evento_ejecucion_sin_borrado BEFORE DELETE ON evento_ejecucion_jornada
FOR EACH ROW EXECUTE FUNCTION proteger_evento_ejecucion();

-- TRUNCATE no dispara triggers de fila: se bloquea con un trigger de sentencia, también para posicion_gps (V9 protege UPDATE/DELETE por fila).
CREATE FUNCTION proteger_vaciado_evidencia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La evidencia operativa persistida no se vacía' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER evento_ejecucion_sin_vaciado BEFORE TRUNCATE ON evento_ejecucion_jornada
FOR EACH STATEMENT EXECUTE FUNCTION proteger_vaciado_evidencia();
CREATE TRIGGER posicion_gps_sin_vaciado BEFORE TRUNCATE ON posicion_gps
FOR EACH STATEMENT EXECUTE FUNCTION proteger_vaciado_evidencia();
