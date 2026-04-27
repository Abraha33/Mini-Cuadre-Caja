import {timestamp, db} from "../core/firestore";

export type LogInput = {
  modulo: string;
  accion: string;
  referencia_id?: string;
  usuario_id: string;
  rol?: string;
  detalle?: unknown;
};

export async function crearLog(data: LogInput) {
  await db.collection("logs").add({
    ...data,
    timestamp: timestamp(),
  });
}

