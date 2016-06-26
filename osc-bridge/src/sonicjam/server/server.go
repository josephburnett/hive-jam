package main

import "sonicjam/bootstrap"
import "sonicjam/bridge"
import "sonicjam/ui"

func main() {
	bootstrap.Boot()
	
	done := make(chan bool)

	go bridge.Serve(done)
	defer bridge.Shutdown()

	go ui.Serve(done)
	defer ui.Shutdown()
	
	<- done
}
