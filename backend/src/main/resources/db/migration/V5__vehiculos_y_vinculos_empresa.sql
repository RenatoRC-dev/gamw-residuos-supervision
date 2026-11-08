-- CU-06 / RF-04: identidad vehicular independiente y vínculos históricos con empresas.
-- Sin catálogos institucionales inventados, cascadas de borrado ni exclusión de solapamientos.
CREATE TABLE marca_vehiculo (
    id uuid CONSTRAINT marca_vehiculo_pk PRIMARY KEY,
    nombre text NOT NULL CONSTRAINT marca_vehiculo_nombre_unico UNIQUE
        CONSTRAINT marca_vehiculo_nombre_no_vacio CHECK (length(btrim(nombre)) > 0),
    activo boolean NOT NULL
);

CREATE TABLE modelo_vehiculo (
    id uuid CONSTRAINT modelo_vehiculo_pk PRIMARY KEY,
    marca_vehiculo_id uuid NOT NULL CONSTRAINT modelo_vehiculo_marca_fk REFERENCES marca_vehiculo(id),
    nombre text NOT NULL CONSTRAINT modelo_vehiculo_nombre_no_vacio CHECK (length(btrim(nombre)) > 0),
    activo boolean NOT NULL,
    CONSTRAINT modelo_vehiculo_nombre_por_marca_unico UNIQUE (marca_vehiculo_id, nombre)
);

CREATE TABLE tipo_vehiculo (
    id uuid CONSTRAINT tipo_vehiculo_pk PRIMARY KEY,
    codigo text NOT NULL CONSTRAINT tipo_vehiculo_codigo_unico UNIQUE
        CONSTRAINT tipo_vehiculo_codigo_no_vacio CHECK (length(btrim(codigo)) > 0),
    nombre text NOT NULL CONSTRAINT tipo_vehiculo_nombre_unico UNIQUE
        CONSTRAINT tipo_vehiculo_nombre_no_vacio CHECK (length(btrim(nombre)) > 0),
    activo boolean NOT NULL
);

CREATE TABLE vehiculo (
    id uuid CONSTRAINT vehiculo_pk PRIMARY KEY,
    placa text NOT NULL CONSTRAINT vehiculo_placa_unica UNIQUE
        CONSTRAINT vehiculo_placa_normalizada CHECK (length(btrim(placa)) > 0 AND placa = btrim(placa) AND placa = upper(placa)),
    modelo_vehiculo_id uuid CONSTRAINT vehiculo_modelo_fk REFERENCES modelo_vehiculo(id),
    tipo_vehiculo_id uuid CONSTRAINT vehiculo_tipo_fk REFERENCES tipo_vehiculo(id),
    anio integer CONSTRAINT vehiculo_anio_valido CHECK (anio IS NULL OR anio >= 1900),
    capacidad_carga_kg numeric CONSTRAINT vehiculo_carga_positiva CHECK (capacidad_carga_kg IS NULL OR
        (capacidad_carga_kg > 0 AND capacidad_carga_kg < 'Infinity'::numeric)),
    capacidad_volumen_m3 numeric CONSTRAINT vehiculo_volumen_positivo CHECK (capacidad_volumen_m3 IS NULL OR
        (capacidad_volumen_m3 > 0 AND capacidad_volumen_m3 < 'Infinity'::numeric)),
    activo boolean NOT NULL,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL
);
CREATE INDEX vehiculo_modelo_idx ON vehiculo(modelo_vehiculo_id);
CREATE INDEX vehiculo_tipo_idx ON vehiculo(tipo_vehiculo_id);

CREATE TABLE vinculo_vehiculo_empresa (
    id uuid CONSTRAINT vinculo_vehiculo_empresa_pk PRIMARY KEY,
    vehiculo_id uuid NOT NULL CONSTRAINT vinculo_vehiculo_empresa_vehiculo_fk REFERENCES vehiculo(id),
    empresa_operadora_id uuid NOT NULL CONSTRAINT vinculo_vehiculo_empresa_empresa_fk REFERENCES empresa_operadora(id),
    fecha_inicio date NOT NULL,
    fecha_fin date,
    activo boolean NOT NULL,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT vinculo_vehiculo_empresa_fechas_validas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
);
CREATE INDEX vinculo_vehiculo_empresa_vehiculo_idx ON vinculo_vehiculo_empresa(vehiculo_id);
CREATE INDEX vinculo_vehiculo_empresa_empresa_idx ON vinculo_vehiculo_empresa(empresa_operadora_id);
