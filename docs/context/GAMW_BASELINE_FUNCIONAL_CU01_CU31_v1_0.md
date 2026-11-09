# GAMW — Especificación funcional consolidada de los 31 casos de uso

**Proyecto:** Plataforma Web y Móvil para la Supervisión, Fiscalización y Trazabilidad del Servicio de Recolección de Residuos Urbanos mediante Georreferenciación y Monitoreo GPS para el Gobierno Autónomo Municipal de Warnes.

- **Versión:** 1.0 — línea base funcional de desarrollo aprobada por el Product Owner el 9 de noviembre de 2026.
- **Cobertura:** CU-01 a CU-31 (ocho paquetes funcionales).
- **Estado de aprobación:** el PO autoriza utilizar estas especificaciones como base de trabajo; **no equivale a validación institucional de todos los procedimientos por el GAMW**.
- **Fuentes primarias:** especificación CU-01 en conversación; `GAMW_Especificacion_Funcional_CU02_CU31_Baseline_1_0.docx`; `Contexto Maestro reestructuracion.txt`; `docs/context/02_ACTORS_USE_CASES_BASELINE.md`, `03_END_TO_END_OPERATIONAL_FLOW.md`, `05_DOMAIN_MODEL_BUSINESS_RULES.md`, `11_RISKS_OPEN_DECISIONS_VALIDATION.md`.

## Reglas de uso para Codex y la documentación

1. **Este es el catálogo funcional de 31 CU que se debe preservar.** Los 37 CU antiguos sirven solo como referencias históricas por equivalencia semántica, no por número.
2. **No reestructurar RF-01–RF-25 ahora.** Su desactualización está reconocida; la presente aprobación se limita a especificar CU y orientar incrementos de desarrollo.
3. **Distinguir origen y estado:** CONFIRMADO (entrevista/decisión PO), PRELIMINAR (baseline aún por validar), PROPUESTA (solución/CA de diseño), PENDIENTE (decisión sin aprobar). Las `RN-CU` y `CA-CU` no representan por sí mismas obligaciones contractuales.
4. **No convertir este archivo en permiso para programar indiscriminadamente.** Antes de cada CU, revisar sus *Decisiones pendientes y límites de definición*, dependencias, código y pruebas. Si depende de potestades institucionales sin validar, implementar solo incrementos seguros, justificados y verificables, manteniendo `denyAll` donde corresponda.
5. **No confundir estados:** especificado, implementado, probado automáticamente, probado manualmente y aceptado institucionalmente son hitos distintos. Los estados técnicos descritos reproducen reportes y deben comprobarse con repositorio y pruebas actuales.
6. **Preservar historia:** rutas/versiones, relaciones empresa-persona/vehículo, jornadas reprogramadas, posiciones GPS e inspecciones no se sustituyen destructivamente.
7. **GPS es evidencia, no cumplimiento:** el fiscalizador municipal decide el resultado, sin sanciones automáticas ni supuestas garantías de que el dispositivo viajó en el camión.
8. **A6 es actor externo**, limitado a formulario público, no usuario interno autenticado.
9. **Arquitectura por capacidades:** paquete funcional → subsistema → CU → componentes → pruebas; reutilizar módulos existentes sin refactorizaciones ceremoniales.
10. **No alterar repositorio mediante este documento.** Es un artefacto de contexto listo para incorporar tras revisión; no representa edición directa del árbol de trabajo ni autorización de commit/push.

## Índice de los casos de uso

| CU | Caso de uso | Paquete | Prioridad |
|---|---|---|---|
| CU-01 | Autenticar usuario | Administración institucional | Media |
| CU-02 | Gestionar usuarios | Administración institucional | Media |
| CU-03 | Gestionar roles y permisos | Administración institucional | Media |
| CU-04 | Gestionar empresas operadoras | Administración institucional | Media |
| CU-05 | Gestionar periodos de servicio | Administración institucional | Media |
| CU-06 | Gestionar vehículos | Administración institucional | Media |
| CU-07 | Gestionar responsables operativos | Administración institucional | Media |
| CU-08 | Gestionar estructura territorial del servicio | Planificación territorial y programación | Media |
| CU-09 | Gestionar rutas operativas de referencia | Planificación territorial y programación | Media |
| CU-10 | Gestionar puntos de control y geocercas | Planificación territorial y programación | Media |
| CU-11 | Programar jornada | Planificación territorial y programación | Alta |
| CU-12 | Asignar recursos a jornada | Planificación territorial y programación | Alta |
| CU-13 | Ejecutar jornada asignada | Ejecución operativa | Alta |
| CU-14 | Registrar incidencia operativa | Ejecución operativa | Media |
| CU-15 | Monitorear posición de jornada | Monitoreo GPS y trazabilidad | Alta |
| CU-16 | Consultar trayectoria GPS de jornada | Monitoreo GPS y trazabilidad | Alta |
| CU-17 | Revisar continuidad e integridad del seguimiento GPS | Monitoreo GPS y trazabilidad | Media |
| CU-18 | Revisar coincidencias con puntos de control y geocercas | Monitoreo GPS y trazabilidad | Media |
| CU-19 | Revisar desvíos y detenciones prolongadas | Monitoreo GPS y trazabilidad | Media |
| CU-20 | Registrar inspección y evidencias de campo | Fiscalización | Media |
| CU-21 | Fiscalizar jornada y registrar resultado | Fiscalización | Alta |
| CU-22 | Gestionar acciones correctivas | Incidencias y acciones correctivas | Media |
| CU-23 | Verificar atención de acciones correctivas | Incidencias y acciones correctivas | Media |
| CU-24 | Reprogramar jornada conservando el antecedente | Incidencias y acciones correctivas | Media |
| CU-25 | Presentar reporte ciudadano | Reclamos y puntos críticos | Media |
| CU-26 | Registrar reclamo recibido por canal institucional | Reclamos y puntos críticos | Media |
| CU-27 | Validar y gestionar reclamos | Reclamos y puntos críticos | Media |
| CU-28 | Registrar punto crítico o microbasural identificado | Reclamos y puntos críticos | Baja |
| CU-29 | Consultar historial institucional del servicio | Historial, reportes e indicadores | Media |
| CU-30 | Generar reportes e indicadores | Historial, reportes e indicadores | Media |
| CU-31 | Consultar bitácora de acciones relevantes | Historial, reportes e indicadores | Media |

---

## Alcance de esta aprobación

La aprobación del PO consolida **la definición funcional como baseline de trabajo**, incluyendo flujos y criterios propuestos. Los elementos explícitamente `PENDIENTE` siguen pendientes: matriz D1 de permisos, parámetros GPS, clasificación de incidentes, resultados oficiales de fiscalización, gestión de acciones correctivas, datos públicos/fotografías, puntos críticos y métricas institucionales. Toda decisión posterior debe reflejar impacto sobre CU, reglas, componentes y pruebas, sin fabricar validación municipal.

---


## CU-01. Autenticar usuario

| Campo | Descripción |
|---|---|
| **ID** | CU-01 |
| **Nombre** | Autenticar usuario |
| **Paquete** | Administración institucional |
| **Prioridad** | Media |
| **Actores** | Administrador municipal (A1); Responsable municipal / autoridad (A2); Fiscalizador municipal / Ornato Público (A3); Supervisor de empresa operadora (A4); Conductor / responsable móvil (A5). |
| **Actor iniciador** | Cualquiera de los actores autorizados que necesite ingresar a la plataforma. |

### Propósito

Permitir que los usuarios registrados y habilitados acrediten su identidad mediante credenciales de acceso, estableciendo una sesión autenticada para interactuar con las funcionalidades de la plataforma conforme a las autorizaciones que les correspondan.

### Descripción

Este caso de uso permite verificar la identidad de los usuarios que acceden a la plataforma web o móvil del GAMW. El usuario proporciona sus credenciales, que son validadas por el sistema utilizando la información de su cuenta registrada.

Cuando la autenticación es satisfactoria, la plataforma establece una sesión vinculada con la identidad del usuario y habilita el acceso a las funcionalidades para las cuales exista autorización. La autenticación no concede por sí misma permisos administrativos, operativos o de fiscalización.

El caso de uso contempla el rechazo de credenciales incorrectas, cuentas deshabilitadas, sesiones inválidas y situaciones que impidan completar la verificación de identidad.

### Precondiciones

1. El usuario debe contar con una cuenta registrada y habilitada en la plataforma.
2. El usuario debe disponer de credenciales de acceso.
3. La plataforma debe tener disponible el servicio de autenticación y la información necesaria para validar la identidad.
4. Debe existir conectividad con el servicio de autenticación para efectuar un nuevo inicio de sesión.

### Flujo principal

1. El usuario accede a la plataforma web o aplicación móvil.
2. La plataforma presenta el formulario de inicio de sesión.
3. El usuario introduce su nombre de usuario y contraseña.
4. El usuario solicita iniciar sesión.
5. La plataforma comprueba que los datos requeridos estén presentes.
6. La plataforma envía las credenciales al servicio de autenticación mediante una comunicación protegida.
7. El sistema verifica que la cuenta exista, se encuentre habilitada y que la contraseña corresponda con las credenciales registradas.
8. El sistema establece una autenticación válida e identifica la cuenta del usuario.
9. La plataforma verifica la identidad asociada con la autenticación obtenida.
10. La plataforma establece el contexto de sesión del usuario.
11. La plataforma permite continuar hacia las funcionalidades disponibles, sujetas a las verificaciones de autorización correspondientes.

### Flujos alternativos

- **FA-01. El usuario corrige los datos antes de autenticarse.** Modifica el nombre de usuario o contraseña, la plataforma permite la corrección y se reintenta el flujo de validación.
- **FA-02. El usuario dispone de una sesión válida.** El cliente puede identificar una sesión anteriormente establecida, comprobar su vigencia y recuperar o confirmar identidad según el mecanismo admitido; de lo contrario, se trata como inválida.
- **FA-03. El usuario cierra su sesión.** La plataforma elimina el contexto autenticado del cliente, deja de presentar información protegida correspondiente a esa sesión y retorna al estado no autenticado.

### Excepciones

- **E1. Datos obligatorios incompletos.** Se solicita completar nombre de usuario y contraseña sin cursar una autenticación válida.
- **E2. Credenciales incorrectas.** Se rechaza el acceso sin divulgar información sensible.
- **E3. Cuenta deshabilitada.** El sistema impide iniciar sesión.
- **E4. Autenticación o sesión inválida.** Una credencial inválida, expirada o no aceptada no permite continuar operaciones autenticadas.
- **E5. Error de comunicación.** Se informa que no se completó la autenticación y no se considera autenticado al usuario.
- **E6. Identidad no verificable.** Si no puede confirmarse una identidad consistente, no se establece nueva sesión.
- **E7. Respuesta tardía.** Una respuesta anterior no puede restablecer una sesión que ya fue descartada o sustituida.

### Postcondiciones

- **Éxito:** sesión asociada a la identidad válida; se habilitan únicamente operaciones autorizadas; las credenciales originales no quedan almacenadas como texto legible en el cliente.
- **Fallo o rechazo:** no se establece una nueva sesión, no se habilita acceso protegido y se informa de manera controlada sin exponer datos sensibles.

### Reglas funcionales y de negocio relacionadas

- **RN-CU-01-01.** La autenticación utiliza una cuenta registrada y habilitada.
- **RN-CU-01-02.** Identidad de acceso y PersonaNatural son conceptos distintos.
- **RN-CU-01-03.** FuncionOperativa no implica RolSistema ni permisos automáticamente.
- **RN-CU-01-04.** Una autenticación válida no habilita el acceso indiscriminado a módulos institucionales.
- **RN-CU-01-05.** Contraseñas y secretos no se exponen en respuestas, registros técnicos ni almacenamiento inseguro.
- **RN-CU-01-06.** Las operaciones protegidas comprueban autenticación y autorización aplicables.
- **RN-CU-01-07.** Una sesión expirada o inválida no permite nuevas operaciones autenticadas.

### Criterios de aceptación propuestos

- **CA-CU-01-01.** Una cuenta registrada, activa y con credenciales válidas puede autenticarse.
- **CA-CU-01-02.** Contraseña incorrecta impide iniciar sesión.
- **CA-CU-01-03.** Cuenta deshabilitada no puede autenticarse.
- **CA-CU-01-04.** Los campos obligatorios se validan antes de cursar la solicitud.
- **CA-CU-01-05.** La sesión identifica correctamente la cuenta autenticada.
- **CA-CU-01-06.** Credenciales inválidas/expiradas impiden operaciones protegidas.
- **CA-CU-01-07.** El cierre elimina el contexto autenticado del cliente.
- **CA-CU-01-08.** Estar autenticado no concede privilegios no autorizados.
- **CA-CU-01-09.** Errores no exponen contraseñas, hashes ni secretos.
- **CA-CU-01-10.** Un fallo de comunicación no produce autenticación aparentemente exitosa.
- **CA-CU-01-11.** Respuesta tardía no restaura una sesión descartada.

### Dependencias funcionales

- **CU-02:** administración y habilitación/deshabilitación de cuentas.
- **CU-03:** administración institucional de roles y permisos. La autenticación de cuentas ya provisionadas no exige que CU-03 esté terminado.

### Decisiones pendientes y límites de definición

- Matriz institucional definitiva de roles/permisos (**D1 / PENDING-12**) y procedimiento de provisión de cuentas.
- La recuperación de contraseñas, MFA y políticas de sesión institucionales no se presumen incluidas.

### Estado de definición y evidencia

Definición funcional aprobada como baseline de trabajo por el PO. Autenticación técnica reportada como implementada y verificada automáticamente en web y móvil; validación manual e institucional siguen separadas. La sesión web actual es en memoria, sin persistencia entre recargas. El cierre local no revoca globalmente un JWT stateless ya emitido.

> **Trazabilidad de reestructuración:** CU-01 anterior y actual; separación de autenticación y autorización.

---

## CU-02. Gestionar usuarios

| ID | CU-02 |
| --- | --- |
| Nombre | Gestionar usuarios |
| Paquete | Administración institucional |
| Prioridad | Media |
| Actores | Administrador municipal (A1) |
| Actor iniciador | Administrador municipal (A1) |


### Propósito

Administrar las cuentas que permiten al personal autorizado acceder a la plataforma sin confundir identidad de acceso con identidad de persona natural.

### Descripción

El Administrador municipal consulta y mantiene cuentas asociadas a personas naturales registradas. La gestión abarca provisión, consulta, actualización de datos permitidos y habilitación o deshabilitación, conservando referencias históricas. No incluye gestión indiscriminada de funciones operativas ni permisos: estos corresponden a CU-07 y CU-03.

### Precondiciones
1. El Administrador dispone de una sesión autenticada y de autorización para gestionar cuentas.
2. Existe un registro de PersonaNatural para vincular la cuenta cuando corresponda.
3. Se encuentran definidos los datos mínimos de la cuenta y el procedimiento de provisión autorizado.

### Flujo principal
1. El Administrador ingresa a la gestión de usuarios.
2. La plataforma verifica su autorización y presenta las cuentas visibles dentro del alcance permitido.
3. El Administrador selecciona registrar, consultar, actualizar, habilitar o deshabilitar una cuenta.
4. La plataforma muestra los datos de la persona vinculada y la información de acceso pertinente, sin presentar contraseñas ni hashes.
5. El Administrador proporciona o modifica únicamente los campos habilitados para la operación.
6. La plataforma valida identidad, unicidad del nombre de usuario, integridad de referencias y restricciones de seguridad.
7. El Administrador confirma la acción solicitada.
8. La plataforma guarda el resultado y conserva la relación histórica con los registros previos.
9. La plataforma comunica el resultado sin revelar credenciales sensibles.

### Flujos alternativos
- FA-01 — Registro de cuenta. Si no existe una cuenta para la persona seleccionada, el Administrador registra el identificador de acceso y utiliza el procedimiento de provisión de credenciales aprobado; no se presupone generación ni envío automático de contraseñas.
- FA-02 — Deshabilitación. Cuando se requiere impedir futuros accesos, se cambia la disponibilidad de la cuenta sin eliminar a PersonaNatural ni su historial de actuaciones.
- FA-03 — Consulta. Si solo se consulta una cuenta, la plataforma muestra la información autorizada sin alterar datos.

### Excepciones
- E1. Identidad inexistente — No se crea una cuenta referida a una persona natural inexistente o no válida.
- E2. Nombre de usuario repetido — La plataforma rechaza el registro o la actualización y conserva la información previa.
- E3. Operación no autorizada — Se deniega el acceso, incluido el intento de administrar cuentas fuera del ámbito autorizado.
- E4. Restricción de seguridad — Se rechaza una operación que deje el sistema sin administración autorizada, únicamente si esta salvaguarda forma parte de la política aprobada.
- E5. Error transaccional — No se registra una modificación parcial ni se pierde el estado anterior.

### Postcondiciones
- Éxito. La cuenta queda creada, actualizada o deshabilitada según la operación permitida; conserva su relación con PersonaNatural y los antecedentes previos.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-02-01  Usuario y PersonaNatural son conceptos distintos; no se crea una persona por cada rol.
- RN-CU-02-02  La identidad de acceso se obtiene de datos persistidos, no de identificadores arbitrarios enviados por el cliente.
- RN-CU-02-03  Deshabilitar una cuenta no elimina sus eventos de ejecución, fiscalización o auditoría.
- RN-CU-02-04  No se presume que una persona deba tener una única cuenta, salvo decisión institucional expresa.
- RN-CU-02-05  La gestión de cuentas no otorga automáticamente roles ni autorizaciones funcionales.

### Criterios de aceptación propuestos
- CA-CU-02-01  Se registra una cuenta válida vinculada a una persona natural existente.
- CA-CU-02-02  No es posible reutilizar un nombre de usuario que ya está ocupado conforme a la restricción de unicidad.
- CA-CU-02-03  Una cuenta deshabilitada no puede iniciar nuevas sesiones autenticadas.
- CA-CU-02-04  La deshabilitación conserva la identidad y los registros históricos asociados.
- CA-CU-02-05  Un administrador no autorizado no accede a la operación.
- CA-CU-02-06  No se exhiben ni recuperan contraseñas en texto plano ni hashes desde la interfaz.
- CA-CU-02-07  Si falla la persistencia, la operación no deja cambios parciales.
- CA-CU-02-08  La provisión de credenciales se ejecuta únicamente según el procedimiento formalmente seleccionado.

### Dependencias funcionales
- CU-01: sesión autenticada y verificación de identidad.
- CU-03: asignación y administración de roles y permisos.
- CU-07: PersonaNatural, vínculos y funciones operativas.

### Decisiones pendientes y límites de definición
- D1/PENDING-12: identificación y atribuciones del administrador; límites de gestión y separación de funciones.
- Procedimiento inicial de creación de cuentas y provisión/entrega de credenciales.
- Política de reactivación, modificación de datos sensibles y protección frente a deshabilitación del último administrador.

### Estado de definición y evidencia

Definición funcional condicionada por política institucional; implementación integral no acreditada.

> Trazabilidad de reestructuración: CU-02. El modelo de Usuario ya existe para autenticación; no confundirlo con una gestión administrativa de cuentas terminada.

## CU-03. Gestionar roles y permisos

| ID | CU-03 |
| --- | --- |
| Nombre | Gestionar roles y permisos |
| Paquete | Administración institucional |
| Prioridad | Media |
| Actores | Administrador municipal (A1) |
| Actor iniciador | Administrador municipal (A1) |


### Propósito

Controlar qué operaciones y ámbitos de información pueden utilizar las cuentas institucionales y empresariales autorizadas.

### Descripción

El Administrador municipal consulta los roles de seguridad, sus autorizaciones y asignaciones a cuentas. La política debe distinguir permisos funcionales de alcance sobre datos (institución, empresa, periodo, jornada), así como funciones operativas de acceso informático. La configuración final necesita una decisión institucional aprobada.

### Precondiciones
1. El Administrador está autenticado y cuenta con autorización específica para administrar permisos.
2. Existe una política institucional de roles, autorizaciones y límites de delegación aprobada al menos para el incremento que se va a habilitar.
3. Las cuentas a las que se asignarán roles se encuentran registradas.

