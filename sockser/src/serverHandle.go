package main

import (
	"fmt"
	"time"
	"strconv"
	"encoding/json"
)


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
			clientConn.Conn.SetWriteDeadline(time.Now().Add(10 * time.Second))

			// 发送数据
			_, err := clientConn.Conn.Write(data)
			if err != nil {
				clientConn.Close()
				return
			}

		case <-clientConn.CloseChan:
			// 收到关闭信号
			return
		}
	}

}



// 处理单个客户端连接
func (s *TCPServer) handleClientConnection(clientConn *ClientConnection) {

	defer func() {
		if r := recover(); r != nil {
			// 连接处理协程异常
		}
		clientConn.Close()
	}()

	// 启动发送协程
	go s.handleSendMessages(clientConn)

	// 主循环：读取和处理消息
	for clientConn.IsActive {

		// 创建1KB缓冲区 读取固定大小的消息
		buffer := make([]byte, MESSAGE_BUFFER_SIZE)
		// 设置读取超时
		clientConn.Conn.SetReadDeadline(time.Now().Add(30 * time.Second))

		// 读取数据到缓冲区
		n, err := clientConn.Conn.Read(buffer)
		if err != nil {
			//fmt.Printf("读取缓冲区错误 %v \n" , err)
			return
		}

		// 只返回实际读取的数据
		data := buffer[:n]
		
		// 跳过空消息
		if len(data) == 0 {
			continue
		}

		// 解析JSON消息
		var msg Message
		err = json.Unmarshal(data, &msg)
		if err != nil {
			fmt.Printf("JSON序列化 错误 %v \n" , err)
			return
		}
		fmt.Printf("客户端 %v 收到消息 %v \n", clientConn, msg)

		// 更新连接信息
		err = clientConn.UpdateClientInfo( msg.Type,  msg.Host)
		if err != nil {
			// 打印错误消息
			fmt.Printf("客户端 %v 更新信息错误 %v \n", clientConn, err)
			continue
		}

		// 处理消息
		switch msg.Cmd {

			case CMD_HEARTBEAT:
				// 更新心跳
				clientConn.UpdateHeartbeat()
				
				// 创建心跳响应
				responseMsg := &Message{
					Cmd:  CMD_HEARTBEAT,
					Data: strconv.FormatInt(time.Now().UnixMilli(), 10),
				}
				// 回复信息
				responseData, err := json.Marshal(responseMsg)
				if err != nil {
					fmt.Printf("JSON反序列化 错误: %v \n", err)
					continue
				}
				
				clientConn.SendMessage(responseData)

			case CMD_FORWARD:
				// 更新心跳
				clientConn.UpdateHeartbeat()

				// 转发消息
				if msg.Type == TYPE_APP {
					fmt.Printf("客户端 %v 转发消息: %v \n", clientConn, msg.Data)
					sendnum := s.forwardToEsp8266(msg.Data)
					// 回复转发结果
					responseMsg := &Message{
						Cmd:  CMD_FORWARD,
						Data: strconv.Itoa(sendnum),
					}
					responseData, err := json.Marshal(responseMsg)
						if err != nil {
						fmt.Printf("JSON反序列化 错误: %v \n", err)
						continue
					}
					clientConn.SendMessage(responseData)
				}

			default:
				continue
		}

	}

}



// 转发消息
func (s *TCPServer) forwardToEsp8266(data string) int {
	s.mutex.RLock()
	defer s.mutex.RUnlock()

	// 创建命令响应
	responseMsg := &Message{
		Cmd:  CMD_WAKE_ON_LAN,
		Data: data,
	}

	responseData, err := json.Marshal(responseMsg)
	if err != nil {
		fmt.Printf("JSON反序列化 错误: %v \n", err)
		return 0
	}
	sendnum := 0
	for _, clientConn := range s.clientConnections {
		if clientConn.IsActive && clientConn.ClientType == TYPE_ESP8266 {
			 // 发送消息
			 clientConn.SendMessage(responseData)
			 sendnum++
		}
	}
	return sendnum
}