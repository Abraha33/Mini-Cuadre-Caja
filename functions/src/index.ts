/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import { initializeApp } from "firebase-admin/app";
import {
  FieldValue,
  getFirestore,
  Timestamp,
  type QueryDocumentSnapshot,
} from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { setGlobalOptions } from "firebase-functions/v2";
import * as logger from "firebase-functions/logger";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onObjectFinalized } from "firebase-functions/v2/storage";
import { createWriteStream, promises as fsp } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import * as pdfParse from "pdf-parse";
import { crearLog } from "./services/log.service";
import { getUserRole } from "./services/user.service";
import { logError } from "./core/logger";
import { crearAlerta } from "./services/alert.service";
import { resolverAlerta } from "./services/alert.service";

// Start writing functions
// https://firebase.google.com/docs/functions/typescript

// For cost control, you can set the maximum number of containers that can be
// running at the same time. This helps mitigate the impact of unexpected
// traffic spikes by instead downgrading performance. This limit is a
// per-function limit. You can override the limit for each function using the
// `maxInstances` option in the function's options, e.g.
// `onRequest({ maxInstances: 5 }, (req, res) => { ... })`.
// NOTE: setGlobalOptions does not apply to functions using the v1 API. V1
// functions should each use functions.runWith({ maxInstances: 10 }) instead.
// In the v1 API, each function can only serve one request per container, so
// this will be the maximum concurrent request count.
setGlobalOptions({maxInstances: 10});

initializeApp();

/** URL del despliegue Web App de Apps Script; definida en `functions/.env` o `.env.<projectId>`. */
function getSheetsWebhookUrl(): string {
  return (process.env.SHEETS_WEBHOOK_URL ?? "").trim();
}

type ZResumen = {
  ventas?: number;
  devoluciones?: number;
  neto?: number;
  pagos?: Record<string, number>;
  diffVsIngresosTotal?: number;
  nivel?: "OK" | "WARNING" | "ERROR";
  rawTextSample?: string;
};

function parseMoney(input: string): number | undefined {
  const s = input.trim();
  if (!s) return undefined;

  const negativeByParen = /\(.*\)/.test(s);
  const negativeByLeading = /^[-–—]/.test(s.replace(/^\s*\$?\s*/, ""));

  // Deja solo dígitos, coma, punto y signo.
  let cleaned = s.replace(/[^\d.,-]/g, "");
  if (!cleaned) return undefined;

  const applySign = (n: number): number => {
    if (!Number.isFinite(n)) return n;
    if (negativeByParen) return -Math.abs(n);
    if (negativeByLeading || cleaned.startsWith("-")) return -Math.abs(n);
    return n;
  };

  const hasComma = cleaned.includes(",");
  const hasDot = cleaned.includes(".");

  // Si hay ambos, asumimos separador decimal el último.
  if (hasComma && hasDot) {
    const lastComma = cleaned.lastIndexOf(",");
    const lastDot = cleaned.lastIndexOf(".");
    const decimalSep = lastComma > lastDot ? "," : ".";
    const thousandsSep = decimalSep === "," ? "." : ",";
    const noThousands = cleaned.split(thousandsSep).join("");
    const normalized = decimalSep === "," ? noThousands.replace(",", ".") : noThousands;
    const n = Number(normalized);
    return Number.isFinite(n) ? applySign(n) : undefined;
  }

  // Solo coma: si parece decimal (2 dígitos al final), coma decimal; si no, miles.
  if (hasComma && !hasDot) {
    const parts = cleaned.split(",");
    const last = parts[parts.length - 1] ?? "";
    const normalized =
      last.length === 2 ? parts.slice(0, -1).join("") + "." + last : parts.join("");
    const n = Number(normalized);
    return Number.isFinite(n) ? applySign(n) : undefined;
  }

  // Solo punto: idem.
  if (hasDot && !hasComma) {
    const parts = cleaned.split(".");
    const last = parts[parts.length - 1] ?? "";
    const normalized =
      last.length === 2 ? parts.slice(0, -1).join("") + "." + last : parts.join("");
    const n = Number(normalized);
    return Number.isFinite(n) ? applySign(n) : undefined;
  }

  const n = Number(cleaned);
  if (!Number.isFinite(n)) return undefined;
  return applySign(n);
}