### Flujo principal
1. El Administrador solicita gestionar roles y permisos.
2. La plataforma verifica que tiene facultades para realizar esa administración.
3. La plataforma muestra los roles y autorizaciones configurados, diferenciando sus ámbitos de datos.
4. El Administrador selecciona crear, consultar o ajustar un rol, o asignar o retirar un rol a una cuenta.
5. La plataforma presenta las operaciones permitidas y los límites de asignación derivados de la política institucional.
6. El Administrador introduce los cambios propuestos.
7. La plataforma valida la consistencia de autorizaciones, cuentas y ámbito de aplicación.
8. El Administrador confirma la operación.
9. La plataforma persiste los cambios y registra la actuación relevante para auditoría.
10. Las solicitudes posteriores se autorizan en el backend aplicando la política vigente.

### Flujos alternativos
- FA-01 — Cambio de asignación. Se asigna o retira un rol a una cuenta, respetando restricciones de delegación y conservando el antecedente auditable.
- FA-02 — Solo consulta. El Administrador visualiza permisos y alcance actual sin modificar la configuración.
- FA-03 — Actualización de política. La operación modifica la configuración autorizada; no convierte automáticamente funciones como CONDUCTOR en roles de seguridad.

### Excepciones
- E1. Rol o permiso inexistente — Se rechaza la operación y no se crea una autorización implícita.
- E2. Asignación fuera de facultades — Se deniega una elevación de privilegios que exceda la administración autorizada.
- E3. Cuenta no válida — No se asignan nuevos privilegios a cuentas inexistentes o no elegibles según la política.
- E4. Configuración inconsistente — Se rechaza la transacción sin habilitar accesos parciales.
- E5. Falta de política aprobada — La plataforma conserva la denegación por defecto; no infiere permisos de un JWT válido.

### Postcondiciones
- Éxito. Los roles, permisos o asignaciones aprobados quedan registrados y son aplicables en las decisiones posteriores de autorización, con auditoría de la modificación.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-03-01  Los roles de seguridad no equivalen a FunciónOperativa ni a VinculoPersonaEmpresa.
- RN-CU-03-02  Autenticación válida no implica autorización general.
- RN-CU-03-03  Las decisiones de acceso se verifican en backend, no únicamente ocultando botones.
- RN-CU-03-04  El supervisor de empresa no accede a registros de empresas o periodos no autorizados.
- RN-CU-03-05  La capacidad de modificar permisos exige atribuciones específicas y verificables.
- RN-CU-03-06  Las decisiones de asignación y retiro deben poder auditarse sin destruir antecedentes.

### Criterios de aceptación propuestos
- CA-CU-03-01  Una cuenta sin privilegio requerido recibe una denegación efectiva al invocar una API protegida.
- CA-CU-03-02  Una cuenta autorizada puede utilizar solo las operaciones definidas para su rol y ámbito.
- CA-CU-03-03  No se obtienen permisos por poseer únicamente una FunciónOperativa.
- CA-CU-03-04  Un supervisor no puede consultar datos de otra empresa a través de ID manipulados.
- CA-CU-03-05  Una asignación administrativa queda registrada y es trazable.
- CA-CU-03-06  Un cambio inválido no crea privilegios parciales.
- CA-CU-03-07  No se habilitan de forma general las APIs administrativas al validar JWT.
- CA-CU-03-08  Una política incompleta mantiene cerradas las operaciones no definidas.

### Dependencias funcionales
- CU-01: autenticación.
- CU-02: cuentas a las que se asignan roles.
- CU-06, CU-07, CU-09, CU-15, CU-16, CU-24 y CU-29: requieren decisiones de autorización de datos.

### Decisiones pendientes y límites de definición
- D1/PENDING-12: catálogo inicial de roles y matriz exacta de permisos.
- Quién provisiona el primer Administrador y quién puede modificar permisos de otro administrador.
- Alcances de empresa/periodo, acceso municipal histórico y vigencia de autorizaciones.
- Reglas de revocación y efecto de cambios sobre sesiones ya emitidas.

### Estado de definición y evidencia

Definición parcialmente fundamentada; requiere aprobación de política para implementar comportamiento institucional.

> Trazabilidad de reestructuración: CU-03.

## CU-04. Gestionar empresas operadoras

| ID | CU-04 |
| --- | --- |
| Nombre | Gestionar empresas operadoras |
| Paquete | Administración institucional |
| Prioridad | Media |
| Actores | Administrador municipal (A1) |
| Actor iniciador | Administrador municipal (A1) |


### Propósito

Conservar la identidad institucional de cada empresa que participa en el servicio, independientemente de los periodos en que opere.

### Descripción

Permite registrar, consultar y mantener los datos básicos de una empresa operadora, conservando las relaciones históricas con periodos, vehículos, personal y jornadas. No equivale a gestionar contratación pública ni pagos.

### Precondiciones
1. El Administrador tiene sesión y autorización administrativa.
2. La información básica de la empresa se encuentra disponible para registrar o consultar.

### Flujo principal
1. El Administrador accede al registro de empresas operadoras.
2. La plataforma muestra el listado y permite consultar antecedentes de cada empresa.
3. El Administrador selecciona registrar o actualizar los datos permitidos.
4. La plataforma presenta razón social, nombre comercial y NIT cuando se disponga de él.
5. El Administrador completa o modifica la información.
6. La plataforma valida campos obligatorios, formatos y unicidad de NIT cuando exista.
7. El Administrador confirma la operación.
8. La plataforma registra los datos o la actualización sin alterar relaciones históricas.
9. La plataforma presenta el resultado y mantiene disponible el registro para periodos posteriores.

### Flujos alternativos
- FA-01 — Empresa sin NIT registrado. Si el NIT es opcional según la línea base técnica, el Administrador puede registrar la empresa con razón social; la obligatoriedad institucional sigue pendiente.
- FA-02 — Consulta histórica. El Administrador visualiza los distintos periodos y relaciones existentes sin modificar su identidad.
- FA-03 — Desactivación lógica. Se impide uso futuro cuando esté autorizado, sin borrar periodos ni evidencias anteriores.

### Excepciones
- E1. Razón social faltante — La plataforma solicita información requerida.
- E2. NIT duplicado — Si se proporciona un NIT ya registrado, la operación se rechaza.
- E3. Referencias históricas — No se permite una eliminación o sustitución que rompa periodos o jornadas anteriores.
- E4. Acceso no autorizado — Se deniega la acción administrativa.
- E5. Error de guardado — La plataforma conserva el estado anterior.

### Postcondiciones
- Éxito. La empresa queda registrada o actualizada como identidad estable, manteniendo intactos sus periodos y antecedentes.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-04-01  La empresa es una identidad estable; su periodo de prestación se registra por separado.
- RN-CU-04-02  Los datos no desaparecen cuando cambie el operador del servicio.
- RN-CU-04-03  El NIT se almacena como texto y es único si está informado, según la línea base.
- RN-CU-04-04  No se agregan contratos, cuentas bancarias o procesos de licitación a este CU.
- RN-CU-04-05  Las relaciones históricas no se eliminan por desactivación de la empresa.

### Criterios de aceptación propuestos
- CA-CU-04-01  Se registra una empresa con razón social válida.
- CA-CU-04-02  Dos empresas no pueden compartir el mismo NIT informado.
- CA-CU-04-03  Una empresa puede tener varios periodos históricos sin duplicar su identidad.
- CA-CU-04-04  Una desactivación autorizada preserva los periodos, vínculos y jornadas anteriores.
- CA-CU-04-05  Una modificación de datos no reescribe referencias históricas.
- CA-CU-04-06  No se permite eliminar físicamente la empresa cuando ello afecte antecedentes.
- CA-CU-04-07  Un error de persistencia conserva los datos anteriores.

### Dependencias funcionales
- CU-05: periodos de servicio asociados.
- CU-06 y CU-07: relaciones históricas de vehículos y personas.
- CU-11: programación vinculada con un periodo.

### Decisiones pendientes y límites de definición
- D1: qué actores municipales pueden administrar empresas en producción.
- Confirmación institucional sobre obligatoriedad y formato del NIT y atributos adicionales si realmente son necesarios.

### Estado de definición y evidencia

Implementación técnica previa con pruebas reportadas; aceptación y autorización institucional pendientes.

> Trazabilidad de reestructuración: CU-04.

## CU-05. Gestionar periodos de servicio

| ID | CU-05 |
| --- | --- |
| Nombre | Gestionar periodos de servicio |
| Paquete | Administración institucional |
| Prioridad | Media |
| Actores | Administrador municipal (A1) |
| Actor iniciador | Administrador municipal (A1) |


### Propósito

Registrar los intervalos durante los cuales cada empresa operadora presta el servicio y conservar la continuidad entre operadores sucesivos.

### Descripción

Permite establecer periodos con fechas de inicio y fin, asociados a la identidad de la empresa. Este contexto permite vincular jornadas y consultar la prestación histórica incluso si cambia la empresa.

### Precondiciones
1. El Administrador está autenticado y autorizado.
2. La empresa operadora existe en el registro institucional.

### Flujo principal
1. El Administrador accede a los periodos de servicio.
2. La plataforma muestra los periodos existentes y sus empresas asociadas.
3. El Administrador selecciona registrar o consultar un periodo, o actualizar datos que no comprometan el historial.
4. La plataforma solicita empresa, fechas y demás datos mínimos habilitados.
5. El Administrador proporciona las fechas y selecciona una empresa registrada.
6. La plataforma valida que la fecha final no sea anterior a la inicial y que las referencias existan.
7. El Administrador confirma el registro.
8. La plataforma conserva el periodo y su relación histórica con la empresa.
9. El sistema presenta el resultado para uso posterior en jornadas.

### Flujos alternativos
- FA-01 — Consulta por empresa. El Administrador filtra periodos asociados a una empresa sin alterar sus datos.
- FA-02 — Corrección permitida. Solo se actualiza información cuya modificación no cambie retroactivamente la empresa de jornadas ya registradas.
- FA-03 — Periodos coexistentes. Si dos periodos se solapan, no se introduce una prohibición automática mientras la política institucional siga pendiente.

### Excepciones
- E1. Empresa inexistente — La plataforma impide registrar un periodo sin empresa válida.
- E2. Fechas inválidas — Se rechaza fin anterior al inicio.
- E3. Modificación histórica incompatible — Se rechaza el cambio que desnaturaliza referencias de jornadas previas.
- E4. Sin autorización — La operación se deniega.
- E5. Fallo de persistencia — El estado de periodos anteriores se conserva.

### Postcondiciones
- Éxito. El periodo queda asociado a su empresa con fechas válidas y permanece disponible para jornadas e historial.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-05-01  Cada periodo pertenece a una única identidad de empresa operadora.
- RN-CU-05-02  No se cambia la empresa de una jornada modificando retroactivamente su periodo.
- RN-CU-05-03  La fecha final es obligatoria en el modelo vigente y no puede ser anterior a la inicial.
- RN-CU-05-04  No se prohíbe solapamiento de periodos sin definición institucional.
- RN-CU-05-05  Los periodos históricos se conservan tras cambios de operador.

### Criterios de aceptación propuestos
- CA-CU-05-01  Se crea un periodo vinculado a una empresa registrada.
- CA-CU-05-02  Se rechaza un periodo con fechas inconsistentes.
- CA-CU-05-03  Las consultas recuperan periodos antiguos aunque la empresa haya dejado de operar.
- CA-CU-05-04  Las jornadas históricas mantienen su periodo original.
- CA-CU-05-05  No se aplican restricciones de solapamiento inventadas.
- CA-CU-05-06  Una modificación incompatible con historial es rechazada.
- CA-CU-05-07  Un fallo de guardado no modifica parcialmente el periodo.

### Dependencias funcionales
- CU-04: empresa operadora.
- CU-11: selección del periodo para programar.
- CU-24: preservación de la relación al reprogramar.
- CU-29: consulta histórica de periodos.

### Decisiones pendientes y límites de definición
- Política institucional de solapamiento, continuidad contractual y fechas exactas cuando corresponda.
- Autorización institucional de edición de periodos ya utilizados.

### Estado de definición y evidencia

Implementación técnica previa con pruebas reportadas; restricciones institucionales adicionales pendientes.

> Trazabilidad de reestructuración: CU-05.

## CU-06. Gestionar vehículos

| ID | CU-06 |
| --- | --- |
| Nombre | Gestionar vehículos |
| Paquete | Administración institucional |
| Prioridad | Media |
| Actores | Administrador municipal (A1), Supervisor de empresa operadora (A4) |
| Actor iniciador | Uno de los actores autorizados, según operación y ámbito |


### Propósito

Conservar la identidad y disponibilidad administrativa de vehículos que pueden participar en jornadas, incluidos sus vínculos históricos con las empresas.

### Descripción

Permite registrar y consultar vehículos sin asignarlos permanentemente a una empresa. El vínculo vehículo–empresa representa un periodo de relación, con lo que se evitan pérdidas históricas si el mismo vehículo opera para otra empresa.

### Precondiciones
1. El usuario está autenticado y posee autorización para el ámbito correspondiente.
2. Existe empresa operadora cuando la operación involucre un vínculo vehículo–empresa.

### Flujo principal
1. El usuario ingresa al registro de vehículos.
2. La plataforma presenta vehículos y vínculos de empresa visibles según su alcance autorizado.
3. El usuario selecciona registrar un vehículo, consultar su historial o gestionar un vínculo permitido.
4. La plataforma solicita los datos pertinentes, incluida placa y atributos disponibles.
5. El usuario completa la información y, en su caso, selecciona la empresa y las fechas del vínculo.
6. La plataforma valida unicidad de placa, referencias, coherencia temporal y límites de acceso.
7. El usuario confirma la operación.
8. La plataforma registra la identidad del vehículo separadamente del vínculo con la empresa.
9. La plataforma conserva los vínculos anteriores para futuras jornadas y auditoría.

### Flujos alternativos
- FA-01 — Nuevo vínculo. Un vehículo ya registrado se relaciona con una empresa mediante un nuevo vínculo sin modificar su identidad.
- FA-02 — Cierre de vínculo. Se cierra el periodo de relación autorizada conservando asignaciones históricas.
- FA-03 — Datos opcionales. Año y capacidades se completan solo si la información existe; no se presume capacidad única ambigua.

### Excepciones
- E1. Placa repetida — La plataforma rechaza duplicar la identidad del vehículo.
- E2. Empresa o catálogo inexistente — Se rechazan referencias inválidas sin crear catálogos oficiales ficticios.
- E3. Ámbito de otra empresa — Un supervisor no puede alterar vínculos fuera de su alcance.
- E4. Historial comprometido — Se impide borrar o sobrescribir vínculos utilizados por jornadas anteriores.
- E5. Error transaccional — Se mantiene íntegra la relación previa.

### Postcondiciones
- Éxito. El vehículo y, cuando corresponda, su vínculo temporal con la empresa quedan registrados sin alterar las relaciones anteriores.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-06-01  Vehiculo es independiente de EmpresaOperadora.
- RN-CU-06-02  La relación temporal se representa por VinculoVehiculoEmpresa.
- RN-CU-06-03  La placa identifica al vehículo de acuerdo con la unicidad actual.
- RN-CU-06-04  Masa y volumen son unidades distintas; no se crean atributos de mantenimiento, combustible ni seguros.
- RN-CU-06-05  Las asignaciones antiguas conservan el vínculo que estaba vigente en el momento correspondiente.

### Criterios de aceptación propuestos
- CA-CU-06-01  Se registra un vehículo con placa única.
- CA-CU-06-02  Se consulta el vehículo sin obligarlo a permanecer asociado a una empresa.
- CA-CU-06-03  Se crea un vínculo histórico válido con una empresa existente.
- CA-CU-06-04  El cierre del vínculo no modifica asignaciones pasadas.
- CA-CU-06-05  Un supervisor no puede intervenir datos de otra empresa.
- CA-CU-06-06  No se aceptan referencias inexistentes.
- CA-CU-06-07  El sistema conserva el historial cuando el vehículo vuelve a una empresa anterior.

### Dependencias funcionales
- CU-04: empresa operadora.
- CU-12: vehículo elegible para asignación.
- CU-24: conservar vínculo de vehículo en jornada antecedente.

### Decisiones pendientes y límites de definición
- D1: alcance exacto de A1 y A4 para registrar y modificar vehículos.
- Catálogo real de tipos y disponibilidad de datos de marca, modelo y capacidades.

### Estado de definición y evidencia

Implementación técnica previa con pruebas reportadas; autorización institucional no resuelta.

> Trazabilidad de reestructuración: CU-06.

## CU-07. Gestionar responsables operativos

| ID | CU-07 |
| --- | --- |
| Nombre | Gestionar responsables operativos |
| Paquete | Administración institucional |
| Prioridad | Media |
| Actores | Administrador municipal (A1), Supervisor de empresa operadora (A4) |
| Actor iniciador | Uno de los actores autorizados, según operación y ámbito |


### Propósito

Identificar a las personas que participan operativamente en el servicio, preservar sus vínculos con empresas y las funciones que ejercieron.

### Descripción

El registro parte de PersonaNatural, VinculoPersonaEmpresa y FuncionOperativa; una persona puede existir sin cuenta y puede mantener vínculos sucesivos. Este CU no crea una entidad duplicada ResponsableOperativo ni concede roles informáticos al asignar funciones laborales.

### Precondiciones
1. El usuario está autenticado y autorizado para el ámbito administrativo correspondiente.
2. Existe empresa operadora para los vínculos que se registran.
3. El catálogo de funciones vigente incluye al menos las funciones operativas sustentadas.

### Flujo principal
1. El usuario accede a la gestión de responsables operativos.
2. La plataforma presenta personas y vínculos accesibles según el ámbito autorizado.
3. El usuario selecciona registrar persona, consultar antecedentes o crear/cerrar un vínculo con empresa.
4. La plataforma solicita los datos de identidad y la información temporal de la relación.
5. El usuario selecciona las funciones operativas disponibles para el vínculo nuevo.
6. La plataforma valida unicidad de CI, fechas, empresa y al menos una función activa no duplicada.
7. El usuario confirma el registro o cierre.
8. La plataforma conserva la identidad de la persona y los segmentos históricos de sus vínculos y funciones.
9. La plataforma informa el resultado y deja disponible el vínculo para asignaciones válidas.

### Flujos alternativos
- FA-01 — Cambio de funciones. Se cierra el vínculo anterior y se registra otro segmento con las nuevas funciones; no se reemplazan funciones en el segmento histórico.
- FA-02 — Persona existente. Se reutiliza PersonaNatural y se crea un nuevo vínculo con la empresa pertinente.
- FA-03 — Sin cuenta de acceso. La persona puede quedar registrada para fines operativos aunque no exista todavía Usuario.

### Excepciones
- E1. CI duplicado — Se reutiliza la identidad existente o se rechaza la creación duplicada.
- E2. Función inexistente o inactiva — No se registra un vínculo con funciones inválidas.
- E3. Funciones vacías o duplicadas — La operación se rechaza conforme a la regla del segmento histórico.
- E4. Vínculo de empresa no autorizado — El supervisor no puede administrar personas de otra empresa.
- E5. Cambio retroactivo — No se sustituyen funciones de una relación ya registrada.
- E6. Error al guardar — La transacción preserva los registros anteriores.

### Postcondiciones
- Éxito. La identidad de PersonaNatural y sus vínculos/funciones quedan preservados, y los segmentos válidos son elegibles para asignaciones.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-07-01  PersonaNatural no equivale a Usuario.
- RN-CU-07-02  FuncionOperativa no equivale a RolSistema.
- RN-CU-07-03  La persona no contiene un empresa_id permanente; su relación es histórica.
- RN-CU-07-04  Las funciones de un vínculo histórico no se reemplazan destructivamente.
- RN-CU-07-05  CI es texto y se conserva como identificador único.
- RN-CU-07-06  No se obliga a que todo responsable posea cuenta móvil.

