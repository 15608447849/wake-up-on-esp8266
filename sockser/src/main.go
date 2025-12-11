package main

import "fmt"
import "os"
import "time"

func main() {
	
	tcpServer := NewTCPServer(DEFAULT_PORT)

	// 启动服务器
	err := tcpServer.Start()
	if err != nil {
		fmt.Printf("启动服务器失败: %v \n", err)
		os.Exit(1)
	}
	
	// 等待服务器运行
	for tcpServer.isRunning {
		time.Sleep(1 * time.Second)
	}

}


