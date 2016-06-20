package bootstrap

import "github.com/josephburnett/sonic-jam/osc-bridge/common"
import "github.com/josephburnett/sonic-jam/osc-bridge/data"
import "github.com/scgolang/osc"
import "log"

var oscClient = common.Connect("127.0.0.1", 4557)

func send(address, message string) error {
	oscMessage, err := osc.NewMessage(address)
	if err != nil {
		return err
	}
	oscMessage.WriteString("sonicjam/bootstrap")
	if message != "" {
		oscMessage.WriteString(message)
	}
	err = oscClient.Send(oscMessage)
	if err != nil {
		return err
	}
	return nil
}

func ping() error {
	return send("/ping", "")
}

func upload(filename string) error {
	data, err := data.Asset(filename)
	if err != nil {
		return err
	}
	return send("/run-code", string(data))
}

var files = [...]string{
	"sonic-pi/lib/sonicjam/state.rb",
	"sonic-pi/lib/sonicjam/params.rb",
	"sonic-pi/lib/sonicjam/dispatch.rb",
	"sonic-pi/server.rb",
}

func Boot() {
	for i := 0; i < 10; i++ {
		err := ping()
		if err != nil {
			log.Print("Have you started Sonic Pi?")
			panic(err)
		}
	}
	for _, file := range files {
		err := upload(file)
		if err != nil {
			log.Print("Error uploading file ", file)
			panic(err)
		}
	}
}