function extractAfterLabel(t: string, labelVariants: string[]): string | undefined {
  for (const lab of labelVariants) {
    const re = new RegExp(
      `${lab}\\s*[:\\-]?\\s*(\\(?\\$?\\s*[-–—]?\\s*[0-9.,]+\\)?)`,
      "i"
    );
    const m = t.match(re);
    if (m?.[1]) return m[1];
  }
  return undefined;
}

function extractPago(t: string, label: string): number | undefined {
  const re = new RegExp(
    `${label}\\s*[:\\-]?\\s*(\\(?\\$?\\s*[-–—]?\\s*[0-9.,]+\\)?)`,
    "i"
  );
  const m = t.match(re);
  return m?.[1] ? parseMoney(m[1]) : undefined;
}

function extractZTotals(text: string): ZResumen {
  const t = text.replace(/\s+/g, " ");

  // 80/20 para PDFs tipo POS: buscamos etiquetas comunes y capturamos el monto inmediato.
  const ventasRaw = extractAfterLabel(t, [
    "TOTAL\\s+VENTAS\\s+BRUTAS",
    "TOTAL\\s+VENTAS",
    "TOTAL\\s+DE\\s+VENTAS",
    "VENTAS\\s+TOTALES",
    "TOTAL\\s+VENTA",
    "VENTAS\\s+BRUTAS",
  ]);
  const devolRaw = extractAfterLabel(t, [
    "TOTAL\\s+DEVOLUCIONES",
    "DEVOLUCIONES\\s+TOTALES",
    "TOTAL\\s+ANULACIONES",
    "ANULACIONES",
    "DEVOLUCIONES",
  ]);
  const netoRaw = extractAfterLabel(t, [
    "TOTAL\\s+NETO\\s+VENTAS",
    "TOTAL\\s+NETO",
    "VENTAS\\s+NETAS",
    "NETO\\s+VENTAS",
    "NETO",
    "TOTAL\\s+VENTAS\\s*[-–]\\s*DEVOLUCIONES",
  ]);

  const ventas = ventasRaw ? parseMoney(ventasRaw) : undefined;
  const devoluciones = devolRaw ? parseMoney(devolRaw) : undefined;
  const neto = netoRaw ? parseMoney(netoRaw) : undefined;

  const computedNeto =
    ventas != null && devoluciones != null ? ventas - devoluciones : undefined;

  const pagos: Record<string, number> = {};
  const put = (key: string, v: number | undefined) => {
    if (v != null) pagos[key] = v;
  };
  put("efectivo", extractPago(t, "EFECTIVO"));
  put("nequi", extractPago(t, "NEQUI"));
  put("daviplata", extractPago(t, "DAVIPLATA"));
  put("bancolombia", extractPago(t, "BANCOLOMBIA"));
  put("pse", extractPago(t, "PSE"));
  put("tarjeta_debito", extractPago(t, "TARJETA\\s+DEBITO"));
  put("tarjeta_credito", extractPago(t, "TARJETA\\s+CREDITO"));

  return {
    ventas,
    devoluciones,
    neto: neto ?? computedNeto,
    pagos: Object.keys(pagos).length ? pagos : undefined,
    rawTextSample: t.slice(0, 4000),
  };
}

