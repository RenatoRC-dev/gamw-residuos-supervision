-- CU-15 independiente de CU-16. Sin concesiones automáticas ni modificación de GPS.
ALTER TABLE concesion_permiso DROP CONSTRAINT concesion_permiso_permiso_check;
ALTER TABLE concesion_permiso ADD CONSTRAINT concesion_permiso_permiso_check
    CHECK (permiso IN ('TRAYECTORIA_JORNADA_CONSULTAR','MONITOREO_JORNADA_CONSULTAR'));
CREATE INDEX posicion_gps_ultima_captura_idx
    ON posicion_gps(jornada_id,capturado_en DESC,captura_id DESC,id DESC);
