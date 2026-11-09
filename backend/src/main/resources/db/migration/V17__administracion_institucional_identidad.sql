ALTER TABLE concesion_permiso DROP CONSTRAINT concesion_permiso_permiso_check;
ALTER TABLE concesion_permiso ADD CONSTRAINT concesion_permiso_permiso_check CHECK(permiso IN (
 'TRAYECTORIA_JORNADA_CONSULTAR','MONITOREO_JORNADA_CONSULTAR','REFERENCIA_ESPACIAL_CONSULTAR','REFERENCIA_ESPACIAL_GESTIONAR',
 'GPS_INTEGRIDAD_REVISAR','GPS_COINCIDENCIAS_REVISAR','GPS_DESVIOS_REVISAR','INSPECCION_REGISTRAR',
 'FISCALIZACION_CONSULTAR','FISCALIZACION_EMITIR','CORRECTIVA_CONSULTAR','CORRECTIVA_SOLICITAR','CORRECTIVA_VERIFICAR',
 'USUARIOS_CONSULTAR','USUARIOS_GESTIONAR','PERMISOS_ADMINISTRAR'));

-- Ningún límite ni facultad A1 precargados. El operador local debe aprobar cada delegación.
CREATE TABLE facultad_delegacion (
 id uuid PRIMARY KEY, administrador_id uuid NOT NULL REFERENCES usuario(id), permiso text NOT NULL,
 periodo_servicio_id uuid REFERENCES periodo_servicio(id), permite_historico boolean NOT NULL DEFAULT false,
 motivo text NOT NULL CHECK(btrim(motivo)<>''), operador_local text NOT NULL CHECK(btrim(operador_local)<>''),
 otorgado_en timestamptz NOT NULL DEFAULT clock_timestamp(), revocado_en timestamptz,
 revocado_por_local text, motivo_revocacion text,
 CHECK((periodo_servicio_id IS NULL AND NOT permite_historico AND permiso IN (
  'TRAYECTORIA_JORNADA_CONSULTAR','MONITOREO_JORNADA_CONSULTAR','REFERENCIA_ESPACIAL_CONSULTAR','REFERENCIA_ESPACIAL_GESTIONAR',
  'GPS_INTEGRIDAD_REVISAR','GPS_COINCIDENCIAS_REVISAR','GPS_DESVIOS_REVISAR','INSPECCION_REGISTRAR',
  'FISCALIZACION_CONSULTAR','FISCALIZACION_EMITIR','CORRECTIVA_CONSULTAR','CORRECTIVA_SOLICITAR','CORRECTIVA_VERIFICAR'))
  OR (periodo_servicio_id IS NOT NULL AND permiso IN ('CORRECTIVA_CONSULTAR','CORRECTIVA_RESPONDER','JORNADA_REPROGRAMAR'))),
 CHECK((revocado_en IS NULL AND revocado_por_local IS NULL AND motivo_revocacion IS NULL)
  OR (revocado_en>=otorgado_en AND revocado_por_local IS NOT NULL AND btrim(revocado_por_local)<>''
      AND motivo_revocacion IS NOT NULL AND btrim(motivo_revocacion)<>''))
);
CREATE UNIQUE INDEX facultad_delegacion_municipal_idx ON facultad_delegacion(administrador_id,permiso) WHERE periodo_servicio_id IS NULL AND revocado_en IS NULL;
CREATE UNIQUE INDEX facultad_delegacion_periodo_idx ON facultad_delegacion(administrador_id,permiso,periodo_servicio_id) WHERE periodo_servicio_id IS NOT NULL AND revocado_en IS NULL;
CREATE FUNCTION proteger_facultad_delegacion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Las facultades se revocan, no se eliminan' USING ERRCODE='23514'; END IF;
 IF OLD.revocado_en IS NOT NULL OR NEW.revocado_en IS NULL OR
  ROW(NEW.id,NEW.administrador_id,NEW.permiso,NEW.periodo_servicio_id,NEW.permite_historico,NEW.motivo,NEW.operador_local,NEW.otorgado_en)
  IS DISTINCT FROM ROW(OLD.id,OLD.administrador_id,OLD.permiso,OLD.periodo_servicio_id,OLD.permite_historico,OLD.motivo,OLD.operador_local,OLD.otorgado_en) THEN
  RAISE EXCEPTION 'Solo se permite revocar una facultad vigente' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER facultad_delegacion_historia BEFORE UPDATE OR DELETE ON facultad_delegacion FOR EACH ROW EXECUTE FUNCTION proteger_facultad_delegacion();
CREATE TABLE actuacion_administrativa (
 id uuid PRIMARY KEY, actor_usuario_id uuid NOT NULL REFERENCES usuario(id), usuario_objetivo_id uuid NOT NULL REFERENCES usuario(id),
 tipo text NOT NULL CHECK(tipo IN ('CUENTA_CREAR','CUENTA_ACTUALIZAR','CUENTA_DESACTIVAR','PERMISO_OTORGAR','PERMISO_REVOCAR')),
 motivo text NOT NULL CHECK(btrim(motivo)<>''), registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 anterior jsonb NOT NULL, posterior jsonb NOT NULL
);
CREATE INDEX actuacion_administrativa_usuario_idx ON actuacion_administrativa(usuario_objetivo_id,registrada_en,id);
CREATE TRIGGER actuacion_administrativa_inmutable BEFORE UPDATE OR DELETE ON actuacion_administrativa FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
