package main

import (
	"encoding/json"
	"fmt"
	"net"
	"strconv"
	"time"
)

// 处理单个客户端连接
func (s *TCPServer) handleClientConnection(clientConn *ClientConnection) {

	// defer 延迟执行
	// panic 是一种内置机制，用于表示程序遇到了无法恢复的严重错误，必须立即停止当前 goroutine 的正常执行流程
	// recover() 是 Go 中 唯一能捕获 panic 的方式，但 必须在 defer 函数中调用才有效
	// 如果不捕获，整个程序会崩溃退出（Go 中 panic 默认终止进程
	// 隔离单个客户端错误，不影响其他连接 优雅关闭异常连接
	defer func() {
		if r := recover(); r != nil {
			// 连接处理协程异常
		}
		_ = clientConn.Close()
	}()

	// 启动发送协程
	go s.handleSendMessages(clientConn)

	// 通知客户端 net地址
	publicIpAddress, _, _ := net.SplitHostPort(clientConn.RemoteAddr)
	message := &Message{
		Cmd:  CMD_NETWORK_ADDRESS,
		Data: publicIpAddress,
	}

	err := clientConn.SendMessageEntity(message)
	if err != nil {
		fmt.Printf("客户端 %v 发送错误 %v \n", clientConn, err)
		return
	}

	// 主循环：读取和处理消息
	for clientConn.IsActive {

		// 创建1KB缓冲区 读取固定大小的消息
		buffer := make([]byte, MESSAGE_BUFFER_SIZE)
		// 设置读取超时
		_ = clientConn.Conn.SetReadDeadline(time.Now().Add(30 * time.Second))

		// 读取数据到缓冲区
		n, err := clientConn.Conn.Read(buffer)
		if err != nil {
			fmt.Printf("客户端 %v 读取缓冲区错误 %v \n", clientConn, err)
			return
		}

		// 只返回实际读取的数据
		data := buffer[:n]

		// 跳过空消息
		if len(data) == 0 {
			continue
		}

		// 解析 JSON 消息
		var msg Message
		err = json.Unmarshal(data, &msg)
		if err != nil {
			fmt.Printf("客户端 %v JSON反序列化 错误 %v \n", clientConn, err)
			return
		}
		fmt.Printf("客户端 %v 收到消息 %v \n", clientConn, msg)

		// 更新连接信息
		err = clientConn.UpdateClientInfo(msg.Type, msg.Host)
		if err != nil {
			// 打印错误消息
			fmt.Printf("客户端 %v 更新信息错误 %v \n", clientConn, err)
			return
		}
		// 更新心跳时间
		clientConn.UpdateHeartbeat()

		// 处理消息
		switch msg.Cmd {

		case CMD_HEARTBEAT:
			// 回复心跳响应
			message := &Message{
				Cmd:  CMD_HEARTBEAT,
				Data: strconv.FormatInt(time.Now().UnixMilli(), 10),
			}
			err := clientConn.SendMessageEntity(message)
			if err != nil {
				fmt.Printf("客户端 %v 发送错误 %v \n", clientConn, err)
				return
			}

		case CMD_WAKE_ON_LAN:
			// 网络唤醒
			if msg.Type == TYPE_APP {
				fmt.Printf("客户端 %v 网络唤醒: %v \n", clientConn, msg.Data)
				sendDeviceNumber := s.wakeOnLanToEsp8266(msg.Data)
				// 回复下发结果
				message := &Message{
					Cmd:  CMD_WAKE_ON_LAN_DEVICE_SIZE,
					Data: strconv.Itoa(sendDeviceNumber), // 下发设备数量
				}
				err := clientConn.SendMessageEntity(message)
				if err != nil {
					fmt.Printf("客户端 %v 发送错误 %v \n", clientConn, err)
					return
				}
			}
		case CMD_WAKE_ON_LAN_DEVICE_RECEIPT:
			// 网络唤醒回执
			fmt.Printf("客户端 %v 网络唤醒回执: %v \n", clientConn, msg.Data)
			s.wakeOnLanReceiptToApp(msg.Data)

		default:
			continue
		}

	}

}

// 处理发送消息的协程
func (s *TCPServer) handleSendMessages(clientConn *ClientConnection) {
	defer func() {
		if r := recover(); r != nil {
			// 发送协程异常
		}
	}()

	for {
		select {
		case data, ok := <-clientConn.SendChan:
			if !ok {
				// 通道已关闭
				return
			}

			// 设置写入超时
			_ = clientConn.Conn.SetWriteDeadline(time.Now().Add(10 * time.Second))

			// 发送数据
			_, err := clientConn.Conn.Write(data)
			if err != nil {
				_ = clientConn.Close()
				return
			}
			fmt.Printf("客户端 %v 发送 %d 字节:%s\n", clientConn, len(data), string(data))

		case <-clientConn.CloseChan:
			// 收到关闭信号
			return
		}
	}

}

// 网络唤醒 通过esp8266开发板完成命令
func (s *TCPServer) wakeOnLanToEsp8266(macAddress string) int {
	s.mutex.RLock()
	defer s.mutex.RUnlock()

	// 创建命令响应
	message := &Message{
		Cmd:  CMD_WAKE_ON_LAN,
		Data: macAddress,
	}

	// 发送设备数量
	sendDeviceNumber := 0
	for _, clientConn := range s.clientConnections {
		if clientConn.IsActive && clientConn.ClientType == TYPE_ESP8266 {
			// 下发 wol 命令消息
			err := clientConn.SendMessageEntity(message)
			if err != nil {
				fmt.Printf("客户端 %v 发送错误 %v \n", clientConn, err)
				continue
			}
			sendDeviceNumber++
		}
	}
	return sendDeviceNumber
}

// 网络唤醒 通过esp8266开发板完成命令
func (s *TCPServer) wakeOnLanReceiptToApp(macAddress string) {
	s.mutex.RLock()
	defer s.mutex.RUnlock()

	// 创建命令响应
	message := &Message{
		Cmd:  CMD_WAKE_ON_LAN_DEVICE_RECEIPT,
		Data: macAddress,
	}

	for _, clientConn := range s.clientConnections {
		if clientConn.IsActive && clientConn.ClientType == TYPE_APP {
			// 下发 wol 命令消息
			err := clientConn.SendMessageEntity(message)
			if err != nil {
				fmt.Printf("客户端 %v 发送错误 %v \n", clientConn, err)
				continue
			}

		}
	}

}
