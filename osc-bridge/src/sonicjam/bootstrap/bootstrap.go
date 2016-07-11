package bootstrap

import "sonicjam/common"
import "sonicjam/data"

import "log"
import "os"

import _ "github.com/jteeuwen/go-bindata"
import "github.com/scgolang/osc"

var oscClient = common.Connect("127.0.0.1", 4557)
var BootComplete = make(chan bool)

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

func fail(err, msg string) {
	log.Print(err)
	log.Print(msg)
	os.Exit(1)
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
			fail(err.Error(), "Have you started Sonic Pi?")
		}
	}
	for _, file := range files {
		err := upload(file)
		if err != nil {
			fail(err.Error(), "Error uploading file "+file)
		}
	}
	<- BootComplete
	log.Print("Bootstrapping complete.")
}
