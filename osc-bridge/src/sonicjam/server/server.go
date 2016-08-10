package main

import "sonicjam/bootstrap"
import "sonicjam/bridge"
import "sonicjam/ui"

import "flag"
import "log"

func main() {

	flag.Parse()
	
	done := make(chan bool)

	go bridge.Serve(done)
	defer bridge.Shutdown()

	bootstrap.Boot()

	go ui.Serve(done)
	defer ui.Shutdown()

	log.Print("Sonic Jam is up and running!")
	
	<- done
}
