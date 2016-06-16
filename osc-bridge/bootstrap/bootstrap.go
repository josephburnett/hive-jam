package bootstrap

import "github.com/josephburnett/sonic-jam/osc-bridge/common"
import "github.com/scgolang/osc"
import "io/ioutil"
import "log"
import "time"

var oscClient = common.Connect("127.0.0.1", 4557)

func serverUp() bool {
	oscMessage, err := osc.NewMessage("/ping")
	if err != nil {
		panic(err)
	}
	oscMessage.WriteString("sonicjam/bootstrap")
	for i := 0; i < 10; i++ {
		err = oscClient.Send(oscMessage)
		if err != nil {
			log.Print(err)
			return false
		}
	}
	log.Print("Sonic Pi is up.")
	return true
}

func upload(filename string) {
	file, err := ioutil.ReadFile(filename)
	if err != nil {
		panic(err)
	}
	oscMessage, err := osc.NewMessage("/run-code")
	if err != nil {
		panic(err)
	}
	oscMessage.WriteString("sonicjam/bootstrap")
	oscMessage.WriteString(string(file))
	err = oscClient.Send(oscMessage)
	if err != nil {
		panic(err)
	}
}

func Boot() {
	for !serverUp() {
		log.Print("Have you started Sonic Pi?")
		time.Sleep(5 * time.Second)
	}
	upload("/Users/josephburnett/sonic-jam/sonic-pi/lib/sonicjam/state.rb")
	upload("/Users/josephburnett/sonic-jam/sonic-pi/lib/sonicjam/params.rb")
	upload("/Users/josephburnett/sonic-jam/sonic-pi/lib/sonicjam/dispatch.rb")
	upload("/Users/josephburnett/sonic-jam/sonic-pi/server.rb")
}
