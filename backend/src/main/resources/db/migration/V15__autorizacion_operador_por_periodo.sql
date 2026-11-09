-- D1-A4 provisional: ninguna cuenta recibe acceso por función laboral o por defecto.
CREATE TABLE concesion_periodo_operador (
 id uuid PRIMARY KEY, usuario_id uuid NOT NULL REFERENCES usuario(id),
 periodo_servicio_id uuid NOT NULL REFERENCES periodo_servicio(id),
 permiso text NOT NULL CHECK(permiso IN ('CORRECTIVA_CONSULTAR','CORRECTIVA_RESPONDER')),
 incluye_historico boolean NOT NULL DEFAULT false,
 otorgado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 operador_local text NOT NULL CHECK(btrim(operador_local)<>''), motivo text NOT NULL CHECK(btrim(motivo)<>''),
 revocado_en timestamptz, revocado_por_local text, motivo_revocacion text,
 CHECK((revocado_en IS NULL AND revocado_por_local IS NULL AND motivo_revocacion IS NULL)
  OR (revocado_en>=otorgado_en AND revocado_por_local IS NOT NULL AND btrim(revocado_por_local)<>''
      AND motivo_revocacion IS NOT NULL AND btrim(motivo_revocacion)<>''))
);
CREATE UNIQUE INDEX concesion_operador_vigente_idx ON concesion_periodo_operador(usuario_id,periodo_servicio_id,permiso) WHERE revocado_en IS NULL;
CREATE FUNCTION proteger_concesion_operador() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Las concesiones se revocan; no se eliminan' USING ERRCODE='23514'; END IF;
 IF OLD.revocado_en IS NOT NULL OR NEW.revocado_en IS NULL OR
  ROW(NEW.id,NEW.usuario_id,NEW.periodo_servicio_id,NEW.permiso,NEW.incluye_historico,NEW.otorgado_en,NEW.operador_local,NEW.motivo)
  IS DISTINCT FROM ROW(OLD.id,OLD.usuario_id,OLD.periodo_servicio_id,OLD.permiso,OLD.incluye_historico,OLD.otorgado_en,OLD.operador_local,OLD.motivo) THEN
  RAISE EXCEPTION 'Solo se permite revocar la concesión vigente' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER concesion_operador_historia BEFORE UPDATE OR DELETE ON concesion_periodo_operador FOR EACH ROW EXECUTE FUNCTION proteger_concesion_operador();

-- Columnas nulas conservan las respuestas históricas sin reescribirlas.
ALTER TABLE respuesta_correctiva ADD COLUMN solicitud_id uuid UNIQUE;
ALTER TABLE respuesta_correctiva ADD COLUMN huella_solicitud text;
ALTER TABLE respuesta_correctiva ADD COLUMN concesion_periodo_id uuid REFERENCES concesion_periodo_operador(id);
ALTER TABLE respuesta_correctiva ADD CONSTRAINT respuesta_procedencia_check CHECK (
 (solicitud_id IS NULL AND huella_solicitud IS NULL AND concesion_periodo_id IS NULL)
 OR (solicitud_id IS NOT NULL AND huella_solicitud IS NOT NULL AND btrim(huella_solicitud)<>'' AND concesion_periodo_id IS NOT NULL));
CREATE TABLE respuesta_correctiva_evidencia (
 respuesta_id uuid NOT NULL REFERENCES respuesta_correctiva(id),
 evidencia_id uuid NOT NULL REFERENCES evidencia_inspeccion(id),
 PRIMARY KEY(respuesta_id,evidencia_id)
);
CREATE TRIGGER respuesta_correctiva_evidencia_inmutable BEFORE UPDATE OR DELETE ON respuesta_correctiva_evidencia
 FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