export const procesarInformeZ = onObjectFinalized(
  {
    region: "us-east1",
    memory: "1GiB",
    timeoutSeconds: 120,
  },
  async (event) => {
    const name = event.data.name ?? "";
    const bucket = event.data.bucket ?? "";

    if (!name.startsWith("cierres/") || !name.toLowerCase().endsWith(".pdf")) return;
    if (!bucket) return;

    logger.info("procesarInformeZ: object finalized", {bucket, name});

    const storage = getStorage();
    const db = getFirestore();

    // uid viene de cierres/{uid}_{timestamp}.pdf
    const base = name.replace(/^cierres\//, "");
    const uid = base.split("_")[0] ?? "";
    if (!uid) return;

    const tmpPath = join(tmpdir(), base.replace(/[^\w.\-]+/g, "_"));

    // Descarga a /tmp
    await new Promise<void>((resolve, reject) => {
      const file = storage.bucket(bucket).file(name);
      const out = createWriteStream(tmpPath);
      file
        .createReadStream()
        .on("error", reject)
        .pipe(out)
        .on("error", reject)
        .on("finish", () => resolve());
    });

    try {
      const buf = await fsp.readFile(tmpPath);
      const parsed = await (pdfParse as any)(buf);
      const resumen = extractZTotals(parsed.text ?? "");

      // Busca el cierre por z_pdf_path para actualizar el mismo doc.
      const cierresSnap = await db
        .collection("cierres_caja")
        .where("usuario_id", "==", uid)
        .where("z_pdf_path", "==", name)
        .limit(1)
        .get();

      const cierreDoc = cierresSnap.docs[0];
      if (!cierreDoc) {
        logger.warn("procesarInformeZ: no cierre found for pdf", {uid, name});
        return;
      }

      const cierreData = cierreDoc.data() as any;
      const ingresosEfectivo = cierreData?.resumen?.ingresos_efectivo ?? 0;
      const transferencias = cierreData?.resumen?.transferencias ?? 0;
      const ingresosTotal = Number(ingresosEfectivo) + Number(transferencias);

      let diffVsIngresosTotal: number | undefined;
      if (resumen.neto != null && Number.isFinite(ingresosTotal)) {
        diffVsIngresosTotal = resumen.neto - ingresosTotal;
      }

      const absDiff = diffVsIngresosTotal != null ? Math.abs(diffVsIngresosTotal) : undefined;
      const nivel: "OK" | "WARNING" | "ERROR" =
        absDiff == null ? "ERROR" : absDiff <= 5000 ? "OK" : absDiff <= 20000 ? "WARNING" : "ERROR";

      await cierreDoc.ref.set(
        {
          z: {
            ...(cierreData?.z ?? {}),
            resumen: {
              ventas: resumen.ventas ?? null,
              devoluciones: resumen.devoluciones ?? null,
              neto: resumen.neto ?? null,
              pagos: resumen.pagos ?? null,
              ingresos_total: ingresosTotal,
              diff_vs_ingresos_total: diffVsIngresosTotal ?? null,
              nivel,
            },
            validacion: nivel === "OK" ? "VALIDADO" : nivel === "WARNING" ? "WARNING" : "ERROR",
            processed_at: FieldValue.serverTimestamp(),
          },
        },
        {merge: true}
      );

      await crearLog({
        modulo: "cierre_caja",
        accion: "VALIDATE_Z",
        referencia_id: cierreDoc.id,
        usuario_id: uid,
        rol: await getUserRole(uid),
        detalle: {
          resultado: nivel,
          diffVsIngresosTotal: diffVsIngresosTotal ?? null,
          ingresosTotal: ingresosTotal ?? null,
        },
      });

      if (nivel !== "OK") {
        await crearAlerta({
          tipo: "Z_MISMATCH",
          nivel,
          modulo: "cierre_caja",
          referencia_id: cierreDoc.id,
          mensaje: "Z no coincide con ERP",
          usuario_id: uid,
        });
      }

      logger.info("procesarInformeZ: updated cierre", {
        cierreId: cierreDoc.id,
        uid,
        name,
        nivel,
      });
    } catch (err) {
      logger.error("procesarInformeZ: parsing failed", err);
      await logError(err, uid);
      // Intentar marcar ERROR en cierres_caja si existe
      const cierresSnap = await db
        .collection("cierres_caja")
        .where("usuario_id", "==", uid)
        .where("z_pdf_path", "==", name)
        .limit(1)
        .get();
      const cierreDoc = cierresSnap.docs[0];
      if (cierreDoc) {
        await cierreDoc.ref.set(
          {
            z: {
              validacion: "ERROR",
              error: String((err as any)?.message ?? err),
              processed_at: FieldValue.serverTimestamp(),
            },
          },
          {merge: true}
        );
      }
    } finally {
      await fsp.unlink(tmpPath).catch(() => undefined);
    }
  }
);

type MovExport = {
  tipo: string;
  monto: number;
  metodo: string;
  usuario_id: string;
  caja_id?: string;
  turno_id?: string;
  created_at?: string | null;
};

function serializeMovimiento(doc: QueryDocumentSnapshot): MovExport {
  const x = doc.data() as Record<string, unknown>;
  const ca = x.created_at as {toDate?: () => Date} | undefined;
  const createdAt =
    ca && typeof ca.toDate === "function" ? ca.toDate().toISOString() : null;
  return {
    tipo: String(x.tipo ?? ""),
    monto: Number(x.monto ?? 0),
    metodo: String(x.metodo ?? ""),
    usuario_id: String(x.usuario_id ?? ""),
    caja_id: x.caja_id != null ? String(x.caja_id) : undefined,
    turno_id: x.turno_id != null ? String(x.turno_id) : undefined,
    created_at: createdAt,
  };
}

type MovDocData = {
  tipo?: string;
  monto?: number;
  metodo?: string;
  created_at?: Timestamp;
};

/** Misma lógica que `CierreCajaViewModel.calcularResumen` (fuente: Firestore). */
function resumenDesdeMovimientos(docs: QueryDocumentSnapshot[]): {
  ingresos: number;
  egresos: number;
  transferencias: number;
  saldoEsperado: number;
  fechaInicio: Timestamp | null;
  fechaFin: Timestamp | null;
} {
  let ingresos = 0;
  let egresos = 0;
  let transferencias = 0;
  const times: Timestamp[] = [];

  for (const d of docs) {
    const m = d.data() as MovDocData;
    const tipo = String(m.tipo ?? "");
    const metodo = String(m.metodo ?? "");
    const monto = Number(m.monto ?? 0);
    if (!Number.isFinite(monto)) continue;
    if (m.created_at instanceof Timestamp) times.push(m.created_at);

    if (metodo === "efectivo") {
      if (tipo === "ingreso") ingresos += monto;
      else egresos += monto;
    } else {
      transferencias += monto;
    }
  }

  times.sort((a, b) => a.seconds - b.seconds || a.nanoseconds - b.nanoseconds);
  const fechaInicio = times.length ? times[0]! : null;
  const fechaFin = times.length ? times[times.length - 1]! : null;

  return {
    ingresos,
    egresos,
    transferencias,
    saldoEsperado: ingresos - egresos,
    fechaInicio,
    fechaFin,
  };
}

function nivelPorDiferencia(absDiff: number): "LOW" | "MEDIUM" | "CRITICAL" {
  if (absDiff <= 5000) return "LOW";
  if (absDiff <= 20000) return "MEDIUM";
  return "CRITICAL";
}

function numInput(v: unknown): number {
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : 0;
}

/**
 * Callable: cierra turno y persiste cierre con totales calculados en servidor (no confiar en el cliente).
 */
export const crearCierre = onCall(
  {
    region: "us-east1",
    memory: "512MiB",
    timeoutSeconds: 60,
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    }

    const data = (request.data ?? {}) as Record<string, unknown>;
    const turnoId = String(data.turnoId ?? "").trim();
    const cajaId = String(data.cajaId ?? "").trim();
    const cierreDocId = String(data.cierreDocId ?? "").trim();
    const conteo = (data.conteo ?? {}) as Record<string, unknown>;

    if (!turnoId || !cajaId || !cierreDocId) {
      throw new HttpsError(
        "invalid-argument",
        "Faltan turnoId, cajaId o cierreDocId."
      );
    }

    const monedas = numInput(conteo.monedas);
    const billetes = numInput(conteo.billetes);
    const totalContado = monedas + billetes;
    if (totalContado <= 0) {
      throw new HttpsError(
        "invalid-argument",
        "El conteo (monedas + billetes) debe ser mayor que cero."
      );
    }

    const db = getFirestore();
    const rol = await getUserRole(uid);

    const turnoRef = db.collection("turnos_caja").doc(turnoId);
    const cierreRef = db.collection("cierres_caja").doc(cierreDocId);
    const movQuery = db
      .collection("movimientos_caja")
      .where("turno_id", "==", turnoId)
      .where("usuario_id", "==", uid);

    try {
      const result = await db.runTransaction(async (transaction) => {
        const [turnoSnap, cierreSnap, movSnap] = await Promise.all([
          transaction.get(turnoRef),
          transaction.get(cierreRef),
          transaction.get(movQuery),
        ]);

        if (!turnoSnap.exists) {
          throw new HttpsError("not-found", "Turno no encontrado.");
        }
        const turno = turnoSnap.data() as Record<string, unknown>;
        if (String(turno.usuario_id ?? "") !== uid) {
          throw new HttpsError(
            "permission-denied",
            "Este turno no pertenece al usuario."
          );
        }
        if (String(turno.caja_id ?? "") !== cajaId) {
          throw new HttpsError(
            "failed-precondition",
            "La caja no coincide con el turno."
          );
        }
        if (String(turno.estado ?? "") !== "ABIERTO") {
          throw new HttpsError(
            "failed-precondition",
            "El turno ya está cerrado o no está abierto."
          );
        }

        if (!cierreSnap.exists) {
          throw new HttpsError("not-found", "Cierre borrador no encontrado.");
        }
        const draft = cierreSnap.data() as Record<string, unknown>;
        if (String(draft.usuario_id ?? "") !== uid) {
          throw new HttpsError(
            "permission-denied",
            "Este cierre no pertenece al usuario."
          );
        }
        if (String(draft.estado ?? "") === "COMPLETADO") {
          throw new HttpsError(
            "failed-precondition",
            "Este cierre ya está completado."
          );
        }
        if (String(draft.estado ?? "") !== "PDF_CARGADO") {
          throw new HttpsError(
            "failed-precondition",
            "El cierre debe estar en estado PDF_CARGADO."
          );
        }

        const z = (draft.z ?? {}) as Record<string, unknown>;
        const zValidacion = String(z.validacion ?? "");
        if (zValidacion !== "VALIDADO" && zValidacion !== "WARNING") {
          throw new HttpsError(
            "failed-precondition",
            "Informe Z debe estar VALIDADO o WARNING antes de cerrar."
          );
        }

        const movDocs = movSnap.docs;
        const r = resumenDesdeMovimientos(movDocs);
        const diferencia = totalContado - r.saldoEsperado;
        const nivel = nivelPorDiferencia(Math.abs(diferencia));

        if (nivel === "CRITICAL" && rol !== "admin") {
          throw new HttpsError(
            "permission-denied",
            "Diferencia CRITICAL: requiere rol admin para cerrar."
          );
        }

        const fechaInicio = r.fechaInicio ?? Timestamp.now();
        const fechaFin = r.fechaFin ?? Timestamp.now();

        transaction.set(
          cierreRef,
          {
            caja_id: cajaId,
            usuario_id: uid,
            turno_id: turnoId,
            fecha_inicio: fechaInicio,
            fecha_fin: fechaFin,
            resumen: {
              ingresos_efectivo: r.ingresos,
              egresos_efectivo: r.egresos,
              saldo_esperado: r.saldoEsperado,
              transferencias: r.transferencias,
            },
            conteo: {
              monedas,
              billetes,
              total: totalContado,
            },
            diferencia,
            nivel,
            estado: "COMPLETADO",
            completed_at: FieldValue.serverTimestamp(),
          },
          {merge: true}
        );

        transaction.update(turnoRef, {
          estado: "CERRADO",
          cierre_id: cierreDocId,
          fecha_fin: FieldValue.serverTimestamp(),
        });

        return {
          cierreId: cierreDocId,
          diferencia,
          nivel,
          zValidacion,
          resumen: {
            ingresos_efectivo: r.ingresos,
            egresos_efectivo: r.egresos,
            saldo_esperado: r.saldoEsperado,
            transferencias: r.transferencias,
          },
        };
      });

      await crearLog({
        modulo: "cierre_caja",
        accion: "CREATE",
        referencia_id: result.cierreId,
        usuario_id: uid,
        rol,
        detalle: {
          diferencia: result.diferencia,
          nivel: result.nivel,
          validacionZ: result.zValidacion,
        },
      });

      if (result.nivel === "CRITICAL") {
        await crearAlerta({
          tipo: "DIFERENCIA_CRITICAL",
          nivel: result.nivel,
          modulo: "cierre_caja",
          referencia_id: result.cierreId,
          mensaje: `Diferencia crítica detectada: ${result.diferencia}`,
          usuario_id: uid,
        });
      }

      // En este sistema, Z está OK cuando `procesarInformeZ` marcó VALIDADO.
      if (result.zValidacion !== "VALIDADO") {
        await crearAlerta({
          tipo: "Z_MISMATCH",
          nivel: result.zValidacion,
          modulo: "cierre_caja",
          referencia_id: result.cierreId,
          mensaje: "Z no coincide con ERP",
          usuario_id: uid,
        });
      }

      logger.info("crearCierre: success", {uid, cierreId: cierreDocId, turnoId});
      return {ok: true, ...result};
    } catch (e) {
      await logError(e, uid);
      throw e;
    }
  }
);

