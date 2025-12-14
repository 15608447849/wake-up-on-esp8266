#include <Adafruit_GFX.h>
#include <Adafruit_ST7735.h>
#include <SPI.h>
#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <ESP8266WebServer.h>
#include <NTPClient.h>
#include <WiFiUdp.h>
#include <ezTime.h> 
#include <ArduinoJson.h>
#include <DNSServer.h>
#include <EEPROM.h>
#include "qrcode.h" 
 
// 硬件引脚
#define TFT_CS     D1
#define TFT_RST    D2
#define TFT_DC     D3

// 二维码相关定义
#define QR_VERSION 3        // 二维码版本(1-40，版本越高容量越大)
#define QR_MODULE_SIZE 3    // 每个模块的像素大小

// EEPROM 地址定义 
#define WIFI_SSID_ADDR  0 // ssid存储地址
#define WIFI_PASS_ADDR  32 // 密码存储地址
#define CONFIG_FLAG_ADDR 64 // 标志位

// 屏幕对象 
Adafruit_ST7735 tft = Adafruit_ST7735(TFT_CS, TFT_DC, TFT_RST);
// 时区对象
Timezone myTZ; 

// socket 服务信息
const char* serverIP = "espsock.devtask.cn"; // socket 服务器 IP
const uint16_t serverPort = 8080;// 端口
// sock 客户端
WiFiClient tcpClient;
// 最后心跳时间
unsigned long lastHeartbeat = 0;
// 心跳间隔 
const unsigned long HEARTBEAT_INTERVAL = 10 * 1000; 
// 是否已经连接
bool isConnected = false;
// 缓冲区大小
uint8_t msgBuffer[100] = {0};
// 实际接收大小
size_t msgLen = 0;
// 超时阈值-毫秒
const unsigned long TIMEOUT_MS = 500; 

// 保存的SSID
String inputSSID = "";
// 保存的密码
String inputPassword = "";
// 是否配置中
bool isConfigMode = false;

// 配网热点配置 热点名称
const char* ap_ssid = "ESP8266-Config"; 
// 配网热点配置 热点密码
const char* ap_password = "00000000";
// Web服务 端口80
ESP8266WebServer server(80);


// EEPROM - 读取 Wi-Fi 配置
bool readWifi() {
  EEPROM.begin(65);
  if (EEPROM.read(CONFIG_FLAG_ADDR) != 0xAA) {
    Serial.println("没有wifi配置");
    EEPROM.end();
    return false; // 无有效配置
  }
  // 读 SSID
  for (int i = 0; i < 32; i++) {
    char c = EEPROM.read(WIFI_SSID_ADDR + i);
    if (c == 0) break;
    inputSSID += c;
  }
  Serial.print("read wifi ssid:");
  Serial.println(inputSSID);
 
  // 读 密码
  for (int i = 0; i < 32; i++) {
    char c = EEPROM.read(WIFI_PASS_ADDR + i);
    if (c == 0) break;
    inputPassword += c;
  }
  Serial.print("read wifi password:");
  Serial.println(inputPassword);
  
  EEPROM.end();
  return true;
}

// EEPROM - 写入 Wi-Fi 配置
void saveWifi(const String& ssid, const String& password) {
  EEPROM.begin(65);
  // 清空旧数据
  for (int i = 0; i < 64; i++) EEPROM.write(i, 0);

  // 写 SSID 31字符 + 1个结束符('\0')
  for (int i = 0; i < ssid.length() && i < 31; i++) {
    EEPROM.write(WIFI_SSID_ADDR + i , ssid[i]);
  }
  // 写密码 31字符 + 1个结束符('\0')
  for (int i = 0; i < password.length() && i < 31; i++) {
    EEPROM.write(WIFI_PASS_ADDR + i, password[i]);
  }
  // 标记配置有效 1字节
  EEPROM.write(CONFIG_FLAG_ADDR, 0xAA);
  EEPROM.commit();  // 提交到Flash
  EEPROM.end();

  Serial.println("WiFi配置已保存");
}



