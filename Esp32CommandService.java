package ec.com.advanceit.iotmonitoreo.service;

import org.springframework.stereotype.Service;

@Service
public class Esp32CommandService {

    private String comandoPendiente = "NONE";

    public synchronized void establecerComando(String comando) {
        comandoPendiente = comando;
        System.out.println("Comando enviado al ESP32: " + comando);
    }

    public synchronized String obtenerComando() {

        String comando = comandoPendiente;

        comandoPendiente = "NONE";

        return comando;
    }
}