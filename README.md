# api-gateway

Componente de [LogiTrack Atom](https://github.com/LogiTrack-Atom) para Sprint03.

## Responsabilidad

Entrada única, enrutamiento y seguridad de LogiTrack Atom.

Responsable de implementación: [@MaxDZ-10](https://github.com/MaxDZ-10).

## Estado

La base de [#1](https://github.com/LogiTrack-Atom/api-gateway/issues/1) incorpora WebFlux,
Config Client, Eureka Client, LoadBalancer, salud y construcción Docker. Las rutas
públicas se desarrollan en #2 y la validación JWT y autorización en #3.

## Requisitos y configuración

Java 25, Spring Boot 4.1.1 y Spring Cloud 2025.1.4. Se conserva el verificador de
compatibilidad. La aplicación reactiva usa Netty; no agrega MVC ni una base de datos.

| Variable | Uso |
| --- | --- |
| SPRING_PROFILES_ACTIVE | dev o prod para consumir Config Server; local para prueba aislada |
| CONFIG_SERVER_URL | URL de Config Server; en Compose, http://configserver:8085 |
| CONFIG_LABEL | Rama develop durante desarrollo; commit o tag explícito en entrega |
| SERVER_PORT | Puerto HTTP; usar el mismo valor en configuración remota y sonda si se modifica |

Los archivos api-gateway.yml y api-gateway-dev.yml/api-gateway-prod.yml se mantienen
en configserver-ms y deben suministrar logitrack.config.marker, puerto, Eureka y rutas.
Dev/prod requieren Config Server disponible: no usan una importación optional.
El perfil local desactiva Config Client y Eureka y no define rutas de negocio.

```sh
./mvnw -B -ntp verify javadoc:javadoc
SPRING_PROFILES_ACTIVE=local java -jar target/api-gateway-0.3.0-SNAPSHOT.jar
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

El fixture HTTP de configuración no reemplaza la integración con el Config Server
y Git del equipo. El registro real en Eureka y la construcción/ejecución Docker
deben verificarse como parte del cierre de #1 y de la integración general.

```sh
docker build -t logitrack/api-gateway:desarrollo .
docker run --rm -e SPRING_PROFILES_ACTIVE=local -p 127.0.0.1:8066:8066 logitrack/api-gateway:desarrollo
```

La imagen usa UID/GID 10001 y una sonda Java sin utilidades de red adicionales.
CI ejecuta las pruebas, JavaDoc, construcción y salud del contenedor local en las PR
y los cambios integrados en develop. No publica imágenes de entrega automáticamente.

## Flujo de trabajo

- Crear una rama `feature/<numero-de-issue>-<descripcion>` desde `develop` al iniciar cada tarea.
- Enlazar la issue y abrir el pull request con destino exclusivo a `develop`.
- Mantener el seguimiento en el [Project común](https://github.com/orgs/LogiTrack-Atom/projects/1).
- Integrar con pruebas pertinentes y documentación de los cambios.

El Compose general y los contratos se mantienen en [shipment-service](https://github.com/LogiTrack-Atom/shipment-service/tree/develop).