export const resolverAlertaHandler = onCall(
  {
    region: "us-east1",
    memory: "256MiB",
    timeoutSeconds: 30,
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Acceso denegado.");
    }

    const rol = await getUserRole(uid);
    if (rol !== "admin") {
      throw new HttpsError("permission-denied", "Privilegios insuficientes.");
    }

    const data = (request.data ?? {}) as Record<string, unknown>;
    const alertaId = String(data.alertaId ?? "").trim();
    const resolucion = String(data.resolucion ?? "").trim();
    if (!alertaId || !resolucion) {
      throw new HttpsError("invalid-argument", "Datos incompletos.");
    }
    if (resolucion.length > 1000) {
      throw new HttpsError(
        "invalid-argument",
        "La nota de resolución excede 1000 caracteres."
      );
    }

    try {
      const ip =
        (typeof request.rawRequest?.ip === "string" && request.rawRequest.ip) ||
        undefined;

      const result = await resolverAlerta(alertaId, uid, resolucion, ip);

      await crearLog({
        modulo: "alertas",
        accion: "RESOLVE",
        referencia_id: alertaId,
        usuario_id: uid,
        rol,
        detalle: {
          resolucion,
        },
      });

      logger.info("resolverAlertaHandler: resolved", {alertaId, admin: uid});
      return result;
    } catch (e) {
      await logError(e, uid);

      const msg = String((e as any)?.message ?? "");
      if (msg === "ALERTA_NOT_FOUND") {
        throw new HttpsError("not-found", "ALERTA_NOT_FOUND");
      }
      if (msg === "ALERTA_ALREADY_RESOLVED") {
        throw new HttpsError("failed-precondition", "ALERTA_ALREADY_RESOLVED");
      }
      throw new HttpsError("internal", msg || "Error interno.");
    }
  }
);

