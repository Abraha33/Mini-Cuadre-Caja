# Prompt drafts — token-efficient (Cuadre Caja)

Paste **one block** per Cursor / Prompt Wizard turn. No filler.

**Deprecated (do not paste):** callable `parseZReport` / `validarZ` + Android `getHttpsCallable` — repo uses **Storage `onObjectFinalized` → `procesarInformeZ`** + Android **snapshot listener** on `cierres_caja/{id}`.

---

## Canonical paths

```text
Android:   Android/cuadre_caja_app/
Functions: functions/src/index.ts   → export procesarInformeZ
Rules:     firestore.rules  storage.rules
```

---

## BLOCK — refine Z parser (80/20)

**[GOAL]** Improve `extractZTotals` / `parseMoney` in `functions/src/index.ts` for Colombian POS PDFs (`TOTAL VENTAS`, devoluciones negativas / `(...)`, `NETO`, optional `EFECTIVO|NEQUI|BANCOLOMBIA`). Keep ERP compare: `neto Z` vs `ingresos_efectivo + transferencias` from same `cierres_caja` doc.

**[CONTEXT]** `procesarInformeZ` fires on `cierres/{uid}_*.pdf`, finds doc by `usuario_id` + `z_pdf_path`, merges `z.resumen` + sets `z.validacion` ∈ {`VALIDADO`,`WARNING`,`ERROR`}.

**[CHANGES]** Touch only parser + merge fields; `npm run build` in `functions/` must pass.

**[VALIDATION]** After deploy: upload PDF → Firestore `z.resumen` non-null; `z.validacion` set; Android `estadoZ` updates without new buttons.

---

## BLOCK — Firestore composite index

**[GOAL]** If `cierres_caja` query (`usuario_id` + `z_pdf_path`) returns index error, create suggested composite index OR change upload to write `cierre_id` into Storage metadata and query by doc id (only if you refactor).

**[VALIDATION]** Function logs show no `FAILED_PRECONDITION` for that query.

---

## BLOCK — Sheets + email (next)

**[GOAL]** When `cierres_caja.estado == COMPLETADO`, POST snapshot `{cierreId, usuario_id, resumen, z, timestamps}` to HTTPS Function or Apps Script; append `logs` row with outcome. Idempotent per `cierreId`.

**[CONSTRAINTS]** Secrets only in Function env; client sends `cierreId` + auth token, not API keys.

---

## Version

| Ver | Note |
|-----|------|
| v3 | Replaced reverted 282-line draft; removed obsolete callable/Android flow; fixed paths. |
