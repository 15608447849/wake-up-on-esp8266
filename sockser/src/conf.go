package main

import "time"
import "fmt"

// 服务器配置参数
const (
	DEFAULT_PORT             = 8080             // 默认监听端口
	MAX_CONNECTIONS          = 1000             // 最大连接数
	MESSAGE_BUFFER_SIZE      = 1024             // 缓冲区大小
	HEARTBEAT_CHECK_INTERVAL = 10 * time.Second // 心跳检查间隔 秒
	HEARTBEAT_TIMEOUT        = 30 * time.Second // 心跳超时时间 秒
)

// 命令类型常量
const (
	CMD_NETWORK_ADDRESS            = "net_ip"           // 远程地址
	CMD_HEARTBEAT                  = "heartbeat"        // 心跳命令
	CMD_WAKE_ON_LAN                = "wol"              // 网络唤醒命令
	CMD_WAKE_ON_LAN_DEVICE_SIZE    = "wol_rec_dev_size" // 网络唤醒命令 接收设备数量
	CMD_WAKE_ON_LAN_DEVICE_RECEIPT = "wol_rec_dev_recp" // 网络唤醒命令 接收设备 回执
)

// 客户端类型常量
const (
	TYPE_APP     = "app"     // 移动应用
	TYPE_ESP8266 = "esp8266" // ESP8266设备
)

// 错误代码常量
const (
	ERR_CONNECTION_TIMEOUT         = 1001 // 连接超时
	ERR_SEND_FAILED                = 1002 // 发送失败
	ERR_INVALID_CLIENT_TYPE        = 1003 // 无效客户端类型
	ERR_JSON_SERIALIZATION_ERROR   = 1004 // 序列化错误
	ERR_JSON_DESERIALIZATION_ERROR = 1004 // 反序列化错误
)

// JSON消息结构体
type Message struct {
	Host string `json:"host,omitempty"` // 客户端 内网IP
	Type string `json:"type,omitempty"` // 客户端 类型
	Cmd  string `json:"cmd"`            // 命令类型
	Data string `json:"data"`           // 消息内容
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
