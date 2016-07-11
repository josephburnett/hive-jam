package main

import "sonicjam/bootstrap"
import "sonicjam/bridge"
import "sonicjam/ui"

func main() {
	
	done := make(chan bool)

	go bridge.Serve(done)
	defer bridge.Shutdown()

	bootstrap.Boot()

	go ui.Serve(done)
	defer ui.Shutdown()
	
	<- done
}
