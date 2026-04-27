import {FieldValue, getFirestore} from "firebase-admin/firestore";

export const db = getFirestore();

export function timestamp() {
  return FieldValue.serverTimestamp();
}

