package ec.com.advanceit.iotmonitoreo.service;

import ec.com.advanceit.iotmonitoreo.controller.SensorController;
import ec.com.advanceit.iotmonitoreo.model.Measurement;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class TelegramService {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.chat.id}")
    private String chatId;

    private final HttpClient client = HttpClient.newHttpClient();

    private final Esp32CommandService commandService;
    private final SensorController sensorController;

    private long ultimoUpdateId = 0;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public TelegramService(
            Esp32CommandService commandService,
            SensorController sensorController) {

        this.commandService = commandService;
        this.sensorController = sensorController;
    }


    // ============================================================
    // INICIAR BOT
    // ============================================================

    @PostConstruct
    public void iniciarBot() {

        Thread hilo = new Thread(
                this::recibirMensajes
        );

        hilo.setDaemon(true);
        hilo.start();

        System.out.println(
                "===== RECEPCION DE TELEGRAM INICIADA ====="
        );
    }


    // ============================================================
    // ENVIAR MENSAJE
    // ============================================================

    public void enviarMensaje(String mensaje) {

        try {

            String url =
                    "https://api.telegram.org/bot"
                            + botToken
                            + "/sendMessage";

            String json = """
                    {
                      "chat_id": "%s",
                      "text": "%s"
                    }
                    """.formatted(
                    chatId,
                    escaparJson(mensaje)
            );

            enviarPeticion(url, json);

        } catch (Exception e) {

            System.err.println(
                    "Error enviando mensaje a Telegram: "
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // MOSTRAR MENU DE BOTONES
    // ============================================================

    private void enviarMenu() {

        try {

            String url =
                    "https://api.telegram.org/bot"
                            + botToken
                            + "/sendMessage";

            String json = """
                    {
                      "chat_id": "%s",
                      "text": "¡Hola! 👋 Bienvenido/a al sistema de monitoreo 🤗\\n\\n¿Qué quieres hacer?",
                      "reply_markup": {
                        "inline_keyboard": [
                          [
                            {
                              "text": "▶ 💡 ENCENDER LED",
                              "callback_data": "LED_ON"
                            }
                          ],
                          [
                            {
                              "text": "⏹ 💡 APAGAR LED",
                              "callback_data": "LED_OFF"
                            }
                          ],
                          [
                            {
                              "text": "▶ 🪭 ENCENDER VENTILADOR",
                              "callback_data": "FAN_ON"
                            }
                          ],
                          [
                            {
                              "text": "⏹ 🪭 APAGAR VENTILADOR",
                              "callback_data": "FAN_OFF"
                            }
                          ],
                          [
                            {
                              "text": "👀 📊 VER LOS DATOS MÁS RECIENTES",
                              "callback_data": "DATA_ALL"
                            }
                          ]
                        ]
                      }
                    }
                    """.formatted(chatId);

            enviarPeticion(url, json);

        } catch (Exception e) {

            System.err.println(
                    "Error mostrando menu: "
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // RECIBIR MENSAJES
    // ============================================================

    private void recibirMensajes() {

        while (true) {

            try {

                String url =
                        "https://api.telegram.org/bot"
                                + botToken
                                + "/getUpdates"
                                + "?offset="
                                + (ultimoUpdateId + 1)
                                + "&timeout=20";

                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .GET()
                                .build();

                HttpResponse<String> response =
                        client.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                if (response.statusCode() == 200) {

                    procesarRespuesta(
                            response.body()
                    );
                }

            } catch (Exception e) {

                System.err.println(
                        "Error recibiendo mensajes: "
                                + e.getMessage()
                );

                try {

                    Thread.sleep(5000);

                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }


    // ============================================================
    // PROCESAR RESPUESTA DE TELEGRAM
    // ============================================================

    private void procesarRespuesta(String respuesta) {

        try {

            // ----------------------------------------------------
            // UPDATE ID
            // ----------------------------------------------------

            int posicionUpdate =
                    respuesta.indexOf("\"update_id\":");

            if (posicionUpdate == -1) {
                return;
            }

            String parteUpdate =
                    respuesta.substring(
                            posicionUpdate + 12
                    );

            int coma =
                    parteUpdate.indexOf(",");

            if (coma == -1) {
                return;
            }

            String updateIdTexto =
                    parteUpdate
                            .substring(0, coma)
                            .trim();

            ultimoUpdateId =
                    Long.parseLong(updateIdTexto);


            // ----------------------------------------------------
            // COMPROBAR SI ES UN BOTON
            // ----------------------------------------------------

            if (respuesta.contains("\"callback_query\"")) {

                procesarBoton(respuesta);

                return;
            }


            // ----------------------------------------------------
            // MENSAJE NORMAL
            // ----------------------------------------------------

            int posicionTexto =
                    respuesta.indexOf("\"text\":\"");

            if (posicionTexto == -1) {
                return;
            }

            String parteTexto =
                    respuesta.substring(
                            posicionTexto + 8
                    );

            int finTexto =
                    parteTexto.indexOf("\"");

            if (finTexto == -1) {
                return;
            }

            String mensaje =
                    parteTexto
                            .substring(0, finTexto)
                            .trim();

            System.out.println(
                    "===== MENSAJE RECIBIDO ====="
            );

            System.out.println(
                    "Mensaje: " + mensaje
            );

            procesarComando(mensaje);

        } catch (Exception e) {

            System.err.println(
                    "Error procesando mensaje: "
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // PROCESAR BOTON
    // ============================================================

    private void procesarBoton(String respuesta) {

        try {

            int posicionData =
                    respuesta.indexOf("\"data\":\"");

            if (posicionData == -1) {
                return;
            }

            String parteData =
                    respuesta.substring(
                            posicionData + 8
                    );

            int finData =
                    parteData.indexOf("\"");

            if (finData == -1) {
                return;
            }

            String boton =
                    parteData
                            .substring(0, finData)
                            .trim();

            System.out.println(
                    "===== BOTON TELEGRAM ====="
            );

            System.out.println(
                    "Boton: " + boton
            );


            // ----------------------------------------------------
            // QUITAR "CARGANDO..." DEL BOTON
            // ----------------------------------------------------

            responderCallback(respuesta);


            // ----------------------------------------------------
            // EJECUTAR COMANDO
            // ----------------------------------------------------

            switch (boton) {

                case "LED_ON":

                    commandService.establecerComando(
                            "LED_ON"
                    );

                    enviarMensaje(
                            "💡 LED ENCENDIDO"
                    );

                    break;


                case "LED_OFF":

                    commandService.establecerComando(
                            "LED_OFF"
                    );

                    enviarMensaje(
                            "💡 LED APAGADO"
                    );

                    break;


                case "FAN_ON":

                    commandService.establecerComando(
                            "FAN_ON"
                    );

                    enviarMensaje(
                            "🪭 VENTILADOR ENCENDIDO"
                    );

                    break;


                case "FAN_OFF":

                    commandService.establecerComando(
                            "FAN_OFF"
                    );

                    enviarMensaje(
                            "🪭 VENTILADOR APAGADO"
                    );

                    break;


                case "DATA_ALL":

                    enviarDatosESP32();

                    break;


                default:

                    System.out.println(
                            "Boton no reconocido: "
                                    + boton
                    );

                    break;
            }

        } catch (Exception e) {

            System.err.println(
                    "Error procesando boton: "
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // RESPONDER CALLBACK
    // ============================================================

    private void responderCallback(String respuesta) {

        try {

            int posicionId =
                    respuesta.indexOf("\"id\":\"");

            if (posicionId == -1) {
                return;
            }

            String parteId =
                    respuesta.substring(
                            posicionId + 6
                    );

            int finId =
                    parteId.indexOf("\"");

            if (finId == -1) {
                return;
            }

            String callbackId =
                    parteId.substring(
                            0,
                            finId
                    );

            String url =
                    "https://api.telegram.org/bot"
                            + botToken
                            + "/answerCallbackQuery";

            String json = """
                    {
                      "callback_query_id": "%s"
                    }
                    """.formatted(callbackId);

            enviarPeticion(url, json);

        } catch (Exception e) {

            System.err.println(
                    "Error respondiendo callback: "
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // PROCESAR COMANDOS ESCRITOS
    // ============================================================

    private void procesarComando(String comando) {

        comando = comando.trim();

        if (comando.contains("@")) {

            comando =
                    comando.substring(
                            0,
                            comando.indexOf("@")
                    );
        }

        System.out.println(
                "COMANDO RECIBIDO = ["
                        + comando
                        + "]"
        );


        switch (comando) {

            case "/start":

                enviarMenu();

                break;


            case "/led_on":

                commandService.establecerComando(
                        "LED_ON"
                );

                enviarMensaje(
                        "💡 LED ENCENDIDO"
                );

                break;


            case "/led_off":

                commandService.establecerComando(
                        "LED_OFF"
                );

                enviarMensaje(
                        "💡 LED APAGADO"
                );

                break;


            case "/fan_on":

                commandService.establecerComando(
                        "FAN_ON"
                );

                enviarMensaje(
                        "🪭 VENTILADOR ENCENDIDO"
                );

                break;


            case "/fan_off":

                commandService.establecerComando(
                        "FAN_OFF"
                );

                enviarMensaje(
                        "🪭 VENTILADOR APAGADO"
                );

                break;


            case "/data":

                enviarDatosESP32();

                break;


            default:

                System.out.println(
                        "Comando no reconocido: "
                                + comando
                );

                enviarMensaje(
                        "❌ Comando no reconocido."
                );

                break;
        }
    }


    // ============================================================
    // ENVIAR DATOS DEL ESP32
    // ============================================================

    private void enviarDatosESP32() {

        Measurement data =
                sensorController.getUltimoDato();

        if (data == null) {

            enviarMensaje(
                    "⚠️ No hay datos recibidos del ESP32."
            );

            return;
        }

        String mensaje = """
                📊 DATOS MÁS RECIENTES DEL ESP32

                🌡️ Temperatura: %s °C
                💧 Humedad: %s %%
                📶 RSSI: %s dBm
                🪭 Ventilador: %s
                """.formatted(
                data.getTemperature(),
                data.getHumidity(),
                data.getRssi(),
                data.isFan()
                        ? "Encendido"
                        : "Apagado"
        );

        enviarMensaje(mensaje);
    }


    // ============================================================
    // PETICION HTTP
    // ============================================================

    private void enviarPeticion(
            String url,
            String json) {

        try {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(json)
                            )
                            .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            System.out.println(
                    "Codigo HTTP: "
                            + response.statusCode()
            );

            System.out.println(
                    "Respuesta Telegram: "
                            + response.body()
            );

        } catch (Exception e) {

            System.err.println(
                    "Error en peticion Telegram: "
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // ESCAPAR JSON
    // ============================================================

    private String escaparJson(String texto) {

        return texto
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}