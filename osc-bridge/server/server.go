package main

import "github.com/josephburnett/sonic-jam/osc-bridge/bootstrap"
import "github.com/josephburnett/sonic-jam/osc-bridge/bridge"
import "github.com/josephburnett/sonic-jam/osc-bridge/ui"

func main() {
	bootstrap.Boot()
	
	done := make(chan bool)

	go bridge.Serve(done)
	defer bridge.Shutdown()

	go ui.Serve(done)
	defer ui.Shutdown()
	
	<- done
}
