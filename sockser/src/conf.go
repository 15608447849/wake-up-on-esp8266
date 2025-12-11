package main

import "time"
import "fmt"
 

// 服务器配置参数
const (
	DEFAULT_PORT           = 8080                // 默认监听端口
	MAX_CONNECTIONS        = 1000               // 最大连接数
	MESSAGE_BUFFER_SIZE            = 1024                // 缓冲区大小
	HEARTBEAT_CHECK_INTERVAL = 10 * time.Second // 心跳检查间隔 秒 
	HEARTBEAT_TIMEOUT      = 30 * time.Second   // 心跳超时时间 秒
)

// 命令类型常量
const (
	CMD_HEARTBEAT = "heartbeat" // 心跳命令
	CMD_FORWARD   = "forward"   // 转发命令
	CMD_WAKE_ON_LAN   = "wake_on_lan"   // 下发命令
)

// 客户端类型常量
const (
	TYPE_APP     = "app"     // 移动应用
	TYPE_ESP8266 = "esp8266" // ESP8266设备
)


// 错误代码常量
const (
	ERR_JSON_INVALID       = 1001 // JSON格式错误
	ERR_MESSAGE_TOO_LARGE  = 1002 // 消息过大
	ERR_CLIENT_NOT_FOUND   = 1003 // 客户端未找到
	ERR_DEVICE_OFFLINE     = 1004 // 设备离线
	ERR_CONNECTION_TIMEOUT = 1005 // 连接超时
	ERR_SEND_FAILED        = 1006 // 发送失败
	ERR_INVALID_CLIENT_TYPE = 1007 // 无效客户端类型
	ERR_MISSING_FIELD      = 1008 // 缺少必填字段
)


// JSON消息结构体
type Message struct {
	Host string `json:"host"`          // 客户端内网IP
	Type string `json:"type"`          // 客户端类型: app, esp8266
	Cmd  string `json:"cmd"`           // 命令类型: heartbeat, forward
	Data string `json:"data"`          // 消息内容
	ToIP string `json:"toip,omitempty"` // 目标设备IP（可选）
}
 







// 自定义错误类型
type ServerError struct {
	Code    int    // 错误代码
	Message string // 错误信息
}

// 创建新的服务器错误
func NewServerError(code int, message string) *ServerError {
	return &ServerError{
		Code:    code,
		Message: message,
	}
}

// 实现error接口
func (e *ServerError) Error() string {
	 
	return fmt.Sprintf("错误码[%d] 错误原因:%s", e.Code, e.Message)
}