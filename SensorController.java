package ec.com.advanceit.iotmonitoreo.controller;

import ec.com.advanceit.iotmonitoreo.model.Measurement;
import ec.com.advanceit.iotmonitoreo.repository.MeasurementRepository;
import ec.com.advanceit.iotmonitoreo.service.TelegramService;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Lazy;

@RestController
@RequestMapping("/esp32")
public class SensorController {

    private final MeasurementRepository repository;
    private final TelegramService telegramService;

    private Boolean estadoAnteriorFan = null;
    private Measurement ultimoDato;
      public SensorController(
            MeasurementRepository repository,
            @Lazy TelegramService telegramService) {

        this.repository = repository;
        this.telegramService = telegramService;
    }

    @PostMapping("/ingress")
    public String saveData(@RequestBody Measurement data) {

        data.setDate(LocalDateTime.now());

        repository.save(data);

        ultimoDato = data;

        System.out.println("===== Datos recibidos del ESP32 =====");

        System.out.println("Temperatura: "
                + data.getTemperature() + " °C");

        System.out.println("Humedad: "
                + data.getHumidity() + " %");

        System.out.println("RSSI: "
                + data.getRssi() + " dBm");

        System.out.println("Ventilador: "
                + (data.isFan() ? "Encendido" : "Apagado"));
        // ==========================================
        // DETECTAR CAMBIO DEL VENTILADOR
        // ==========================================

        if (estadoAnteriorFan == null) {

            // Primera lectura.
            // Solo guardamos el estado actual.
            estadoAnteriorFan = data.isFan();

        } else if (estadoAnteriorFan != data.isFan()) {

            // El ventilador cambió de estado
            estadoAnteriorFan = data.isFan();

            String hora = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern(
                            "dd/MM/yyyy HH:mm:ss"
                    ));

            if (data.isFan()) {

                String mensaje =
                        "VENTILADOR ENCENDIDO\n\n"
                                + "Temperatura: "
                                + data.getTemperature()
                                + " °C\n"
                                + "Humedad: "
                                + data.getHumidity()
                                + " %\n"
                                + "Hora: "
                                + hora;

                telegramService.enviarMensaje(mensaje);

            } else {

                String mensaje =
                        "VENTILADOR APAGADO\n\n"
                                + "Temperatura: "
                                + data.getTemperature()
                                + " °C\n"
                                + "Humedad: "
                                + data.getHumidity()
                                + " %\n"
                                + "Hora: "
                                + hora;

                telegramService.enviarMensaje(mensaje);
            }
        }

        return "OK";
    }

    @GetMapping("/getData")
    public Object getData() {
        return repository.findAll();
    }
    public Measurement getUltimoDato() {
        return ultimoDato;
    }
}