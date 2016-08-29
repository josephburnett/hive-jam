package bootstrap

import "sonicjam/common"
import "sonicjam/config"
import "sonicjam/data"

import "log"
import "os"
import "time"

import _ "github.com/jteeuwen/go-bindata"
import "github.com/josephburnett/osc"

var oscClient = common.Connect(*config.Flags.SpIp, *config.Flags.SpPort)
var oscServer = common.Listen(*config.Flags.SpIp, *config.Flags.SpUiPort)

var BootComplete = make(chan bool)

func serveOSC() {
	dispatcher := make(map[string]osc.Method)
	dispatcher["/*"] = logHandler
	for {
		err := oscServer.Serve(dispatcher)
		if err != nil {
			panic(err)
		}
	}
}

func logHandler(oscMsg *osc.Message) error {
	common.LogInfo(oscMsg.Address())
	for i := 0; i < oscMsg.CountArguments(); i++ {
		arg, err := oscMsg.ReadString()
		if err != nil {
			common.LogError("Error reading string from Sonic Pi OSC message.")
		} else {
			common.LogInfo(arg)
		}
	}
	return nil
}

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
	if err != "" {
		log.Print(err)
	}
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
	timeout := make(chan bool, 1)
	go func() {
		time.Sleep(time.Duration(*config.Flags.BootstrapTimeoutSeconds) * time.Second)
		timeout <- true
	}()
	go serveOSC()
	time.Sleep(100 * time.Millisecond)
	for i := 0; i < 10; i++ {
		err := ping()
		if err != nil {
			fail(err.Error(), "Have you started Sonic Pi?")
		}
	}
	rubyConfig, err := config.RubyConfig()
	if err != nil {
		fail(err.Error(), "Error generating Ruby config")
	}
	err = send("/run-code", string(rubyConfig))
	if err != nil {
		fail(err.Error(), "Error uploading Ruby config")
	}
	for _, file := range files {
		// Give the files a moment to be interpretted.
		time.Sleep(100 * time.Millisecond)
		err := upload(file)
		if err != nil {
			fail(err.Error(), "Error uploading file "+file)
		}
	}
	select {
	case <- BootComplete:
	case <- timeout:
		fail("", "Timeout while waiting for Sonic Jam to bootstrap. Check Sonic Pi UI for errors.")
	}
	log.Print("Bootstrapping complete.")
}
