# api-gateway

Componente de [LogiTrack Atom](https://github.com/LogiTrack-Atom) para Sprint03.

## Responsabilidad

Entrada única, enrutamiento y seguridad de LogiTrack Atom.

Responsable de implementación: [@MaxDZ-10](https://github.com/MaxDZ-10).

## Estado

La base de [#1](https://github.com/LogiTrack-Atom/api-gateway/issues/1) incorpora WebFlux,
Config Client, Eureka Client, LoadBalancer, salud y construcción Docker. Las rutas
públicas se desarrollan en #2. La seguridad de #3 valida JWT RS256 y aplica permisos
por método/ruta antes de enviar una petición al destino.

## Requisitos y configuración

Java 25, Spring Boot 4.1.1 y Spring Cloud 2025.1.4. Se conserva el verificador de
compatibilidad. La aplicación reactiva usa Netty; no agrega MVC ni una base de datos.

| Variable | Uso |
| --- | --- |
| SPRING_PROFILES_ACTIVE | dev o prod para consumir Config Server; local para prueba aislada |
| CONFIG_SERVER_URL | URL de Config Server; en Compose, http://configserver:8085 |
| CONFIG_LABEL | Rama develop durante desarrollo; commit o tag explícito en entrega |
| SERVER_PORT | Puerto HTTP; usar el mismo valor en configuración remota y sonda si se modifica |
| JWT_PUBLIC_KEY_LOCATION | URI file: de la clave pública RSA externa; obligatoria también en local |

Los archivos api-gateway.yml y api-gateway-dev.yml/api-gateway-prod.yml se mantienen
en configserver-ms y deben suministrar logitrack.config.marker, puerto, Eureka y rutas.
Dev/prod requieren Config Server disponible: no usan una importación optional.
El perfil local desactiva Config Client y Eureka y no define rutas de negocio.

```sh
./mvnw -B -ntp verify javadoc:javadoc
JWT_PUBLIC_KEY_LOCATION=file:/ruta/externa/public.pem SPRING_PROFILES_ACTIVE=local \
  java -jar target/api-gateway-0.3.0-SNAPSHOT.jar
```

La salud está disponible en /actuator/health sin detalles. No se habilitan env,
configprops ni el endpoint de rutas de Actuator. La base no incluye rutas locales
de negocio; los prefijos, permisos y configuración final se acuerdan en
[Contratos técnicos](https://github.com/LogiTrack-Atom/shipment-service/blob/develop/docs/sprint03/Contratos_tecnicos.md).

## Pruebas y construcción

La verificación inicial del 02/11/2026 ejecutó 13 pruebas sin fallos ni omisiones y
generó JavaDoc. Se comprueban HTTP reactivo real, salud sin detalles, ausencia de
diagnósticos y rutas locales, consumo del protocolo Config Server en dev/prod,
fallo ante ausencia de Config Server/marcador y comportamiento de la sonda.

El fixture HTTP de configuración no reemplaza la integración con Config Server y
Git del equipo. El registro real en Eureka se comprueba al disponer de esa infraestructura;
la construcción Docker y la salud del contenedor se verifican en CI.

La verificación de seguridad amplía la suite a 144 pruebas. Incluye la matriz de los
tres roles con HTTP real y un destino controlado, errores 401/403 sin envío al destino,
firma/algoritmo/claims/tiempos inválidos, cabeceras ambiguas, ausencia de sesión y
propagación exclusiva del Bearer validado. Se conserva la validación de pertenencia
en shipment/courier. El [contrato de seguridad](docs/Seguridad.md) detalla configuración,
permisos, límites y las verificaciones de integración pendientes.

```sh
docker build -t logitrack/api-gateway:desarrollo .
docker run --rm -e SPRING_PROFILES_ACTIVE=local \
  -e JWT_PUBLIC_KEY_LOCATION=file:/run/keys/public.pem \
  --group-add "$(id -g)" \
  --mount type=bind,source=/ruta/externa/public.pem,target=/run/keys/public.pem,readonly \
  -p 127.0.0.1:8066:8066 logitrack/api-gateway:desarrollo
```

La imagen usa UID/GID 10001 y una sonda Java sin utilidades de red adicionales.
El archivo público debe ser legible por ese usuario o su grupo suplementario; la
clave privada no se monta en Gateway.
CI ejecuta las pruebas, JavaDoc, construcción y salud del contenedor local en las PR
y los cambios integrados en develop. Genera claves efímeras de comprobación y monta
únicamente la pública en Gateway. No publica imágenes de entrega automáticamente.

## Flujo de trabajo

- Crear una rama `feature/<numero-de-issue>-<descripcion>` desde `develop` al iniciar cada tarea.
- Enlazar la issue y abrir el pull request con destino exclusivo a `develop`.
- Mantener el seguimiento en el [Project común](https://github.com/orgs/LogiTrack-Atom/projects/1).
- Integrar con pruebas pertinentes y documentación de los cambios.

El Compose general y los contratos se mantienen en [shipment-service](https://github.com/LogiTrack-Atom/shipment-service/tree/develop).


## CORS del frontend · Sprint04

`CORS_ALLOWED_ORIGINS` define una lista separada por comas de orígenes HTTP/HTTPS
exactos, por ejemplo `http://localhost:5173,https://frontend.example.com`.
Sin configuración, Gateway no autoriza solicitudes cross-origin. Se rechazan
comodines, patrones, `null`, credenciales, rutas (también `/` final), query y fragmento.
El origen remoto definitivo debe confirmarse con el hosting de frontend#2.

La única política se aplica en Spring Security antes de JWT a `/applications/**`:
GET, POST, PUT, PATCH y OPTIONS; cabeceras Authorization/Content-Type; Location
expuesta al navegador; caché de preflight de 600 segundos; sin cookies ni Allow-Credentials. No agregar otro CorsWebFilter,
CORS de Nginx ni globalcors en Gateway, porque duplicaría la configuración.
Un OPTIONS válido no requiere Bearer; la petición de negocio conserva JWT, roles
y pertenencia de los servicios. CORS no sustituye autorización ni bloquea clientes
que no sean navegadores.

En el proxy del mismo origen, conservar `/applications/` al reenviar al Gateway:

```nginx
location /applications/ {
    proxy_pass http://gateway:8066;
    proxy_set_header Host $http_host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_intercept_errors off;
}
location / {
    try_files $uri $uri/ /index.html;
}
```

No añadir `/` al final de proxy_pass: cambiaría la ruta enviada. El bloque de API
no lleva fallback SPA ni cabeceras CORS. En producción, usar el destino interno
acordado y el origen/TLS público del hosting; el ejemplo no los configura por sí solo.
La preparación de red no aprueba G0 ni la integración funcional del frontend.


### Evidencia de #8 · 2026-11-09

En checkout aislado de develop `283cbf80423705b3768787b77c8d35f9a0fd6305`, con
Java 25.0.4.1, Boot 4.1.1 y Cloud 2025.1.4:

```sh
bash mvnw -B -ntp -o verify javadoc:javadoc
```

Resultado: **162 casos, cero fallos/errores/omisiones**, incluidos 17 casos CORS
nuevos; JavaDoc de producción con doclint y warnings fatales. Las clases y métodos
nuevos de prueba también están documentados, aunque este goal no genera test-javadoc.
`GatewayCorsTest` inicia Gateway y un proveedor HTTP propios, valida allowlist,
preflight antes de JWT, PATCH, 401, cabeceras/métodos rechazados, configuración
por defecto cerrada y ausencia de credenciales CORS. La regresión completa verifica
además las rutas y roles anteriores; no se sustituyó el decoder por un mock.

La aceptación adicional abrió un navegador real frente a Nginx en loopback y
Gateway ejecutado desde el JAR construido. El origen permitido se inyectó mediante
`CORS_ALLOWED_ORIGINS`. Se usaron RSA/JWT efímeros y un proveedor HTTP ficticio sin
base de datos; no acredita las reglas de dominio ni el hosting definitivo de Gab.

| Comprobación del navegador | Resultado |
| --- | --- |
| PATCH cross-origin con JWT válido; preflight automático | 200, cuerpo EN_RUTA |
| Location leída desde JavaScript | /applications/apishipment/api/v1/envios/42 |
| Negocio anónimo cross-origin | 401 legible por el origen autorizado |
| PATCH por proxy del mismo origen | 200 |
| Recurso inexistente por proxy | 404 JSON, sin fallback index.html |
| Negocio anónimo por proxy | 401 |
| PATCH desde otro puerto/origen no permitido | Fetch bloqueado por CORS |

El proveedor observó exactamente dos PATCH (directo y por proxy) y dos GET 404
(sondeo inicial del proxy y navegador), todos con Bearer. Ningún OPTIONS ni petición
anónima/ajena alcanzó ese proveedor. Nginx no agrega cabeceras CORS y conserva el
prefijo al enviar a Gateway; StripPrefix=2 se aplica una vez en Gateway.

Imagen oficial de Nginx utilizada en ese laboratorio:
`nginx@sha256:583b7f2fce1cdb8da5ad5c39ebd3c33b98b87d493190e548bdee1fa8c619c311`.
Los servidores, páginas con credenciales ficticias, claves y contenedor del ensayo
se retiraron; los contenedores previos quedaron conservados. No se modificó el
servicio del equipo ni su configuración. El origen público/TLS y la integración de
negocio continúan sujetos al traspaso del hosting y a las decisiones pendientes de G0.

Para reproducir la comprobación de navegador sobre un laboratorio autorizado:
servir la SPA con el bloque Nginx anterior, configurar su origen exacto en Gateway,
probar un PATCH con JWT emitido por el security del laboratorio y comprobar en la
pestaña de red el OPTIONS sin Authorization, la respuesta CORS y el PATCH posterior.
Repetir desde un origen ajeno, sin credencial y por el proxy del mismo origen;
comprobar que el 404 de una ruta de API existente con ID inexistente sigue siendo
JSON. No usar JWT del equipo ni convertir el proveedor ficticio en aceptación de datos.

## Sesión renovable del navegador (security-service#11)

POST de `/applications/apisecurity/api/v1/auth/session`, `/refresh` y `/logout`
se reenvían públicamente al servicio de seguridad, que exige Origin exacto y
`X-LogiTrack-Session: 1`. El resto del negocio sigue con Bearer. No se agregan rutas
GET de sesión, cookies cross-origin ni cabeceras CORS con credenciales.

Para un proxy HTTPS que termina TLS antes de Gateway, fijar
`CORS_ALLOWED_ORIGINS` al mismo origen público exacto de `SESSION_ORIGIN`; evita
confundir el origen público con el transporte HTTP interno. La cabecera de sesión
no se agrega a la allowlist cross-origin: esta funcionalidad usa Nginx del mismo origen.

Las tres rutas POST y la denegación GET se prueban con Gateway HTTP real y proveedor
controlado. La integración completa con Security nuevo, cookies, rotación y Nginx
se probó en el proyecto descartable `lt-s04-session11`. No publica imágenes ni cambia
el label centralizado o los demás contratos.
