#include <WiFi.h>
#include <WebServer.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <SimpleDHT.h>
#include <ESP32Ping.h>

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define LED_PIN 27
#define RELAY_PIN 26
#define DHTPIN 4

const unsigned long INTERVALO_BD = 10000;
const unsigned long INTERVALO_COMANDO = 500;
const unsigned long INTERVALO_DHT = 2000;

unsigned long tiempoDHT = 0;
unsigned long tiempoJava = 0;
unsigned long tiempoComando = 0;

const char* red_wifi = "NETLIFE-stdmachicaa1";
const char* password = "1705552279";
const char* servidorJava = "http://192.168.1.5:8080/esp32/ingress";
const char* servidorComandos = "http://192.168.1.5:8080/esp32/command";

WebServer server(80);
Adafruit_SSD1306 display(SCREEN_WIDTH,SCREEN_HEIGHT,&Wire,-1);
SimpleDHT11 dht11(DHTPIN);

byte temperatura = 0;
byte humedad = 0;
int umbral = 30;
bool ventilador = false;
bool led = false;
bool modoManualFan = false;

String obtenerUptime() {
  unsigned long tiempo = millis() / 1000;
  int horas = tiempo / 3600;
  tiempo %= 3600;
  int minutos = tiempo / 60;
  int segundos = tiempo % 60;
  char buffer[20];
  sprintf(buffer,"%02d:%02d:%02d",horas,minutos,segundos);
  return String(buffer);
}

String barraWiFi() {
  int rssi = WiFi.RSSI();
  int nivel;
  if(rssi > -50) nivel = 5;
  else if(rssi > -60) nivel = 4;
  else if(rssi > -70) nivel = 3;
  else if(rssi > -80) nivel = 2;
  else nivel = 1;
  String barra = "";
  for(int i=0;i<nivel;i++) barra += "█";
  for(int i=nivel;i<5;i++) barra += "░";
  return barra;
}

void actualizarOLED() {
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0,0);
  display.println("ESP32 IoT");
  display.print("Temp: ");
  display.print(temperatura);
  display.println(" C");
  display.print("Hum: ");
  display.print(humedad);
  display.println(" %");
  display.print("Fan: ");
  display.println(ventilador ? "ON" : "OFF");
  display.print("Limite: ");
  display.print(umbral);
  display.println(" C");
  display.display();
}

void leerDHT() {
  if(millis()-tiempoDHT >= INTERVALO_DHT) {
    tiempoDHT = millis();
    int error = dht11.read(&temperatura,&humedad,NULL);
    if(error == SimpleDHTErrSuccess) {
      Serial.print("Temperatura: ");
      Serial.print(temperatura);
      Serial.println(" C");
      Serial.print("Humedad: ");
      Serial.print(humedad);
      Serial.println(" %");
      if(!modoManualFan) {
        if(temperatura >= umbral) {
          digitalWrite(RELAY_PIN,HIGH);
          ventilador = true;
        } else {
          digitalWrite(RELAY_PIN,LOW);
          ventilador = false;
        }
      }
      actualizarOLED();
    } else {
      Serial.println("Error DHT11");
    }
  }
}

void consultarComandoJava() {
  if(WiFi.status() != WL_CONNECTED) return;
  HTTPClient http;
  http.begin(servidorComandos);
  int httpCode = http.GET();
  if(httpCode > 0) {
    Serial.print("Codigo HTTP: ");
    Serial.println(httpCode);
    if(httpCode == HTTP_CODE_OK) {
      String comando = http.getString();
      comando.trim();
      Serial.print("Comando recibido: ");
      Serial.println(comando);
      if(comando == "LED_ON") {
        digitalWrite(LED_PIN,HIGH);
        led = true;
        Serial.println("LED ENCENDIDO POR TELEGRAM");
      } else if(comando == "LED_OFF") {
        digitalWrite(LED_PIN,LOW);
        led = false;
        Serial.println("LED APAGADO POR TELEGRAM");
      } else if(comando == "FAN_ON") {
        modoManualFan = true;
        digitalWrite(RELAY_PIN,HIGH);
        ventilador = true;
        Serial.println("VENTILADOR ENCENDIDO POR TELEGRAM");
      } else if(comando == "FAN_OFF") {
        modoManualFan = true;
        digitalWrite(RELAY_PIN,LOW);
        ventilador = false;
        Serial.println("VENTILADOR APAGADO POR TELEGRAM");
      }
    }
  } else {
    Serial.print("Error consultando Java: ");
    Serial.println(http.errorToString(httpCode));
  }
  http.end();
}

void enviarDatosJava() {
  Serial.println("--- DIAGNOSTICO RED ---");
  Serial.print("IP local: ");
  Serial.println(WiFi.localIP());
  Serial.print("Gateway: ");
  Serial.println(WiFi.gatewayIP());
  Serial.print("Subnet: ");
  Serial.println(WiFi.subnetMask());
  Serial.print("RSSI: ");
  Serial.println(WiFi.RSSI());
  Serial.print("Status: ");
  Serial.println(WiFi.status());
  if(WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi desconectado");
    return;
  }
  if(Ping.ping("192.168.1.5")) Serial.println("Ping al servidor OK");
  else Serial.println("Ping fallido");
  WiFiClient client;
  if(!client.connect("192.168.1.5",8080)) {
    Serial.println("No conecta al servidor Java");
    return;
  }
  Serial.println("Conexion TCP OK");
  client.stop();
  HTTPClient http;
  http.begin(servidorJava);
  http.addHeader("Content-Type","application/json");
  String json = "{";
  json += "\"temperature\":";
  json += String(temperatura);
  json += ",";
  json += "\"humidity\":";
  json += String(humedad);
  json += ",";
  json += "\"rssi\":";
  json += String(WiFi.RSSI());
  json += ",";
  json += "\"fan\":";
  json += ventilador ? "true" : "false";
  json += "}";
  Serial.println("Enviando a Java:");
  Serial.println(json);
  int respuesta = http.POST(json);
  if(respuesta > 0) {
    Serial.print("Respuesta Java: ");
    Serial.println(respuesta);
    Serial.println(http.getString());
  } else {
    Serial.print("Error HTTP: ");
    Serial.println(respuesta);
  }
  http.end();
}

