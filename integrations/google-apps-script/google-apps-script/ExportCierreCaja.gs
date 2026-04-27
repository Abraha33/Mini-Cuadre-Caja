/**
 * Apps Script vinculado al Google Sheet del reporte.
 * 1) Crea hoja "REPORTE" (o cambia el nombre abajo).
 * 2) Project Settings → Script properties: REPORT_EMAIL = tu correo destino.
 * 3) Deploy → Web app → Execute as: Me / Access: Anyone.
 * 4) Pega la URL en Firebase: SHEETS_WEBHOOK_URL (param de Functions).
 *
 * Payload (JSON POST) desde Cloud Function exportarCierre:
 * cierre_id, usuario_id, fecha, total_ingresos, total_egresos, saldo, validacion_z, movimientos[]
 */

function doPost(e) {
  if (!e || !e.postData || !e.postData.contents) {
    return jsonOut({ ok: false, error: "empty body" });
  }

  var data;
  try {
    data = JSON.parse(e.postData.contents);
  } catch (err) {
    return jsonOut({ ok: false, error: "invalid json" });
  }

  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("REPORTE");
  if (!sheet) {
    return jsonOut({ ok: false, error: 'missing sheet "REPORTE"' });
  }

  sheet.appendRow([
    "CIERRE",
    data.cierre_id,
    data.usuario_id,
    data.fecha,
    data.total_ingresos,
    data.total_egresos,
    data.saldo,
    data.validacion_z,
  ]);

  var movs = data.movimientos || [];
  movs.forEach(function (m) {
    sheet.appendRow([
      "MOV",
      m.tipo,
      m.monto,
      m.metodo,
      m.usuario_id,
    ]);
  });

  var emailTo =
    PropertiesService.getScriptProperties().getProperty("REPORT_EMAIL") ||
    Session.getActiveUser().getEmail();
  if (emailTo) {
    MailApp.sendEmail({
      to: emailTo,
      subject: "Cierre de caja generado",
      body:
        "Cierre " +
        data.cierre_id +
        " exportado. Movimientos: " +
        movs.length,
    });
  }

  return jsonOut({ ok: true });
}

function jsonOut(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(
    ContentService.MimeType.JSON
  );
}
