package bridge

import "sonicjam/bootstrap"
import "sonicjam/common"
import "sonicjam/config"

import "encoding/json"
import "io"
import "log"
import "math/rand"
import "net/http"
import "strconv"
import "time"

import "golang.org/x/net/websocket"
import "github.com/josephburnett/osc"

type Params []string

type Message struct {
	Address string
	Params  Params
}

func Serve(done chan bool) {
	go sendToClient()
	go sendToServer()
	log.Print("Starting OSC server.")
	go serveOSC()
	http.Handle("/oscbridge", websocket.Handler(websocketHandler))
	endpoint := *config.Flags.UiIp+":"+strconv.Itoa(*config.Flags.UiBridgePort)
	log.Print("Starting websocket server on "+endpoint)
	err := http.ListenAndServe(endpoint, nil)
	if err != nil {
		panic("ListenAndServe: " + err.Error())
	}
	done <- true
}

func Shutdown() {
	log.Print("Shutting down OSC bridge.")
}

func (m *Message) Clone() *Message {
	p := make(Params, len(m.Params))
	for i,v := range m.Params {
		p[i] = v
	}
	return &Message{
		Address: m.Address,
		Params: p,
	}
}

var toServer = make(chan *Message, 10)
var toClient = make(chan *Message, 10)

var clients = make(map[string]*websocket.Conn)

var oscClient = common.Connect(*config.Flags.SpBridgeIp, *config.Flags.SpBridgePortClient)
var oscServer = common.Listen(*config.Flags.SpBridgeIp, *config.Flags.SpBridgePortServer)

func sendToClient() {
	for {
		msg := <-toClient
		if msg.Address == "" {
			common.LogError("Dropping message. No address.")
			continue
		}
		if len(msg.Params) == 0 {
			common.LogError("Dropping message. No client id.")
			continue
		}
		clientId := msg.Params[0]
		ws, ok := clients[clientId]
		if !ok {
			common.LogError("Dropping message. Unknown client id: " + clientId)
			continue
		}
		msg.Params = msg.Params[1:]
		msgJson, err := json.Marshal(msg)
		if err != nil {
			common.LogError("Error marshalling message to client.")
			common.LogError(err)
			continue
		}
		if _, err = ws.Write([]byte(msgJson)); err != nil {
			common.LogError("Error writing message to client websocket.")
			common.LogError(err)
			continue
		}
	}
}

func sendToServer() {
	for {
		msg := <-toServer
		if msg.Address == "" {
			common.LogError("Dropping message. No address.")
			continue
		}
		oscMessage, err := osc.NewMessage(msg.Address)
		if err != nil {
			common.LogError("Error create new OSC message to server.")
			common.LogError(err)
			continue
		}
		for _, p := range msg.Params {
			oscMessage.WriteString(p)
		}
		err = oscClient.Send(oscMessage)
		if err != nil {
			common.LogError("Error sending OSC message to server.")
			common.LogError(err)
			continue
		}
	}
}

func websocketHandler(ws *websocket.Conn) {
	clientId := randStringRunes(16)
	common.LogInfo("Connected client: " + clientId)
	clients[clientId] = ws
	for {
		msgJson := make([]byte, *config.Flags.WsBufferByteSize)
		n, err := ws.Read(msgJson)
		if err != nil {
			if err == io.EOF {
				common.LogInfo("Disconnected client: " + clientId)
				delete(clients, clientId)
				return
			}
			common.LogError("Error reading from client websocket.")
			common.LogError(err)
			continue
		}
		if n > *config.Flags.WsBufferByteSize-1 {
			common.LogError("Message from UI exceeded the websocket buffer size.")
		}
		common.LogInfo("Received from client " + clientId + ": " + string(msgJson[:n]))
		msg := &Message{}
		err = json.Unmarshal(msgJson[:n], msg)
		if err != nil {
			common.LogError("Error unmarshalling message from client.")
			common.LogError(err)
			continue
		}
		params := make([]string, 1)
		params[0] = clientId
		for _, p := range msg.Params {
			params = append(params, p)
		}
		msg.Params = params
		toServer <- msg
	}
}

func oscHandler(oscMsg *osc.Message) error {
	// https://github.com/josephburnett/sonic-jam/issues/28
	// "Debug flag causes panic"
	//
	// if *config.Flags.Debug {
	// 	// Check the debug flag value before pretty printing message
	// 	buf := new(bytes.Buffer)
	// 	oscMsg.Print(buf)
	// 	common.LogDebug("Received from server: " + buf.String())
	// }
	msg := &Message{
		Address: oscMsg.Address(),
	}
	for i := 0; i < oscMsg.CountArguments(); i++ {
		paramsJson, err := oscMsg.ReadString()
		if err != nil {
			common.LogError("Error reading string from OSC message.")
			common.LogError(err)
			return err
		}
		params := &Params{}
		err = json.Unmarshal([]byte(paramsJson), params)
		if err != nil {
			common.LogError("Error parsing OSC message as JSON.")
			common.LogError(err)
			return err
		}
		for _, param := range *params {
			msg.Params = append(msg.Params, param)
		}
	}
	if msg.Params[0] == "*" {
		for key := range clients {
			m := msg.Clone()
			m.Params[0] = key
			toClient <- m
		}
	} else {
		toClient <- msg
	}
	return nil
}

func bootHandler(oscMsg *osc.Message) error {
	bootstrap.BootComplete <- true
	return nil
}

func serveOSC() {
	dispatcher := make(map[string]osc.Method)
	dispatcher["/pong"] = oscHandler
	dispatcher["/state"] = oscHandler
	dispatcher["/samples"] = oscHandler
	dispatcher["/synths"] = oscHandler
	dispatcher["/errors"] = oscHandler
	dispatcher["/cursors"] = oscHandler
	dispatcher["/console"] = oscHandler
	dispatcher["/boot-complete"] = bootHandler
	for {
		err := oscServer.Serve(dispatcher)
		if err != nil {
			panic(err)
		}
	}
}

// http://stackoverflow.com/questions/22892120

func init() {
	rand.Seed(time.Now().UnixNano())
}

var letterRunes = []rune("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")

func randStringRunes(n int) string {
	b := make([]rune, n)
	for i := range b {
		b[i] = letterRunes[rand.Intn(len(letterRunes))]
	}
	return string(b)
}
