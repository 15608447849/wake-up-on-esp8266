package main

import (
	"fmt"
	"net"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"
)


 



// TCP服务器结构
type TCPServer struct {
	port              int                            // 监听端口
	listener          net.Listener                   // TCP监听器
	isRunning         bool                           // 服务器运行状态
	mutex             sync.RWMutex                   // 读写锁
	shutdownChan      chan bool                      // 关闭信号通道
	wg                sync.WaitGroup                 // 等待组，用于优雅关闭
	clientConnections 	      []*ClientConnection   		 // 客户端连接映射
}

// 创建新的TCP服务器
func NewTCPServer(port int) *TCPServer {
	return &TCPServer{
		port:              port,
		isRunning:         false,
		shutdownChan:      make(chan bool, 1),
		clientConnections: make([]*ClientConnection,0,10),
	}
}

// 清理连接
func (s *TCPServer) cleanupConnections() {
	s.mutex.RLock()
	defer s.mutex.RUnlock()

	var validConns []*ClientConnection

	for _, clientConn := range s.clientConnections {
		if clientConn.IsActive {
			 // 检查上一次心跳时间间隔
			if clientConn.IsTimeout() {
				// 停止连接
				clientConn.Close()
				continue
			}
			validConns = append(validConns, clientConn)
		}
	}

	s.clientConnections = validConns
}



// 处理系统信号
func (s *TCPServer) handleSignals() {
	defer s.wg.Done()// 延迟调用 当前协程退出时，通知WaitGroup完成一个任务
	s.wg.Add(1) // 给WaitGroup加1：标记有一个任务（监听关闭信号）正在执行

	// 创建缓冲为1的信号通道（避免信号发送时阻塞）
	sigChan := make(chan os.Signal, 1)
	// 将SIGINT（Ctrl+C）、SIGTERM（kill）信号转发到sigChan
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	// select 无 default 分支：确保 select 一直阻塞，直到收到关闭信号
	select {
	case <-sigChan: //  收到系统退出信号（Ctrl+C/kill）
		s.Stop()
	case <-s.shutdownChan: // 收到内部关闭信号
		
	}
}


// 定期清理非活跃连接和心跳超时检测
func (s *TCPServer) cleanupRoutine() {
	defer s.wg.Done()
	s.wg.Add(1)

	// 定时器 
	// ticker：是 *time.Ticker 类型的实例，包含一个只读通道 ticker.C
	// 定时器会每隔 d 时长向该通道发送当前时间（time.Time 类型）
	ticker := time.NewTicker(HEARTBEAT_CHECK_INTERVAL)
	defer ticker.Stop()// 延迟执行 停止定时器

	 

	for s.isRunning {
		select {
		case <-ticker.C:
			// 清理 心跳超时 和 无效的 连接
			s.cleanupConnections()
		case <-s.shutdownChan: // 收到服务关闭信号
			return // 退出循环，协程结束（defer 会执行 ticker.Stop()）
		}
	}
}



// 处理新连接
func (s *TCPServer) handleNewConnection(conn net.Conn) {
	defer s.wg.Done()
	s.wg.Add(1)

	// 创建客户端连接对象
	clientConn := NewClientConnection( conn)

	// 添加连接
	s.clientConnections = append(s.clientConnections, clientConn)


	// 启动连接处理协程 (serverHandler中)
	s.handleClientConnection(clientConn)
	 
}



// 接受客户端连接的主循环
func (s *TCPServer) acceptConnections() {
	defer s.wg.Done()
	s.wg.Add(1)

	for s.isRunning {
		// 设置接受连接的超时时间
		if tcpListener, ok := s.listener.(*net.TCPListener); ok {
			tcpListener.SetDeadline(time.Now().Add(1 * time.Second))
		}

		conn, err := s.listener.Accept()
		
		if err != nil {
			// 检查是否是超时错误
			if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
				continue // 超时继续循环
			}
			// 接受连接失败
			continue
		}

		fmt.Printf("新客户端连接 | 远程：%s | 本地：%s | 类型：%s \n",
    		conn.RemoteAddr(), 
			conn.LocalAddr(), 
			conn.RemoteAddr().Network(),
		)
		

		// 检查连接数限制
		if len(s.clientConnections) >= MAX_CONNECTIONS {
			fmt.Printf("拒绝连接, 超过服务器最大连接数(%d) \n", MAX_CONNECTIONS)
			conn.Close()
			continue
		}

		// 为新连接创建处理协程
		go s.handleNewConnection(conn)
	}
}


// 启动服务器
func (s *TCPServer) Start() error {
	s.mutex.Lock()
	defer s.mutex.Unlock()

	if s.isRunning {
		return NewServerError(ERR_CONNECTION_TIMEOUT,"服务器已在运行")
	}

	// 创建TCP监听器
	listener, err := net.Listen("tcp", fmt.Sprintf(":%d", s.port))
	if err != nil {
		return NewServerError(
			ERR_CONNECTION_TIMEOUT,
			fmt.Sprintf("启动TCP监听器失败,%v", err),
		)
	}

	s.listener = listener
	s.isRunning = true

	// 服务器启动成功
	fmt.Printf("服务器启动成功，监听端口 %d \n", s.port)


	// 启动信号处理协程
	go s.handleSignals()

	// 启动连接清理协程
	go s.cleanupRoutine()


	// 主循环：接受客户端连接
	go s.acceptConnections()

	return nil
}



// 停止服务器
func (s *TCPServer) Stop() error {
	s.mutex.Lock()
	defer s.mutex.Unlock()

	if !s.isRunning {
		return nil
	}

	s.isRunning = false

	// 发送关闭信号
	select {
	case s.shutdownChan <- true:
	default:
	}

	// 关闭监听器
	if s.listener != nil {
		s.listener.Close()
	}

	// 等待所有协程结束
	done := make(chan bool, 1)
	go func() {
		s.wg.Wait()
		done <- true
	}()

	// 设置超时时间
	select {
	case <-done:
		// 服务器已优雅关闭
	case <-time.After(10 * time.Second):
		// 服务器关闭超时，强制退出
	}

	return nil
}