### Criterios de aceptación propuestos
- CA-CU-07-01  Se registra una persona sin necesidad de crear un Usuario.
- CA-CU-07-02  Se asocia la persona con una empresa mediante vínculo histórico.
- CA-CU-07-03  Se asigna al vínculo al menos una función válida.
- CA-CU-07-04  Se rechazan funciones inactivas, duplicadas o inexistentes.
- CA-CU-07-05  Un cambio de función conserva el segmento anterior.
- CA-CU-07-06  Se conserva una misma PersonaNatural después de cambiar de empresa.
- CA-CU-07-07  No se autorizan modificaciones fuera del alcance de A4.
- CA-CU-07-08  Las jornadas anteriores conservan su referencia al vínculo histórico.

### Dependencias funcionales
- CU-04: empresas.
- CU-12: selección de responsable elegible.
- CU-01/CU-02: cuenta de acceso solo si se necesita autenticación móvil.

### Decisiones pendientes y límites de definición
- D1: permisos de A1/A4 sobre personas y vínculos.
- Criterios municipales para la función de conducción y obligatoriedad de cuenta para cada responsable.

### Estado de definición y evidencia

Implementado técnicamente y verificado automáticamente según último reporte; aceptación institucional pendiente.

> Trazabilidad de reestructuración: CU-07.

PAQUETE 2  •  PLANIFICACIÓN TERRITORIAL Y PROGRAMACIÓN

## CU-08. Gestionar estructura territorial del servicio

| ID | CU-08 |
| --- | --- |
| Nombre | Gestionar estructura territorial del servicio |
| Paquete | Planificación territorial y programación |
| Prioridad | Media |
| Actores | Administrador municipal (A1) |
| Actor iniciador | Administrador municipal (A1) |


### Propósito

Mantener referencias geográficas institucionales para ubicar rutas, jornadas, incidencias, reclamos y puntos críticos sin depender exclusivamente de un proveedor de mapa base.

### Descripción

El Administrador organiza unidades territoriales mediante información geoespacial validada. El mapa permite visualizarla, pero el CU trata sobre las entidades territoriales, su identidad y sus relaciones, no sobre la configuración visual del mapa en sí.

### Precondiciones
1. El Administrador está autenticado y cuenta con autorización para administrar territorio.
2. Existen fuentes geográficas institucionales o datos identificados explícitamente como preliminares para su revisión.

### Flujo principal
1. El Administrador accede a la gestión territorial.
2. La plataforma muestra las unidades disponibles en una lista y una vista geográfica.
3. El Administrador selecciona consultar, registrar o actualizar una unidad autorizada.
4. La plataforma solicita tipo, identificación, referencia superior cuando corresponda y geometría espacial.
5. El Administrador introduce los datos y representa o importa la delimitación disponible.
6. La plataforma valida estructura y geometría sin suponer límites oficiales no proporcionados.
7. El Administrador revisa las relaciones con rutas o registros existentes.
8. El Administrador confirma la operación.
9. La plataforma guarda el registro territorial y conserva la trazabilidad de referencias utilizadas.

### Flujos alternativos
- FA-01 — Consulta de unidades. Se visualizan límites y jerarquías existentes sin editar los registros.
- FA-02 — Geometría provisional. Se permite trabajar en un entorno de prueba con geometría identificada como sintética, sin promoverla a delimitación oficial.
- FA-03 — Cambio territorial. Si un límite cambia, se aplica una estrategia que conserve la interpretación histórica de registros anteriores; la regla de versionado definitiva debe validarse.

### Excepciones
- E1. Geometría inválida — Se rechaza una delimitación mal formada.
- E2. Unidad o superior inexistente — No se crea una relación jerárquica colgante.
- E3. Ciclo jerárquico — No se admite una unidad que sea antecesora de sí misma.
- E4. Afectación histórica — La plataforma advierte y evita sustituir silenciosamente una referencia territorial utilizada.
- E5. Falta de autorización — Se deniega la edición.

### Postcondiciones
- Éxito. La unidad territorial queda registrada o actualizada con geometría y jerarquía coherentes, conservando referencias históricas aplicables.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-08-01  Los límites oficiales del GAMW no se inventan a partir de OpenStreetMap.
- RN-CU-08-02  Las capas institucionales se conservan en la base de datos propia.
- RN-CU-08-03  No se imponen niveles rígidos distrito/zona/sector sin evidencia.
- RN-CU-08-04  Las referencias territoriales usadas por registros históricos deben permanecer interpretables.
- RN-CU-08-05  La geometría se intercambia con convención espacial establecida, sin invertir coordenadas.

### Criterios de aceptación propuestos
- CA-CU-08-01  Se puede consultar una unidad territorial con geometría válida.
- CA-CU-08-02  Una geometría inválida no se persiste.
- CA-CU-08-03  No se permite una relación jerárquica circular.
- CA-CU-08-04  El registro queda disponible para asociar rutas y otras evidencias.
- CA-CU-08-05  No se borran vínculos históricos por editar una unidad.
- CA-CU-08-06  Las capas institucionales pueden recuperarse sin depender de teselas externas.
- CA-CU-08-07  Una geometría de prueba no se presenta como oficial.

### Dependencias funcionales
- CU-09: rutas de referencia.
- CU-10: puntos y geocercas.
- CU-25 a CU-29: reportes y consultas territoriales.

### Decisiones pendientes y límites de definición
- Estructura y nombres de niveles territoriales verdaderamente usados por GAMW.
- Fuentes y validación de límites; política de vigencia y corrección histórica de geometrías.
- Permisos para autorizar la modificación de referencias territoriales.

### Estado de definición y evidencia

Definición funcional parcial; faltan datos territoriales institucionales y políticas de versionado.

> Trazabilidad de reestructuración: CU-08.

## CU-09. Gestionar rutas operativas de referencia

| ID | CU-09 |
| --- | --- |
| Nombre | Gestionar rutas operativas de referencia |
| Paquete | Planificación territorial y programación |
| Prioridad | Media |
| Actores | Supervisor de empresa operadora (A4) |
| Actor iniciador | Supervisor de empresa operadora (A4) |


### Propósito

Digitalizar y consultar los recorridos que la empresa operadora ya utiliza, preservando versiones que sirvan como referencia para planificación y fiscalización.

### Descripción

El supervisor registra una ruta existente mediante un mapa, la consulta y genera versiones nuevas cuando cambia su geometría. La plataforma no calcula recorridos óptimos ni presume que el municipio diseñe las rutas de la empresa.

### Precondiciones
1. El supervisor cuenta con sesión y acceso a la operación correspondiente.
2. Existe información del recorrido utilizado por la empresa; las referencias territoriales oficiales se emplean cuando están disponibles.

### Flujo principal
1. El supervisor accede a la gestión de rutas operativas.
2. La plataforma presenta las rutas registradas y su información histórica disponible.
3. El supervisor selecciona registrar una nueva ruta o crear versión de una existente.
4. La plataforma muestra una interfaz cartográfica y los datos básicos de identificación.
5. El supervisor digitaliza o ajusta la geometría del recorrido existente.
6. El supervisor asocia referencias territoriales y frecuencia solo cuando existan datos válidos y el alcance lo permita.
7. La plataforma valida el trazado geoespacial y las condiciones del versionado.
8. El supervisor confirma el registro.
9. La plataforma conserva una Ruta o una RutaVersion nueva, manteniendo intactas las versiones anteriores.
10. La plataforma permite recuperar y visualizar el recorrido guardado.

### Flujos alternativos
- FA-01 — Consulta sin cambios. El supervisor visualiza versiones y la geometría histórica asociada.
- FA-02 — Nueva versión. Si la ruta ya existe y cambia, se cierra el intervalo de vigencia anterior según la regla de dominio y se crea una versión sucesora.
- FA-03 — Referencias incompletas. La ruta puede quedar como referencia operativa con datos adicionales pendientes, si las validaciones vigentes lo permiten; no se inventan frecuencias.

### Excepciones
- E1. Línea inválida — No se registra una geometría que no cumpla las reglas espaciales.
- E2. Ruta inexistente — No se crea una versión para un identificador inválido.
- E3. Versión histórica — Se impide sobrescribir destructivamente la geometría usada por jornadas antiguas.
- E4. Ámbito de operador no autorizado — Se rechaza gestión fuera de permisos.
- E5. Guardado fallido — No se deja una versión parcialmente registrada.

### Postcondiciones
- Éxito. La Ruta y su RutaVersion quedan registradas y recuperables, y ninguna versión histórica utilizada por jornadas es sobrescrita.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-09-01  Ruta y RutaVersion son entidades distintas.
- RN-CU-09-02  La geometría histórica de una versión se conserva.
- RN-CU-09-03  La ruta de referencia no es propiedad permanente de una empresa.
- RN-CU-09-04  Una jornada utiliza una versión precisa, no la geometría más reciente por defecto.
- RN-CU-09-05  No se implementa optimización automática ni ajustes de trazado no justificados.
- RN-CU-09-06  La validación municipal de una ruta continúa pendiente de definición institucional.

### Criterios de aceptación propuestos
- CA-CU-09-01  Se dibuja y registra una ruta de referencia con geometría válida.
- CA-CU-09-02  Se recupera la geometría registrada y se representa nuevamente.
- CA-CU-09-03  Una modificación genera nueva versión sin reescribir la anterior.
- CA-CU-09-04  Una jornada histórica continúa ligada a su versión original.
- CA-CU-09-05  Se rechaza una geometría inválida.
- CA-CU-09-06  No se crean recorridos optimizados automáticamente.
- CA-CU-09-07  Una consulta histórica muestra versiones distinguibles de la vigente.

### Dependencias funcionales
- CU-08: referencias territoriales cuando estén disponibles.
- CU-11: selección de versión de ruta.
- CU-16 y CU-19: comparación con trayectoria registrada.

### Decisiones pendientes y límites de definición
- Responsabilidad formal de creación, modificación y validación municipal de rutas.
- Disponibilidad de información de frecuencias y horarios de la empresa.
- Alcance exacto de A4 respecto de referencias de otras empresas.

### Estado de definición y evidencia

Implementación técnica previa con versionado y pruebas reportadas; validación municipal pendiente.

> Trazabilidad de reestructuración: CU-09 anterior, reformulado.

## CU-10. Gestionar puntos de control y geocercas

| ID | CU-10 |
| --- | --- |
| Nombre | Gestionar puntos de control y geocercas |
| Paquete | Planificación territorial y programación |
| Prioridad | Media |
| Actores | Administrador municipal (A1), Fiscalizador municipal / Ornato Público (A3) |
| Actor iniciador | Administrador municipal (A1), Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Definir referencias espaciales verificables que el fiscalizador pueda contrastar con las posiciones GPS sin interpretarlas como certificaciones de recolección.

### Descripción

Personal autorizado registra puntos de control y áreas geográficas de referencia y los relaciona con rutas, versiones o unidades territoriales cuando corresponda. El sistema conserva geometría y contexto para posteriores evaluaciones espaciales.

### Precondiciones
1. El usuario está autenticado y autorizado para gestionar estas referencias.
2. Existen rutas, versiones o referencias territoriales válidas cuando se requiera asociación.

### Flujo principal
1. El usuario abre la gestión de puntos de control y geocercas.
2. La plataforma muestra las referencias existentes en mapa y listado.
3. El usuario selecciona registrar un punto, definir un área o consultar/actualizar una referencia.
4. La plataforma solicita nombre, ubicación o geometría y asociación contextual permitida.
5. El usuario indica la ubicación o delimita la geometría sobre el mapa.
6. La plataforma valida integridad, sistema de coordenadas y referencias asociadas.
7. El usuario revisa la información y confirma.
8. La plataforma conserva la referencia espacial y sus relaciones.
9. La nueva referencia queda disponible para CU-18 según su contexto temporal.

### Flujos alternativos
- FA-01 — Punto de control. El usuario registra una ubicación puntual con nombre y referencia operacional.
- FA-02 — Geocerca. El usuario delimita una superficie o parametrización de proximidad admitida por el diseño aprobado.
- FA-03 — Consulta. Se visualiza la referencia y su asociación, sin emitir evaluación de cumplimiento.

### Excepciones
- E1. Geometría inválida — Se rechaza el punto o polígono fuera del contrato espacial.
- E2. Referencia inexistente — No se asocia un control a una ruta o versión inválida.
- E3. Parametrización no aprobada — No se adoptan radios, umbrales ni significados operativos por defecto como reglas institucionales.
- E4. Afectación histórica — No se sustituye silenciosamente una referencia ya utilizada para evaluar jornadas pasadas.
- E5. Acceso no autorizado — Se deniega la creación o modificación.

### Postcondiciones
- Éxito. El punto o geocerca queda almacenado con su geometría y asociaciones, disponible como referencia espacial para revisión.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-10-01  Los puntos de control/geocercas representan referencias de análisis, no prueba de que el camión recogió residuos.
- RN-CU-10-02  Su geometría debe permanecer en el repositorio institucional PostGIS.
- RN-CU-10-03  La relación con RutaVersion se preserva cuando resulta necesaria para la historia.
- RN-CU-10-04  Los parámetros de proximidad se deben declarar y documentar, no ocultar.
- RN-CU-10-05  La competencia exacta de A1/A3 sobre creación y validación requiere decisión institucional.

### Criterios de aceptación propuestos
- CA-CU-10-01  Se registra y recupera un punto de control con ubicación válida.
- CA-CU-10-02  Se rechaza una geocerca con geometría inválida.
- CA-CU-10-03  La referencia se vincula correctamente con la ruta/versión correspondiente.
- CA-CU-10-04  Una actualización no descontextualiza evaluaciones históricas.
- CA-CU-10-05  La consulta presenta geometría y denominación sin inferir cumplimiento.
- CA-CU-10-06  Un usuario sin autorización no modifica controles.
- CA-CU-10-07  La referencia queda utilizable por una consulta espacial posterior.

### Dependencias funcionales
- CU-08 y CU-09: estructura y versiones de ruta.
- CU-18: revisión de coincidencias.
- CU-21: evidencia de fiscalización.

### Decisiones pendientes y límites de definición
- Definición de autoridad A1/A3 y procedimiento para validar controles.
- Tipos y radios/áreas permitidos; vigencia temporal y posible versionado.
- Fuentes de puntos reales proporcionados por GAMW o empresa.

### Estado de definición y evidencia

Definición funcional parcial; ausencia de política espacial y autoridad de registro.

> Trazabilidad de reestructuración: CU-10.

## CU-11. Programar jornada

| ID | CU-11 |
| --- | --- |
| Nombre | Programar jornada |
| Paquete | Planificación territorial y programación |
| Prioridad | Alta |
| Actores | Supervisor de empresa operadora (A4) |
| Actor iniciador | Supervisor de empresa operadora (A4) |


### Propósito

Registrar una ejecución prevista del servicio vinculada con empresa, periodo, fecha y versión de ruta de referencia.

### Descripción

El supervisor establece la programación operativa. La empresa se determina por el periodo de servicio; la jornada identifica una versión histórica de ruta y conserva una referencia temporal estable para posteriores evidencias.

### Precondiciones
1. El supervisor está autenticado y autorizado para el periodo correspondiente.
2. Existen un periodo de servicio y una RutaVersion seleccionables.

### Flujo principal
1. El supervisor abre la programación de jornadas.
2. La plataforma muestra periodos y rutas de referencia disponibles según alcance.
3. El supervisor selecciona el periodo de servicio.
4. La plataforma identifica la empresa operadora vinculada al periodo.
5. El supervisor selecciona la versión de ruta y define fecha/horario programado disponible.
6. La plataforma comprueba consistencia temporal y referencial de la propuesta.
7. La plataforma presenta los datos de la jornada para revisión.
8. El supervisor confirma la programación.
9. La plataforma crea una Jornada nueva con referencias históricas invariables.
10. La plataforma confirma el registro y permite continuar a CU-12.

### Flujos alternativos
- FA-01 — Consulta de programación. El supervisor recupera jornadas anteriores con periodo, empresa y versión de ruta originales.
- FA-02 — Recursos no asignados. La programación puede existir antes de CU-12; la asignación constituye una operación distinta.
- FA-03 — Cambio posterior. Si procede una reprogramación, se aplica CU-24, conservando el antecedente.

### Excepciones
- E1. Periodo inexistente — Se rechaza la programación.
- E2. Versión de ruta no válida — Se impide asociar un recorrido inexistente.
- E3. Fechas inconsistentes — Se rechaza un horario con fin programado anterior al inicio o fuera de reglas validadas.
- E4. Acceso fuera de empresa — El supervisor no programa datos de otro ámbito no autorizado.
- E5. Error de persistencia — No se crea una jornada incompleta.

### Postcondiciones
- Éxito. Se crea una Jornada vinculada al PeriodoServicio y a una RutaVersion precisa, preparada para asignación operativa.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-11-01  Jornada se vincula a PeriodoServicio y RutaVersion.
- RN-CU-11-02  La identidad de la empresa se obtiene del periodo, no se duplica como atributo mutable.
- RN-CU-11-03  El campo vigente de programación no equivale al estado de ejecución ni al cumplimiento.
- RN-CU-11-04  La asignación de vehículo y persona se maneja mediante AsignacionJornada en CU-12.
- RN-CU-11-05  La reprogramación genera nueva jornada, no reescribe el plan precedente.

### Criterios de aceptación propuestos
- CA-CU-11-01  Se registra jornada con periodo y versión válidos.
- CA-CU-11-02  La empresa resultante coincide con el periodo seleccionado.
- CA-CU-11-03  La jornada no persiste una asociación de empresa incompatible con su periodo.
- CA-CU-11-04  La versión de ruta permanece fija al consultar la jornada posteriormente.
- CA-CU-11-05  Puede consultarse la jornada aunque todavía no tenga recursos asignados.
- CA-CU-11-06  La fecha y horario deben cumplir las validaciones establecidas.
- CA-CU-11-07  No es posible programar para una empresa ajena sin permiso.
- CA-CU-11-08  Una reprogramación preserva el antecedente original.

### Dependencias funcionales
- CU-04 y CU-05: empresa y periodo.
- CU-09: versión de ruta.
- CU-12: recursos.
- CU-13: ejecución.
- CU-24: reprogramación.

### Decisiones pendientes y límites de definición
- Responsabilidad A4 y reglas institucionales finales sobre calendarios y frecuencia.
- Posibles restricciones oficiales de disponibilidad, turnos y vigencia que aún no están acreditadas.

### Estado de definición y evidencia

Implementación técnica previa con pruebas reportadas; condiciones institucionales adicionales pendientes.

> Trazabilidad de reestructuración: CU-11.

## CU-12. Asignar recursos a jornada

| ID | CU-12 |
| --- | --- |
| Nombre | Asignar recursos a jornada |
| Paquete | Planificación territorial y programación |
| Prioridad | Alta |
| Actores | Supervisor de empresa operadora (A4) |
| Actor iniciador | Supervisor de empresa operadora (A4) |


### Propósito

Asociar un vehículo y responsable operativo habilitados para la ejecución de una jornada sin perder los antecedentes de asignaciones anteriores.

### Descripción

El supervisor crea una AsignacionJornada basada en vínculos históricos vehículo–empresa y persona–empresa, verificando que sean consistentes con el periodo de servicio. Las reasignaciones conservan segmentos sucesivos.

### Precondiciones
1. El supervisor está autenticado y autorizado para la jornada seleccionada.
2. La jornada ya existe.
3. Existen vehículo y responsable con vínculos compatibles con el contexto temporal de la jornada.

### Flujo principal
1. El supervisor consulta la jornada que necesita recursos.
2. La plataforma obtiene el periodo, empresa y referencias de la jornada.
3. La plataforma presenta los vehículos y responsables elegibles según sus vínculos históricos y autorización.
4. El supervisor selecciona vehículo y persona.
5. La plataforma comprueba correspondencia de los vínculos con la empresa y la jornada.
6. La plataforma verifica que no se infrinja la regla de una asignación abierta por jornada.
7. El supervisor confirma la asignación.
8. La plataforma crea AsignacionJornada referenciando los vínculos elegidos.
9. La plataforma confirma el registro y mantiene el historial para la ejecución móvil.