// 显示配网信息页面
void showQRInfo(String url) {
  tft.fillScreen(ST7735_BLACK);
  tft.setTextSize(1);
  tft.setTextColor(ST7735_WHITE);
  
  // 显示标题
  tft.setCursor(25, 10);
  tft.println("WiFi connect to");
  
  // 显示热点信息
  tft.setCursor(5, 30);
  tft.setTextColor(ST7735_YELLOW);
  tft.print("ssid:");
  tft.println(ap_ssid);
  
  tft.setCursor(5, 45);
  tft.print("password:");
  tft.println(ap_password);
  
  // 显示配网地址
  tft.setCursor(5, 65);
  tft.setTextColor(ST7735_CYAN);
  tft.println("config url:");
  tft.setCursor(5, 80);
  tft.println(url);
  
}

// 绘制二维码
void drawQRCode(String qr_url) {
  // 创建二维码对象
  QRCode qrcode;
  uint8_t qrcodeData[qrcode_getBufferSize(QR_VERSION)];
  
  // 生成二维码数据
  qrcode_initText(&qrcode, qrcodeData, QR_VERSION, 0, qr_url.c_str());
  
  // 清屏准备绘制全屏二维码
  tft.fillScreen(ST7735_BLACK);
  
  // 计算二维码在屏幕上的位置(居中显示)
  int qr_size = qrcode.size;
  int pixel_size = min(110 / qr_size, QR_MODULE_SIZE);  // 自适应像素大小
  int start_x = (128 - qr_size * pixel_size) / 2;
  // int start_y = (128 - qr_size * pixel_size) / 2;
  int start_y = 5; 


  Serial.print("二维码大小: ");
  Serial.print(qr_size);
  Serial.print("x");
  Serial.println(qr_size);
  Serial.print("像素大小: ");
  Serial.println(pixel_size);

  // 绘制二维码
  for (int y = 0; y < qr_size; y++) {
    for (int x = 0; x < qr_size; x++) {
      if (qrcode_getModule(&qrcode, x, y)) {
        // 绘制黑色模块
        tft.fillRect(start_x + x * pixel_size, 
                     start_y + y * pixel_size, 
                     pixel_size, pixel_size, 
                     ST7735_WHITE);
      }
    }
  }
  
 
  // 在二维码下方显示SSID和密码
  tft.setTextSize(1);
  // 第一行：显示SSID
  tft.setCursor(5, 110);
  tft.setTextColor(ST7735_YELLOW);
  tft.print("SSID: ");
  tft.setTextColor(ST7735_WHITE);
  tft.println(ap_ssid);
  // 第二行：显示密码
  tft.setCursor(5, 120);
  tft.setTextColor(ST7735_CYAN);
  tft.print("PWD: ");
  tft.setTextColor(ST7735_WHITE);
  tft.println(ap_password);

}

