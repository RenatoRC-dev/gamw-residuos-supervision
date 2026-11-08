package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.persistencia;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioVehiculosPostgres implements RepositorioVehiculos {
    private final JdbcTemplate jdbc;
    public RepositorioVehiculosPostgres(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void insertarMarca(MarcaVehiculo m) {
        jdbc.update("INSERT INTO marca_vehiculo(id,nombre,activo) VALUES (?,?,?)",m.id(),m.nombre(),m.activo());
    }
    public List<MarcaVehiculo> marcas() { return jdbc.query("SELECT * FROM marca_vehiculo ORDER BY nombre,id",this::leerMarca); }
    public Optional<MarcaVehiculo> marca(UUID id) {
        return jdbc.query("SELECT * FROM marca_vehiculo WHERE id=?",this::leerMarca,id).stream().findFirst();
    }
    public void insertarModelo(ModeloVehiculo m) {
        jdbc.update("INSERT INTO modelo_vehiculo(id,marca_vehiculo_id,nombre,activo) VALUES (?,?,?,?)",m.id(),m.marcaVehiculoId(),m.nombre(),m.activo());
    }
    public List<ModeloVehiculo> modelos(UUID marcaVehiculoId) {
        return marcaVehiculoId == null
                ? jdbc.query("SELECT * FROM modelo_vehiculo ORDER BY nombre,id",this::leerModelo)
                : jdbc.query("SELECT * FROM modelo_vehiculo WHERE marca_vehiculo_id=? ORDER BY nombre,id",this::leerModelo,marcaVehiculoId);
    }
    public Optional<ModeloVehiculo> modelo(UUID id) {
        return jdbc.query("SELECT * FROM modelo_vehiculo WHERE id=?",this::leerModelo,id).stream().findFirst();
    }
    public void insertarTipo(TipoVehiculo t) {
        jdbc.update("INSERT INTO tipo_vehiculo(id,codigo,nombre,activo) VALUES (?,?,?,?)",t.id(),t.codigo(),t.nombre(),t.activo());
    }
    public List<TipoVehiculo> tipos() { return jdbc.query("SELECT * FROM tipo_vehiculo ORDER BY nombre,id",this::leerTipo); }
    public Optional<TipoVehiculo> tipo(UUID id) {
        return jdbc.query("SELECT * FROM tipo_vehiculo WHERE id=?",this::leerTipo,id).stream().findFirst();
    }
    public void insertarVehiculo(Vehiculo v) {
        jdbc.update("""
                INSERT INTO vehiculo(id,placa,modelo_vehiculo_id,tipo_vehiculo_id,anio,capacidad_carga_kg,capacidad_volumen_m3,activo,creado_en,actualizado_en)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,v.id(),v.placa(),v.modeloVehiculoId(),v.tipoVehiculoId(),v.anio(),v.capacidadCargaKg(),v.capacidadVolumenM3(),
                v.activo(),Timestamp.from(v.creadoEn()),Timestamp.from(v.actualizadoEn()));
    }
    public void actualizarVehiculo(Vehiculo v) {
        jdbc.update("""
                UPDATE vehiculo SET placa=?,modelo_vehiculo_id=?,tipo_vehiculo_id=?,anio=?,capacidad_carga_kg=?,capacidad_volumen_m3=?,activo=?,actualizado_en=? WHERE id=?
                """,v.placa(),v.modeloVehiculoId(),v.tipoVehiculoId(),v.anio(),v.capacidadCargaKg(),v.capacidadVolumenM3(),
                v.activo(),Timestamp.from(v.actualizadoEn()),v.id());
    }
    public Optional<Vehiculo> vehiculo(UUID id, boolean bloquear) {
        return jdbc.query("SELECT * FROM vehiculo WHERE id=?" + (bloquear ? " FOR UPDATE" : ""),this::leerVehiculo,id).stream().findFirst();
    }
    public List<Vehiculo> vehiculos() { return jdbc.query("SELECT * FROM vehiculo ORDER BY placa,id",this::leerVehiculo); }
    public void insertarVinculo(VinculoVehiculoEmpresa v) {
        jdbc.update("""
                INSERT INTO vinculo_vehiculo_empresa(id,vehiculo_id,empresa_operadora_id,fecha_inicio,fecha_fin,activo,creado_en,actualizado_en)
                VALUES (?,?,?,?,?,?,?,?)
                """,v.id(),v.vehiculoId(),v.empresaOperadoraId(),v.fechaInicio(),v.fechaFin(),v.activo(),Timestamp.from(v.creadoEn()),Timestamp.from(v.actualizadoEn()));
    }
    public void cerrarVinculo(VinculoVehiculoEmpresa v) {
        jdbc.update("UPDATE vinculo_vehiculo_empresa SET fecha_fin=?,activo=?,actualizado_en=? WHERE id=? AND empresa_operadora_id=?",
                v.fechaFin(),v.activo(),Timestamp.from(v.actualizadoEn()),v.id(),v.empresaOperadoraId());
    }
    public Optional<VinculoVehiculoEmpresa> vinculo(UUID empresaId, UUID id) {
        return jdbc.query("SELECT * FROM vinculo_vehiculo_empresa WHERE empresa_operadora_id=? AND id=?",this::leerVinculo,empresaId,id).stream().findFirst();
    }
    public List<VinculoVehiculoEmpresa> vinculos(UUID empresaId) {
        return jdbc.query("SELECT * FROM vinculo_vehiculo_empresa WHERE empresa_operadora_id=? ORDER BY fecha_inicio,id",this::leerVinculo,empresaId);
    }
    private MarcaVehiculo leerMarca(ResultSet r,int i) throws SQLException {
        return new MarcaVehiculo(r.getObject("id",UUID.class),r.getString("nombre"),r.getBoolean("activo"));
    }
    private ModeloVehiculo leerModelo(ResultSet r,int i) throws SQLException {
        return new ModeloVehiculo(r.getObject("id",UUID.class),r.getObject("marca_vehiculo_id",UUID.class),r.getString("nombre"),r.getBoolean("activo"));
    }
    private TipoVehiculo leerTipo(ResultSet r,int i) throws SQLException {
        return new TipoVehiculo(r.getObject("id",UUID.class),r.getString("codigo"),r.getString("nombre"),r.getBoolean("activo"));
    }
    private Vehiculo leerVehiculo(ResultSet r,int i) throws SQLException {
        return new Vehiculo(r.getObject("id",UUID.class),r.getString("placa"),r.getObject("modelo_vehiculo_id",UUID.class),r.getObject("tipo_vehiculo_id",UUID.class),
                r.getObject("anio",Integer.class),r.getBigDecimal("capacidad_carga_kg"),r.getBigDecimal("capacidad_volumen_m3"),
                r.getBoolean("activo"),r.getTimestamp("creado_en").toInstant(),r.getTimestamp("actualizado_en").toInstant());
    }
    private VinculoVehiculoEmpresa leerVinculo(ResultSet r,int i) throws SQLException {
        var fin = r.getDate("fecha_fin");
        return new VinculoVehiculoEmpresa(r.getObject("id",UUID.class),r.getObject("vehiculo_id",UUID.class),r.getObject("empresa_operadora_id",UUID.class),
                r.getDate("fecha_inicio").toLocalDate(),fin == null ? null : fin.toLocalDate(),r.getBoolean("activo"),
                r.getTimestamp("creado_en").toInstant(),r.getTimestamp("actualizado_en").toInstant());
    }
}