### Flujos alternativos
- FA-01 — Reasignación. Se cierra la asignación anterior y se registra otra con su contexto temporal; no se sustituyen los identificadores históricos.
- FA-02 — Consulta histórica. El supervisor revisa vehículo y responsable utilizados en distintos momentos sin alterar referencias.
- FA-03 — Recursos no elegibles. Se solicita seleccionar otra combinación cuando alguna relación no corresponde al periodo o empresa.

### Excepciones
- E1. Vehículo o persona inexistente — Se rechazan referencias no válidas.
- E2. Vínculo incompatible — No se aceptan recursos cuya relación no corresponda al ámbito de jornada.
- E3. Concurrencia — Dos operaciones simultáneas no deben crear dos asignaciones abiertas.
- E4. Reasignación inconsistente — No se elimina la asignación anterior si no puede crearse la sucesora.
- E5. Acceso no autorizado — No se modifica jornada ajena.

### Postcondiciones
- Éxito. La AsignacionJornada identifica recursos y vínculos históricos válidos; cualquier reasignación conserva el segmento anterior.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-12-01  AsignacionJornada conserva IDs de vínculos históricos, no relaciones mutables a empresa.
- RN-CU-12-02  Solo puede existir una asignación abierta por jornada según el modelo vigente.
- RN-CU-12-03  La reasignación cierra segmento anterior y agrega segmento posterior.
- RN-CU-12-04  La compatibilidad se verifica con empresa/periodo y temporalidad aplicable.
- RN-CU-12-05  La asignación no demuestra ejecución ni cumplimiento.

### Criterios de aceptación propuestos
- CA-CU-12-01  La primera asignación válida queda registrada para una jornada existente.
- CA-CU-12-02  Un recurso de otra empresa se rechaza.
- CA-CU-12-03  No pueden coexistir dos asignaciones abiertas para una misma jornada.
- CA-CU-12-04  Una reasignación conserva el recurso y periodo anteriores.
- CA-CU-12-05  Una transacción fallida no deja una asignación anterior cerrada sin reemplazo previsto.
- CA-CU-12-06  La jornada presenta el recurso correspondiente a su segmento histórico.
- CA-CU-12-07  Un supervisor no puede asignar recursos en jornadas de otro ámbito.

### Dependencias funcionales
- CU-05: periodo y empresa.
- CU-06: vínculo de vehículo.
- CU-07: vínculo de persona y funciones.
- CU-11: jornada existente.
- CU-13: responsable autenticado y ejecución.
- CU-24: antecedentes en reprogramación.

### Decisiones pendientes y límites de definición
- Reglas institucionales sobre disponibilidad concurrente de vehículos y responsables.
- Obligatoriedad institucional específica de función CONDUCTOR.
- Autorizaciones definitivas del supervisor A4.

### Estado de definición y evidencia

Implementación técnica previa con pruebas reportadas; reglas institucionales de disponibilidad pendientes.

> Trazabilidad de reestructuración: CU-12.

PAQUETE 3  •  EJECUCIÓN OPERATIVA

## CU-13. Ejecutar jornada asignada

| ID | CU-13 |
| --- | --- |
| Nombre | Ejecutar jornada asignada |
| Paquete | Ejecución operativa |
| Prioridad | Alta |
| Actores | Conductor / responsable móvil (A5) |
| Actor iniciador | Conductor / responsable móvil (A5) |


### Propósito

Permitir al responsable móvil consultar su asignación, registrar inicio y fin y generar evidencia de recorrido, incluso cuando se interrumpa Internet.

### Descripción

Integra consulta de jornada, INICIO, captura GPS, persistencia local, sincronización y FIN como un ciclo de ejecución. Registrar incidencias es una interacción opcional relacionada con CU-14; la aplicación no declara el servicio cumplido por recibir GPS o finalizar la jornada.

### Precondiciones
1. El usuario móvil está autenticado o dispone de un contexto offline previamente validado según el diseño vigente.
2. Existe una jornada y una AsignacionJornada atribuida a su PersonaNatural.
3. El dispositivo dispone de condiciones para ejecutar la aplicación; la ausencia de Internet no impide guardar evidencia local que ya esté autorizada.

### Flujo principal
1. El conductor ingresa a la aplicación móvil y consulta Mis jornadas.
2. La plataforma identifica la cuenta y recupera las asignaciones que corresponden a su persona.
3. El conductor selecciona una jornada y revisa periodo, ruta, vehículo y contexto operativo disponible.
4. El conductor solicita iniciar la ejecución.
5. La plataforma comprueba identidad, asignación y vigencia de inicio, y registra un evento INICIO con identificador único y fecha de captura.
6. La aplicación activa la captura GPS conforme a los permisos y la política técnica de seguimiento aprobada.
7. Durante el recorrido, la aplicación persiste localmente posiciones con fecha de captura, coordenadas y precisión, y conserva su relación con jornada y cuenta.
8. Cuando existe conectividad y se inicia la sincronización conforme al mecanismo implementado, la aplicación transmite evidencias pendientes de forma idempotente.
9. Si ocurre una incidencia, el conductor puede acceder a CU-14 sin perder el contexto de jornada.
10. Al concluir, el conductor solicita FIN; la plataforma registra fecha y hora originales, detiene la captura y conserva registros aún pendientes.
11. La jornada y sus evidencias quedan disponibles para monitoreo y fiscalización conforme a las autorizaciones de consulta.

### Flujos alternativos
- FA-01 — Internet no disponible. La aplicación sigue capturando localmente si dispone de ubicación y almacenamiento; los registros permanecen pendientes hasta sincronización.
- FA-02 — Sin posición GPS. La plataforma conserva INICIO/FIN y señala ausencia de posiciones; no fabrica coordenadas ni concluye incumplimiento.
- FA-03 — Reintento. La misma evidencia con el mismo identificador no produce duplicados; un contenido incompatible produce conflicto conservado.
- FA-04 — Cambio de responsable. Una reasignación histórica válida puede permitir FIN bajo otra asignación autorizada, sin reatribuir INICIO previo.
- FA-05 — Restauración. Tras reinicio o terminación del proceso, la aplicación recupera evidencia local y estado de la cola sin descartarla.

### Excepciones
- E1. Jornada ajena — El sistema deniega inicio o fin sobre una asignación no perteneciente a la identidad autenticada.
- E2. Segundo INICIO o FIN incompatible — La operación se rechaza conforme a la unicidad de evidencia.
- E3. FIN anterior a INICIO — No se admite una secuencia temporal inválida.
- E4. Falta de permisos de ubicación — Se informa la limitación y se evita afirmar que hubo captura exitosa.
- E5. Almacenamiento local fallido — La aplicación no presenta un dato no persistido como evidencia segura.
- E6. Cambio de cuenta — No se envían registros pendientes capturados por otra cuenta.
- E7. Pérdida de conectividad — No se reemplaza capturadoEn por recibidoEn ni se borran pendientes.

### Postcondiciones
- Éxito. La jornada conserva INICIO, FIN y posiciones GPS efectivamente capturadas, con identidad y fechas originales; la evidencia sigue disponible para supervisión sin declarar cumplimiento.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-13-01  GPS, Internet y sincronización son condiciones diferentes.
- RN-CU-13-02  INICIO, FIN y posiciones son evidencia; el fiscalizador determina el cumplimiento.
- RN-CU-13-03  Toda evidencia conserva identificador de cliente y fecha original de captura.
- RN-CU-13-04  La sincronización es idempotente y preserva conflictos para revisión.
- RN-CU-13-05  La identidad del usuario se obtiene desde autenticación, no de un campo arbitrario de solicitud.
- RN-CU-13-06  Una jornada referencia la versión histórica de ruta y sus asignaciones correspondientes.
- RN-CU-13-07  La captura GPS no prueba por sí sola la presencia física del camión.

### Criterios de aceptación propuestos
- CA-CU-13-01  El conductor recupera únicamente sus jornadas mediante identidad autenticada.
- CA-CU-13-02  INICIO válido registra un único evento atribuible a una asignación autorizada.
- CA-CU-13-03  Se rechaza INICIO duplicado diferente y FIN anterior a INICIO.
- CA-CU-13-04  Cada posición conserva fecha original, coordenadas y precisión cuando el proveedor las suministra.
- CA-CU-13-05  Sin Internet y con GPS disponible, la posición se guarda localmente y puede sincronizarse después.
- CA-CU-13-06  Un reintento idéntico no duplica evidencia ni modifica recibidoEn original.
- CA-CU-13-07  Una cuenta B no puede sincronizar evidencia capturada bajo cuenta A.
- CA-CU-13-08  FIN detiene captura y conserva registros todavía pendientes.
- CA-CU-13-09  La recuperación tras cierre del proceso no borra evidencias locales.
- CA-CU-13-10  La jornada queda como evidencia pendiente de fiscalización, sin cumplimiento automático.

### Dependencias funcionales
- CU-01: identidad de conductor.
- CU-11 y CU-12: jornada y asignación.
- CU-14: registro opcional de incidencias.
- CU-15 y CU-16: consulta municipal de posiciones y trayectoria.
- CU-21: fiscalización posterior.

### Decisiones pendientes y límites de definición
- Política técnica e institucional de cadencia GPS, transmisión online y gestión de permisos.
- Alcance de consulta de datos de ruta/vehículo móvil y criterios de funcionamiento de seguimiento continuo.
- CA-4A-21: E2E automatizado de captura GPS física sigue pendiente de verificación.

### Estado de definición y evidencia

Implementación de Slice 4A con 20/21 criterios reportados verificados; CU-13 global aún parcial, aceptación institucional pendiente.

> Trazabilidad de reestructuración: CU-13, CU-14 y CU-15 anteriores fusionados; CU-17–CU-19 antiguos incorporados como comportamientos.

## CU-14. Registrar incidencia operativa

| ID | CU-14 |
| --- | --- |
| Nombre | Registrar incidencia operativa |
| Paquete | Ejecución operativa |
| Prioridad | Media |
| Actores | Supervisor de empresa operadora (A4), Conductor / responsable móvil (A5) |
| Actor iniciador | Uno de los actores autorizados, según operación y ámbito |


### Propósito

Documentar situaciones concretas ocurridas durante la prestación del servicio y relacionarlas con la jornada y sus recursos para supervisión posterior.

### Descripción

El conductor o supervisor informa una circunstancia observada (por ejemplo, vía inaccesible o avería), junto con tiempo y contexto disponible. El registro de incidencia no equivale automáticamente a incumplimiento ni genera sanción. Las categorías definitivas serán definidas con GAMW.

### Precondiciones
1. El actor está identificado y autorizado para registrar datos de la jornada.
2. Existe una jornada con la cual se relaciona la incidencia, o un contexto válido definido institucionalmente.
3. Están establecidos los campos mínimos de captura; si la categoría es obligatoria, su catálogo debe haber sido aprobado.

### Flujo principal
1. El actor consulta una jornada bajo su ámbito autorizado.
2. Selecciona registrar incidencia operativa.
3. La plataforma muestra el contexto de jornada y los datos requeridos para documentar el hecho.
4. El actor incorpora descripción, fecha/hora del hecho y ubicación o evidencia disponibles.
5. La plataforma ofrece categorías institucionales únicamente si existe catálogo validado.
6. La plataforma comprueba consistencia del registro y de la relación con la jornada.
7. El actor confirma la incidencia.
8. La plataforma guarda la información y la identidad del registrante cuando corresponda.
9. La incidencia queda disponible para consulta, supervisión y fiscalización.

### Flujos alternativos
- FA-01 — Registro móvil offline. Si la captura offline de incidencias se aprueba e implementa, se conservará el registro local hasta transmitirlo; esa capacidad no se presume existente hoy.
- FA-02 — Incidencia sin ubicación. Se registra la situación con los datos disponibles cuando la ubicación no sea obligatoria; no se inventan coordenadas.
- FA-03 — Consulta por supervisor. A4 visualiza únicamente incidencias de jornadas de su empresa y periodos autorizados.

### Excepciones
- E1. Jornada no válida — No se vincula un evento a una jornada inexistente.
- E2. Datos mínimos insuficientes — Se solicita completar información requerida.
- E3. Categoría no válida — Se rechaza una referencia a categoría inexistente o inactiva, si el catálogo es aplicable.
- E4. Actor ajeno — No se permite registrar incidencias en jornadas de otra empresa o conductor.
- E5. Falla de persistencia — No se comunica éxito si la incidencia no ha quedado guardada.

### Postcondiciones
- Éxito. La incidencia queda asociada a una jornada y a su registrante, con descripción y contexto disponible para consulta y fiscalización.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-14-01  La incidencia conserva vínculo con jornada y autor del registro.
- RN-CU-14-02  La descripción factual no supone una conclusión municipal.
- RN-CU-14-03  No se inventan categorías oficiales de incidencias ni tiempos contractuales.
- RN-CU-14-04  El conductor y el supervisor tienen alcances distintos, definidos por autorización.
- RN-CU-14-05  La evidencia original debe conservarse aunque una incidencia se atienda posteriormente.

### Criterios de aceptación propuestos
- CA-CU-14-01  Se registra una incidencia con referencia válida a jornada.
- CA-CU-14-02  Se conserva la descripción y la fecha real de ocurrencia declarada.
- CA-CU-14-03  Una incidencia de otra empresa es inaccesible para un supervisor no autorizado.
- CA-CU-14-04  La falta de datos obligatorios impide el registro.
- CA-CU-14-05  No se asignan automáticamente sanciones ni resultados de fiscalización.
- CA-CU-14-06  Un error de almacenamiento no genera confirmación falsa.
- CA-CU-14-07  La incidencia queda consultable como antecedente de la jornada.

### Dependencias funcionales
- CU-13: momento de ejecución.
- CU-15 y CU-16: contexto de supervisión.
- CU-21: revisión de evidencia.
- CU-22: acción correctiva posible, sin creación automática obligatoria.

### Decisiones pendientes y límites de definición
- PENDING-06: categorías institucionales y campos obligatorios.
- Reglas de registro fuera de jornada activa o en un momento posterior.
- Autorización A4/A5 y necesidad real de captura offline de incidencias.

### Estado de definición y evidencia

Definición parcial; no implementado integralmente según inventario.

> Trazabilidad de reestructuración: CU-16 anterior.

PAQUETE 4  •  MONITOREO GPS Y TRAZABILIDAD

## CU-15. Monitorear posición de jornada

| ID | CU-15 |
| --- | --- |
| Nombre | Monitorear posición de jornada |
| Paquete | Monitoreo GPS y trazabilidad |
| Prioridad | Alta |
| Actores | Fiscalizador municipal / Ornato Público (A3), Supervisor de empresa operadora (A4) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3), Supervisor de empresa operadora (A4) |


### Propósito

Permitir que el fiscalizador consulte la última posición GPS recibida de jornadas de su ámbito y conozca qué tan reciente es la evidencia presentada.

### Descripción

La interfaz cartográfica presenta jornadas autorizadas, última posición recibida ordenada por captura original, marca temporal y precisión. El mapa puede actualizarse con nuevas evidencias, pero siempre diferencia seguimiento cercano al tiempo real de datos retrasados o ausentes. La participación de A4 requiere autorización limitada a su operación.

### Precondiciones
1. El usuario está autenticado y autorizado para consultar las jornadas correspondientes.
2. Existen jornadas registradas; pueden o no tener posiciones GPS recibidas.
3. La plataforma dispone del servicio de consulta de seguimiento y un mapa operativo.

### Flujo principal
1. El fiscalizador accede al módulo de monitoreo.
2. La plataforma filtra las jornadas disponibles de acuerdo con su autorización.
3. El fiscalizador selecciona una o varias jornadas a observar según la interfaz habilitada.
4. La plataforma obtiene la última posición GPS por fecha/hora original de captura, sin confundirla con orden de recepción.
5. La plataforma presenta en mapa la posición y el contexto de jornada, vehículo y ruta autorizado.
6. La plataforma indica hora de captura, hora de recepción cuando proceda y calidad/precisión disponible.
7. El usuario consulta la antigüedad de la última posición y las advertencias de falta de actualización.
8. Al recibirse nuevas capturas, la vista se actualiza conforme al mecanismo técnico seleccionado.
9. El fiscalizador utiliza los datos como apoyo de supervisión, no como certificación de cumplimiento.

### Flujos alternativos
- FA-01 — Jornada sin posiciones. La plataforma muestra que no existe ubicación disponible; no coloca un punto inventado.
- FA-02 — Posiciones recibidas tarde. Se reevalúa la última posición por capturadoEn y no por la llegada más reciente.
- FA-03 — Conectividad interrumpida. Se mantiene la última evidencia disponible, identificando su antigüedad.
- FA-04 — Vista del supervisor. Solo se presentan jornadas de la empresa y periodos expresamente autorizados para A4.

### Excepciones
- E1. Jornada inexistente o ajena — Se responde sin divulgar posiciones ni datos de terceros.
- E2. Base cartográfica no disponible — Se preservan los datos institucionales y se informa la limitación visual.
- E3. Falta de servicio de actualizaciones — La interfaz informa que no está recibiendo novedades; no simula seguimiento activo.
- E4. Posición con metadatos incompletos — Se presenta únicamente información verificada y se señala ausencia del dato faltante.

### Postcondiciones
- Éxito. El usuario autorizado consulta la posición conocida más reciente y su antigüedad real, sin modificar evidencia ni presumir transmisión continua.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-15-01  La posición más reciente se determina por tiempo original de captura, con desempate estable.
- RN-CU-15-02  Una posición recibida no demuestra que el servicio se ejecutó correctamente.
- RN-CU-15-03  La falta de actualización no implica que el vehículo esté parado.
- RN-CU-15-04  A4 no obtiene facultades de fiscalización por acceder al monitoreo.
- RN-CU-15-05  El sistema distingue capturadoEn, recibidoEn y momento de visualización.
- RN-CU-15-06  La cercanía al tiempo real depende de transmisión online y disponibilidad de red, no solo de un mapa animado.

### Criterios de aceptación propuestos
- CA-CU-15-01  Se presenta la última posición de la jornada según capturadoEn.
- CA-CU-15-02  Una captura antigua recibida después no desplaza erróneamente a una más reciente.
- CA-CU-15-03  Se identifica una jornada sin posiciones sin fabricar ubicación.
- CA-CU-15-04  Se muestra fecha/hora y precisión disponibles.
- CA-CU-15-05  Las posiciones retrasadas pueden distinguirse de las actuales.
- CA-CU-15-06  El fiscalizador accede únicamente a jornadas autorizadas.
- CA-CU-15-07  El supervisor no accede a posiciones de otras empresas.
- CA-CU-15-08  El mapa no determina cumplimiento ni fraude automático.
- CA-CU-15-09  La actualización operativa no se presenta como instantánea cuando no existen nuevos datos.

### Dependencias funcionales
- CU-13: producción de posiciones y transmisión.
- CU-16: trayectoria por jornada.
- CU-17: interrupciones e integridad.
- CU-21: revisión municipal posterior.
- CU-03: permisos por ámbito.

### Decisiones pendientes y límites de definición
- D1: acceso exacto de A3 y A4.
- Política de envío online, actualización visual, latencia y presentación de antigüedad.
- Definición observable de jornada en ejecución, sin inventar un nuevo estado institucional.

### Estado de definición y evidencia

Parcial: consulta interna de última posición verificada; faltan endpoint institucional, mapa y actualizaciones.

> Trazabilidad de reestructuración: CU-20 anterior.

## CU-16. Consultar trayectoria GPS de jornada

| ID | CU-16 |
| --- | --- |
| Nombre | Consultar trayectoria GPS de jornada |
| Paquete | Monitoreo GPS y trazabilidad |
| Prioridad | Alta |
| Actores | Fiscalizador municipal / Ornato Público (A3), Supervisor de empresa operadora (A4) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3), Supervisor de empresa operadora (A4) |


### Propósito

Reconstruir la secuencia temporal de posiciones GPS registradas para examinar el recorrido de una jornada activa o histórica.

### Descripción