// 配网模式
void startConfigMode(){
  isConfigMode = true;

   // 断开所有WiFi连接
  WiFi.disconnect();
  delay(100);
  WiFi.mode(WIFI_AP);
  delay(100);

  Serial.print("热点名称");
  Serial.println(ap_ssid);
  Serial.print("热点密码");
  Serial.println(ap_password);

  // 创建WiFi热点
  bool result = WiFi.softAP(ap_ssid, ap_password);
  if (!result) { 
    Serial.println("热点创建失败");
    return;
  }
  IPAddress ip = WiFi.softAPIP();
  Serial.println("热点已创建");
 
  Serial.println(ip.toString());

 
  // 构建完整的配网URL
  String qr_url = "http://" + ip.toString();
   // 显示二维码信息页面
  showQRInfo(qr_url);
  // 显示信息
  delay(5000);  
  // 然后显示访问二维码
  drawQRCode(qr_url);

  // 设置Web服务器路由
  setupWebServer();
  // 启动Web服务器
  server.begin();
  Serial.println("Web服务器已启动");
}

 
// 配网html页面
String getConfigPageHTML() {
  // 普通字符串拼接：手动转义双引号，精简换行
  String html = "<!DOCTYPE html>\n"
                "<html>\n"
                "<head>\n"
                "    <meta charset=\"UTF-8\">\n"
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                "    <title>WiFi配网</title>\n"
                "    <style>\n"
                "        body{font-family:Arial;margin:20px;background:#f5f5f5}\n"
                "        .container{max-width:350px;margin:0 auto;background:white;padding:15px;border-radius:8px}\n"
                "        h1{text-align:center;color:#333;margin-bottom:20px}\n"
                "        input{width:100%;padding:8px;margin:5px 0;border:1px solid #ddd;border-radius:4px;box-sizing:border-box}\n"
                "        button{width:100%;padding:10px;background:#007bff;color:white;border:none;border-radius:4px;cursor:pointer;margin:5px 0}\n"
                "        button:hover{background:#0056b3}\n"
                "        .scan{background:#28a745}\n"
                "        .scan:hover{background:#1e7e34}\n"
                "        .net{padding:6px;border:1px solid #ddd;margin:2px 0;cursor:pointer;border-radius:3px}\n"
                "        .net:hover{background:#f8f9fa}\n"
                "        .sig{float:right;color:#666}\n"
                "        .loading{text-align:center;color:#666}\n"
                "    </style>\n"
                "</head>\n"
                "<body>\n"
                "    <div class=\"container\">\n"
                "        <h1>WiFi配网</h1>\n"
                "        <button class=\"scan\" onclick=\"scan()\">扫描网络</button>\n"
                "        <div id=\"nets\"></div>\n"
                "        <form onsubmit=\"save(event)\">\n"
                "            <input type=\"text\" id=\"ssid\" placeholder=\"WiFi名称\" required>\n"
                "            <input type=\"password\" id=\"pwd\" placeholder=\"WiFi密码\">\n"
                "            <button type=\"submit\">保存配置</button>\n"
                "        </form>\n"
                "    </div>\n"
                "    <script>\n"
                "        let scanRequest = null;\n"
                "        function escapeHtml(str) {\n"
                "            return str.replace(/['\"\\\\]/g, '\\\\$&').replace(/\\n/g, '\\\\n');\n"
                "        }\n"
                "        function scan(){\n"
                "            if(scanRequest) scanRequest.abort();\n"
                "            document.getElementById('nets').innerHTML='<div class=\"loading\">扫描中...</div>';\n"
                "            scanRequest = fetch('/scan');\n"
                "            scanRequest.then(r=>{\n"
                "                if(!r.ok) throw new Error('请求失败');\n"
                "                return r.json();\n"
                "            }).then(nets=>{\n"
                "                let h='';\n"
                "                if(nets.length === 0) h='<div style=\"color:#666\">未发现WiFi网络</div>';\n"
                "                else nets.forEach(n=>{\n"
                "                    let sec = n.secure ? '🔒' : '';\n"
                "                    let ssidEscaped = escapeHtml(n.ssid);\n"
                "                    h+='<div class=\"net\" onclick=\"sel(\\'' + ssidEscaped + '\\')\">' + n.ssid + ' ' + sec + '<span class=\"sig\">' + n.rssi + 'dBm</span></div>';\n"
                "                });\n"
                "                document.getElementById('nets').innerHTML=h;\n"
                "            }).catch(()=>{\n"
                "                document.getElementById('nets').innerHTML='<div style=\"color:red\">扫描失败，请重试</div>';\n"
                "            }).finally(()=>{\n"
                "                scanRequest = null;\n"
                "            });\n"
                "        }\n"
                "        function sel(ssid){\n"
                "            document.getElementById('ssid').value = ssid.replace(/\\\\'/g, '\\'').replace(/\\\\\"/g, '\"');\n"
                "        }\n"
                "        function save(e){\n"
                "            e.preventDefault();\n"
                "            let s=document.getElementById('ssid').value.trim();\n"
                "            let p=document.getElementById('pwd').value;\n"
                "            if(!s){alert('请输入WiFi名称');return;}\n"
                "            let f=new FormData();\n"
                "            f.append('ssid',s);\n"
                "            f.append('password',p);\n"
                "            fetch('/save',{method:'POST',body:f}).then(r=>{\n"
                "                if(!r.ok) throw new Error('保存失败');\n"
                "                return r.text();\n"
                "            }).then(res=>{\n"
                "                alert(res || '配置保存成功，设备即将重启');\n"
                "                setTimeout(()=>window.location.reload(), 1000);\n"
                "            }).catch(e=>alert('保存失败：' + e.message));\n"
                "        }\n"
                "        window.onload = ()=>setTimeout(scan, 500);\n"
                "    </script>\n"
                "</body>\n"
                "</html>";
  return html;
}

