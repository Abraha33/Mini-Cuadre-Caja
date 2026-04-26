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