La plataforma presenta coordenadas originales en orden de captura, con fechas y precisión; puede superponerlas a la versión de ruta programada. El dibujo de líneas entre posiciones no debe ocultar intervalos sin datos ni representar como recorrido físico comprobado un tramo inferido.

### Precondiciones
1. El usuario está autenticado y autorizado para la jornada solicitada.
2. La jornada existe y puede tener cero, una o varias posiciones GPS registradas.

### Flujo principal
1. El usuario accede al historial o a una jornada de monitoreo.
2. Selecciona la jornada cuya trayectoria desea consultar.
3. La plataforma valida identidad, autorización y existencia de jornada.
4. La plataforma consulta las posiciones asociadas, ordenadas por fecha de captura con un desempate estable.
5. La plataforma devuelve sus coordenadas sin redondeos arbitrarios, timestamps, precisión y metadatos disponibles.
6. La interfaz representa los puntos sobre el mapa y expone el intervalo temporal de cada muestra.
7. Cuando existe, se representa separadamente la versión de ruta de referencia asignada a la jornada.
8. El usuario revisa puntos, intervalos sin registros y coincidencias visuales disponibles.
9. La plataforma conserva las posiciones originales, sin inferir por sí misma cumplimiento del recorrido.

### Flujos alternativos
- FA-01 — Sin registros. Se informa que la jornada aún no posee posiciones.
- FA-02 — Una sola posición. Se muestra el punto registrado, sin afirmar que existe un recorrido completo.
- FA-03 — Sincronización tardía. Las nuevas capturas se integran en su posición cronológica original.
- FA-04 — Superposición. El usuario puede comparar la trayectoria registrada y la ruta de referencia, si está disponible, diferenciando ambas capas.

### Excepciones
- E1. Jornada ajena — Se rechaza consulta fuera del ámbito permitido.
- E2. Geometría inválida — No se representa una coordenada inválida; se documenta el dato problemático para revisión sin ocultar la incidencia.
- E3. Teselas externas caídas — Los registros espaciales siguen conservados y consultables como datos.
- E4. Consulta incompleta — La plataforma no declara una trayectoria total cuando faltan capturas.

### Postcondiciones
- Éxito. La secuencia GPS ordenada por captura queda disponible para revisión cartográfica e histórica sin alterar datos originales.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-16-01  Las posiciones GPS originales son evidencias históricas, no vértices de una ruta oficial.
- RN-CU-16-02  GeoJSON utiliza orden [longitud, latitud] y SRID EPSG:4326.
- RN-CU-16-03  La secuencia cronológica usa capturadoEn, no recibidoEn.
- RN-CU-16-04  El trazado no interpola silenciosamente tramos sin posiciones.
- RN-CU-16-05  La ruta comparada corresponde a RutaVersion asociada a Jornada.
- RN-CU-16-06  La consulta no altera ni depura la evidencia capturada.

### Criterios de aceptación propuestos
- CA-CU-16-01  Se recuperan todas las posiciones autorizadas de una jornada en orden determinista.
- CA-CU-16-02  Las coordenadas GeoJSON respetan [longitud, latitud].
- CA-CU-16-03  Se conserva la precisión y timestamps originales.
- CA-CU-16-04  Un lote sincronizado tardíamente aparece en su orden de captura.
- CA-CU-16-05  Una sola posición no se presenta como trayectoria continua.
- CA-CU-16-06  Los intervalos sin datos se distinguen de recorrido confirmado.
- CA-CU-16-07  La jornada no autorizada no expone coordenadas.
- CA-CU-16-08  La ruta de referencia histórica no es reemplazada por su versión actual.

### Dependencias funcionales
- CU-09: RutaVersion.
- CU-13: evidencia GPS.
- CU-15: acceso desde monitoreo.
- CU-17 a CU-19: revisión de anomalías.
- CU-21 y CU-29: fiscalización e historia.

### Decisiones pendientes y límites de definición
- D1: quién consulta datos históricos, alcance de A4 tras finalizar su periodo.
- Presentación visual concreta de interrupciones y longitud mínima de una trayectoria.
- Política de paginación/volumen de datos según mediciones reales.

### Estado de definición y evidencia

Parcial: consulta interna PostGIS verificada; faltan endpoint protegido y visualización web.

> Trazabilidad de reestructuración: CU-21 anterior.

## CU-17. Revisar continuidad e integridad del seguimiento GPS

| ID | CU-17 |
| --- | --- |
| Nombre | Revisar continuidad e integridad del seguimiento GPS |
| Paquete | Monitoreo GPS y trazabilidad |
| Prioridad | Media |
| Actores | Fiscalizador municipal / Ornato Público (A3) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Identificar indicios de registros incompletos o técnicamente inconsistentes para apoyar la investigación municipal de una jornada.

### Descripción

El fiscalizador revisa periodos sin posiciones, baja precisión, saltos improbables, diferencias entre captura y recepción, y señales de ubicación simulada solo cuando la plataforma pueda observarlas. Los hallazgos constituyen alertas técnicas, nunca declaraciones automáticas de fraude.

### Precondiciones
1. El fiscalizador dispone de autenticación y permiso de consulta.
2. La jornada existe y posee datos GPS o metadatos de captura suficientes para un análisis parcial.
3. Los parámetros técnicos usados para clasificar señales están explicitados y documentados.

### Flujo principal
1. El fiscalizador selecciona una jornada.
2. La plataforma presenta disponibilidad y cobertura temporal de posiciones registradas.
3. La plataforma recupera las muestras originales y metadatos de captura/recepción.
4. La plataforma calcula o recupera indicadores de continuidad según parámetros documentados.
5. La plataforma identifica ausencias de muestras, calidad limitada o saltos que ameritan revisión.
6. La plataforma distingue eventos observados de datos no verificables, incluido el indicador mock cuando esté disponible.
7. La interfaz presenta cada señal con intervalo temporal, evidencia asociada y grado de certeza técnico.
8. El fiscalizador consulta los antecedentes e incidencias relacionados.
9. El resultado queda como material para inspección o fiscalización, sin emitir automáticamente un dictamen.

### Flujos alternativos
- FA-01 — Sin anomalías. La plataforma indica que no se detectaron señales bajo los parámetros usados; no certifica que el vehículo cumplió.
- FA-02 — Datos tardíos. El análisis puede cambiar al recibirse capturas pendientes; se diferencia el cálculo provisional de la evidencia persistida.
- FA-03 — Permiso GPS no observable. La plataforma declara desconocido ese motivo en lugar de afirmar que el usuario desactivó ubicación.
- FA-04 — Posible ubicación simulada. Se muestra solo una señal reportada por la plataforma del dispositivo, sin conclusión de manipulación comprobada.

### Excepciones
- E1. Datos insuficientes — No se emite resultado concluyente cuando no hay muestra temporal suficiente.
- E2. Parámetros no configurados — No se aplican umbrales secretos ni presuntamente contractuales.
- E3. Timestamp inválido — Se señala inconsistencia y se preservan datos originales.
- E4. Acceso no autorizado — Se deniega la consulta de evidencias ajenas.

### Postcondiciones
- Éxito. El fiscalizador puede revisar señales técnicas y huecos de seguimiento con los parámetros utilizados, sin dictamen de fraude o incumplimiento.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-17-01  Una ausencia de Internet no equivale a ausencia de GPS.
- RN-CU-17-02  Una ubicación del teléfono no prueba ubicación física del camión.
- RN-CU-17-03  Los indicadores técnicos de integridad no determinan fraude ni cumplimiento.
- RN-CU-17-04  La precisión debe conservarse y considerarse en interpretaciones espaciales.
- RN-CU-17-05  Los umbrales temporales y espaciales deben ser explícitos y revisables.
- RN-CU-17-06  Los registros originales no se reescriben para que una trayectoria parezca continua.

### Criterios de aceptación propuestos
- CA-CU-17-01  Se identifican huecos temporales con inicio/fin según parámetros explícitos.
- CA-CU-17-02  Se puede distinguir última captura de última recepción.
- CA-CU-17-03  Se presenta precisión y se señala cuando sea baja conforme a un criterio configurado.
- CA-CU-17-04  Un mock-location ausente no se interpreta como garantía de autenticidad.
- CA-CU-17-05  Las posiciones sincronizadas tarde pueden modificar los indicadores sin alterar evidencia original.
- CA-CU-17-06  No se infiere fraude por una interrupción.
- CA-CU-17-07  Un caso sin datos suficientes queda marcado como indeterminado.
- CA-CU-17-08  El actor no autorizado no accede al análisis.

### Dependencias funcionales
- CU-13: telemetría original.
- CU-15 y CU-16: consulta de seguimiento.
- CU-20 y CU-21: inspección y valoración humana.

### Decisiones pendientes y límites de definición
- PENDING-04: intervalo GPS, política de batería y frecuencia esperada.
- Criterios concretos de baja precisión, saltos y antigüedad; disponibilidad de indicadores nativos.
- Requisitos de conservación de cálculos/anomalías frente a recálculo técnico.

### Estado de definición y evidencia

Definición parcial: categorías observables descritas, umbrales y capacidad integral no definidos.

> Trazabilidad de reestructuración: CU-22 anterior, ampliado.

## CU-18. Revisar coincidencias con puntos de control y geocercas

| ID | CU-18 |
| --- | --- |
| Nombre | Revisar coincidencias con puntos de control y geocercas |
| Paquete | Monitoreo GPS y trazabilidad |
| Prioridad | Media |
| Actores | Fiscalizador municipal / Ornato Público (A3) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Comparar las evidencias GPS de una jornada con referencias espaciales previamente registradas para orientar la revisión municipal.

### Descripción

El fiscalizador consulta qué posiciones registradas coinciden o se aproximan a los controles vinculados a la versión histórica de la ruta. La detección es geoespacial y contextual, sin acreditar automáticamente recolección ni presencia física del vehículo.

### Precondiciones
1. El fiscalizador está autenticado y autorizado.
2. Existe una jornada con referencias espaciales asociadas, si fueron definidas.
3. Existe al menos una posición válida para evaluar una coincidencia.

### Flujo principal
1. El fiscalizador selecciona una jornada para revisar controles.
2. La plataforma identifica la versión de ruta de referencia y sus controles/geocercas aplicables.
3. La plataforma recupera las posiciones originales de la jornada.
4. La plataforma calcula las relaciones espaciales conforme a las geometrías y parámetros definidos.
5. La plataforma conserva el instante de captura relacionado con cada coincidencia observable.
6. La interfaz representa puntos, geocercas y posiciones coincidentes o no coincidentes según el análisis.
7. El fiscalizador revisa la precisión, continuidad de muestras y contexto de cada resultado.
8. La plataforma presenta las coincidencias como evidencia espacial complementaria sin emitir un resultado de cumplimiento.

### Flujos alternativos
- FA-01 — Ningún control definido. Se informa que no hay referencias para analizar, sin declarar incumplimiento.
- FA-02 — Sin coincidencias. Se presenta resultado sin muestras coincidentes y la cobertura GPS utilizada.
- FA-03 — Sincronización posterior. El sistema puede actualizar coincidencias al recibir posiciones originales pendientes.
- FA-04 — Proximidad radial. Cuando se use distancia, se calcula en unidades adecuadas y conforme al radio autorizado.

### Excepciones
- E1. Referencia espacial inválida — Se excluye la evaluación afectada con indicación de inconsistencia.
- E2. Sin muestras suficientes — No se emite una conclusión sobre un recorrido no observado.
- E3. Diferencia de versión — No se comparan controles actuales con una jornada histórica si la asociación temporal no corresponde.
- E4. Acceso ajeno — Se deniega consulta no autorizada.

### Postcondiciones
- Éxito. Las coincidencias espaciales disponibles quedan consultables junto con el momento y la precisión de las posiciones que las sustentan.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-18-01  El control o geocerca no certifica actividad de recolección.
- RN-CU-18-02  La referencia espacial utilizada debe corresponder al contexto histórico aplicable.
- RN-CU-18-03  Se conservan coordenadas y precisión originales de GPS.
- RN-CU-18-04  No se inventan radios o tolerancias obligatorios sin decisión.
- RN-CU-18-05  Las coincidencias dependen de muestras disponibles, no del trazado interpolado como si fuera evidencia medida.

### Criterios de aceptación propuestos
- CA-CU-18-01  Se detecta una coincidencia para una posición dentro de una referencia válida.
- CA-CU-18-02  Se distingue una muestra fuera del control de la ausencia total de datos.
- CA-CU-18-03  La respuesta incluye referencia temporal de la posición usada.
- CA-CU-18-04  No se comparan controles de versión histórica incorrecta.
- CA-CU-18-05  Una coincidencia no registra cumplimiento automático.
- CA-CU-18-06  El cálculo funciona con coordenadas espaciales correctas.
- CA-CU-18-07  Un actor no autorizado no consulta los resultados.

### Dependencias funcionales
- CU-09 y CU-10: ruta y geocercas.
- CU-13 y CU-16: posiciones y trayectoria.
- CU-21: interpretación fiscalizadora.

### Decisiones pendientes y límites de definición
- Parámetros de distancia y tipos permitidos de geocerca.
- Autoridad que valida controles y momento en que una referencia se vuelve aplicable.
- Qué registros de coincidencia deben conservarse como evidencia histórica frente a resultados recalculables.

### Estado de definición y evidencia

Definición parcial; método geométrico y reglas de aplicación aún por concretar.

> Trazabilidad de reestructuración: CU-23 anterior.

## CU-19. Revisar desvíos y detenciones prolongadas

| ID | CU-19 |
| --- | --- |
| Nombre | Revisar desvíos y detenciones prolongadas |
| Paquete | Monitoreo GPS y trazabilidad |
| Prioridad | Media |
| Actores | Fiscalizador municipal / Ornato Público (A3) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Facilitar el examen de separaciones respecto de la ruta de referencia y periodos de baja movilidad registrados durante una jornada.

### Descripción

La plataforma identifica candidatos a desvío o detención con base en muestras disponibles y parámetros conocidos. El fiscalizador interpreta la evidencia junto a ruta, precisión, interrupciones e incidencias. No se concluye incumplimiento a partir de umbrales técnicos sin valoración humana.

### Precondiciones
1. El fiscalizador está autenticado y autorizado.
2. Existe jornada con RutaVersion válida y posiciones GPS suficientes para un análisis posible.
3. Los parámetros de tolerancia espacial y duración están definidos para el análisis que se solicita.

### Flujo principal
1. El fiscalizador selecciona la jornada y abre la revisión de desvíos y detenciones.
2. La plataforma recupera RutaVersion asignada y muestras GPS ordenadas temporalmente.
3. La plataforma presenta los parámetros utilizados para buscar candidatos.
4. La plataforma evalúa la distancia de las posiciones a la ruta y secuencias temporales potencialmente estacionarias.
5. La plataforma considera precisión y discontinuidades para no afirmar desplazamientos no observados.
6. La plataforma lista los eventos candidatos con inicio, fin, ubicación y magnitud calculada cuando sea posible.
7. El fiscalizador contrasta los candidatos con incidencias operativas y demás contexto.
8. La plataforma conserva o presenta el resultado como indicio para revisión, sin sanción ni dictamen automático.

### Flujos alternativos
- FA-01 — Sin candidatos. Se informa que no se identificaron casos bajo los parámetros dados, sin certificar ruta cumplida.
- FA-02 — Vehículo detenido por causa reportada. Se visualiza la incidencia relacionada para interpretación, sin descartar automáticamente la alerta.
- FA-03 — Trayectoria incompleta. Se expresa que el análisis cubre solo las muestras disponibles.
- FA-04 — Parámetros exploratorios. Los umbrales de prueba se etiquetan como técnicos/propuestos, no oficiales.

### Excepciones
- E1. Falta RutaVersion — No se calcula desvío respecto de una referencia inexistente.
- E2. Sin densidad temporal suficiente — No se declara una detención prolongada confirmada.
- E3. Parámetros inválidos — Se rechazan distancias o duraciones no válidas.
- E4. GPS poco preciso — Se advierte la incertidumbre y no se infiere un desvío con certeza injustificada.
- E5. Acceso no autorizado — Se impide consulta ajena.

### Postcondiciones
- Éxito. Se presentan candidatos a desviación o detención con parámetros explícitos y contexto temporal, sin emitir automáticamente un incumplimiento.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-19-01  Un desvío calculado no implica incumplimiento contractual.
- RN-CU-19-02  Una detención GPS del dispositivo no certifica detención física del camión.
- RN-CU-19-03  Las geometrías de referencia son las vigentes para la jornada histórica.
- RN-CU-19-04  Los valores umbral necesitan origen, unidad y justificación explícitos.
- RN-CU-19-05  Sin datos suficientes, el sistema debe poder declarar resultado indeterminado.
- RN-CU-19-06  El fiscalizador conserva la atribución de interpretar la situación.

### Criterios de aceptación propuestos
- CA-CU-19-01  Se calculan candidatos a desvío frente a la versión histórica correcta cuando hay datos suficientes.
- CA-CU-19-02  Un umbral configurado controla la selección de candidatos de forma reproducible.
- CA-CU-19-03  Un conjunto sin posiciones suficientes no produce conclusiones falsas.
- CA-CU-19-04  Se muestran las unidades y parámetros del cálculo.
- CA-CU-19-05  Las causas operativas registradas son visibles para contraste.
- CA-CU-19-06  Las muestras de baja precisión no se consideran certeza sin advertencia.
- CA-CU-19-07  No se generan sanciones ni resultados oficiales automáticamente.
- CA-CU-19-08  El análisis no expone jornadas no autorizadas.

### Dependencias funcionales
- CU-09: ruta de referencia.
- CU-14: incidencias de contexto.
- CU-16 y CU-17: GPS y calidad de muestras.
- CU-21: valoración municipal.

### Decisiones pendientes y límites de definición
- PENDING: distancias máximas, duraciones mínimas y criterio para analizar detenciones.
- Tratamiento de segmentos sin GPS, ruido y consecuencias operativas oficiales.
- Si se admite parametrización técnica por configuración sin convertirla en regla contractual.

### Estado de definición y evidencia

Definición parcial crítica: no existe política aprobada de umbrales/interpretación.

> Trazabilidad de reestructuración: CU-24 anterior.

PAQUETE 5  •  FISCALIZACIÓN

## CU-20. Registrar inspección y evidencias de campo

| ID | CU-20 |
| --- | --- |
| Nombre | Registrar inspección y evidencias de campo |
| Paquete | Fiscalización |
| Prioridad | Media |
| Actores | Fiscalizador municipal / Ornato Público (A3) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Documentar verificaciones municipales realizadas en terreno, vinculando observaciones y evidencias con una jornada o contexto de servicio.

### Descripción

El fiscalizador registra fecha, lugar, observaciones y material disponible. Se integran inspección y evidencias de campo como un mismo objetivo funcional, pero se mantiene la identidad de los distintos soportes y su procedencia.

### Precondiciones
1. El fiscalizador está autenticado y habilitado para registrar inspecciones.
2. Existe jornada o contexto institucional al que asociar el acta/registro, según la política aprobada.
3. La plataforma dispone de los medios de almacenamiento para la evidencia permitida.

### Flujo principal
1. El fiscalizador accede a la opción de registrar inspección.
2. La plataforma presenta jornadas o contextos institucionales autorizados.
3. El fiscalizador selecciona el contexto y registra fecha/hora, ubicación y observaciones de campo.
4. La plataforma permite incorporar fotografías u otros soportes únicamente de los tipos habilitados.
5. El fiscalizador identifica el origen y describe los elementos adjuntos.
6. La plataforma valida campos requeridos, archivos, contexto y permisos.
7. El fiscalizador revisa la información y confirma su registro.
8. La plataforma guarda inspección y metadatos de evidencias de manera consistente.
9. La inspección y sus soportes quedan disponibles como antecedente para CU-21.

### Flujos alternativos
- FA-01 — Sin fotografías. La inspección se registra solo con observaciones si los requisitos mínimos lo permiten.
- FA-02 — Inspección vinculada a incidencia/reclamo. Se conserva la referencia adicional a la situación observada cuando existe contexto comprobable.
- FA-03 — Evidencia incorporada después. Si el procedimiento institucional lo autoriza, se agrega evidencia nueva como anexo fechado sin sobrescribir la evidencia original.

