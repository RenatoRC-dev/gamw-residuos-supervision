-- D1-A: política provisional de desarrollo, no designación institucional de A3.
-- Ninguna cuenta existente recibe permisos automáticamente.
CREATE TABLE concesion_permiso (
    id uuid PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES usuario(id),
    permiso text NOT NULL CHECK (permiso = 'TRAYECTORIA_JORNADA_CONSULTAR'),
    alcance text NOT NULL CHECK (alcance = 'MUNICIPAL_GAMW'),
    otorgado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
    operador_local text NOT NULL CHECK (btrim(operador_local) <> ''),
    motivo text NOT NULL CHECK (btrim(motivo) <> ''),
    revocado_en timestamptz,
    revocado_por_local text,
    motivo_revocacion text,
    CHECK ((revocado_en IS NULL AND revocado_por_local IS NULL AND motivo_revocacion IS NULL)
        OR (revocado_en >= otorgado_en AND btrim(revocado_por_local) <> ''
            AND btrim(motivo_revocacion) <> '' AND revocado_por_local IS NOT NULL AND motivo_revocacion IS NOT NULL))
);
CREATE UNIQUE INDEX concesion_permiso_vigente_unica
    ON concesion_permiso(usuario_id,permiso,alcance) WHERE revocado_en IS NULL;
CREATE FUNCTION proteger_concesion_permiso() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Las concesiones se revocan; no se borran' USING ERRCODE='23514';
    END IF;
    IF OLD.revocado_en IS NOT NULL
       OR ROW(NEW.id,NEW.usuario_id,NEW.permiso,NEW.alcance,NEW.otorgado_en,NEW.operador_local,NEW.motivo)
          IS DISTINCT FROM ROW(OLD.id,OLD.usuario_id,OLD.permiso,OLD.alcance,OLD.otorgado_en,OLD.operador_local,OLD.motivo)
       OR NEW.revocado_en IS NULL THEN
        RAISE EXCEPTION 'Solo se permite revocar una concesión vigente' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER concesion_permiso_historia BEFORE UPDATE OR DELETE ON concesion_permiso
    FOR EACH ROW EXECUTE FUNCTION proteger_concesion_permiso();