// 扫描WiFi网络
String scanWiFiNetworks() {
  Serial.println("开始扫描WiFi网络...");
  int n = WiFi.scanNetworks();
  String json = "[";
  for (int i = 0; i < n; i++) {
    if (i > 0) json += ",";
    json += "{";
    json += "\"ssid\":\"" + WiFi.SSID(i) + "\",";
    json += "\"rssi\":" + String(WiFi.RSSI(i)) + ",";
    json += "\"secure\":" + String(WiFi.encryptionType(i) != ENC_TYPE_NONE);
    json += "}";
  }
  json += "]";
  Serial.println("扫描完成，发现 " + String(n) + " 个网络");
  return json;
}

// 设置Web服务器路由
void setupWebServer() {
  // 主页路由 - 显示配网页面
  server.on("/", []() {
    String html = getConfigPageHTML();
    server.send(200, "text/html", html);
  });
  
  // 扫描WiFi网络路由
  server.on("/scan", []() {
    String json = scanWiFiNetworks();
    server.send(200, "application/json", json);
  });
  
  // 保存WiFi配置路由
  server.on("/save", []() {
    if (server.hasArg("ssid") && server.hasArg("password")) {
      String ssid = server.arg("ssid");
      String password = server.arg("password");
  
      Serial.println("收到WiFi配置: " + ssid);
      // 保存配置
      saveWifi(ssid, password);

      // 返回成功响应
      server.send(200, "text/plain", "配置保存成功，正在重启...");
      
      tft.fillScreen(ST7735_BLACK);
      tft.setTextColor(ST7735_GREEN);
      tft.println("config wifi success");
      tft.setTextColor(ST7735_RED);
      tft.println("restart...");

      delay(2000);
      ESP.restart();  // 重启ESP8266
    } else {
      server.send(400, "text/plain", "参数错误");
    }
  });
}



void startConnectWifi(){
    // 连接wifi
      WiFi.begin(inputSSID.c_str(), inputPassword.c_str());
      Serial.println(inputSSID.c_str());
      Serial.println(inputPassword.c_str());
      Serial.println("wifi 开始连接");
     
      int attempts = 0;
      while (WiFi.status() != WL_CONNECTED && attempts < 20) {
        delay(500);
        attempts++;

        // Serial.print("连接状态: ");
        // Serial.println(WiFi.status());
        Serial.print("尝试次数: ");
        Serial.println(attempts);
        
         // 更新屏幕显示连接进度
        tft.fillScreen(ST7735_BLACK);
        tft.setTextColor(ST7735_CYAN);
        tft.setTextSize(1);
        String connectText = "wifi connect";
        for (int i = 0; i < (attempts % 4); i++) {
           connectText += ".";
        }
        // 水平和垂直都居中
        int textWidth = connectText.length() * 6;
        int centerX = (128 - textWidth) / 2;
        int centerY = (128 - 8) / 2;  // 8是字符高度
        tft.setCursor(centerX, centerY);
        tft.print(connectText);
      }

      if (WiFi.status() == WL_CONNECTED) {
        Serial.println("WiFi连接成功");
        Serial.print("IP地址: ");
        Serial.println(WiFi.localIP().toString());
        tft.fillScreen(ST7735_BLACK);
        tft.setCursor(5, 50);
        tft.setTextSize(2);
        tft.println(WiFi.localIP().toString());
      } else {
        Serial.println("WiFi连接失败");
        tft.fillScreen(ST7735_BLACK);
        tft.setTextColor(ST7735_RED);
        tft.setTextSize(2);
        tft.setCursor(5, 50);
        tft.print("connect fail, status:");
        tft.print(WiFi.status());
         
        delay(3000);
      }
}