### Excepciones
- E1. Jornada o contexto inexistente — La asociación inválida se rechaza.
- E2. Archivo no permitido o demasiado grande — No se admite el anexo y se informa la restricción.
- E3. Información mínima incompleta — Se solicita completar datos.
- E4. Fallo de almacenamiento — No se declara registrada una evidencia cuyo archivo no pudo conservarse.
- E5. Actor no autorizado — Se deniega el registro.

### Postcondiciones
- Éxito. La inspección y sus evidencias admisibles quedan registradas y vinculadas con el antecedente correspondiente, identificando su autor y momento.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-20-01  La inspección es observación de campo, diferente del resultado oficial de fiscalización.
- RN-CU-20-02  La evidencia conserva su autor y referencia temporal.
- RN-CU-20-03  No se modifica retroactivamente una evidencia como si nunca hubiera existido.
- RN-CU-20-04  La fotografía no determina por sí sola cumplimiento o incumplimiento.
- RN-CU-20-05  Tipos, retención y privacidad de archivos requieren política institucional.
- RN-CU-20-06  La vinculación con jornada debe reflejar un antecedente real, no una atribución inventada.

### Criterios de aceptación propuestos
- CA-CU-20-01  Se registra una inspección con jornada/contexto válido.
- CA-CU-20-02  Se conserva la fecha, el autor y la observación original.
- CA-CU-20-03  Los archivos permitidos quedan asociados de manera recuperable.
- CA-CU-20-04  Un archivo rechazado no genera evidencia ficticia.
- CA-CU-20-05  Una inspección sin fotografías se maneja conforme a datos mínimos aprobados.
- CA-CU-20-06  No se reemplaza destructivamente una evidencia histórica.
- CA-CU-20-07  No se registra automáticamente un resultado de fiscalización.
- CA-CU-20-08  Solo el fiscalizador autorizado puede registrar la inspección.

### Dependencias funcionales
- CU-13: jornada de origen.
- CU-14: incidencias relacionadas.
- CU-21: fiscalización que consulta inspección.
- CU-27: reclamo eventualmente relacionado.

### Decisiones pendientes y límites de definición
- PENDING-09: formatos, tamaño, retención, almacenamiento y política de acceso a fotografías.
- Obligatoriedad de coordenadas y necesidad de registrar inspección sin jornada identificada.
- Procedimiento de ampliación/corrección de una inspección ya registrada.

### Estado de definición y evidencia

Definición parcial; fuentes sustentan el flujo, política de evidencia pendiente.

> Trazabilidad de reestructuración: CU-25 y CU-26 anteriores integrados.

## CU-21. Fiscalizar jornada y registrar resultado

| ID | CU-21 |
| --- | --- |
| Nombre | Fiscalizar jornada y registrar resultado |
| Paquete | Fiscalización |
| Prioridad | Alta |
| Actores | Fiscalizador municipal / Ornato Público (A3) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Permitir que el fiscalizador municipal registre su valoración de una jornada basándose en evidencia disponible, sin delegar al sistema una decisión de cumplimiento.

### Descripción

Este es el núcleo institucional de la plataforma. El fiscalizador examina programación, asignación, GPS, incidencias, reclamos e inspecciones y emite el resultado que establezca el procedimiento del GAMW. La interfaz ayuda a correlacionar datos sin transformar automáticamente alertas o trazas en sanciones.

### Precondiciones
1. El fiscalizador está autenticado y autorizado a determinar resultados municipales.
2. Existe la jornada que será evaluada.
3. Se dispone de los antecedentes disponibles y de la taxonomía institucional de resultados para guardar una decisión final.

### Flujo principal
1. El fiscalizador selecciona la jornada que desea fiscalizar.
2. La plataforma presenta periodo, empresa, versión de ruta y recursos históricos correspondientes.
3. La plataforma recupera INICIO/FIN, posiciones GPS, continuidad y otras evidencias disponibles.
4. El fiscalizador consulta incidencias, inspecciones, reclamos y acciones relacionadas.
5. La plataforma distingue registros confirmados de información ausente o todavía pendiente de sincronización.
6. El fiscalizador analiza los antecedentes sin que la plataforma declare automáticamente cumplimiento.
7. El fiscalizador selecciona un resultado del catálogo institucional aprobado y redacta observaciones cuando corresponda.
8. La plataforma valida su autorización, campos mínimos y vínculo con jornada.
9. El fiscalizador confirma la determinación.
10. La plataforma registra autor, resultado, fecha/hora y referencias de evidencia conservadas para el historial institucional.

### Flujos alternativos
- FA-01 — Revisión sin dictamen. El fiscalizador consulta información y decide continuar la revisión sin registrar resultado definitivo.
- FA-02 — Evidencia insuficiente. Se representa explícitamente la falta de información; cualquier decisión de resultado se rige por el procedimiento municipal, no por un valor inventado.
- FA-03 — Información tardía. Si llegan posiciones después de una revisión, la plataforma conserva las evidencias originales y debe permitir identificar posteriores incorporaciones sin modificar silenciosamente la determinación previa.

### Excepciones
- E1. Jornada no encontrada — No se registra fiscalización sin objeto válido.
- E2. Resultado no aprobado — La plataforma no acepta valores inventados que no pertenezcan al catálogo habilitado.
- E3. Falta de autorización fiscalizadora — Se deniega la operación, aunque el usuario tenga JWT válido.
- E4. Datos obligatorios incompletos — Se solicita completar la fundamentación que se haya definido.
- E5. Fallo de guardado — La determinación no se comunica como registrada.
- E6. Decisión duplicada/conflictiva — No se reemplaza silenciosamente una decisión previa; revisión y rectificación requieren procedimiento definido.

### Postcondiciones
- Éxito. El resultado municipal aprobado queda asociado con la jornada, el fiscalizador y las evidencias consultadas sin borrar registros anteriores.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-21-01  El GPS es evidencia y no prueba automática de recolección.
- RN-CU-21-02  Solo el fiscalizador municipal autorizado determina el resultado.
- RN-CU-21-03  El resultado y su autor deben quedar relacionados con la jornada y sus antecedentes.
- RN-CU-21-04  No se crean sanciones, multas o responsabilidades contractuales automáticas.
- RN-CU-21-05  La plataforma debe distinguir ausencia de evidencia de evidencia negativa.
- RN-CU-21-06  No se reescriben decisiones previas de forma destructiva; cualquier corrección requiere trazabilidad.

### Criterios de aceptación propuestos
- CA-CU-21-01  Un fiscalizador autorizado consulta el conjunto disponible de evidencias de una jornada.
- CA-CU-21-02  Un JWT de conductor o supervisor no habilita emitir resultado municipal.
- CA-CU-21-03  No se crea un resultado por el simple registro de INICIO/FIN o GPS.
- CA-CU-21-04  La determinación se vincula con la jornada y usuario fiscalizador.
- CA-CU-21-05  Los datos originales permanecen disponibles después de registrar el resultado.
- CA-CU-21-06  Se rechaza un valor de resultado no reconocido por el catálogo vigente.
- CA-CU-21-07  La ausencia de GPS se muestra como dato faltante, no como incumplimiento automático.
- CA-CU-21-08  Las rectificaciones posteriores no eliminan sin rastro el resultado anterior.

### Dependencias funcionales
- CU-11 a CU-13: programación y ejecución.
- CU-16 a CU-20: trayectorias, anomalías e inspecciones.
- CU-22 y CU-23: actuaciones posteriores.
- CU-27: reclamos asociados.
- CU-29: conservación histórica.

### Decisiones pendientes y límites de definición
- PENDING-05: categorías oficiales de resultado de fiscalización.
- Competencia específica del fiscalizador y reglas de emisión, revisión o corrección de resultados.
- Criterios oficiales de aceptación, observación y evidencias mínimas; no inferirlos del GPS.

### Estado de definición y evidencia

Definición parcial y prioritaria; todavía falta decisión institucional esencial.

> Trazabilidad de reestructuración: CU-27 anterior.

PAQUETE 6  •  INCIDENCIAS Y ACCIONES CORRECTIVAS

## CU-22. Gestionar acciones correctivas

| ID | CU-22 |
| --- | --- |
| Nombre | Gestionar acciones correctivas |
| Paquete | Incidencias y acciones correctivas |
| Prioridad | Media |
| Actores | Fiscalizador municipal / Ornato Público (A3), Supervisor de empresa operadora (A4) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Registrar una solicitud municipal de corrección y la atención comunicada por la empresa operadora, conservando sus antecedentes.

### Descripción

El fiscalizador inicia una acción correctiva sustentada en una observación, inspección, reclamo u otro antecedente válido. El supervisor empresarial consulta la acción dentro de su ámbito y registra la atención realizada. La verificación municipal del resultado es una meta independiente en CU-23.

### Precondiciones
1. El fiscalizador está autenticado y autorizado para registrar una acción correctiva.
2. Existe un antecedente institucional identificable que justifica la actuación.
3. La empresa a la que se dirige la actuación está determinada por el contexto autorizado.

### Flujo principal
1. El fiscalizador selecciona el antecedente que requiere una actuación correctiva.
2. La plataforma presenta la jornada, inspección, reclamo u observación relacionada.
3. El fiscalizador describe la corrección requerida con los datos institucionalmente exigidos.
4. La plataforma valida referencias y guarda la acción con autor y fecha.
5. El supervisor de empresa consulta las acciones de su ámbito autorizado.
6. El supervisor selecciona una acción y registra la respuesta o atención realizada, con evidencia cuando corresponda.
7. La plataforma valida la información y conserva su procedencia y momento de registro.
8. La plataforma presenta el historial de la acción sin sustituir las actuaciones anteriores.
9. La acción queda disponible para revisión y verificación por CU-23.

### Flujos alternativos
- FA-01 — Atención no disponible. El fiscalizador registra la acción y permanece pendiente de respuesta sin inventar un plazo contractual.
- FA-02 — Respuesta con evidencia. El supervisor incorpora descripción y soporte autorizado que se agrega al historial.
- FA-03 — Consulta de actuaciones. Cada actor visualiza el historial permitido sin alterar versiones previas.

### Excepciones
- E1. Antecedente inexistente — La acción no se vincula con un objeto inválido.
- E2. Empresa ajena — Un supervisor no consulta ni responde una acción fuera de su ámbito.
- E3. Información insuficiente — No se registra una acción sin los campos mínimos aprobados.
- E4. Documento no permitido — Se rechaza evidencia inválida sin borrar las actuaciones anteriores.
- E5. Error de guardado — Se mantiene intacto el historial previo.

### Postcondiciones
- Éxito. La acción correctiva, su origen y las atenciones empresariales quedan conservados como actuaciones trazables susceptibles de verificación.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-22-01  Toda acción correctiva tiene origen trazable.
- RN-CU-22-02  La atención de la empresa no equivale a verificación municipal.
- RN-CU-22-03  La plataforma no aplica multas ni sanciones automáticamente.
- RN-CU-22-04  Las respuestas y observaciones posteriores se agregan al historial.
- RN-CU-22-05  Los estados y plazos del procedimiento requieren decisión institucional.
- RN-CU-22-06  La información permanece bajo control histórico del GAMW.

### Criterios de aceptación propuestos
- CA-CU-22-01  El fiscalizador registra una acción asociada a antecedente válido.
- CA-CU-22-02  Se conserva la identidad de quien solicitó la corrección.
- CA-CU-22-03  La empresa ve solamente acciones de su ámbito.
- CA-CU-22-04  El supervisor registra atención sin sustituir la solicitud original.
- CA-CU-22-05  La acción queda disponible para CU-23.
- CA-CU-22-06  No se marca atendida y verificada automáticamente por la respuesta empresarial.
- CA-CU-22-07  El rechazo de un archivo no destruye la acción.
- CA-CU-22-08  No se generan sanciones automáticas.

### Dependencias funcionales
- CU-14 y CU-20/CU-21: posibles antecedentes.
- CU-27: reclamo validado relacionado.
- CU-23: verificación de atención.
- CU-29: historial institucional.

### Decisiones pendientes y límites de definición
- PENDING-07: estados, plazos y procedimientos oficiales de acciones correctivas.
- Autoridad para crear/editar actuaciones, política de notificación y evidencias exigidas.
- Regla sobre corrección o cancelación de una acción registrada por error.

### Estado de definición y evidencia

Definición parcial; el ciclo está explicado, el procedimiento institucional final no.

> Trazabilidad de reestructuración: CU-28 anterior.

## CU-23. Verificar atención de acciones correctivas

| ID | CU-23 |
| --- | --- |
| Nombre | Verificar atención de acciones correctivas |
| Paquete | Incidencias y acciones correctivas |
| Prioridad | Media |
| Actores | Fiscalizador municipal / Ornato Público (A3) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Permitir al fiscalizador comprobar y registrar la valoración municipal de la respuesta que una empresa dio a una acción correctiva.

### Descripción

El fiscalizador revisa antecedente, solicitud y respuesta empresarial, contrasta evidencia y registra una verificación humana. La plataforma preserva la distinción entre atención comunicada y atención verificada.

### Precondiciones
1. El fiscalizador está autenticado y habilitado para verificar actuaciones.
2. Existe una acción correctiva con atención o información de respuesta disponible para revisar.

### Flujo principal
1. El fiscalizador accede a las acciones que requieren verificación.
2. La plataforma muestra las actuaciones y su contexto histórico.
3. El fiscalizador selecciona una acción correctiva.
4. La plataforma presenta la solicitud original, la empresa y las respuestas con evidencia.
5. El fiscalizador revisa el contenido y, cuando corresponda, contrasta con inspecciones o antecedentes nuevos.
6. El fiscalizador registra el resultado de verificación permitido por el procedimiento institucional y sus observaciones.
7. La plataforma valida autorización, vínculo e información obligatoria.
8. El fiscalizador confirma la verificación.
9. La plataforma agrega el registro de verificación, conserva el historial y actualiza el seguimiento solo según las reglas aprobadas.

### Flujos alternativos
- FA-01 — No satisface la revisión. El fiscalizador registra observaciones y se mantiene el seguimiento según estados aprobados; no se impone automáticamente sanción.
- FA-02 — Requiere inspección. El fiscalizador puede recurrir a CU-20 antes de emitir resultado.
- FA-03 — Evidencia insuficiente. Puede posponer la determinación si el procedimiento lo permite, conservando el estado anterior.

### Excepciones
- E1. Acción inexistente — No se registra verificación sin objeto válido.
- E2. Sin atención registrable — Se indica que todavía no existe respuesta susceptible de verificar.
- E3. Resultado no permitido — No se acepta una categoría de verificación inexistente.
- E4. Actor no autorizado — El supervisor no determina el resultado municipal.
- E5. Error transaccional — No se pierde la atención previa ni se confirma verificación incompleta.

### Postcondiciones
- Éxito. La verificación municipal y sus observaciones quedan vinculadas a la acción correctiva, preservando la atención empresarial previa.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-23-01  La verificación municipal es diferente de la respuesta del supervisor.
- RN-CU-23-02  Una acción no se cierra automáticamente por adjuntar evidencia empresarial.
- RN-CU-23-03  Cada verificación conserva autor, momento y relación con la solicitud original.
- RN-CU-23-04  No se elimina una evaluación anterior sin registrar una rectificación.
- RN-CU-23-05  Los resultados y transiciones deben proceder de una política de estados aprobada.

### Criterios de aceptación propuestos
- CA-CU-23-01  El fiscalizador consulta solicitud y respuestas históricas de una acción.
- CA-CU-23-02  Solo un usuario municipal autorizado puede registrar verificación.
- CA-CU-23-03  No se permite registrar un resultado inexistente en el catálogo vigente.
- CA-CU-23-04  No se declara atención validada solo porque la empresa dijo atenderla.
- CA-CU-23-05  Las verificaciones quedan asociadas a su autor y fecha.
- CA-CU-23-06  Una falla de guardado preserva el estado previo.
- CA-CU-23-07  La acción mantiene historia después de una eventual verificación final.

### Dependencias funcionales
- CU-22: acción y atención empresarial.
- CU-20: inspección complementaria.
- CU-21: fiscalización antecedente.
- CU-29: historial de actuaciones.

### Decisiones pendientes y límites de definición
- PENDING-07: catálogo de resultados, reaperturas, transiciones y criterios de cierre.
- Obligatoriedad de inspección de campo o plazo de verificación.
- Política de corrección de un resultado de verificación.

### Estado de definición y evidencia

Definición parcial, con objetivo y separación de actores claros.

> Trazabilidad de reestructuración: CU-29 anterior.

## CU-24. Reprogramar jornada conservando el antecedente

| ID | CU-24 |
| --- | --- |
| Nombre | Reprogramar jornada conservando el antecedente |
| Paquete | Incidencias y acciones correctivas |
| Prioridad | Media |
| Actores | Supervisor de empresa operadora (A4) |
| Actor iniciador | Supervisor de empresa operadora (A4) |


### Propósito

Permitir crear una nueva programación cuando cambien las condiciones operativas sin sustituir ni eliminar el plan original.

### Descripción

La reprogramación genera una Jornada sucesora vinculada con su antecedente y motivo. Conserva periodo, referencia histórica de ruta y asignaciones conforme a las reglas implementadas, evitando que una actualización destructiva borre el plan anterior.

### Precondiciones
1. El supervisor está autenticado y autorizado para la jornada.
2. Existe una jornada registrada susceptible de reprogramación.
3. Se dispone del motivo de cambio y de nuevos datos coherentes con el contexto de servicio.

### Flujo principal
1. El supervisor selecciona una jornada que necesita nueva programación.
2. La plataforma muestra el plan anterior con periodo, versión de ruta, fecha y asignaciones.
3. El supervisor solicita reprogramarla.
4. La plataforma muestra los datos que pueden modificarse y requiere un motivo.
5. El supervisor introduce fecha/horario y referencias permitidas.
6. La plataforma comprueba la elegibilidad del antecedente y coherencia del nuevo plan.
7. El supervisor confirma la reprogramación.
8. La plataforma registra una Jornada sucesora vinculada con la antecedente y conserva el motivo.
9. La plataforma conserva la jornada original y su historial de asignaciones.
10. La plataforma presenta ambas jornadas y la relación sucesora/antecedente.

### Flujos alternativos
- FA-01 — Cadena de reprogramaciones. Una jornada sucesora puede convertirse en antecedente de otra, manteniendo cadena histórica sin sobrescritura.
- FA-02 — Recursos existentes. La copia o nueva asignación de recursos sigue las reglas actuales; no se presume transferencia automática de una ejecución iniciada.
- FA-03 — Consulta. El usuario examina causa, fecha y origen de cada cambio sin reabrir el registro anterior.

### Excepciones
- E1. Jornada inexistente — Se rechaza la solicitud.
- E2. Motivo omitido — No se crea sucesora sin motivo requerido.
- E3. Antecedente no elegible — Se impide reprogramación incompatible con restricciones ya establecidas.
- E4. Concurrencia — No se permiten dos sucesoras contradictorias a partir del mismo antecedente cuando infringen el modelo de historia.
- E5. Fallo de transacción — No queda la antigua jornada invalidada si la sucesora no se pudo crear.
- E6. Permiso insuficiente — Se deniega el cambio sobre jornada ajena.

### Postcondiciones
- Éxito. Se crea una Jornada sucesora relacionada con el antecedente y motivo, conservando las programaciones y asignaciones históricas.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-24-01  Reprogramar es crear una jornada nueva, no sobrescribir la anterior.
- RN-CU-24-02  Se conserva antecedente, motivo y relación temporal.
- RN-CU-24-03  La jornada anterior y su evidencia INICIO/FIN/GPS no se reasignan a la sucesora.
- RN-CU-24-04  Las asignaciones históricas conservan vínculos originales.
- RN-CU-24-05  Las restricciones de concurrencia deben cumplirse también en base de datos.
- RN-CU-24-06  La autorización empresarial exacta depende de política institucional.

