-- CU-04/CU-05/CU-07 (parcial), RF-03/RF-04 (parcial). BR-03: conservar vínculos históricos.
CREATE TABLE persona_natural (
    id uuid CONSTRAINT persona_natural_pk PRIMARY KEY,
    ci text NOT NULL CONSTRAINT persona_natural_ci_unico UNIQUE CONSTRAINT persona_natural_ci_no_vacio CHECK (length(btrim(ci)) > 0),
    nombres text NOT NULL CONSTRAINT persona_natural_nombres_no_vacios CHECK (length(btrim(nombres)) > 0),
    apellido_paterno text,
    apellido_materno text,
    telefono text,
    activo boolean NOT NULL,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL
);

CREATE TABLE empresa_operadora (
    id uuid CONSTRAINT empresa_operadora_pk PRIMARY KEY,
    nit text CONSTRAINT empresa_operadora_nit_unico UNIQUE CONSTRAINT empresa_operadora_nit_no_vacio CHECK (nit IS NULL OR length(btrim(nit)) > 0),
    razon_social text NOT NULL CONSTRAINT empresa_operadora_razon_social_no_vacia CHECK (length(btrim(razon_social)) > 0),
    nombre_comercial text,
    activo boolean NOT NULL,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL
);

CREATE TABLE periodo_servicio (
    id uuid CONSTRAINT periodo_servicio_pk PRIMARY KEY,
    empresa_operadora_id uuid NOT NULL CONSTRAINT periodo_servicio_empresa_fk REFERENCES empresa_operadora(id),
    fecha_inicio date NOT NULL,
    fecha_fin date NOT NULL,
    activo boolean NOT NULL,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT periodo_servicio_fechas_validas CHECK (fecha_fin >= fecha_inicio)
);
CREATE INDEX periodo_servicio_empresa_idx ON periodo_servicio(empresa_operadora_id);

CREATE TABLE vinculo_persona_empresa (
    id uuid CONSTRAINT vinculo_persona_empresa_pk PRIMARY KEY,
    persona_id uuid NOT NULL CONSTRAINT vinculo_persona_empresa_persona_fk REFERENCES persona_natural(id),
    empresa_operadora_id uuid NOT NULL CONSTRAINT vinculo_persona_empresa_empresa_fk REFERENCES empresa_operadora(id),
    fecha_inicio date NOT NULL,
    fecha_fin date,
    activo boolean NOT NULL,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT vinculo_persona_empresa_fechas_validas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
);
CREATE INDEX vinculo_persona_empresa_persona_idx ON vinculo_persona_empresa(persona_id);
CREATE INDEX vinculo_persona_empresa_empresa_idx ON vinculo_persona_empresa(empresa_operadora_id);

-- Funciones del trabajo operativo (actores A4/A5), nunca roles de acceso al sistema.
CREATE TABLE funcion_operativa (
    id uuid CONSTRAINT funcion_operativa_pk PRIMARY KEY,
    codigo text NOT NULL CONSTRAINT funcion_operativa_codigo_unico UNIQUE CONSTRAINT funcion_operativa_codigo_no_vacio CHECK (length(btrim(codigo)) > 0),
    nombre text NOT NULL CONSTRAINT funcion_operativa_nombre_no_vacio CHECK (length(btrim(nombre)) > 0),
    activo boolean NOT NULL
);
INSERT INTO funcion_operativa(id, codigo, nombre, activo) VALUES
('00000000-0000-4000-8000-000000000001', 'CONDUCTOR', 'Conductor', true),
('00000000-0000-4000-8000-000000000002', 'SUPERVISOR_EMPRESA', 'Supervisor de empresa', true);

CREATE TABLE vinculo_persona_funcion (
    vinculo_persona_empresa_id uuid NOT NULL CONSTRAINT vinculo_persona_funcion_vinculo_fk REFERENCES vinculo_persona_empresa(id),
    funcion_operativa_id uuid NOT NULL CONSTRAINT vinculo_persona_funcion_funcion_fk REFERENCES funcion_operativa(id),
    CONSTRAINT vinculo_persona_funcion_pk PRIMARY KEY (vinculo_persona_empresa_id, funcion_operativa_id)
);
CREATE INDEX vinculo_persona_funcion_funcion_idx ON vinculo_persona_funcion(funcion_operativa_id);
