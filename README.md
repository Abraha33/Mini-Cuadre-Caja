# Cuadre Caja (Firebase)

Repo del backend Firebase y la app Android de **cierre de caja** (Firestore + Storage + Auth). El objetivo es un flujo contable con **snapshot inmutable** del cierre y trazabilidad mínima.

## Estructura del repo

- `firebase.json`, `firestore.rules`, `storage.rules`: configuración y reglas de Firebase.
- `functions/`: Cloud Functions (Node/TypeScript); variables como `SHEETS_WEBHOOK_URL` vía `process.env` y archivos `.env` locales.
- `google-apps-script/`: código **Apps Script** (`ExportCierreCaja.gs`) gestionado con **clasp** (`npm run clasp:push` / `deploy:all`).
- `package.json` (raíz): script `deploy:all` (clasp + build + deploy Firebase).
- `Android/cuadre_caja_app/`: app Android (Jetpack Compose + Firebase SDK).

## Requisitos

- Cuenta Firebase del proyecto (este repo apunta a `cuadre-caja-oficial` vía `.firebaserc`).
- Android Studio (JDK embebido) para compilar `Android/cuadre_caja_app`.
- Firebase CLI (opcional) si vas a desplegar reglas desde terminal: [Firebase CLI](https://firebase.google.com/docs/cli).
- Node.js 22+ (para Cloud Functions y scripts del repo).

## Apps Script (clasp) + webhook + deploy en un comando

### 1) Instalar clasp (una vez)

Puedes usar el **clasp local** del paquete `google-apps-script/` (se instala con `npm run clasp:push`), o instalarlo global:

```bash
npm install -g @google/clasp
```

### 2) Autenticar clasp

```bash
clasp login
```

Se abre el navegador para autorizar el CLI con tu cuenta de Google.

### 3) Obtener el **Script ID**

1. Abre tu Google Sheet → **Extensiones** → **Apps Script**.
2. Engrane **Configuración del proyecto** (Project Settings).
3. Copia **Script ID** (cadena alfanumérica).

### 4) Vincular este repo al proyecto de Apps Script

En la carpeta `google-apps-script/`:

1. Copia el ejemplo de configuración de clasp:

   ```bash
   copy google-apps-script\.clasp.json.example google-apps-script\.clasp.json
   ```

   (En macOS/Linux: `cp google-apps-script/.clasp.json.example google-apps-script/.clasp.json`.)

2. Edita `google-apps-script/.clasp.json` y pega tu **Script ID** en `scriptId`.

3. Si el proyecto de Apps Script **no** tenía aún el archivo `ExportCierreCaja.gs`, el primer `clasp push` lo creará en la nube a partir de `google-apps-script/ExportCierreCaja.gs`.

Alternativa oficial: desde una carpeta vacía, `clasp clone "<SCRIPT_ID>"` y luego copiar el contenido de este repo; para este flujo prefijamos **push** desde `google-apps-script/` con `.clasp.json` ya apuntando al script.

### 5) URL del Web App (Sheets)

1. En Apps Script: **Implementar** → **Implementar como aplicación web**.
2. Ejecutar como: **Yo** · Quién tiene acceso: **Cualquier usuario** (para que Cloud Functions pueda hacer `POST` sin OAuth interactivo).
3. Copia la URL que termina en `/exec`.

### 6) Variables de entorno en Firebase Functions

En `functions/` crea **uno** de estos archivos (no se suben al git; ver `functions/.gitignore`):

- `functions/.env` — simple, o
- `functions/.env.cuadre-caja-oficial` — recomendado si coincide con el **Project ID** de `.firebaserc` (Firebase CLI lo carga al desplegar ese proyecto).

Contenido mínimo:

```env
SHEETS_WEBHOOK_URL=https://script.google.com/macros/s/.../exec
```

Plantilla: `functions/.env.example`.

Referencia: [configurar entorno en Functions (2nd gen)](https://firebase.google.com/docs/functions/config-env).

### 7) Deploy completo (Sheets + Functions + índices)

Desde la **raíz del repo** (donde está `firebase.json` y el `package.json` nuevo):

```bash
npm run deploy:all
```

Esto ejecuta, en orden:

1. `clasp push` del código en `google-apps-script/` (sube `ExportCierreCaja.gs` al proyecto vinculado).
2. `npm run build` dentro de `functions/`.
3. `firebase deploy --only functions,firestore:indexes`.

Requisitos: `firebase` CLI logueado (`firebase login`) y proyecto activo correcto (`firebase use`).

## Configuración Firebase (una vez)

### 1) Android: `google-services.json`

Coloca `google-services.json` en:

`Android/cuadre_caja_app/app/google-services.json`

### 2) Authentication

En Firebase Console:

- **Authentication → Sign-in method → Email/Password → Enable**

La app incluye pantalla mínima **Login / Register** y crea el perfil en Firestore al registrar.

### 3) Firestore: colecciones esperadas (MVP)

- `movimientos_caja` (fuente de verdad operativa)
  - Campos mínimos: `tipo`, `monto`, `metodo`, `usuario_id`, `created_at`
  - Campos de bloqueo por turno (requeridos por reglas actuales): `caja_id`, `turno_id`
- `cierres_caja` (snapshot del cierre + evidencia Z)
- `logs` (auditoría append-only desde cliente)
- `usuarios/{uid}` (perfil/rol)
- `turnos_caja` (control de turno: `ABIERTO` → `CERRADO` con `cierre_id`)

### 4) Storage: PDF del Informe Z

Ruta usada por la app:

`cierres/{uid}_{timestamp}.pdf`

Asegúrate de que `storage.rules` en consola coincida con el archivo del repo y esté **publicado**.

## Reglas (importante)

Los archivos fuente de reglas viven aquí:

- `firestore.rules`
- `storage.rules`

Hasta que publiques reglas en consola, verás errores como `PERMISSION_DENIED` aunque el código esté bien.

Desde terminal (si tienes CLI logueado al proyecto correcto):

```bash
firebase deploy --only firestore:rules,storage
```

## App Android: cómo abrirla

Abre la carpeta:

`Android/cuadre_caja_app`

Luego **Sync Gradle** y **Run**.

### Flujo funcional mínimo (checklist)

1. Register (crea usuario + doc `usuarios/{uid}`)
2. Login
3. Ver resumen desde `movimientos_caja`
4. Subir PDF Z (Storage + referencia en `cierres_caja`)
5. Conteo físico + diferencia
6. Cerrar caja (merge del snapshot + log en `logs`)

## Troubleshooting rápido

- **`No autenticado` al subir PDF**: no hay sesión Firebase Auth (Email/Password deshabilitado o no hiciste login).
- **`PERMISSION_DENIED`**: reglas no publicadas o colección/campos no alineados (`usuario_id` debe coincidir con `request.auth.uid` donde aplique).
- **`FAILED_PRECONDITION` / error de índice**: Firestore te pedirá crear índices compuestos para queries con varios `whereEqualTo` + `orderBy`. Abre el link del error en consola y crea el índice sugerido.
- **Live Edit / Apply Changes rompe Compose**: desactiva Live Edit o reinstala la app; evita “hot swap” de firmas `@Composable`.

## Nota de arquitectura

Mantén **un solo sistema de identidad** (Firebase Auth) para que Storage/Firestore/Functions compartan el mismo `request.auth.uid`.