### Criterios de aceptación propuestos
- CA-CU-24-01  La reprogramación crea una Jornada con referencia a su antecedente.
- CA-CU-24-02  El antecedente original permanece recuperable e íntegro.
- CA-CU-24-03  El motivo no puede estar ausente cuando es obligatorio.
- CA-CU-24-04  Una cadena de varias reprogramaciones conserva todos los eslabones.
- CA-CU-24-05  No se trasladan retrospectivamente evidencias GPS de jornada original.
- CA-CU-24-06  Una operación concurrente no genera sucesoras incompatibles.
- CA-CU-24-07  Un fallo completo revierte cambios parciales.
- CA-CU-24-08  El usuario no autorizado no reprograma una jornada ajena.

### Dependencias funcionales
- CU-11: programación inicial.
- CU-12: asignaciones.
- CU-13: evidencia no transferible de ejecución.
- CU-29: historial institucional.

### Decisiones pendientes y límites de definición
- D1: permisos y alcance de A4 para reprogramar.
- Política institucional para reprogramar una jornada ya iniciada o fiscalizada, no inventar aprobaciones automáticas.

### Estado de definición y evidencia

Núcleo funcional implementado y verificado automáticamente según último informe; CU global condicionado por autorización.

> Trazabilidad de reestructuración: CU-30 anterior.

PAQUETE 7  •  RECLAMOS Y PUNTOS CRÍTICOS

## CU-25. Presentar reporte ciudadano

| ID | CU-25 |
| --- | --- |
| Nombre | Presentar reporte ciudadano |
| Paquete | Reclamos y puntos críticos |
| Prioridad | Media |
| Actores | Ciudadano / reportante (actor externo) (A6) |
| Actor iniciador | Ciudadano / reportante (actor externo) (A6) |


### Propósito

Permitir a una persona externa informar una situación relacionada con la prestación del servicio mediante un formulario público, sin requerir una cuenta institucional.

### Descripción

El ciudadano presenta descripción y ubicación del hecho, y puede adjuntar fotografía y contacto si la política habilita dichos datos. El sistema guarda un reporte pendiente de revisión municipal; no lo convierte automáticamente en reclamo validado, microbasural ni incumplimiento.

### Precondiciones
1. El formulario público se encuentra accesible mediante la interfaz prevista.
2. Los campos de captura y la política de privacidad de datos de contacto y archivos están definidos para el piloto.
3. El ciudadano no necesita Usuario institucional.

### Flujo principal
1. El ciudadano ingresa al formulario público de reportes.
2. La plataforma explica el propósito del canal, los datos solicitados y el tratamiento de privacidad aplicable.
3. El ciudadano selecciona o describe el problema, e indica una ubicación mediante mapa o mecanismo accesible.
4. El ciudadano incorpora una descripción del hecho y, si está habilitado, una fotografía o medio de contacto opcional.
5. La plataforma verifica campos, formatos, tamaño de archivos y controles contra envíos automatizados.
6. El ciudadano confirma el envío.
7. La plataforma registra el reporte con fecha/hora de recepción y ubicación indicada, sin considerarlo evidencia concluyente.
8. La plataforma genera una referencia o código de recepción cuando así se haya definido.
9. La plataforma confirma la recepción e informa que el reporte será revisado por personal municipal.

### Flujos alternativos
- FA-01 — Sin fotografía. El reporte se acepta con datos mínimos suficientes cuando la foto es opcional.
- FA-02 — Sin contacto. La persona puede enviar datos sin identificarse cuando la política de privacidad lo permita.
- FA-03 — Posible duplicado. Se informa o agrupa el candidato conforme a las reglas técnicas, sin borrar automáticamente una denuncia legítima.
- FA-04 — Ubicación incierta. El ciudadano puede ajustar el marcador; si no existe ubicación válida, se aplica el tratamiento mínimo aprobado.

### Excepciones
- E1. Campos indispensables vacíos — No se recibe un reporte incompleto como válido.
- E2. Archivo dañino/no permitido — Se rechaza el archivo y se mantiene el resto del formulario cuando sea seguro.
- E3. Límite de envíos — La plataforma frena envíos sospechosos de acuerdo con límites técnicos definidos.
- E4. Fallo de mapa externo — No se expone información privada; se usa alternativa de ubicación si está prevista.
- E5. Error de recepción — No se muestra código de recepción si el reporte no quedó guardado.

### Postcondiciones
- Éxito. El reporte ciudadano queda recibido y pendiente de revisión con su procedencia y datos admitidos, sin certificación automática del hecho.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-25-01  El ciudadano es actor externo, no usuario de la aplicación institucional privada.
- RN-CU-25-02  La recepción no implica validación ni prueba de incumplimiento.
- RN-CU-25-03  La información de ubicación y fotografía puede ser inexacta o manipulada.
- RN-CU-25-04  No se debe exponer información de jornadas, vehículos o fiscalizaciones en la interfaz pública.
- RN-CU-25-05  Se aplican controles de seguridad y privacidad proporcionados por la política aprobada.
- RN-CU-25-06  Los reportes se conservan para revisión sin crear automáticamente un punto crítico oficial.

### Criterios de aceptación propuestos
- CA-CU-25-01  Se recibe un reporte público sin exigir cuenta GAMW.
- CA-CU-25-02  La plataforma registra descripción y ubicación válida conforme a campos mínimos.
- CA-CU-25-03  Una foto opcional puede omitirse.
- CA-CU-25-04  Un archivo no permitido se rechaza.
- CA-CU-25-05  Los mecanismos anti-spam limitan envíos automatizados sin abrir APIs internas.
- CA-CU-25-06  El ciudadano no puede consultar información institucional privada.
- CA-CU-25-07  El reporte queda pendiente de validación municipal, no como incumplimiento confirmado.
- CA-CU-25-08  Un fallo de persistencia no genera una confirmación de recepción falsa.

### Dependencias funcionales
- CU-27: validación municipal del reporte.
- CU-28: posible punto crítico tras validación.
- CU-08: referencia territorial disponible, no obligatoria para recibir datos.

### Decisiones pendientes y límites de definición
- PENDING-08: política de privacidad y datos de contacto.
- PENDING-09: almacenamiento y retención de imágenes.
- Canal oficial y aceptación institucional del formulario público (función aprobada por PO para prototipo, no constatada como obligación GAMW).
- Política de confirmación/código, límites anti-spam y tratamiento de reportes sin precisión de ubicación.

### Estado de definición y evidencia

Función adoptada en baseline PO, pero su definición institucional y su implementación están pendientes.

> Trazabilidad de reestructuración: Nuevo CU; no existía como actor ciudadano en los 37 CU originales. La incorporación de A6 mediante formulario público fue una decisión expresa del PO para el prototipo.

## CU-26. Registrar reclamo recibido por canal institucional

| ID | CU-26 |
| --- | --- |
| Nombre | Registrar reclamo recibido por canal institucional |
| Paquete | Reclamos y puntos críticos |
| Prioridad | Media |
| Actores | Fiscalizador municipal / Ornato Público (A3) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Documentar en el sistema los reclamos que llegan al GAMW por WhatsApp, llamadas, atención presencial u otros canales ajenos al formulario público.

### Descripción

El fiscalizador registra un reclamo recibido y su procedencia, conservando la descripción y su contexto territorial o de jornada cuando la asociación pueda sustentarse. No se exige que todo reclamante utilice el nuevo portal público.

### Precondiciones
1. El fiscalizador está autenticado y autorizado.
2. Existe información mínima de un reclamo recibido por un canal institucional.
3. El procedimiento de registro identifica los datos indispensables sin inventar su origen.

### Flujo principal
1. El fiscalizador accede al registro institucional de reclamos.
2. La plataforma solicita canal de recepción, descripción y datos disponibles del hecho.
3. El fiscalizador transcribe la información recibida sin atribuir al ciudadano una validación que no ha ocurrido.
4. Si existe referencia territorial o de jornada comprobable, el fiscalizador la relaciona con el reclamo.
5. La plataforma valida campos mínimos, autor y referencias ingresadas.
6. El fiscalizador revisa el registro y confirma.
7. La plataforma conserva la información de ingreso con fecha de registro y canal declarado.
8. El reclamo queda disponible para evaluación posterior mediante CU-27.

### Flujos alternativos
- FA-01 — Sin jornada identificada. Se registra el reclamo con su descripción y territorio conocido, sin inventar una jornada relacionada.
- FA-02 — Contacto no proporcionado. Se conserva información disponible cuando el contacto es opcional según política.
- FA-03 — Reporte ya recibido por portal. Si se identifica que el mismo hecho llegó por dos canales, se registra o vincula el antecedente según procedimiento, sin borrar evidencia original.

### Excepciones
- E1. Datos mínimos insuficientes — Se solicita completar lo posible o se documenta la limitación conforme al procedimiento.
- E2. Referencia incorrecta — No se asocia reclamo con jornada inexistente.
- E3. Falta de autorización — Se impide registro por actor no permitido.
- E4. Error transaccional — No se confirma ingreso incompleto.
- E5. Archivo de respaldo inválido — Se rechaza un anexo no permitido sin perder información principal conservada.

### Postcondiciones
- Éxito. El reclamo institucional queda registrado con su canal de recepción y antecedentes disponibles, listo para evaluación posterior.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-26-01  El reporte recibido no prueba por sí solo incumplimiento del operador.
- RN-CU-26-02  El reclamo conserva procedencia y fecha de registro.
- RN-CU-26-03  No se crea un Usuario para el ciudadano que llama o escribe.
- RN-CU-26-04  La ausencia de relación con jornada no autoriza a inventarla.
- RN-CU-26-05  Se conserva trazabilidad del canal original y actuaciones posteriores.

### Criterios de aceptación propuestos
- CA-CU-26-01  Un fiscalizador registra reclamo proveniente de un canal institucional.
- CA-CU-26-02  Se conserva descripción y canal de procedencia.
- CA-CU-26-03  El registro puede existir sin jornada vinculada cuando la información no permite identificarla.
- CA-CU-26-04  Un reclamo no se declara válido automáticamente al registrarlo.
- CA-CU-26-05  No se crea una cuenta interna para el reclamante.
- CA-CU-26-06  No se acepta una asociación con jornada inexistente.
- CA-CU-26-07  El actor no autorizado no registra ni consulta indebidamente el reclamo.

### Dependencias funcionales
- CU-25: reportes públicos, canal diferente.
- CU-27: validación y gestión.
- CU-28: punto crítico eventual, con validación municipal.

### Decisiones pendientes y límites de definición
- Canales institucionales definitivos y datos mínimos obligatorios.
- Política de datos personales y evidencia de mensajes o llamadas.
- Tratamiento de casos duplicados entre reporte público y reclamo ingresado.

### Estado de definición y evidencia

Definición parcial; se sustenta directamente en el procedimiento relatado en entrevista.

> Trazabilidad de reestructuración: CU-31 anterior, ajustado a canal institucional.

## CU-27. Validar y gestionar reclamos

| ID | CU-27 |
| --- | --- |
| Nombre | Validar y gestionar reclamos |
| Paquete | Reclamos y puntos críticos |
| Prioridad | Media |
| Actores | Fiscalizador municipal / Ornato Público (A3), Supervisor de empresa operadora (A4) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Permitir que el fiscalizador determine la pertinencia de un reporte/reclamo y dé seguimiento a la respuesta de la empresa operadora cuando corresponda.

### Descripción

El fiscalizador revisa origen, ubicación, jornada y evidencia, registra una decisión de tratamiento y, si procede, vincula a la empresa autorizada. El supervisor aporta respuesta; la verificación y eventual cierre permanecen en el ámbito municipal.

### Precondiciones
1. El fiscalizador está autenticado y autorizado.
2. Existe un reporte ciudadano o reclamo registrado que requiere revisión.
3. La empresa destinataria puede identificarse de forma justificada cuando se decida canalizar la actuación.

### Flujo principal
1. El fiscalizador accede a las entradas pendientes de revisión.
2. La plataforma presenta reporte/reclamo, procedencia, ubicación, anexos y antecedentes relacionados.
3. El fiscalizador contrasta la información con territorio, rutas, jornadas e inspecciones disponibles.
4. El fiscalizador registra la valoración de pertinencia o necesidad de mayor información.
5. Si corresponde gestión con empresa, selecciona destinatario y registra la actuación.
6. La plataforma limita la información visible al supervisor empresarial autorizado.
7. El supervisor registra su atención o respuesta con los antecedentes disponibles.
8. El fiscalizador consulta la respuesta y, si es necesario, solicita una inspección complementaria.
9. El fiscalizador registra la verificación y situación de seguimiento conforme al procedimiento aprobado.
10. La plataforma conserva la secuencia de actuaciones sin convertir automáticamente el reporte en incumplimiento.

### Flujos alternativos
- FA-01 — Reporte no relacionado con el servicio. El fiscalizador registra resultado de valoración según catálogo aprobado, preservando el antecedente.
- FA-02 — Falta contexto. El reporte permanece para revisión y no se asocia artificialmente con jornada.
- FA-03 — Empresa responde parcialmente. Se conserva la respuesta y el fiscalizador mantiene seguimiento si el procedimiento lo admite.
- FA-04 — Reclamo con punto crítico. Puede enlazarse con CU-28 tras validación municipal, sin clasificación automática.

### Excepciones
- E1. Reporte inexistente — No se crea actuación sin origen válido.
- E2. Empresa no identificada — No se remite un reclamo a una empresa arbitraria.
- E3. Permiso insuficiente — A4 no accede a reclamos de otra empresa ni determina su validez municipal.
- E4. Resultado no definido — No se guarda un estado/categoría inventado.
- E5. Respuesta fallida — El historial existente permanece intacto y el registro de atención no se confirma falsamente.

### Postcondiciones
- Éxito. Las decisiones de revisión y las respuestas de la empresa quedan trazadas sin atribuir cumplimiento o veracidad automática al reclamo.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-27-01  La validación de reclamos corresponde al fiscalizador municipal.
- RN-CU-27-02  Una queja recibida no es automáticamente incumplimiento probado.
- RN-CU-27-03  La empresa responde dentro de su ámbito autorizado; no valida su propia actuación en nombre del GAMW.
- RN-CU-27-04  La procedencia pública o institucional debe conservarse.
- RN-CU-27-05  No se crean plazos/SLA sin respaldo institucional.
- RN-CU-27-06  Las decisiones y respuestas se conservan históricamente.

### Criterios de aceptación propuestos
- CA-CU-27-01  El fiscalizador puede consultar una entrada ciudadana o institucional.
- CA-CU-27-02  Se conserva el origen y la fecha del reporte original.
- CA-CU-27-03  Se registra una valoración humana y su autor.
- CA-CU-27-04  No se atribuye empresa o jornada sin evidencia que justifique la relación.
- CA-CU-27-05  El supervisor solo ve los casos de su empresa autorizada.
- CA-CU-27-06  La respuesta empresarial no cierra automáticamente la revisión municipal.
- CA-CU-27-07  No se determina incumplimiento por mera denuncia o fotografía.
- CA-CU-27-08  Se conserva el historial de todas las actuaciones registradas.

### Dependencias funcionales
- CU-25 y CU-26: entradas.
- CU-16, CU-20, CU-21: evidencias para contraste.
- CU-22: acción correctiva eventual.
- CU-28: punto crítico identificado.
- CU-29: consulta histórica.

### Decisiones pendientes y límites de definición
- PENDING: categorías y estados de tratamiento, criterios de admisibilidad y responsables por tipo de reclamo.
- SLA/plazos y reglas de comunicación oficial con empresa.
- Política de seguimiento de denuncias de identidad anónima o sin datos suficientes.

### Estado de definición y evidencia

Definición parcial; validación municipal confirmada, estados y procedimiento pendientes.

> Trazabilidad de reestructuración: CU-32 anterior, ampliado para reporte ciudadano.

## CU-28. Registrar punto crítico o microbasural identificado

| ID | CU-28 |
| --- | --- |
| Nombre | Registrar punto crítico o microbasural identificado |
| Paquete | Reclamos y puntos críticos |
| Prioridad | Baja |
| Actores | Fiscalizador municipal / Ornato Público (A3) |
| Actor iniciador | Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Conservar como referencia territorial municipal una ubicación con problemática recurrente identificada y validada mediante observación o antecedentes.

### Descripción

El fiscalizador registra ubicación, descripción, evidencia y relación con reclamos/inspecciones cuando corresponda. El punto crítico es una referencia institucional para focalizar inspecciones y análisis histórico, no un agregado automático de denuncias ni una obligación de instalar contenedores.

### Precondiciones
1. El fiscalizador está autenticado y autorizado para registrar referencias críticas.
2. Existe observación o antecedentes que permitan sustentar una identificación municipal.
3. La ubicación puede representarse con la precisión requerida para el registro.

### Flujo principal
1. El fiscalizador accede al módulo de puntos críticos.
2. La plataforma muestra referencias previamente identificadas sobre el mapa y su histórico.
3. El fiscalizador solicita registrar una ubicación identificada.
4. La plataforma solicita denominación, localización, descripción y antecedentes disponibles.
5. El fiscalizador selecciona el punto geográfico y vincula reportes, inspecciones u otras evidencias verificables cuando existan.
6. La plataforma valida geometría, referencias y autorización.
7. El fiscalizador revisa y confirma la información.
8. La plataforma guarda el punto y la constancia de validación municipal si esta forma parte del procedimiento.
9. El punto queda disponible para consultas territoriales y análisis de recurrencia.

### Flujos alternativos
- FA-01 — Punto preexistente. Se conserva el antecedente y se agrega una nueva observación o relación, sin crear duplicación indebida.
- FA-02 — Evidencia limitada. El fiscalizador registra lo disponible conforme a mínimos aprobados; no presume recurrencia no observada.
- FA-03 — Visualización de concentración. El sistema puede representar agregados de reportes como insumos de análisis separados de los puntos críticos oficialmente reconocidos.

### Excepciones
- E1. Ubicación inválida — No se crea punto sin geometría utilizable.
- E2. Antecedente inexistente — No se crea asociación con reportes o inspecciones ficticias.
- E3. Falta de validación — No se representa un candidato no validado como punto crítico oficial.
- E4. Permiso insuficiente — No se habilita a ciudadanos o supervisores empresariales la declaración institucional.
- E5. Error de almacenamiento — No se confirma el registro si la transacción falla.

### Postcondiciones
- Éxito. El punto crítico identificado queda georreferenciado y relacionado con su sustento municipal, sin reemplazar el historial territorial.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-28-01  El punto crítico se identifica o valida municipalmente.
- RN-CU-28-02  Repetición de reportes por sí sola no crea automáticamente una zona roja.
- RN-CU-28-03  La referencia conserva historia y antecedentes de recurrencia cuando existan.
- RN-CU-28-04  Los datos cartográficos oficiales se distinguen de capas basadas en conteos o heatmaps.
- RN-CU-28-05  No se incorporan contenedores, sensores o gestión del botadero a este CU.
- RN-CU-28-06  Una intervención posterior no borra la historia del punto.

### Criterios de aceptación propuestos
- CA-CU-28-01  Solo un fiscalizador autorizado registra/valida el punto.
- CA-CU-28-02  El punto posee coordenada válida y descripción.
- CA-CU-28-03  Los reportes asociados mantienen su identidad y procedencia originales.
- CA-CU-28-04  No aparece un punto crítico oficial solo por acumular denuncias.
- CA-CU-28-05  Se consulta el historial de observaciones del lugar.
- CA-CU-28-06  Un punto no validado se distingue de una referencia aprobada.
- CA-CU-28-07  No se borra el pasado ante nuevas observaciones o intervenciones.
- CA-CU-28-08  Un punto crítico no produce automáticamente sanción o reprogramación.

### Dependencias funcionales
- CU-20: inspecciones y evidencia.
- CU-25 a CU-27: reportes y reclamos.
- CU-29 y CU-30: historia e indicadores territoriales.

### Decisiones pendientes y límites de definición
- PENDING-10: definición institucional de punto crítico, recurrencia, validación, actualización y retiro.
- Reglas para clasificación de atención o visualizaciones de intensidad territorial.
- Fuentes verificadas de ubicaciones reales de microbasurales.

