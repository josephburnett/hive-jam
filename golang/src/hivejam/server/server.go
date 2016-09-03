package main

import "hivejam/bootstrap"
import "hivejam/bridge"
import "hivejam/ui"

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

	log.Print("Hive Jam is up and running!")
	
	<- done
}
