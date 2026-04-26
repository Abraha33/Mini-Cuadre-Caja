import {db, timestamp} from "../core/firestore";

export type AlertInput = {
  tipo: "DIFERENCIA_CRITICAL" | "Z_MISMATCH" | "ERROR_SISTEMA" | string;
  nivel: "LOW" | "WARNING" | "CRITICAL" | "OK" | "ERROR" | string;
  modulo: string;
  referencia_id?: string;
  mensaje: string;
  usuario_id: string;
};

export async function crearAlerta(data: AlertInput) {
  await db.collection("alertas").add({
    ...data,
    estado: "PENDIENTE",
    created_at: timestamp(),
    resolved_at: null,
  });
}

export async function resolverAlerta(
  alertaId: string,
  adminId: string,
  nota: string,
  ipAddress?: string
) {
  const alertaRef = db.collection("alertas").doc(alertaId);

  return await db.runTransaction(async (transaction) => {
    const alertaDoc = await transaction.get(alertaRef);

    if (!alertaDoc.exists) throw new Error("ALERTA_NOT_FOUND");
    if (String(alertaDoc.data()?.estado ?? "") === "RESUELTA") {
      throw new Error("ALERTA_ALREADY_RESOLVED");
    }

    transaction.update(alertaRef, {
      estado: "RESUELTA",
      "audit.resolved_at": timestamp(),
      "audit.resolved_by": adminId,
      "audit.resolucion_nota": nota,
      ...(ipAddress ? {"audit.ip_address": ipAddress} : {}),
    });

    return {id: alertaId, status: "OK"};
  });
}