void paginaWeb() {
  String estadoLED = digitalRead(LED_PIN) == HIGH ? "ENCENDIDO" : "APAGADO";
  String estadoFan = ventilador ? "ENCENDIDO" : "APAGADO";
  String pagina = R"=====(
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>ESP32 Dashboard</title>
<style>
body{background:black;color:#98FF96;font-family:Fantasy;padding:20px;}
h1{text-align:center;color:#98FF96;}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:20px;}
.card{background:#1e293b;padding:20px;border-radius:15px;text-align:center;box-shadow:0 4px 12px rgba(0,0,0,.3);}
.valor{font-size:28px;font-weight:bold;}
button{padding:15px 25px;border-radius:10px;border:none;font-size:18px;cursor:pointer;}
.verde{background:#98FF96;color:black;}
.rojo{background:#98FF96;color:black;}
input{padding:10px;font-size:18px;width:100px;}
</style>
</head>
<body>
<h1>ESP32 Dashboard</h1>
<div class="grid">
<div class="card"><h2>IP</h2><div class="valor">
)=====";
  pagina += WiFi.localIP().toString();
  pagina += R"=====(
</div></div>
<div class="card"><h2>WiFi</h2><div class="valor">
)=====";
  pagina += barraWiFi();
  pagina += R"=====(
<br>
)=====";
  pagina += String(WiFi.RSSI());
  pagina += R"=====(
dBm
</div></div>
<div class="card"><h2>Temperatura</h2><div class="valor">
)=====";
  pagina += String(temperatura);
  pagina += R"=====(
°C
</div></div>
<div class="card"><h2>Humedad</h2><div class="valor">
)=====";
  pagina += String(humedad);
  pagina += R"=====(
%
</div></div>
<div class="card"><h2>Ventilador</h2><div class="valor">
)=====";
  pagina += estadoFan;
  pagina += R"=====(
</div></div>
<div class="card"><h2>LED</h2><div class="valor">
)=====";
  pagina += estadoLED;
  pagina += R"=====(
</div></div>
<div class="card"><h2>Tiempo activo</h2><div class="valor">
)=====";
  pagina += obtenerUptime();
  pagina += R"=====(
</div></div>
<div class="card"><h2>Umbral</h2><div class="valor">
)=====";
  pagina += String(umbral);
  pagina += R"=====(
°C
</div></div>
</div>
<br>
<h2>Configurar temperatura máxima</h2>
<form action="/umbral">
<input type="number" name="valor" value=")=====";
  pagina += String(umbral);
  pagina += R"=====(">
<button class="verde">Guardar</button>
</form>
<br><br>
<a href="/on"><button class="verde">LED ON</button></a>
<a href="/off"><button class="rojo">LED OFF</button></a>
</body>
</html>
)=====";
  server.send(200,"text/html",pagina);
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN,OUTPUT);
  pinMode(RELAY_PIN,OUTPUT);
  digitalWrite(LED_PIN,LOW);
  digitalWrite(RELAY_PIN,LOW);
  Wire.begin(21,22);
  if(!display.begin(SSD1306_SWITCHCAPVCC,0x3C)) {
    Serial.println("No se encontró OLED");
    while(true);
  }
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0,0);
  display.println("Conectando WiFi...");
  display.display();
  WiFi.begin(red_wifi,password);
  WiFi.setSleep(false);
  Serial.print("Conectando WiFi");
  while(WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.println("WiFi conectado");
  Serial.print("IP ESP32: ");
  Serial.println(WiFi.localIP());
  display.clearDisplay();
  display.setCursor(0,0);
  display.println("WiFi OK");
  display.print("IP:");
  display.println(WiFi.localIP());
  display.display();
  server.on("/",paginaWeb);
  server.on("/on",[]() {
    digitalWrite(LED_PIN,HIGH);
    led = true;
    server.sendHeader("Location","/");
    server.send(303);
  });
  server.on("/off",[]() {
    digitalWrite(LED_PIN,LOW);
    led = false;
    server.sendHeader("Location","/");
    server.send(303);
  });
  server.on("/umbral",[]() {
    if(server.hasArg("valor")) {
      umbral = server.arg("valor").toInt();
      Serial.print("Nuevo umbral: ");
      Serial.println(umbral);
    }
    server.sendHeader("Location","/");
    server.send(303);
  });
  server.on("/api/datos",[]() {
    String json = "{";
    json += "\"temperatura\":";
    json += String(temperatura);
    json += ",";
    json += "\"humedad\":";
    json += String(humedad);
    json += ",";
    json += "\"rssi\":";
    json += String(WiFi.RSSI());
    json += ",";
    json += "\"ventilador\":";
    json += ventilador ? "true" : "false";
    json += "}";
    server.send(200,"application/json",json);
  });
  server.begin();
  Serial.println("Servidor web iniciado");
}

void loop() {
  server.handleClient();
  leerDHT();
  if(millis()-tiempoComando >= INTERVALO_COMANDO) {
    tiempoComando = millis();
    consultarComandoJava();
  }
  if(millis()-tiempoJava >= INTERVALO_BD) {
    tiempoJava = millis();
    enviarDatosJava();
  }
}