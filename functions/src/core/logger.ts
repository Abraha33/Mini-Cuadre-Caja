import {crearLog} from "../services/log.service";

export async function logError(e: unknown, uid?: string) {
  const err = e as {message?: unknown; stack?: unknown};
  await crearLog({
    modulo: "system",
    accion: "ERROR",
    usuario_id: uid || "unknown",
    detalle: {
      message: typeof err?.message === "string" ? err.message : String(err?.message ?? e),
      stack: typeof err?.stack === "string" ? err.stack : undefined,
    },
  });
}

