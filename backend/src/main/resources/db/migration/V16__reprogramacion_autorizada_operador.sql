ALTER TABLE concesion_periodo_operador DROP CONSTRAINT concesion_periodo_operador_permiso_check;
ALTER TABLE concesion_periodo_operador ADD CONSTRAINT concesion_periodo_operador_permiso_check
 CHECK(permiso IN ('CORRECTIVA_CONSULTAR','CORRECTIVA_RESPONDER','JORNADA_REPROGRAMAR'));
-- Identidad de la actuación y recuperación idempotente; no modifica el antecedente histórico.
CREATE TABLE reprogramacion_autorizada (
 solicitud_id uuid PRIMARY KEY, jornada_antecedente_id uuid NOT NULL UNIQUE REFERENCES jornada(id),
 jornada_sucesora_id uuid NOT NULL UNIQUE REFERENCES jornada(id),
 usuario_id uuid NOT NULL REFERENCES usuario(id), concesion_periodo_id uuid NOT NULL REFERENCES concesion_periodo_operador(id),
 huella_solicitud text NOT NULL, registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK(jornada_antecedente_id<>jornada_sucesora_id)
);
CREATE TRIGGER reprogramacion_autorizada_inmutable BEFORE UPDATE OR DELETE ON reprogramacion_autorizada
 FOR EACH ROW EXECUTE FUNCTION proteger_antecedente_fiscal();
