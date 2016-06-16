package main

import "github.com/josephburnett/sonic-jam/osc-bridge/bootstrap"
import "github.com/josephburnett/sonic-jam/osc-bridge/bridge"

func main() {
	bootstrap.Boot()
	bridge.Serve()
}
