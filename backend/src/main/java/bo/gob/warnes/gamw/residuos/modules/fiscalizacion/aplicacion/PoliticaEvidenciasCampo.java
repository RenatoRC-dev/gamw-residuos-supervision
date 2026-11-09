package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.aplicacion;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.MultipartConfigElement;

@Configuration(proxyBeanMethods=false)
public class PoliticaEvidenciasCampo {
    public static final int MAX_ARCHIVO=5*1024*1024;
    private final boolean habilitadas;
    private final String referencia;
    public PoliticaEvidenciasCampo(@Value("${gamw.evidencias.fotografias-habilitadas:false}") boolean habilitadas,
            @Value("${gamw.evidencias.referencia-politica:}") String referencia) {
        if(habilitadas && referencia.isBlank()) throw new IllegalStateException("Habilitar fotografías requiere referencia explícita de política.");
        this.habilitadas=habilitadas; this.referencia=referencia;
    }
    @Bean MultipartConfigElement limitesAdjuntos() { return new MultipartConfigElement("",MAX_ARCHIVO,16*1024*1024,MAX_ARCHIVO); }
    public record Vista(boolean fotografiasHabilitadas,String referenciaPolitica,int maximoArchivos,int maximoBytes,List<String> formatos) { }
    public Vista vista() { return new Vista(habilitadas,referencia,3,MAX_ARCHIVO,List.of("image/jpeg","image/png")); }
    public record Validado(String nombre,String mime,String sha256,byte[] bytes) { }
    public Validado validar(MultipartFile archivo) {
        if(!habilitadas) throw new ResponseStatusException(HttpStatus.CONFLICT,"Fotografías deshabilitadas: política pendiente.");
        String nombre=archivo.getOriginalFilename();
        if(nombre==null || nombre.isBlank() || nombre.length()>255 || nombre.contains("/") || nombre.contains("\\") || nombre.codePoints().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nombre de evidencia inválido.");
        if(archivo.isEmpty() || archivo.getSize()>MAX_ARCHIVO) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Archivo vacío o superior a 5 MiB.");
        try {
            byte[] datos=archivo.getBytes();
            if(datos.length>MAX_ARCHIVO) throw new IOException();
            String formato;
            try(var entrada=ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
                if(entrada==null) throw new IOException();
                var lectores=ImageIO.getImageReaders(entrada);
                if(!lectores.hasNext()) throw new IOException();
                var lector=lectores.next();
                try {
                    lector.setInput(entrada,true,true); formato=lector.getFormatName().toLowerCase(Locale.ROOT);
                    if(!Set.of("png","jpeg","jpg").contains(formato)) throw new IOException();
                    int ancho=lector.getWidth(0),alto=lector.getHeight(0);
                    if(ancho<1 || alto<1 || (long)ancho*alto>10000000 || lector.read(0)==null) throw new IOException();
                } finally { lector.dispose(); }
            }
            String mime=formato.equals("png")?"image/png":"image/jpeg";
            String sufijo=nombre.toLowerCase(Locale.ROOT);
            if((mime.equals("image/png") && !sufijo.endsWith(".png")) || (mime.equals("image/jpeg") && !(sufijo.endsWith(".jpg") || sufijo.endsWith(".jpeg")))) throw new IOException();
            if(archivo.getContentType()!=null && !archivo.getContentType().equals(mime)) throw new IOException();
            return new Validado(nombre,mime,huella(datos),datos); // Nunca reencodear ni reemplazar metadatos originales.
        } catch(IOException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Solo fotografías JPEG/PNG válidas y acotadas; evidencia no registrada."); }
    }
    public static String huella(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 no disponible."); }
    }
}
