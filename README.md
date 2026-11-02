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
