import { initializeApp } from "firebase/app";
import { connectAuthEmulator, getAuth, signInWithEmailAndPassword } from "firebase/auth";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";

const firebaseConfig = { apiKey: "fake-key", projectId: "demo-proyecto" }; // "demo-" permite usar emuladores sin proyecto real
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const functions = getFunctions(app, "us-central1");

// Conexión a emuladores
connectAuthEmulator(auth, "http://127.0.0.1:9099");
connectFunctionsEmulator(functions, "127.0.0.1:5001");

// Definición de casos de prueba
const casos = {
  normal: {
    user: { email: "cajero@test.com", pass: "password123" },
    payload: {
      turnoId: "turno_abierto_1",
      conteo: { monedas: 0, billetes: 80000 },
      zData: { ventas: 100000, devoluciones: 0, neto: 100000 },
      clientRequestId: "req-normal-1"
    }
  },
  idempotencia: {
    user: { email: "cajero@test.com", pass: "password123" },
    payload: {
      turnoId: "turno_abierto_1",
      conteo: { monedas: 0, billetes: 80000 },
      zData: { ventas: 100000, devoluciones: 0, neto: 100000 },
      clientRequestId: "req-normal-1" // Mismo ID que el anterior
    }
  },
  auxiliar: {
    user: { email: "aux@test.com", pass: "password123" },
    payload: {
      turnoId: "turno_abierto_1",
      conteo: { monedas: 0, billetes: 80000 },
      clientRequestId: "req-aux-1"
    }
  },
  critico: {
    user: { email: "cajero@test.com", pass: "password123" },
    payload: {
      turnoId: "turno_abierto_2", // Asegúrate de tener este turno en Firestore
      conteo: { monedas: 0, billetes: 120001 },
      zData: { ventas: 100000, devoluciones: 0, neto: 100000 },
      clientRequestId: "req-crit-1"
    }
  }
};

async function run() {
  const modo = process.argv[2];
  if (!casos[modo]) {
    console.error("❌ Indica un caso válido: " + Object.keys(casos).join(", "));
    process.exit(1);
  }

  const { user, payload } = casos[modo];

  try {
    console.log(`\n🚀 Ejecutando prueba: [${modo.toUpperCase()}]`);
    console.log(`👤 Logueando como: ${user.email}...`);
    
    await signInWithEmailAndPassword(auth, user.email, user.pass);
    const crearCierre = httpsCallable(functions, "crearCierre");
    
    console.log("📡 Llamando a la función...");
    const { data } = await crearCierre(payload);
    
    console.log("✅ RESULTADO:");
    console.dir(data, { depth: null });
  } catch (err) {
    console.error("❌ ERROR:");
    console.error(`- Código: ${err.code}`);
    console.error(`- Mensaje: ${err.message}`);
    if (err.details) console.table(err.details);
  }
}

run();