// 网络唤醒函数
void sendWakeOnLAN(String macAddress) {
  WiFiUDP udp;
  
  // 解析MAC地址 (格式: "AA:BB:CC:DD:EE:FF")
  uint8_t mac[6];
  if (!parseMacAddress(macAddress, mac)) {
    Serial.println("MAC地址格式错误");
    return;
  }
  
  // 创建魔术包 (102字节)
  uint8_t magicPacket[102];
  
  // 前6个字节填充0xFF
  for (int i = 0; i < 6; i++) {
    magicPacket[i] = 0xFF;
  }
  
  // 后面96字节：MAC地址重复16次
  for (int i = 0; i < 16; i++) {
    for (int j = 0; j < 6; j++) {
      magicPacket[6 + i * 6 + j] = mac[j];
    }
  }
  
  // 发送UDP广播包
  udp.beginPacket("255.255.255.255", 9);  // 广播地址，端口9
  udp.write(magicPacket, 102);
  bool result = udp.endPacket();
  
  tft.fillScreen(ST7735_BLACK);
  tft.setTextSize(1);
  tft.setCursor(0, 30);
  if (result) {
    tft.println("WOL sendto ");
    tft.setTextColor(ST7735_YELLOW);
    tft.println(macAddress);
  } else {
    tft.setTextColor(ST7735_RED);
    Serial.println("WOL send fail");
  }
  delay(3000);
}

// 解析MAC地址字符串
bool parseMacAddress(String macStr, uint8_t* mac) {
  // 移除可能的分隔符并转换为大写
  macStr.replace(":", "");
  macStr.replace("-", "");
  macStr.replace(" ", "");
  macStr.toUpperCase();
  
  // 检查长度
  if (macStr.length() != 12) {
    return false;
  }
  
  // 转换16进制字符串到字节数组
  for (int i = 0; i < 6; i++) {
    String byteStr = macStr.substring(i * 2, i * 2 + 2);
    mac[i] = (uint8_t)strtol(byteStr.c_str(), NULL, 16);
  }
  
  return true;
}

// 连接tcp服务
void connectTcpServer(){
  if(isConnected) return;

  if (!tcpClient.connect(serverIP, serverPort)) {
    Serial.println("Tcp- 连接失败");
    return;
  }

  isConnected = true;
  Serial.println("Tcp- 连接成功");

  sendTcpHeartbeat();
  lastHeartbeat = millis();
}


// 发送心跳
void sendTcpHeartbeat() {
  if (!isConnected || !tcpClient.connected()) {
    isConnected = false;
    return;
  }

  char formatted[20];
  sprintf(formatted, "%lu", millis());

  StaticJsonDocument<100> doc;
    doc["data"] = formatted;
    doc["cmd"] = "heartbeat";
    doc["host"] = WiFi.localIP().toString();
    doc["type"] = "esp8266";

  String json;
  size_t jsonLength = serializeJson(doc, json);
  tcpClient.write(json.c_str(),jsonLength); 

  Serial.printf("Tcp- 心跳已发送 | json=%s 字节数：%d\n", json.c_str(),jsonLength);
}

// 处理命令
void handleTcpCommand(const String& msg) {
  Serial.print("Tcp- 收到命令: ");
  Serial.println(msg);

  StaticJsonDocument<100> doc;
  DeserializationError error = deserializeJson(doc, msg);
  if (error) {
    Serial.print("Tcp- 处理命令 JSON 解析失败: ");
    Serial.println(error.c_str());
    return;
  }

  String cmd = doc["cmd"];
  // TODO: 根据 cmd 执行功能（如重启、控制 LED 等）
  if (cmd == "heartbeat") {
    Serial.print("Tcp- 收到心跳: ");
    Serial.println(doc["data"].as<const char*>());
    lastHeartbeat = millis();
  }
  if (cmd == "wake_on_lan") {
    Serial.print("Tcp- 网络唤醒: ");
    String mac = doc["data"];
    Serial.println(mac);
    sendWakeOnLAN(mac);
    Serial.println("Tcp- 网络唤醒 已发送");
  }
  
}

