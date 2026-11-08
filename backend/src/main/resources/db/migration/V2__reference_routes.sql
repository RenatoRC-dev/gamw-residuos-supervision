CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE route (
    id uuid PRIMARY KEY,
    code varchar(64) NOT NULL UNIQUE CHECK (length(btrim(code)) > 0),
    name varchar(160) NOT NULL CHECK (length(btrim(name)) > 0),
    active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE route_version (
    id uuid PRIMARY KEY,
    route_id uuid NOT NULL REFERENCES route(id),
    version_number integer NOT NULL CHECK (version_number > 0),
    geometry geometry(LineString, 4326) NOT NULL,
    valid_from timestamptz NOT NULL,
    valid_to timestamptz,
    change_reason varchar(500),
    created_at timestamptz NOT NULL,
    UNIQUE (route_id, version_number),
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    CHECK (ST_SRID(geometry) = 4326 AND GeometryType(geometry) = 'LINESTRING'),
    CHECK (NOT ST_IsEmpty(geometry) AND ST_NPoints(geometry) >= 2 AND ST_IsValid(geometry)),
    CHECK (ST_XMin(Box3D(geometry)) >= -180 AND ST_XMax(Box3D(geometry)) <= 180
       AND ST_YMin(Box3D(geometry)) >= -90 AND ST_YMax(Box3D(geometry)) <= 90)
);

CREATE INDEX route_version_geometry_gist ON route_version USING gist (geometry);
CREATE UNIQUE INDEX route_version_one_open ON route_version(route_id) WHERE valid_to IS NULL;

-- A historical version can only be closed once; its geometry and provenance never change.
CREATE FUNCTION guard_route_version_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.route_id, NEW.version_number, ST_AsEWKB(NEW.geometry),
           NEW.valid_from, NEW.change_reason, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.id, OLD.route_id, OLD.version_number, ST_AsEWKB(OLD.geometry),
           OLD.valid_from, OLD.change_reason, OLD.created_at)
       OR OLD.valid_to IS NOT NULL OR NEW.valid_to IS NULL OR NEW.valid_to <= OLD.valid_from THEN
        RAISE EXCEPTION 'Historical route versions are immutable except for their first validity closure'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER route_version_history_guard BEFORE UPDATE ON route_version
FOR EACH ROW EXECUTE FUNCTION guard_route_version_history();
