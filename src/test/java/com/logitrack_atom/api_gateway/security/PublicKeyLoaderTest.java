package com.logitrack_atom.api_gateway.security;

import com.logitrack_atom.api_gateway.support.TestRsaKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.UrlResource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que solo una clave pública local RSA suficientemente fuerte habilita el arranque.
 *
 * @author MaxDz
 * @version 0.3.0
 */
class PublicKeyLoaderTest {
    @TempDir
    Path directory;

    /** La clave pública se carga sin necesitar una clave privada en el servicio. */
    @Test
    void cargaPublicaValida() {
        assertThat(new PublicKeyLoader().cargar(TestRsaKeys.publicResource()).getEncoded())
                .isEqualTo(TestRsaKeys.pair().getPublic().getEncoded());
    }

    /** Una clave RSA pequeña no cumple el contrato de arranque. */
    @Test
    void rechazaRsaDebil() throws Exception {
        Path path = directory.resolve("weak.pem");
        TestRsaKeys.escribir(path, "PUBLIC KEY", TestRsaKeys.generar(1024).getPublic().getEncoded());
        assertThatThrownBy(() -> new PublicKeyLoader().cargar(new FileSystemResource(path)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Un archivo privado PKCS#8 no se acepta como clave pública del consumidor. */
    @Test
    void rechazaMaterialPrivado() throws Exception {
        Path path = directory.resolve("fixture-private.pem");
        TestRsaKeys.escribir(path, "PRIVATE KEY", TestRsaKeys.pair().getPrivate().getEncoded());
        assertThatThrownBy(() -> new PublicKeyLoader().cargar(new FileSystemResource(path)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("PRIVATE KEY");
    }

    /** Los archivos ausentes o inválidos no habilitan fallback ni revelan su contenido. */
    @Test
    void rechazaArchivoAusenteOInvalido() throws Exception {
        Path path = directory.resolve("invalid.pem");
        assertThatThrownBy(() -> new PublicKeyLoader().cargar(new FileSystemResource(path)))
                .isInstanceOf(IllegalStateException.class);
        Files.writeString(path, "contenido-privado-del-fixture");
        assertThatThrownBy(() -> new PublicKeyLoader().cargar(new FileSystemResource(path)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("contenido-privado-del-fixture");
    }

    /** El cargador rechaza URLs antes de realizar una conexión remota. */
    @Test
    void rechazaOrigenRemoto() throws Exception {
        assertThatThrownBy(() -> new PublicKeyLoader().cargar(new UrlResource("https://example.com/public.pem")))
                .isInstanceOf(IllegalStateException.class);
    }
}
