package main

import (
	"fmt"
	"net"
	"sync"
	"time"
)


// 客户端连接信息
type ClientConnection struct {
	ID            string        // 连接唯一标识
	Conn          net.Conn      // TCP连接对象 
	ClientType    string        // 客户端类型: app, esp8266
	HostIP        string        // 客户端内网IP
	RemoteAddr    string        // 客户端连接地址
	LastHeartbeat time.Time     // 最后心跳时间
	IsActive      bool          // 连接状态
	SendChan      chan []byte   // 发送消息通道
	CloseChan     chan bool     // 关闭信号通道
	mutex         sync.RWMutex  // 读写锁保护并发访问
}

// 创建新的客户端连接
func NewClientConnection(conn net.Conn) *ClientConnection {
	return &ClientConnection{
		ID:            fmt.Sprintf("%d", time.Now().UnixNano()), // 生成连接ID
		Conn:          conn,
		ClientType:    "",           // 初始时未知类型
		HostIP:        "",           // 初始时未知IP
		RemoteAddr:    conn.RemoteAddr().String(),
		LastHeartbeat: time.Now(),
		IsActive:      true,
		SendChan:      make(chan []byte, 100), // 缓冲100条消息
		CloseChan:     make(chan bool, 1),
	}
}

// 更新客户端信息
func (c *ClientConnection) UpdateClientInfo(clientType, hostIP string) error {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	// 验证客户端类型
	if clientType != TYPE_APP && clientType != TYPE_ESP8266 {
		return NewServerError(ERR_INVALID_CLIENT_TYPE,"无效的客户端类型")
	}
	
	c.ClientType = clientType
	c.HostIP = hostIP
	return nil
}

// 更新心跳时间
func (c *ClientConnection) UpdateHeartbeat() {
	c.mutex.Lock()
	defer c.mutex.Unlock()
	if c.IsActive {
		c.LastHeartbeat = time.Now()
	}
}



// 关闭连接
func (c *ClientConnection) Close() error {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	if !c.IsActive {
		return nil // 已经关闭
	}

	c.IsActive = false
	
	// 发送关闭信号
	// select 会监听多个 case 中的通道操作（读 / 写），仅当某个 case 的通道操作可立即完成时，才执行该 case
	// 若所有 case 都无法立即完成，且有 default 分支，则执行 default（无 default 则阻塞）
	select {
	case c.CloseChan <- true: // 尝试向 CloseChan 发送 true
	default: // 空操作
	}

	// 关闭通道 标准库定义的函数用于关闭双向/可写通道
	close(c.SendChan)
	close(c.CloseChan)

	// 关闭网络连接
	return c.Conn.Close()
}


// 发送消息到客户端
func (c *ClientConnection) SendMessage(data []byte) error {
	if !c.IsActive {
		return NewServerError(ERR_CONNECTION_TIMEOUT,"连接已关闭")
	}

	select {
	case c.SendChan <- data:
		return nil
	default:
		return NewServerError(ERR_SEND_FAILED,"发送缓冲区已满")
	}

}

// 检查连接是否超时
func (c *ClientConnection) IsTimeout() bool {
	c.mutex.RLock()
	defer c.mutex.RUnlock()
	// 计算「当前时间」与 [传入的时间] 的时间差 返回值是 time.Duration 类型
	return time.Since(c.LastHeartbeat) > HEARTBEAT_TIMEOUT
}


// 获取连接信息字符串
func (c *ClientConnection) String() string {
	c.mutex.RLock()
	defer c.mutex.RUnlock()
	
	return fmt.Sprintf("Connection{ID:%s, Type:%s, HostIP:%s, RemoteAddr:%s, Active:%v}", 
		c.ID, c.ClientType, c.HostIP, c.RemoteAddr, c.IsActive)
}



