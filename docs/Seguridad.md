# Seguridad del API Gateway

Issue: [api-gateway#3](https://github.com/LogiTrack-Atom/api-gateway/issues/3).

## Contrato JWT

Gateway utiliza Spring Security reactivo como Resource Server, con un decoder local
RS256 y una clave pública RSA X.509 de al menos 2048 bits. La clave se carga desde un
archivo externo file:; no se admiten URL remotas, claves privadas ni generación de
respaldo al iniciar. Si falta el archivo o el contrato, el arranque se rechaza.

El JWT conserva el contrato de Sprint02: sub UUID canónico, roles como lista no vacía
de ADMIN/CLIENTE/REPARTIDOR, issuer logitrack-security, audience logitrack-api y fechas
iat/exp obligatorias. Se verifica nbf cuando está presente. La vida admitida se limita
al TTL configurado, inicialmente 900 segundos, con tolerancia temporal máxima de 60
segundos. Scopes y cabeceras de identidad no otorgan roles de negocio.

La clave privada corresponde exclusivamente a security-service. Gateway no consulta
al emisor en cada petición ni usa discovery OAuth o introspección remota. Las claves
de pruebas se generan en memoria/directorios temporales y no forman parte de la imagen.

## Configuración para dev/prod

Estas propiedades deben agregarse a la configuración remota de api-gateway mediante
[configserver-ms#13](https://github.com/LogiTrack-Atom/configserver-ms/issues/13):

```yaml
logitrack:
  security:
    issuer: logitrack-security
    audience: logitrack-api
    access-token-ttl: 15m
    clock-skew: 60s
    public-key-location: ${JWT_PUBLIC_KEY_LOCATION}
```

El secreto de firma no se entrega a Config Server ni al Gateway. El perfil local
declara el mismo contrato de identidad y exige JWT_PUBLIC_KEY_LOCATION. Dev/prod
continúan requiriendo Config Server y un marcador de configuración de su perfil.

## Autorización pública

Los prefijos son /applications/apishipment/api/v1,
/applications/apicourier/api/v1 y /applications/apisecurity/api/v1. Las rutas se
definen por separado en la issue #2; este cambio no publica rutas por descubrimiento.

| Operación | Permiso |
| --- | --- |
| GET /actuator/health del Gateway | Público, sin detalles |
| POST auth/login y auth/register | Público; security conserva registro exclusivo CLIENTE |
| GET auth/me | ADMIN, CLIENTE o REPARTIDOR |
| POST usuarios y GET usuarios/{id} | ADMIN |
| GET envios, envios/{id}, seguimiento/{codigo} e historial | ADMIN, CLIENTE o REPARTIDOR |
| POST envios | ADMIN o CLIENTE |
| PATCH envios/{id}/estado | ADMIN o REPARTIDOR |
| PATCH cancelación y PUT asignación | ADMIN |
| PUT asociaciones de clientes/repartidores | ADMIN |
| Listado y administración de repartidores e identidades | ADMIN |
| Consulta individual y disponibilidad de repartidor | ADMIN o REPARTIDOR |
| GET admin/configuracion de courier | ADMIN |

Los servicios de destino conservan los controles de pertenencia y estado del dominio.
El permiso de lectura de un rol no permite leer los recursos ajenos. Cualquier método
o ruta no declarado se deniega, incluso para ADMIN. Los diagnósticos de Actuator de
los servicios internos no se abren por compartir un prefijo público.

## Bearer, filtros y errores

Se acepta un único encabezado Authorization. Tokens de query strings, cookies y
autenticación Basic no autorizan operaciones protegidas. Un token Bearer inválido
también se rechaza si se adjunta a un endpoint público. No se crean sesiones, formularios
ni caché de solicitudes para login. CSRF se desactiva para la API stateless Bearer.

El filtro global restaura Authorization únicamente desde JwtAuthenticationToken ya
validado. Retira X-User-Id, X-User-Roles, X-Authenticated-User y X-Authenticated-Roles
enviados por el cliente. Una petición pública sin JWT no reenvía otra credencial de
Authorization. El filtro no vuelve a decodificar JWT ni genera identidad por cabeceras.

Los rechazos locales usan ApiError: timestamp, status, error, mensaje y detalles.
Credencial ausente/inválida produce 401 con WWW-Authenticate: Bearer; permisos
insuficientes producen 403. Los mensajes son constantes, no reflejan tokens o
excepciones y usan Cache-Control: no-store. Una denegación no alcanza al destino.

## Validación y límites

Ejecutar ./mvnw -B -ntp verify javadoc:javadoc con Java 25. La suite incluye validación
criptográfica con reloj fijo, límites de configuración, carga de claves y pruebas HTTP
reactivas con JWT firmados y un destino local controlado. La matriz verifica métodos,
rutas y los tres roles, evitando que el éxito de una petición o una cookie cree una
autenticación para otra.

El fixture de rutas permite observar los filtros y las denegaciones; no sustituye
Eureka ni acredita los servicios de negocio completos. El consumo de propiedades Git,
el ruteo lb://, los tokens emitidos por security-service desplegado y la pertenencia
se vuelven a comprobar en la integración de Sprint03. CORS es deseable y no se habilitan
orígenes en esta issue; su configuración centralizada queda para la integración acordada.

Referencias: [Resource Server reactivo](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html),
[autorización reactiva](https://docs.spring.io/spring-security/reference/reactive/authorization/authorize-http-requests.html)
y [filtros globales de Gateway](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/global-filters.html).