### Estado de definición y evidencia

Definición parcial; finalidad clara, criterios municipales pendientes.

> Trazabilidad de reestructuración: CU-33 anterior.

PAQUETE 8  •  HISTORIAL, REPORTES E INDICADORES

## CU-29. Consultar historial institucional del servicio

| ID | CU-29 |
| --- | --- |
| Nombre | Consultar historial institucional del servicio |
| Paquete | Historial, reportes e indicadores |
| Prioridad | Media |
| Actores | Administrador municipal (A1), Responsable municipal / autoridad (A2), Fiscalizador municipal / Ornato Público (A3) |
| Actor iniciador | Administrador municipal (A1), Responsable municipal / autoridad (A2), Fiscalizador municipal / Ornato Público (A3) |


### Propósito

Reconstruir la prestación histórica del servicio a través de cambios de empresa, periodo, rutas, recursos, jornadas y evidencias sin pérdida de continuidad.

### Descripción

El usuario municipal autorizado consulta periodos, operadores, versiones de ruta, asignaciones, ejecución, GPS, inspecciones y fiscalizaciones relacionadas. La navegación conserva el contexto temporal; no altera la información histórica.

### Precondiciones
1. El usuario dispone de sesión y autorización para consulta histórica del ámbito indicado.
2. La plataforma conserva registros institucionales de periodos y jornadas, aunque una búsqueda particular pueda no devolver resultados.

### Flujo principal
1. El usuario ingresa a la consulta histórica del servicio.
2. La plataforma presenta filtros disponibles por periodo, empresa, fecha, ruta, jornada y otros atributos conservados.
3. El usuario elige el contexto y criterios de búsqueda.
4. La plataforma verifica autorización de ámbito y consistencia de filtros.
5. La plataforma recupera jornadas y registros relacionados con su contexto histórico.
6. La interfaz presenta periodos, empresa operadora, versión de ruta y asignaciones de cada jornada.
7. El usuario selecciona una jornada u otro registro de interés.
8. La plataforma muestra evidencias de ejecución, GPS, incidencias, reclamos e inspecciones disponibles, distinguiendo datos ausentes.
9. El usuario consulta la cadena histórica y puede volver a resultados sin modificar registros.

### Flujos alternativos
- FA-01 — Cambio de operador. Una búsqueda por fechas recupera registros de empresas distintas preservando la identidad de cada periodo.
- FA-02 — Sin resultados. Se informa que la consulta no encontró antecedentes; no se crean registros.
- FA-03 — Evidencia parcialmente disponible. La plataforma diferencia módulos sin registros de datos cuyo acceso no está permitido.

### Excepciones
- E1. Filtros inválidos — No se ejecuta una consulta con rangos contradictorios.
- E2. Usuario no autorizado — Se deniega detalle fuera del ámbito permitido.
- E3. Referencia histórica incompleta — Se presenta el vínculo disponible y se informa la inconsistencia, sin reconstruir con datos inventados.
- E4. Error de lectura — Los registros históricos no se modifican y se comunica la incidencia.

### Postcondiciones
- Éxito. El usuario autorizado recupera el historial solicitado con las identidades de empresas, periodos, rutas y recursos aplicables a cada fecha.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-29-01  El GAMW conserva el historial aun cuando cambie la empresa operadora.
- RN-CU-29-02  La Jornada referencia el PeriodoServicio y RutaVersion originales.
- RN-CU-29-03  Vehículos y personas se reconstruyen desde vínculos/asignaciones históricos.
- RN-CU-29-04  La consulta no realiza operaciones destructivas.
- RN-CU-29-05  El acceso histórico del municipio no significa acceso ilimitado de cada empleado.
- RN-CU-29-06  No se sustituyen datos históricos por registros actuales de empresa, ruta o recurso.

### Criterios de aceptación propuestos
- CA-CU-29-01  Se consulta la trayectoria histórica de una jornada sin alterar datos.
- CA-CU-29-02  Se distingue empresa y periodo de cada jornada.
- CA-CU-29-03  Una nueva RutaVersion no modifica el recorrido de referencia antiguo.
- CA-CU-29-04  Una reasignación permite ver quién estaba vinculado en cada segmento.
- CA-CU-29-05  Se conservan registros anteriores a un cambio de empresa operadora.
- CA-CU-29-06  Una búsqueda sin resultados entrega lista vacía clara.
- CA-CU-29-07  Los filtros inválidos se rechazan de modo controlado.
- CA-CU-29-08  La consulta respeta autorizaciones sobre información histórica.

### Dependencias funcionales
- CU-04 a CU-07: datos institucionales y recursos.
- CU-09 a CU-24: programación, ejecución y fiscalización.
- CU-25 a CU-28: reportes y territorio.
- CU-30: consolidación para reportes.
- CU-03: autorización histórica.

### Decisiones pendientes y límites de definición
- D1: facultades de A1/A2/A3 para consultar todo el historial o subconjuntos.
- Filtros oficiales, datos sensibles que requieren ocultación y política de retención institucional.

### Estado de definición y evidencia

Definición funcional sólida; endpoint/interfaz y política institucional no acreditados como completados.

> Trazabilidad de reestructuración: CU-34 anterior.

## CU-30. Generar reportes e indicadores

| ID | CU-30 |
| --- | --- |
| Nombre | Generar reportes e indicadores |
| Paquete | Historial, reportes e indicadores |
| Prioridad | Media |
| Actores | Administrador municipal (A1), Responsable municipal / autoridad (A2), Fiscalizador municipal / Ornato Público (A3), Supervisor de empresa operadora (A4) |
| Actor iniciador | Usuario autorizado que solicita la consulta o generación |


### Propósito

Facilitar la consulta consolidada de la operación del servicio mediante reportes e indicadores calculados a partir de evidencias institucionales disponibles.

### Descripción

Integra la consulta de reportes y la visualización de indicadores en una misma meta. Puede ofrecer recuentos descriptivos de jornadas, GPS, incidencias, reclamos y actuaciones, pero no inventa fórmulas contractuales ni convierte ausencia de información en incumplimiento.

### Precondiciones
1. El usuario está autenticado y autorizado para los datos que pretende consultar.
2. Existen definiciones de reportes o métricas para la versión del producto que se ejecutará.
3. Las fuentes de datos institucionales se encuentran disponibles para el periodo consultado.

### Flujo principal
1. El usuario abre reportes e indicadores.
2. La plataforma presenta reportes/métricas habilitados y filtros disponibles según rol y ámbito.
3. El usuario selecciona un reporte o indicador y define fechas, empresa u otros filtros admitidos.
4. La plataforma valida consistencia temporal y autorización de consulta.
5. La plataforma recupera los datos institucionales originales asociados a los filtros.
6. La plataforma aplica únicamente cálculos con fórmula explícita y versión conocida.
7. La interfaz presenta cifras, unidades y contexto de interpretación, junto con tablas o gráficas cuando corresponda.
8. El usuario consulta el detalle que sustenta los valores y la fecha de actualización.
9. Si está habilitado, el usuario solicita un archivo de reporte en el formato definido.
10. La plataforma entrega información sin modificar registros originales ni emitir juicios de cumplimiento automáticos.

### Flujos alternativos
- FA-01 — Recuento descriptivo. Se muestra número de jornadas o incidencias conservadas sin presentar el valor como porcentaje de cumplimiento.
- FA-02 — Información insuficiente. El indicador se muestra como no calculable/indeterminado, sin convertir faltantes en cero o incumplimiento.
- FA-03 — Supervisor empresarial. Si se autoriza A4, solo consulta datos de su empresa y periodos permitidos.
- FA-04 — Exportación. Se genera un archivo solo cuando existe formato aprobado; de lo contrario permanece disponible la consulta en pantalla.

### Excepciones
- E1. Fórmula no definida — No se publica una métrica como indicador institucional oficial.
- E2. Fechas/filtros inválidos — Se solicita corregir la consulta.
- E3. Datos fuera de ámbito — No se revelan agregados sensibles de otras empresas al supervisor.
- E4. Fuente incompleta — Se documenta cobertura o imposibilidad de cálculo.
- E5. Error de generación — No se entrega un reporte como completo si su cálculo o exportación falló.

### Postcondiciones
- Éxito. El reporte o indicador calculable se presenta con filtros, fuentes y fórmula explícita, sin alterar datos originales.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-30-01  Todo indicador debe tener fórmula, unidad, fuente y periodo de referencia.
- RN-CU-30-02  Una métrica de presencia GPS no equivale a porcentaje de cumplimiento contractual.
- RN-CU-30-03  Los resultados de fiscalización se derivan de decisiones humanas registradas.
- RN-CU-30-04  Los reportes usan datos históricos sin sobrescribirlos.
- RN-CU-30-05  La agregación no debe eludir restricciones de acceso institucional.
- RN-CU-30-06  Se distinguen reportes descriptivos, propuestas técnicas e indicadores oficiales validados.

### Criterios de aceptación propuestos
- CA-CU-30-01  Se filtra un reporte por periodo y empresa cuando están disponibles.
- CA-CU-30-02  Los registros agregados corresponden a los filtros seleccionados.
- CA-CU-30-03  Se muestra fórmula o definición de cada indicador publicado.
- CA-CU-30-04  Un dato faltante no se sustituye silenciosamente por cero.
- CA-CU-30-05  No se calcula cumplimiento oficial con una fórmula no validada.
- CA-CU-30-06  Un supervisor no accede a agregados de otra empresa.
- CA-CU-30-07  La generación no altera registros originales.
- CA-CU-30-08  Los reportes se presentan con referencia temporal y fuente de cálculo.
- CA-CU-30-09  Una exportación no autorizada se deniega.

### Dependencias funcionales
- CU-11 a CU-29: fuentes de evidencia y resultados.
- CU-03: permisos y alcance.
- CU-29: consultas de contexto histórico.

### Decisiones pendientes y límites de definición
- PENDING-11: lista de reportes exigidos, fórmulas y unidades oficiales.
- Aprobación de exportaciones/formato y métricas públicas o internas.
- D1: permisos específicos de A1/A2/A3 y participación posible de A4.

### Estado de definición y evidencia

Definición parcial; fuentes y ejemplos conocidos, fórmulas de indicadores institucionales pendientes.

> Trazabilidad de reestructuración: CU-35 y CU-36 anteriores integrados.

## CU-31. Consultar bitácora de acciones relevantes

| ID | CU-31 |
| --- | --- |
| Nombre | Consultar bitácora de acciones relevantes |
| Paquete | Historial, reportes e indicadores |
| Prioridad | Media |
| Actores | Administrador municipal (A1) |
| Actor iniciador | Administrador municipal (A1) |


### Propósito

Permitir al Administrador municipal examinar quién realizó acciones institucionales relevantes y en qué momento para sostener trazabilidad administrativa.

### Descripción

La bitácora se alimenta de eventos automáticos registrados por operaciones de la plataforma. El CU representa su consulta, no el registro manual de eventos. Debe separar acontecimientos auditables, metadatos permitidos y secretos que nunca deben registrarse.

### Precondiciones
1. El Administrador está autenticado y expresamente autorizado para consultar bitácora.
2. La plataforma dispone de eventos de auditoría; una consulta puede devolver cero registros.

### Flujo principal
1. El Administrador accede a la consulta de bitácora.
2. La plataforma verifica su facultad de auditoría.
3. La interfaz presenta criterios de búsqueda como fecha, usuario, tipo de acción o entidad afectada, según disponibilidad.
4. El Administrador selecciona filtros o un intervalo temporal.
5. La plataforma valida los filtros y consulta eventos conservados.
6. La interfaz muestra fecha/hora, actor, acción y referencia al objeto relacionado cuando esos datos existan.
7. El Administrador selecciona un evento para consultar información adicional permitida.
8. La plataforma presenta el detalle sin permitir su modificación desde la consulta.
9. El Administrador termina la revisión manteniendo íntegros los registros consultados.

### Flujos alternativos
- FA-01 — Sin eventos. La plataforma indica que no hay resultados para los filtros.
- FA-02 — Evento sin usuario histórico. Se distingue una acción técnica o un evento anterior a atribución autenticada, sin inventar identidad.
- FA-03 — Filtro por entidad. El Administrador revisa actuaciones sobre una jornada o empresa concreta si el índice de auditoría lo permite.

### Excepciones
- E1. Usuario no autorizado — Se deniega consulta y detalle de eventos.
- E2. Filtro inválido — Se informa inconsistencia temporal o formato incorrecto.
- E3. Evento inexistente — No se devuelve un registro inventado.
- E4. Datos sensibles — Se ocultan contraseñas, JWT, secretos y otros campos cuya divulgación está prohibida.
- E5. Error de recuperación — Los eventos originales permanecen sin alteración.

### Postcondiciones
- Éxito. El Administrador autorizado consulta los eventos de auditoría y sus atributos permitidos sin modificar ni eliminar los registros.
- Fallo o rechazo. No se confirma una operación no completada; no se conceden autorizaciones indebidas ni se sustituyen destructivamente los antecedentes existentes.

### Reglas funcionales y de negocio relacionadas
- RN-CU-31-01  La generación de eventos auditables ocurre automáticamente durante acciones relevantes.
- RN-CU-31-02  La consulta de bitácora no modifica ni elimina eventos.
- RN-CU-31-03  Los registros conservan fecha/hora, identidad si está disponible y contexto.
- RN-CU-31-04  La bitácora no debe almacenar contraseñas ni tokens en texto claro.
- RN-CU-31-05  El catálogo de acciones auditables y política de retención requieren definición.
- RN-CU-31-06  Las consultas de auditoría están restringidas a usuarios institucionalmente autorizados.

### Criterios de aceptación propuestos
- CA-CU-31-01  El Administrador autorizado puede filtrar eventos por fecha.
- CA-CU-31-02  La consulta presenta actor y acción cuando la evidencia los posee.
- CA-CU-31-03  Un evento histórico sin usuario se muestra como no atribuible, sin inventar uno.
- CA-CU-31-04  No se permite editar ni borrar eventos desde la consulta.
- CA-CU-31-05  Un usuario sin permiso no accede a bitácora.
- CA-CU-31-06  Contraseñas y JWT no aparecen en detalles.
- CA-CU-31-07  Una búsqueda sin eventos es manejada sin error.
- CA-CU-31-08  Los registros auditados permanecen íntegros después de consultarlos.

### Dependencias funcionales
- CU-01 a CU-30: operaciones cuyo registro automático puede generar eventos.
- CU-03: autorización a bitácora.
- CU-29: consulta histórica de datos operativos, distinta del log de acciones.

### Decisiones pendientes y límites de definición
- Catálogo mínimo de acciones que deben auditarse y datos que se conservarán.
- Política institucional de consulta, retención y eventual exportación.
- Separación entre logs técnicos, seguridad y bitácora funcional del sistema.

### Estado de definición y evidencia

Definición parcial; propósito claro, catálogo de eventos y autorización pendientes.

> Trazabilidad de reestructuración: CU-37 anterior.

## Anexo A. Decisiones pendientes priorizadas

Este listado evita convertir hipótesis en obligaciones institucionales. Su aprobación posterior puede modificar flujos y criterios, pero no requiere renumerar inmediatamente los 31 CU.
- D1 / PENDING-12 — Seguridad institucional. Roles A1–A5, provisión inicial de cuentas, delegación de permisos y alcance de empresa/periodo para consultas e intervención.
- PENDING-04 — GPS y sincronización. Política de muestreo, envío online y batería; tratamiento explícito de conexión, captura local y actualización del mapa.
- PENDING-05 — Fiscalización. Resultados oficiales, requisitos mínimos de evaluación y rectificación de una decisión ya registrada.
- PENDING-06 — Incidencias. Catálogo, campos mínimos y operación durante o después de jornada.
- PENDING-07 — Acciones correctivas. Estados, responsables, plazos, atención, verificación y reapertura.
- PENDING-08/09 — Reportes y fotografías. Datos personales, contacto opcional, almacenamiento, tamaños, acceso, retención y eliminación permitida.
- PENDING-10 — Puntos críticos. Criterios de recurrencia, validación, desactivación y significado de alertas territoriales.
- PENDING-11 — Reportes e indicadores. Listado institucional de reportes, fórmulas, unidades y exportaciones.
- Territorio / rutas / puntos de control. Fuentes oficiales, autoridad para registrar/validar, evolución histórica y parámetros espaciales.
- CU-17 a CU-19 — integridad/anomalías. Umbrales técnicos explícitos de huecos, precisión, distancia, velocidad y detenciones; distinción evidencia vs juicio municipal.

## Anexo B. Matriz de continuidad

Mapa de equivalencias para impedir confusiones entre identificadores originales del documento de 37 CU y los 31 CU reestructurados. Las equivalencias son funcionales, no numéricas.

| Baseline vigente | Origen en catálogo anterior |
| --- | --- |
| CU-02 — Gestionar usuarios | CU-02 |
| CU-03 — Gestionar roles y permisos | CU-03 |
| CU-04 — Gestionar empresas operadoras | CU-04 |
| CU-05 — Gestionar periodos de servicio | CU-05 |
| CU-06 — Gestionar vehículos | CU-06 |
| CU-07 — Gestionar responsables operativos | CU-07 |
| CU-08 — Gestionar estructura territorial del servicio | CU-08 |
| CU-09 — Gestionar rutas operativas de referencia | CU-09 anterior, reformulado |
| CU-10 — Gestionar puntos de control y geocercas | CU-10 |
| CU-11 — Programar jornada | CU-11 |
| CU-12 — Asignar recursos a jornada | CU-12 |
| CU-13 — Ejecutar jornada asignada | CU-13, CU-14 y CU-15 anteriores fusionados; CU-17–CU-19 antiguos incorporados como comportamientos |
| CU-14 — Registrar incidencia operativa | CU-16 anterior |
| CU-15 — Monitorear posición de jornada | CU-20 anterior |
| CU-16 — Consultar trayectoria GPS de jornada | CU-21 anterior |
| CU-17 — Revisar continuidad e integridad del seguimiento GPS | CU-22 anterior, ampliado |
| CU-18 — Revisar coincidencias con puntos de control y geocercas | CU-23 anterior |
| CU-19 — Revisar desvíos y detenciones prolongadas | CU-24 anterior |
| CU-20 — Registrar inspección y evidencias de campo | CU-25 y CU-26 anteriores integrados |
| CU-21 — Fiscalizar jornada y registrar resultado | CU-27 anterior |
| CU-22 — Gestionar acciones correctivas | CU-28 anterior |
| CU-23 — Verificar atención de acciones correctivas | CU-29 anterior |
| CU-24 — Reprogramar jornada conservando el antecedente | CU-30 anterior |
| CU-25 — Presentar reporte ciudadano | Nuevo CU; no existía como actor ciudadano en los 37 CU originales |
| CU-26 — Registrar reclamo recibido por canal institucional | CU-31 anterior, ajustado a canal institucional |
| CU-27 — Validar y gestionar reclamos | CU-32 anterior, ampliado para reporte ciudadano |
| CU-28 — Registrar punto crítico o microbasural identificado | CU-33 anterior |
| CU-29 — Consultar historial institucional del servicio | CU-34 anterior |
| CU-30 — Generar reportes e indicadores | CU-35 y CU-36 anteriores integrados |
| CU-31 — Consultar bitácora de acciones relevantes | CU-37 anterior |


## Anexo C. Criterios de uso del documento
- No sustituir la matriz RF vigente ni iniciar su reestructuración como parte de esta entrega.
- Para cada incremento de construcción, cotejar flujo, reglas y CA con el comportamiento real del código y las pruebas antes de declarar el CU implementado.
- Mantener separados los estados: definido funcionalmente, implementado técnicamente, verificado automáticamente, probado en QA manual y aceptado institucionalmente.
- No inferir contratos, sanciones, criterios oficiales de cumplimiento, escalas de penalidad, límites territoriales ni categorías administrativas inexistentes.
- Actualizar diagramas UML 2.5 y trazabilidad PUDS cuando el comportamiento se cierre, conservando historial de decisiones y cambios.