/**
 * Callable: carga cierre + movimientos del turno y POST a Apps Script (SHEETS_WEBHOOK_URL).
 * Idempotente por cierre: si ya hay exported_sheets_at, devuelve ok sin repetir filas/email.
 */
export const exportarCierre = onCall(
  {
    region: "us-east1",
    memory: "512MiB",
    timeoutSeconds: 120,
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    }

    const cierreId = String(request.data?.cierreId ?? "").trim();
    if (!cierreId) {
      throw new HttpsError("invalid-argument", "Falta cierreId.");
    }

    const url = getSheetsWebhookUrl();
    if (!url) {
      throw new HttpsError(
        "failed-precondition",
        "SHEETS_WEBHOOK_URL no definido: crea functions/.env (o .env.<projectId>) con SHEETS_WEBHOOK_URL=..."
      );
    }

    const db = getFirestore();
    const cierreRef = db.collection("cierres_caja").doc(cierreId);
    const cierreSnap = await cierreRef.get();
    if (!cierreSnap.exists) {
      throw new HttpsError("not-found", "Cierre no encontrado.");
    }

    const cierre = cierreSnap.data() as Record<string, unknown>;
    if (String(cierre.usuario_id ?? "") !== uid) {
      throw new HttpsError("permission-denied", "Este cierre no pertenece al usuario.");
    }
    if (String(cierre.estado ?? "") !== "COMPLETADO") {
      throw new HttpsError("failed-precondition", "El cierre debe estar COMPLETADO.");
    }
    if (cierre.exported_sheets_at) {
      return {ok: true, duplicate: true};
    }

    const resumen = (cierre.resumen ?? {}) as Record<string, unknown>;
    const z = (cierre.z ?? {}) as Record<string, unknown>;

    const turnoId = String(cierre.turno_id ?? "");
    let movSnap;
    if (turnoId) {
      movSnap = await db
        .collection("movimientos_caja")
        .where("usuario_id", "==", uid)
        .where("turno_id", "==", turnoId)
        .get();
    } else {
      movSnap = await db
        .collection("movimientos_caja")
        .where("usuario_id", "==", uid)
        .get();
    }

    const movimientos = movSnap.docs.map((d) => serializeMovimiento(d));

    const payload = {
      cierre_id: cierreId,
      usuario_id: uid,
      fecha: new Date().toISOString(),
      total_ingresos: resumen.ingresos_efectivo ?? null,
      total_egresos: resumen.egresos_efectivo ?? null,
      saldo: resumen.saldo_esperado ?? null,
      validacion_z: z.validacion ?? null,
      movimientos,
    };

    try {
      const res = await fetch(url, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(payload),
      });
      const bodySnippet = (await res.text()).slice(0, 500);

      if (!res.ok) {
        await db.collection("logs").add({
          tipo: "EXPORT_SHEETS_CALLABLE",
          usuario_id: uid,
          cierre_id: cierreId,
          ok: false,
          status: res.status,
          detail: bodySnippet,
          created_at: FieldValue.serverTimestamp(),
        });
        throw new HttpsError(
          "internal",
          `Webhook Sheets respondió ${res.status}: ${bodySnippet}`
        );
      }

      await Promise.all([
        cierreRef.set({exported_sheets_at: FieldValue.serverTimestamp()}, {merge: true}),
        db.collection("logs").add({
          tipo: "EXPORT_SHEETS_CALLABLE",
          usuario_id: uid,
          cierre_id: cierreId,
          ok: true,
          status: res.status,
          detail: "webhook_ok",
          created_at: FieldValue.serverTimestamp(),
        }),
      ]);

      logger.info("exportarCierre: success", {cierreId, movCount: movimientos.length});
      return {ok: true};
    } catch (err) {
      if (err instanceof HttpsError) throw err;
      logger.error("exportarCierre: failed", {cierreId, err});
      await db.collection("logs").add({
        tipo: "EXPORT_SHEETS_CALLABLE",
        usuario_id: uid,
        cierre_id: cierreId,
        ok: false,
        detail: String((err as Error)?.message ?? err),
        created_at: FieldValue.serverTimestamp(),
      });
      throw new HttpsError(
        "internal",
        String((err as Error)?.message ?? err)
      );
    }
  }
);