void readTcpMessage(){
  // 清空
  memset(msgBuffer, 0, sizeof(msgBuffer)); // 清空缓冲区
  msgLen = 0;

  if (!isConnected || !tcpClient.connected()) {
    isConnected = false;
    return;
  }

  unsigned long startTime = millis(); 
  // 读取指定超时时间 TIMEOUT_MS 或 msgLen<100
  while (millis() - startTime < TIMEOUT_MS && msgLen < 100) {
    // 检查是否有可读取的字节
    if (tcpClient.available() > 0) {
      // 读取1个字节到缓冲区
      msgBuffer[msgLen] = tcpClient.read();
      msgLen++;
    }else {
      // 无数据时短暂延时，降低CPU占用
      delay(1);
    }
  }


}



// 显示运行中状态
void showRunningStatus(){
  tft.fillScreen(ST7735_BLACK);

  // 显示tcp连接信息
  tft.setCursor(5, 5);
   tft.setTextSize(1);
  if(isConnected){
    tft.setTextColor(ST7735_GREEN);
    tft.print("tcp online");
  }else{
    tft.setTextColor(ST7735_RED);
    tft.print("tcp offline");
  }
  
  String smiley = "(o.o)!";
  tft.setTextSize(2);
  tft.setTextColor(ST7735_CYAN);
  int16_t x1, y1;
  uint16_t w, h; 
  tft.getTextBounds(smiley, 0, 0, &x1, &y1, &w, &h);  // 获取文本尺寸
   int centerX = (128 - w) / 2;
   tft.setCursor(centerX, 20);  // TCP状态下面
   tft.print(smiley);

  // 显示最新时间
  tft.setTextColor(ST7735_MAGENTA);
  tft.setTextSize(2);
  tft.setCursor(5,40);
  tft.print(myTZ.year());
  tft.print("-");
  tft.print(myTZ.month());
  tft.print("-");
  tft.print(myTZ.day());

  tft.setCursor(5,65);
  tft.setTextColor(ST7735_YELLOW);
  tft.print(myTZ.hour()); 
  tft.print(":");
  tft.print(myTZ.minute()); 
  tft.print(":");
  tft.print(myTZ.second());

  // 显示wifi信息
  tft.setTextSize(1);
  tft.setTextColor(ST7735_WHITE);
  tft.setCursor(0, 95);
  tft.print("SSID ");
  tft.println(inputSSID);
  tft.print("IP ");
  tft.println(WiFi.localIP());
  // tft.print("GETWATE:");
  // tft.println(WiFi.gatewayIP());
}

void setup(void) {
  // 串口 波特率115200
  Serial.begin(115200);
 
  // 初始化屏幕
  tft.initR(INITR_144GREENTAB);
  tft.setRotation(2);
  tft.setTextColor(ST7735_WHITE);
  tft.setTextSize(1);
  tft.fillScreen(ST7735_BLACK);
  
  Serial.println("tft初始化完成");

  // 读取保存的WiFi配置
  if(!readWifi()){
      // 进入配网模式
    startConfigMode();
  }else{
    // 连接wifi
    startConnectWifi();
    // wifi连接成功
    if (WiFi.status() == WL_CONNECTED) {
      // 连接tcp服务器
      connectTcpServer();
      // 同步时区
      waitForSync();
      myTZ.setLocation("Asia/Shanghai"); 

    }else{
      // 连接失败 进入配网模式
      startConfigMode();
    }

  }

}

void loop(void) {
  if (isConfigMode) {
    // 配网模式 处理Web服务器请求
    server.handleClient();  
    delay(50);
    return;
  }
  if (WiFi.status() != WL_CONNECTED) {
    delay(1000);
    // 重启设备
    ESP.restart();  
    return;
  }

  // 处理时间更新
  events();

  // 显示运行中状态
  showRunningStatus();

  // tcp连接中
  if (isConnected){
      // 读取消息
      readTcpMessage();

      // 处理命令
      if(msgLen > 0 ) {
        msgBuffer[msgLen] = '\0';
        String message = String((char*)msgBuffer);
        Serial.printf("Tcp- 读取消息 json=%s 字节数: %d\n", message.c_str(), msgLen);
        handleTcpCommand(message);
      }

      // 处理心跳
      if (millis() - lastHeartbeat > HEARTBEAT_INTERVAL) {
        sendTcpHeartbeat();
      }
    }else{
      // 连接tcp服务器
      connectTcpServer();
    }

}










