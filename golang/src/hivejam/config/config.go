package config

import "bytes"
import "flag"
import "reflect"
import "strconv"
import "text/template"

var Flags = struct {
	UiIp *string
	UiExternalIp *string
	UiPort *int
	UiBridgePort *int
	UiAudioPort *int
	SpBridgeIp *string
	SpBridgePortServer *int
	SpBridgePortClient *int
	SpIp *string
	SpPort *int
	WsBufferByteSize *int
	Resolution *string
	StateFile *string
	Verbose *bool
	Debug *bool
	MaxTrackCount *int
	BootstrapTimeoutSeconds *int
	NomsDataset *string
}{
	flag.String("ui_ip", "127.0.0.1", "IP address for the UI server to bind to"),
	flag.String("ui_external_ip", "127.0.0.1", "IP address for the UI client to connect the websocket to"),
	flag.Int("ui_port", 80, "port number for the UI server to bind to"),
	flag.Int("ui_bridge_port", 4550, "port number for the UI end of the OSC bridge to bind to"),
	flag.Int("ui_audio_port", 8000, "port number for the streaming audio server"),
	flag.String("sp_bridge_ip", "127.0.0.1", "IP address for the Sonic Pi end of the OSC bridge to bind to"),
	flag.Int("sp_bridge_port_server", 4560, "port number for the Sonic Pi end of the OSC bridge to bind to for receiving messages"),
	flag.Int("sp_bridge_port_client", 4559, "port number for the Sonic Pi end of the OSC bridge to bind to for transmitting messages"),
	flag.String("sp_ip", "127.0.0.1", "IP address for the Sonic Pi server"),
	flag.Int("sp_port", 4557, "port number for the Sonic Pi server"),
	flag.Int("ws_buffer_byte_size", 10000, "the size in bytes of the UI websocket server message buffer"),
	flag.String("resolution", "1/4", "beat resolution of the Hive Jam server"),
	flag.String("state_file", "", "path to file in which to persist Hive Jam server state"),
	flag.Bool("verbose", false, "verbose output logging"),
	flag.Bool("debug", false, "debug level output logging (overrides --verbose)"),
	flag.Int("max_flag_count", 6, "maximum number of tracks allowed per grid"),
	flag.Int("bootstrap_timeout_millis", 1000, "timeout in milliseconds for bootstrapping files into Sonic Pi"),
	flag.String("noms_dataset", "http://localhost:8001::hivejam", "URL to the Noms state dataset"),
}

type flagType int
const (
	stringFlag flagType = iota
	intFlag flagType = iota
	boolFlag flagType = iota
)

type flagValue struct {
	Type flagType
	String string
	Int int
	Bool bool
}

func (f flagValue) Js() string {
	return renderFlag(f)
}

func (f flagValue) Ruby() string {
	return renderFlag(f)
}

func flagMap() map[string]flagValue {
	t := reflect.TypeOf(Flags)
	v := reflect.ValueOf(Flags)
	values := make(map[string]flagValue)
	for i := 0; i < t.NumField(); i++ {
		key := t.Field(i).Name
		value := v.Field(i).Elem()
		switch value.Kind() {
		case reflect.String:
			values[key] = flagValue{ Type: stringFlag, String: value.String() }
		case reflect.Int:
			values[key] = flagValue{ Type: intFlag, Int: int(value.Int()) }
		case reflect.Bool:
			values[key] = flagValue{ Type: boolFlag, Bool: value.Bool() }
		default:
			panic("Unsupported flag type: " + value.Elem().String())
		}
	}
	return values
}

func renderFlag(f flagValue) string {
	switch f.Type {
	case stringFlag:
		return "\"" + f.String + "\""
	case intFlag:
		return strconv.Itoa(f.Int)
	case boolFlag:
		if f.Bool {
			return "true"
		} else {
			return "false"
		}
	}
	panic("Unknown flag type.")
}

const jsConfig = `
HJ_CONFIG = {
  {{range $key, $value := .}}
  {{$key}}: {{$value.Js}},
  {{end}}
}
`
const rubyConfig = `
define :_hj_config do
  {
    {{range $key, $value := .}}
    {{$key}}: {{$value.Ruby}},
    {{end}}
  }
end
`

// JavaScript and Ruby happen to have the same syntax
func render(templateString string) ([]byte, error) {
	var b bytes.Buffer
	t, err := template.New("template").Parse(templateString)
	if err != nil {
		return nil, err
	}
	values := flagMap()
	err = t.Execute(&b, values)
	if err != nil {
		return nil, err
	}
	return b.Bytes(), nil
}

func JsConfig() ([]byte, error) {
	return render(jsConfig)
}

func RubyConfig() ([]byte, error) {
	return render(rubyConfig)
}
