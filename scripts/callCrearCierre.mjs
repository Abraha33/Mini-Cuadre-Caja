import { initializeApp } from "firebase/app";
import { getFunctions, httpsCallable, connectFunctionsEmulator } from "firebase/functions";
import { getAuth, signInWithEmailAndPassword, connectAuthEmulator } from "firebase/auth";

// 1. Configuración mínima (puedes usar valores ficticios para emuladores)
const firebaseConfig = {
  apiKey: "fake-api-key",
  projectId: "tu-proyecto-id", // Asegúrate que coincida con tu .firebaserc
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const functions = getFunctions(app, "us-central1"); // Ajusta tu región

// 2. Conectar a Emuladores
connectAuthEmulator(auth, "http://127.0.0.1:9099");
connectFunctionsEmulator(functions, "127.0.0.1:5001");

async function ejecutarPrueba() {
  try {
    // 3. Login (Asegúrate de haber creado este usuario en el Auth Emulator UI)
    console.log("--- Autenticando Cajero ---");
    const userCredential = await signInWithEmailAndPassword(auth, "cajero@test.com", "password123");
    console.log("Token obtenido con éxito.\n");

    const crearCierre = httpsCallable(functions, "crearCierre");

    // 4. Payload de la Prueba #1 (Cierre Normal)
    const payload = {
      turnoId: "TURNO_TEST_001",
      conteo: { monedas: 0, billetes: 80000 },
      zData: { ventas: 100000, devoluciones: 0, neto: 100000 },
      clientRequestId: "req-normal-1"
    };

    console.log("--- Llamando a crearCierre ---");
    const resultado = await crearCierre(payload);
    
    console.log("Respuesta del servidor:");
    console.table(resultado.data);

  } catch (error) {
    console.error("Error en la prueba:");
    console.error("Código:", error.code);
    console.error("Mensaje:", error.message);
    if (error.details) console.error("Detalles:", error.details);
  }
}

ejecutarPrueba();