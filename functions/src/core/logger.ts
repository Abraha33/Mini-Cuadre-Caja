import {crearLog} from "../services/log.service";
import {crearAlerta} from "../services/alert.service";

export async function logError(e: unknown, uid?: string) {
  const err = e as {message?: unknown; stack?: unknown};
  const message =
    typeof err?.message === "string" ? err.message : String(err?.message ?? e);

  await crearLog({
    modulo: "system",
    accion: "ERROR",
    usuario_id: uid || "unknown",
    detalle: {
      message,
      stack: typeof err?.stack === "string" ? err.stack : undefined,
    },
  });

  await crearAlerta({
    tipo: "ERROR_SISTEMA",
    nivel: "CRITICAL",
    modulo: "backend",
    mensaje: message,
    usuario_id: uid || "unknown",
  });
}

