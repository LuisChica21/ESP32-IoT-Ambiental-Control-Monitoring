package ec.com.advanceit.iotmonitoreo.controller;

import ec.com.advanceit.iotmonitoreo.service.Esp32CommandService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/esp32")
public class Esp32CommandController {

    private final Esp32CommandService commandService;

    public Esp32CommandController(Esp32CommandService commandService) {
        this.commandService = commandService;
    }

    @GetMapping("/command")
    public String getCommand() {
        return commandService.obtenerComando();
    }
}