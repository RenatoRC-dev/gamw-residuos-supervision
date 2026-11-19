-- INT-04 · Mínimo privilegio del rol de EJECUCIÓN (callback Flyway afterMigrate, NO es una migración versionada).
-- Se activa solo si la ubicación classpath:db/privilegios se incluye explícitamente en spring.flyway.locations
-- (desarrollo y pruebas aisladas). Se ejecuta con la identidad de MIGRACIÓN (propietaria del esquema) tras cada migrate.
-- Nunca omite pasos en silencio: si el rol falta, es privilegiado, coincide con el migrador o una tabla esperada no existe, FALLA.
-- Concesiones derivadas del código (INT04_AUDITORIA_PERMISOS_DB.md, P-05): SELECT/INSERT; UPDATE solo donde el backend
-- actualiza o usa SELECT ... FOR UPDATE; sin DELETE, TRUNCATE, TRIGGER, REFERENCES ni DDL. Las protecciones V20/V21 siguen intactas.
DO $$
DECLARE rol text := '${rol_ejecucion}'; r record;
BEGIN
    IF rol IS NULL OR rol = '' OR rol !~ '^[a-z_][a-z0-9_]{0,62}$' THEN
        RAISE EXCEPTION 'Configure spring.flyway.placeholders.rol_ejecucion con un nombre de rol válido (actual: "%").', rol;
    END IF;
    SELECT * INTO r FROM pg_roles WHERE rolname = rol;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'El rol de ejecución % no existe: debe crearlo el aprovisionamiento antes de migrar.', rol;
    END IF;
    IF rol = current_user THEN
        RAISE EXCEPTION 'El rol de ejecución no puede ser la identidad de migración (%).', rol;
    END IF;
    IF r.rolsuper OR r.rolcreaterole OR r.rolcreatedb OR r.rolbypassrls OR r.rolreplication THEN
        RAISE EXCEPTION 'El rol de ejecución % tiene atributos privilegiados.', rol;
    END IF;
    IF pg_has_role(rol, current_user, 'MEMBER') THEN
        RAISE EXCEPTION 'El rol de ejecución % es miembro de la identidad de migración.', rol;
    END IF;

    REVOKE CREATE ON SCHEMA public FROM PUBLIC;
    EXECUTE format('REVOKE CREATE ON SCHEMA public FROM %I', rol);
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', rol);

    -- Tablas propias del migrador (excluye el historial de Flyway): se parte de cero y se concede SELECT, INSERT.
    FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'public' AND c.relkind IN ('r','p') AND c.relowner = (SELECT oid FROM pg_roles WHERE rolname = current_user)
               AND c.relname <> 'flyway_schema_history' LOOP
        EXECUTE format('REVOKE ALL ON TABLE public.%I FROM %I', r.relname, rol);
        EXECUTE format('GRANT SELECT, INSERT ON TABLE public.%I TO %I', r.relname, rol);
    END LOOP;
    EXECUTE format('REVOKE ALL ON TABLE public.flyway_schema_history FROM %I', rol);

    -- UPDATE en las tablas que el backend actualiza (cierres, sustitución, revocación, mantenimiento de catálogos operativos)
    -- y que además bloquea con SELECT ... FOR UPDATE. Los triggers V6/V12/V15/V17/V21 siguen restringiendo qué columnas cambian.
    EXECUTE format('GRANT UPDATE ON TABLE public.vehiculo, public.vinculo_vehiculo_empresa, public.persona_natural, public.empresa_operadora,
        public.periodo_servicio, public.vinculo_persona_empresa, public.ruta, public.ruta_version, public.referencia_espacial_version,
        public.usuario, public.facultad_delegacion, public.concesion_periodo_operador, public.concesion_permiso, public.jornada,
        public.asignacion_jornada TO %I', rol);
    -- SELECT ... FOR UPDATE / FOR SHARE exige UPDATE en al menos una columna: solo la clave, en tablas que el backend bloquea
    -- pero no actualiza (reclamos, puntos críticos, referencias y catálogos habilitados). Sus triggers de inmutabilidad siguen rechazando UPDATE.
    EXECUTE format('GRANT UPDATE (id) ON TABLE public.reclamo_institucional, public.punto_critico, public.referencia_espacial,
        public.catalogo_resultado_fiscalizacion, public.catalogo_verificacion_correctiva, public.catalogo_valoracion_reclamo TO %I', rol);
END;
$$;
