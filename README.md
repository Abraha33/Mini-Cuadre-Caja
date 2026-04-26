# Cuadre Caja (Firebase)

Repo del backend Firebase y la app Android de **cierre de caja** (Firestore + Storage + Auth). El objetivo es un flujo contable con **snapshot inmutable** del cierre y trazabilidad mínima.

## Estructura del repo

- `firebase.json`, `firestore.rules`, `storage.rules`: configuración y reglas de Firebase.
- `functions/`: Cloud Functions (Node/TypeScript).
- `Android/cuadre_caja_app/`: app Android (Jetpack Compose + Firebase SDK).

## Requisitos

- Cuenta Firebase del proyecto (este repo apunta a `cuadre-caja-oficial` vía `.firebaserc`).
- Android Studio (JDK embebido) para compilar `Android/cuadre_caja_app`.
- Firebase CLI (opcional) si vas a desplegar reglas desde terminal: [Firebase CLI](https://firebase.google.com/docs/cli).

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
- `cierres_caja` (snapshot del cierre + evidencia Z)
- `logs` (auditoría append-only desde cliente)
- `usuarios/{uid}` (perfil/rol)

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
- **Live Edit / Apply Changes rompe Compose**: desactiva Live Edit o reinstala la app; evita “hot swap” de firmas `@Composable`.

## Nota de arquitectura

Mantén **un solo sistema de identidad** (Firebase Auth) para que Storage/Firestore/Functions compartan el mismo `request.auth.uid`.
