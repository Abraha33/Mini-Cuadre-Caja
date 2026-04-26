import {db} from "../core/firestore";

export async function getUserRole(uid: string): Promise<string> {
  const doc = await db.collection("usuarios").doc(uid).get();
  if (!doc.exists) return "unknown";
  const rol = String(doc.data()?.rol ?? "");
  return rol || "unknown";
}

