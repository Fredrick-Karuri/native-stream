package main

import (
	"fmt"
	"log"
	"net/http"
	"os"
)

func main() {
	addr := "127.0.0.1:8888"
	if len(os.Args) > 1 && os.Args[1] == "--help" {
		fmt.Println("NativeStream Server")
		fmt.Println("Usage: nativestream-server [--install-service] [--uninstall-service]")
		os.Exit(0)
	}

	log.Printf("NativeStream Server starting on http://%s", addr)
	if err := http.ListenAndServe(addr, nil); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}