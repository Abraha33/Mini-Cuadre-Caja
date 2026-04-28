# CHANGELOG.md

All meaningful project changes are recorded here.

---

## 2026-04-26

### Founder OS

- Installed management operating system structure
- Added project management folder
- Added sprint tracking files
- Added roadmap file
- Added current status file
- Added changelog process

### AI Workflow

- Cursor rules system configured
- ChatGPT strategic context established
- GitHub operational workflow defined

### Product Direction

- Confirmed focus on M1 Core Usable Product
- Avoid feature creep until core flows complete

---

## 2026-04-27

### Issue #5 — Informe Z (cierre de caja)

- **`UploadInformeZUseCase`**: validación PDF (MIME + cabecera `%PDF`), subida con `UploadTask` cancelable, progreso, timeout y mapeo de errores de Storage a `AppError`.
- **`CierreRepository.registrarPdfCierreInicial`**: registro Firestore del PDF tras Storage (misma forma que antes en el ViewModel).
- **Estado de UI**: `cargandoCierre` vs `subiendoInformeZ` + `progresoSubidaInformeZ` para no bloquear scroll/conteo durante solo la subida Z; `CierreCajaScreen` con `OpenDocument`, permiso persistente cuando aplica, progreso determinado/indeterminado, **Reintentar**, texto de error sin ellipsis agresivo, `CERRAR CAJA` deshabilitado solo donde corresponde.
- **ViewModel**: un solo job de subida (cancelación + `finally`), limpieza de ids Z al reintentar, mensaje claro si Firestore emite `SUBIDO` sin arrastrar error viejo.

#### Regresión manual sugerida (Issue #5)

1. Con sesión y turno abierto, abrir cierre → scroll hasta **CERRAR CAJA** (deshabilitado sin Z válido).
2. **Subir Informe Z** → elegir PDF válido → barra de progreso y porcentaje cuando haya bytes; al terminar, estado Z y URL en flujo (doc `cierres_caja`).
3. Forzar fallo de red (avión) → mensaje recuperable; **Reintentar** sin salir de la pantalla; scroll sigue activo.
4. Elegir archivo no PDF (si el selector lo permite) o MIME no permitido → mensaje de validación claro.
5. Cancelar picker → sin crash ni estado `loading` colgado.
6. Tras Z **VALIDADO/WARNING** y conteo, **CERRAR CAJA** una sola vez (sin doble envío).
7. Tras cierre, exportar si aplica.

**Infra Firestore (Logcat: `FAILED_PRECONDITION` / `PERMISSION_DENIED`):** en `backend/firestore.indexes.json` hay índice **`movimientos_caja`: `usuario_id` + `created_at`** e índice **`turnos_caja`: `usuario_id` + `caja_id` + `estado`**. Despliega con `firebase deploy --only firestore:indexes,firestore:rules` desde `backend` (o el enlace del error en consola). **Mitigación en app:** `observeMovimientos` ya no usa `orderBy(created_at)` en Firestore; ordena en cliente y deja de exigir ese índice compuesto. **Reglas `usuarios`:** lectura del propio doc solo con `request.auth.uid == userId`; lectura de otro usuario solo con `isAdmin()`, para evitar denegaciones al resolver el perfil propio.

---

## Next Expected Entries

Examples:

- feat: stable login session restore
- feat: open shift flow v1
- feat: close shift calculation engine
- fix: duplicate sync prevention
- docs: update roadmap after milestone completion

---

## Logging Rules

Write entries for:

- New features
- Important bug fixes
- Architecture changes
- Major refactors
- Process improvements
- Milestone completions

Do not log trivial edits.

---

## Founder Rule

If progress is invisible, momentum dies.