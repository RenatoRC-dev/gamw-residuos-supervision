-- RF-01 parcial / CU-01 parcial: identidad de acceso autenticada.
-- Usuario es identidad de acceso al sistema; PersonaNatural sigue siendo la persona del negocio.
-- No se crean roles ni permisos: FuncionOperativa no es un rol de seguridad.
CREATE TABLE usuario (
    id uuid CONSTRAINT usuario_pk PRIMARY KEY,
    persona_natural_id uuid NOT NULL CONSTRAINT usuario_persona_natural_fk REFERENCES persona_natural(id),
    nombre_usuario text NOT NULL CONSTRAINT usuario_nombre_usuario_unico UNIQUE,
    contrasena_hash text NOT NULL,
    activo boolean NOT NULL DEFAULT true,
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    -- Sin espacios al inicio ni al final: el nombre de usuario se almacena tal como se compara.
    -- La unicidad es sensible a mayúsculas: no existe regla institucional que la unifique.
    CONSTRAINT usuario_nombre_usuario_no_vacio CHECK (nombre_usuario <> ''),
    CONSTRAINT usuario_nombre_usuario_sin_espacios_extremos CHECK (nombre_usuario = btrim(nombre_usuario)),
    CONSTRAINT usuario_contrasena_hash_no_vacia CHECK (btrim(contrasena_hash) <> '')
);

-- Atribución del actor autenticado sobre la evidencia existente.
-- Permanece NULL para la evidencia histórica de la Slice 3B: no se inventa identidad.
-- Sin ON DELETE CASCADE: la evidencia institucional no debe desaparecer en cascada.
ALTER TABLE evento_ejecucion_jornada
    ADD COLUMN usuario_id uuid NULL CONSTRAINT evento_ejecucion_usuario_fk REFERENCES usuario(id);
CREATE INDEX evento_ejecucion_usuario_idx ON evento_ejecucion_jornada(usuario_id);
